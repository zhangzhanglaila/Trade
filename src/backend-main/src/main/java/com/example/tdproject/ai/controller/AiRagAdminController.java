package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.http.LlmClient;
import com.example.tdproject.ai.rag.NewsRagService;
import com.example.tdproject.ai.rag.NewsVectorStore;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.service.TNewsCorpusService;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 向量索引管理。
 *
 * <p>全量建索引需要调用外部嵌入接口，2.8 万条语料通常要几分钟，
 * 因此 <code>/full</code> 改为后台异步执行，用 <code>/status</code> 查询进度，
 * 避免 HTTP 请求长时间挂起被网关或前端超时打断。</p>
 */
@Slf4j
@RestController
@RequestMapping("/ai/rag/index")
@RequiredArgsConstructor
public class AiRagAdminController {

    private final TNewsCorpusService newsCorpusService;
    private final NewsRagService newsRagService;
    private final NewsVectorStore vectorStore;
    private final LlmClient llmClient;

    private final AtomicBoolean indexing = new AtomicBoolean(false);
    private volatile String lastMessage = "尚未执行过建索引";
    private volatile long lastFinishedAt = 0L;

    /** 后台全量建索引。limit 可选，用于先小批量试跑。 */
    @PostMapping("/full")
    public Result<String> full(@RequestParam(required = false) Integer limit) {
        if (!llmClient.isEmbeddingConfigured()) {
            return Result.build(ResultCodeEnum.SERVICE_ERROR,
                    "嵌入模型未配置，无法建索引。请检查 config/.env 的 SILICONFLOW_BASE_URL / SILICONFLOW_API_KEY "
                            + "以及 application.yaml 的 ai.llm.embedding 配置。");
        }
        if (!indexing.compareAndSet(false, true)) {
            return Result.build("已有建索引任务正在执行中，请稍后用 GET /ai/rag/index/status 查看进度");
        }

        Thread worker = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                // 用流式读取 + 分页，避免一次性把 2.8 万行语料（含长正文）读进内存并逐行打日志
                int done = newsRagService.indexAll(limit == null ? 0 : limit);
                lastMessage = "完成：成功 " + done + " 条，向量库现有 " + newsRagService.indexedCount()
                        + " 条，耗时 " + (System.currentTimeMillis() - t0) / 1000 + " s";
                log.info("[建索引] {}", lastMessage);
            } catch (Exception e) {
                lastMessage = "失败：" + e.getMessage();
                log.error("[建索引] 失败", e);
            } finally {
                lastFinishedAt = System.currentTimeMillis();
                indexing.set(false);
            }
        }, "rag-index-full");
        worker.setDaemon(true);
        worker.start();

        return Result.build("已在后台开始建索引"
                + (limit != null ? "（limit=" + limit + "）" : "（全量 " + newsCorpusService.count() + " 条）")
                + "，请用 GET /ai/rag/index/status 查看进度");
    }

    /** 建索引进度与向量库概况。 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("indexing", indexing.get());
        m.put("message", lastMessage);
        m.put("lastFinishedAt", lastFinishedAt == 0 ? null : lastFinishedAt);
        m.put("vectorCount", vectorStore.count());
        m.put("embeddingConfigured", llmClient.isEmbeddingConfigured());
        m.put("chatConfigured", llmClient.isChatConfigured());
        m.put("chatModel", llmClient.chatDescription());
        return Result.build(m);
    }

    @PostMapping("/incremental")
    public Result<String> incremental(@RequestParam(required = false) Long fromId,
                                      @RequestParam(required = false) Long toId) {
        try {
            vectorStore.ensureCollection(false);
            List<TNewsCorpus> list;
            if (fromId == null && toId == null) {
                list = newsCorpusService.list();
            } else {
                var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TNewsCorpus>();
                if (fromId != null) qw.ge("id", fromId);
                if (toId != null) qw.le("id", toId);
                list = newsCorpusService.list(qw);
            }
            int ok = 0;
            for (TNewsCorpus c : list) {
                try {
                    newsRagService.indexOne(c);
                    ok++;
                } catch (Exception e) {
                    log.warn("索引 newsId={} 失败: {}", c == null ? null : c.getId(), e.getMessage());
                }
            }
            return Result.build("OK, indexed count=" + ok + "/" + list.size());
        } catch (Exception e) {
            log.error("/ai/rag/index/incremental 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/{newsId}")
    public Result<String> delete(@PathVariable Long newsId) {
        try {
            newsRagService.deleteIndex(newsId);
            return Result.build("OK");
        } catch (Exception e) {
            log.error("/ai/rag/index/{} 异常", newsId, e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @PostMapping("/recreate")
    public Result<String> recreate() {
        try {
            vectorStore.ensureCollection(true);
            lastMessage = "已清空向量索引";
            return Result.build("OK, 向量索引已清空");
        } catch (Exception e) {
            log.error("/ai/rag/index/recreate 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }
}

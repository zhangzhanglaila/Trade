package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.rag.MilvusNewsVectorStore;
import com.example.tdproject.ai.rag.NewsRagService;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.service.TNewsCorpusService;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai/rag/index")
@RequiredArgsConstructor
public class AiRagAdminController {

    private final TNewsCorpusService newsCorpusService;
    private final NewsRagService newsRagService;
    private final MilvusNewsVectorStore vectorStore;

    @PostMapping("/full")
    public Result<String> full() {
        try {
            vectorStore.ensureCollection(false);
            List<TNewsCorpus> all = newsCorpusService.list();
            newsRagService.indexFull(all);
            return Result.build("OK, indexed count=" + all.size());
        } catch (Exception e) {
            log.error("/ai/rag/index/full 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @PostMapping("/incremental")
    public Result<String> incremental(@RequestParam(required = false) Long fromId,
                                      @RequestParam(required = false) Long toId) {
        try {
            vectorStore.ensureCollection(false);

            // 这里先做最简单版本：按 ID 范围过滤。
            // 若后续需要按 publishTime 增量，可再扩展 queryWrapper。
            List<TNewsCorpus> list;
            if (fromId == null && toId == null) {
                list = newsCorpusService.list();
            } else {
                var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TNewsCorpus>();
                if (fromId != null) qw.ge("id", fromId);
                if (toId != null) qw.le("id", toId);
                list = newsCorpusService.list(qw);
            }

            for (TNewsCorpus c : list) {
                newsRagService.indexOne(c);
            }

            return Result.build("OK, indexed count=" + list.size());
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
            return Result.build("OK");
        } catch (Exception e) {
            log.error("/ai/rag/index/recreate 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }
}

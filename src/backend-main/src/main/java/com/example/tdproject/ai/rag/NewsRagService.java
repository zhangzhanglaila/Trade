package com.example.tdproject.ai.rag;

import com.example.tdproject.ai.dto.RagHit;
import com.example.tdproject.ai.http.DashScopeClient;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.service.TNewsCorpusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRagService {

    private final DashScopeClient dashScopeClient;
    private final MilvusNewsVectorStore vectorStore;
    private final TNewsCorpusService newsCorpusService;

    public RagAnswer answer(String question, String country, Integer year) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        // 1) embedding
        List<Double> vec = dashScopeClient.embed(question);
        List<Float> q = vec.stream().map(Double::floatValue).collect(Collectors.toList());

        // 2) search
        List<Map<String, Object>> hits = vectorStore.search(q, country, year);
        List<Long> ids = hits.stream()
                .map(h -> (Long) h.get("newsId"))
                .collect(Collectors.toList());

        // 3) mysql 回查补全
        List<TNewsCorpus> corpusList = ids.isEmpty() ? List.of() : newsCorpusService.listByIds(ids);
        Map<Long, TNewsCorpus> byId = corpusList.stream().collect(Collectors.toMap(TNewsCorpus::getId, x -> x));

        List<RagHit> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Long id = (Long) hits.get(i).get("newsId");
            Double score = (Double) hits.get(i).get("score");
            TNewsCorpus c = byId.get(id);
            if (c == null) continue;

            sources.add(RagHit.builder()
                    .newsId(id)
                    .title(c.getNewsTitle())
                    .source(c.getNewsSource())
                    .publishTime(c.getPublishTime())
                    .score(score)
                    .build());

            // 简单截断，防止上下文过长
            String content = c.getNewsContent();
            if (content == null) content = "";
            if (content.length() > 800) {
                content = content.substring(0, 800);
            }

            context.append("【ID:").append(id).append(" | ")
                    .append(c.getPublishTime()).append(" | ")
                    .append(c.getNewsSource()).append("】\n")
                    .append("标题：").append(c.getNewsTitle()).append("\n")
                    .append("内容：").append(content).append("\n\n");
        }

        if (sources.isEmpty()) {
            return RagAnswer.builder()
                    .answer("在当前新闻语料中未检索到与问题高度相关的内容。你可以换个关键词，或先执行 /ai/rag/index/full 构建向量索引后再试。")
                    .sources(List.of())
                    .build();
        }

        // 4) 生成回答
        String system = "你是外贸新闻问答助手。\n"
                + "你只能基于我提供的【新闻片段】回答问题，禁止编造。\n"
                + "回答中必须标注引用来源，格式例如：(来源ID=123)。\n"
                + "如果无法从片段得出结论，请明确说明。";

        String user = "问题：" + question + "\n\n【新闻片段】\n" + context;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user));

        String answer = dashScopeClient.chat(messages, Map.of("temperature", 0.2));

        return RagAnswer.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    public void indexFull(List<TNewsCorpus> allNews) {
        vectorStore.ensureCollection(false);
        for (TNewsCorpus c : allNews) {
            indexOne(c);
        }
    }

    public void indexOne(TNewsCorpus c) {
        if (c == null || c.getId() == null) return;
        String text = (c.getNewsTitle() == null ? "" : c.getNewsTitle()) + "\n" + (c.getNewsContent() == null ? "" : c.getNewsContent());
        List<Double> vec = dashScopeClient.embed(text);
        List<Float> emb = vec.stream().map(Double::floatValue).collect(Collectors.toList());
        vectorStore.upsert(c.getId(), emb, c.getCountry(), c.getYear());
    }

    public void deleteIndex(Long newsId) {
        vectorStore.deleteByNewsId(newsId);
    }
}

package cait.collector.web.service;

import cait.collector.common.model.es.NewsContentEsEntity;
import cait.collector.web.model.response.PagingData;
import co.elastic.clients.elasticsearch._types.SortOptionsBuilders;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Slf4j
@Service
public class NewsContentService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    public NewsContentService(ElasticsearchTemplate elasticsearchTemplate) {
        this.elasticsearchTemplate = elasticsearchTemplate;
    }

    public PagingData<NewsContentEsEntity> list(PagingData<NewsContentEsEntity> pagingData) {
        var query = this.buildSortedNativeQuery(pagingData);
        log.info("query: {}", query.getQuery());
        return this.queryEs(query, pagingData);
    }

    public PagingData<NewsContentEsEntity> search(String keyword, PagingData<NewsContentEsEntity> pagingData) {
        var query = QueryBuilders.match(m -> m.field("content").query(keyword));
        return this.queryEs(this.buildSortedNativeQuery(query, pagingData), pagingData);
    }

    private NativeQuery buildSortedNativeQuery(Query query, PagingData<?> pagingData) {
        return NativeQuery.builder()
                .withPageable(PageRequest.of((int) pagingData.getCurrentPage() - 1, (int) pagingData.getPageSize()))
                .withSort(SortOptionsBuilders.field(f -> f.field("_score").order(SortOrder.Desc)))
                .withQuery(query)
                .build();
    }

    private NativeQuery buildSortedNativeQuery(PagingData<?> pagingData) {
        return NativeQuery.builder()
                .withPageable(PageRequest.of((int) pagingData.getCurrentPage() - 1, (int) pagingData.getPageSize()))
                .withSort(SortOptionsBuilders.field(f -> f.field("createTime").order(SortOrder.Desc)))
                .withQuery(Query.of(f -> f.queryString(q -> q.query("*"))))
                .build();
    }

    private PagingData<NewsContentEsEntity> queryEs(NativeQuery nativeQuery, PagingData<NewsContentEsEntity> pagingData) {
        var result = elasticsearchTemplate.search(nativeQuery, NewsContentEsEntity.class);
        var searchHits = result.getSearchHits();

        var courseEsEntityList = new ArrayList<NewsContentEsEntity>(searchHits.size());
        for (var searchHit : searchHits) {
            courseEsEntityList.add(searchHit.getContent());
        }

        pagingData.setTotalResult(result.getTotalHits());
        pagingData.setTotalPage(result.getTotalHits() / pagingData.getPageSize());
        pagingData.setData(courseEsEntityList);

        return pagingData;
    }

}

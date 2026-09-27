package cait.collector.es;

import cait.collector.common.model.es.NewsContentEsEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsContentEsRepository extends ElasticsearchRepository<NewsContentEsEntity, String> {
}

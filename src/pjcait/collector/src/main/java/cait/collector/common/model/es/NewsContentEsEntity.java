package cait.collector.common.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "news_content")
public class NewsContentEsEntity {

    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Keyword)
    private String contentId;

    @Field(type = FieldType.Keyword)
    private String taskId;

    private String title;

    private String content;

    private Date createTime;

}

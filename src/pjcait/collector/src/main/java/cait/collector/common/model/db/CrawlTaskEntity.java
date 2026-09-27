package cait.collector.common.model.db;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("page_crawl_task")
public class CrawlTaskEntity {

    @TableId
    private String id;

    private Integer crawlType;

    private Integer source;

    private String urls;

    private Boolean cleanHtml;

    private Boolean archive;

    private String additionalInfo;

    private String message;

    private Date createTime;

    private Date updateTime;

    private Integer status;
}

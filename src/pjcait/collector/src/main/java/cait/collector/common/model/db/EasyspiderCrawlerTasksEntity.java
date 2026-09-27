package cait.collector.common.model.db;

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
@TableName("easyspider_crawler_tasks")
public class EasyspiderCrawlerTasksEntity {

    private Long id;

    private String name;

    private String taskId;

    private String extraParams;

    private Date createTime;

    private Date updateTime;

    private Integer status;

}

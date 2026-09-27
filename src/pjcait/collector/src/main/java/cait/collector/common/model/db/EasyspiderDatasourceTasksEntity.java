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
@TableName("easyspider_datasource_tasks")
public class EasyspiderDatasourceTasksEntity {

    private Long id;

    private String name;

    private String taskId;

    private String url;

    /**
     * 指示了获取到页面url之后应当用什么方法爬取页面内容
     */
    private Integer crawlMethod;

    private String extraParams;

    private Date createTime;

    private Date updateTime;

    private Integer status;

}

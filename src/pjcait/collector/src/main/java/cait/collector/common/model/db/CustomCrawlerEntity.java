package cait.collector.common.model.db;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("custom_crawler")
public class CustomCrawlerEntity {

    private Long id;

    private String name;

    private String reqUrl;

    private String params;

    private Date createTime;

    private Date updateTime;

    private Integer status;

}

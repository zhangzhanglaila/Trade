package cait.collector.web.model.request;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Map;

@Data
public class CustomCrawlerRequest {

    private String name;

    private String reqUrl;

    private Map<String, String> params;

    private int status;

}

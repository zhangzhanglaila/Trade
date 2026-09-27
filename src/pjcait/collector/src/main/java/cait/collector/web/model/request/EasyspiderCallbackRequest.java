package cait.collector.web.model.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EasyspiderCallbackRequest {

    private String url;

    private List<String> result;

    private Map<String, String> data;

}

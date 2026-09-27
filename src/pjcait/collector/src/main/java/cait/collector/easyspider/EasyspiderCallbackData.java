package cait.collector.easyspider;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EasyspiderCallbackData {

    private String url;

    private List<String> result;

    private Map<String, String> data;

}

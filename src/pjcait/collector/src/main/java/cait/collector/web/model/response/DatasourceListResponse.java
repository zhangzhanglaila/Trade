package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DatasourceListResponse {

    private List<CustomDatasourceListResponse> custom;

    private List<EasyspiderDatasourceListResponse> easyspider;

    @Data
    @Builder
    public static class GDELTDatasourceListResponse {
        private boolean enabled;
    }

    private GDELTDatasourceListResponse gdelt;
}

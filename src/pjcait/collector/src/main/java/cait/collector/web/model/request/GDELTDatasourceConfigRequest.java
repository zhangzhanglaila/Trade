package cait.collector.web.model.request;

import lombok.Data;

@Data
public class GDELTDatasourceConfigRequest {
    private Boolean enable;
    private Boolean cleanHtml;
}

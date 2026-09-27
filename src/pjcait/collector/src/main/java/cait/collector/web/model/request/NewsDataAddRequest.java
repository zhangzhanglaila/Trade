package cait.collector.web.model.request;

import lombok.Data;

import java.util.List;

@Data
public class NewsDataAddRequest {
    private List<String> contents;
}

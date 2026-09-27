package cait.collector.job.service;

import cait.common.pb.CollectorPb;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DatasourceProvider {
    public static class C {}

    public List<CollectorPb.PageCrawlTask> getDatasource() {
        return Collections.emptyList();
    }
}

package cait.collector.web.api.v1;

import cait.collector.messaging.CrawlTaskPublisher;
import cait.collector.web.model.request.PutCrawlUrlRequest;
import cait.collector.web.model.response.Response;
import cait.common.pb.CollectorPb;
import com.github.yitter.idgen.YitIdHelper;
import org.apache.commons.collections4.ListUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/collector")
public class CollectorController {
    private final CrawlTaskPublisher crawlTaskPublisher;

    public CollectorController(CrawlTaskPublisher crawlTaskPublisher) {
        this.crawlTaskPublisher = crawlTaskPublisher;
    }

    @PostMapping("/put-urls")
    public Response<Object> runJob(@RequestBody PutCrawlUrlRequest request) {
        var urls = request.getUrls();
        var crawlType = CollectorPb.CrawlType.forNumber(request.getCrawlType());
        if (crawlType == null) {
            crawlType = CollectorPb.CrawlType.CrawlTypePageRaw;
        }

        var taskPbList = generateTaskPb(urls, crawlType, request);
        var idList = new ArrayList<String>(taskPbList.size());
        for (var taskPb : taskPbList) {
            idList.add(taskPb.getId());
            crawlTaskPublisher.publishCrawlJob(taskPb);
        }

        return Response.success(idList);
    }

    private List<CollectorPb.PageCrawlTask> generateTaskPb(
            List<String> urls, CollectorPb.CrawlType crawlType, PutCrawlUrlRequest request) {
        if (urls.isEmpty()) {
            return List.of();
        }

        var additionalInfo = request.getAdditionalInfo();
        var cleanHtml = request.isCleanHtml();
        var commitPublish = request.isCommitPublish();

        if (additionalInfo == null) {
            additionalInfo = new HashMap<>();
        }

        if (urls.size() <= 10) {
            var id = String.valueOf(YitIdHelper.nextId());
            return List.of(CollectorPb.PageCrawlTask.newBuilder()
                    .setId(id)
                    .setSource(CollectorPb.Source.SourceManualAdd)
                    .addAllUrl(urls)
                    .setArchive(true)
                    .setCrawlType(crawlType)
                    .setCleanHtml(cleanHtml)
                    .setCommitPublish(commitPublish)
                    .putAllAdditionalInfo(additionalInfo)
                    .build()
            );
        }

        // 分组，10个为一组
        var taskList = new ArrayList<CollectorPb.PageCrawlTask>(urls.size() / 10);
        var partitions = ListUtils.partition(urls, 10);
        for (var partition : partitions) {
            var id = String.valueOf(YitIdHelper.nextId());
            taskList.add(CollectorPb.PageCrawlTask.newBuilder()
                    .setId(id)
                    .setSource(CollectorPb.Source.SourceManualAdd)
                    .addAllUrl(partition)
                    .setArchive(true)
                    .setCrawlType(crawlType)
                    .setCleanHtml(true)
                    .putAllAdditionalInfo(additionalInfo)
                    .build());
        }

        return taskList;
    }
}

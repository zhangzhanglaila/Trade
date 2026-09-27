package cait.collector.web.api.v1;

import cait.collector.common.Constants;
import cait.collector.common.model.EnumValues;
import cait.collector.messaging.CrawlTaskPublisher;
import cait.collector.web.model.request.CustomDatasourceCallbackRequest;
import cait.collector.web.model.response.Response;
import cait.common.pb.CollectorPb;
import com.github.yitter.idgen.YitIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/datasource/custom")
public class CustomDatasourceCallbackController {

    private final CrawlTaskPublisher crawlTaskPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    public CustomDatasourceCallbackController(CrawlTaskPublisher crawlTaskPublisher, StringRedisTemplate stringRedisTemplate) {
        this.crawlTaskPublisher = crawlTaskPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostMapping("/callback")
    public Response<?> callback(@RequestParam("callback_id") String callbackId,
                                @RequestBody CustomDatasourceCallbackRequest request
    ) {
        if (request.getCode() != 0) {
            log.warn("callback error: {} {}", request.getCode(), request.getMsg());
            return Response.success();
        }

        var key = Constants.Redis.CustomDatasourceCallbackInfo.formatted(callbackId);
        var crawlMethodStr = stringRedisTemplate.opsForHash().get(key, "crawlMethod");
        try {
            if (crawlMethodStr == null) {
                return Response.error(-1, "no callback id found: " + callbackId);
            }

            var crawlMethod = Integer.parseInt(crawlMethodStr.toString());
            var data = request.getData();
            var urlList = data.getResult().stream().map(CustomDatasourceCallbackRequest.ResultItem::getLink).toList();
            var task = makeTaskPb(
                    urlList, CollectorPb.Source.SourceCustom, crawlMethod, data.getMetadata()
            );

            if (task.getCommitPublish()) {
                crawlTaskPublisher.publishCrawlJob(task);
            }
        } catch (Exception e) {
            log.error("error when handle callback", e);
        }

        return Response.success();
    }

    private CollectorPb.PageCrawlTask makeTaskPb(
            List<String> urlList, CollectorPb.Source source, int crawlMethod, Map<String, String> metadata
    ) {
        CollectorPb.CrawlType crawlType = switch (crawlMethod) {
            case EnumValues.CrawlMethod.PageRaw -> CollectorPb.CrawlType.CrawlTypePageRaw;
            case EnumValues.CrawlMethod.Selenium -> CollectorPb.CrawlType.CrawlTypeSelenium;
            case EnumValues.CrawlMethod.Adaptor -> CollectorPb.CrawlType.CrawlTypeAdaptor;
            case EnumValues.CrawlMethod.Easyspider -> CollectorPb.CrawlType.CrawlTypeEasySpider;
            case EnumValues.CrawlMethod.Custom -> CollectorPb.CrawlType.CrawlTypeCustom;
            default -> CollectorPb.CrawlType.CrawlTypePageRaw;
        };

        return CollectorPb.PageCrawlTask.newBuilder()
                .setId(String.valueOf(YitIdHelper.nextId()))
                .setCrawlType(crawlType)
                .addAllUrl(urlList)
                .setCleanHtml(true)
                .setArchive(true)
                .setSource(source)
                .putAllAdditionalInfo(metadata)
                .setCommitPublish(true)
                .build();
    }
}

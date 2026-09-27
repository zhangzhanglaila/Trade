package cait.collector.web.api.v1;

import cait.collector.common.model.es.NewsContentEsEntity;
import cait.collector.es.NewsContentEsRepository;
import cait.collector.messaging.ProcessorTaskPublisher;
import cait.collector.web.model.request.NewsDataAddRequest;
import cait.collector.web.model.response.NewsContentResponse;
import cait.collector.web.model.response.PagingData;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.NewsContentService;
import cait.common.pb.ProcessorPb;
import com.github.yitter.idgen.YitIdHelper;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;

@RestController
@RequestMapping("/data")
public class NewsDataController {
    private final ProcessorTaskPublisher processorTaskPublisher;
    private final NewsContentEsRepository newsContentEsRepository;
    private final NewsContentService newsContentService;

    public NewsDataController(ProcessorTaskPublisher processorTaskPublisher,
                              NewsContentEsRepository newsContentEsRepository,
                              NewsContentService newsContentService) {
        this.processorTaskPublisher = processorTaskPublisher;
        this.newsContentEsRepository = newsContentEsRepository;
        this.newsContentService = newsContentService;
    }

    @PostMapping("/put")
    public Response<String> putData(@RequestBody NewsDataAddRequest request) {
        // todo 还有存新闻储据
        var id = String.valueOf(YitIdHelper.nextId());
        var contents = request.getContents();
        var contentsEsIdList = new ArrayList<String>(contents.size());

        for (var content : contents) {
            var contentId = DigestUtils.md5Hex(content);
            var esEntity = NewsContentEsEntity.builder()
                    .id(contentId)
                    .contentId(contentId)
                    .taskId(id)
                    .title("")
                    .content(content)
                    .createTime(new Date())
                    .build();
            newsContentEsRepository.save(esEntity);
            contentsEsIdList.add(contentId);
        }

        var task = ProcessorPb.NewsProcessMessage.newBuilder()
                .setId(id)
                .addAllContentKeys(contentsEsIdList)
                .setStorageType(ProcessorPb.ContentFileStorageType.StorageTypeEs)
                .build();

        processorTaskPublisher.publishJobProcessingMsg(task);

        return Response.success(id);
    }

    @RequestMapping("/search")
    public Response<PagingData<NewsContentResponse>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        var pagedResult = newsContentService.search(keyword, new PagingData<>(page, pageSize));
        var resultList = pagedResult.getData();

        var respList = resultList.stream().map(this::convert).toList();
        var resp = new PagingData<>(pagedResult, respList);

        return Response.success(resp);
    }

    @RequestMapping("/list")
    public Response<PagingData<NewsContentResponse>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        var pagedResult = newsContentService.list(new PagingData<>(page, pageSize));
        var resultList = pagedResult.getData();

        var respList = resultList.stream().map(this::convert).toList();
        var resp = new PagingData<>(pagedResult, respList);

        return Response.success(resp);
    }

    private NewsContentResponse convert(NewsContentEsEntity entity) {
        return NewsContentResponse.builder()
        		.id(entity.getId())
        		.contentId(entity.getContentId())
        		.taskId(entity.getTaskId())
        		.title(entity.getTitle())
        		.content(entity.getContent())
        		.createTime(entity.getCreateTime())
        		.build();
    }
}

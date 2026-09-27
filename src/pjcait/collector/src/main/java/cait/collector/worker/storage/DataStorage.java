package cait.collector.worker.storage;

import cait.collector.common.model.es.NewsContentEsEntity;
import cait.collector.configure.CaitCollectorConfiguration;
import cait.collector.es.NewsContentEsRepository;
import com.github.yitter.idgen.YitIdHelper;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;

@Service
public class DataStorage {

    private final CaitCollectorConfiguration caitCollectorConfiguration;

    private final NewsContentEsRepository newsContentEsRepository;

    public DataStorage(CaitCollectorConfiguration caitCollectorConfiguration,
                       NewsContentEsRepository newsContentEsRepository) {
        this.caitCollectorConfiguration = caitCollectorConfiguration;

        this.newsContentEsRepository = newsContentEsRepository;
    }

    @Data
    @Builder
    public static class StorageResult {

        private String dataKey;

        private String storageType;

    }

    public StorageResult storage(String groupId, String contentId, byte[] content) throws IOException {
        return switch (caitCollectorConfiguration.getDataStorageType()) {
            case "fs" -> {
                var dataKey = fsStorage(groupId, contentId, content);
                yield StorageResult.builder().dataKey(dataKey).storageType("fs").build();
            }
            case "es" -> {
                var dataKey = esStorage(groupId, contentId, new String(content));
                yield StorageResult.builder().dataKey(dataKey).storageType("es").build();
            }
            default -> throw new IllegalStateException("Unexpected value: " + caitCollectorConfiguration.getDataStorageType());
        };
    }

    public StorageResult storage(String groupId, String contentId, String content) throws IOException {
        return switch (caitCollectorConfiguration.getDataStorageType()) {
            case "fs" -> {
                var dataKey = fsStorage(groupId, contentId, content.getBytes(StandardCharsets.UTF_8));
                yield StorageResult.builder().dataKey(dataKey).storageType("fs").build();
            }
            case "es" -> {
                var dataKey = esStorage(groupId, contentId, content);
                yield StorageResult.builder().dataKey(dataKey).storageType("es").build();
            }
            default -> throw new IllegalStateException("Unexpected value: " + caitCollectorConfiguration.getDataStorageType());
        };
    }

    private String fsStorage(String groupId, String contentId, byte[] content) throws IOException {
        var filePath = Path.of(caitCollectorConfiguration.getDataStorageLocation(), groupId, contentId);
        Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return filePath.toAbsolutePath().toString();
    }

    private String esStorage(String groupId, String contentId, String content) {
        var newsContentEsEntity = new NewsContentEsEntity();
        var id = String.valueOf(YitIdHelper.nextId());

        newsContentEsEntity.setId(id);
        newsContentEsEntity.setContentId(contentId);
        newsContentEsEntity.setTaskId(groupId);
        newsContentEsEntity.setTitle("");
        newsContentEsEntity.setContent(content);
        newsContentEsEntity.setCreateTime(new Date());

        newsContentEsRepository.save(newsContentEsEntity);

        return id;
    }
}


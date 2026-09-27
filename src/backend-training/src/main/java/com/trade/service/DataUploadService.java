package com.trade.service;

import com.trade.config.PythonScriptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据文件上传服务
 */
@Slf4j
@Service
public class DataUploadService {

    @Autowired
    private PythonScriptProperties properties;

    /**
     * 保存上传的 CSV 数据文件
     *
     * @param mergedInput  进口数据文件
     * @param mergedOutput 出口/预测数据文件
     * @return 保存结果
     */
    public List<Map<String, Object>> saveDataFiles(MultipartFile mergedInput, MultipartFile mergedOutput) throws IOException {
        if ((mergedInput == null || mergedInput.isEmpty()) && (mergedOutput == null || mergedOutput.isEmpty())) {
            throw new IllegalArgumentException("请至少上传 mergedInput 或 mergedOutput 文件");
        }

        List<Map<String, Object>> savedFiles = new ArrayList<>();

        if (mergedInput != null && !mergedInput.isEmpty()) {
            Path targetPath = resolveAndPreparePath(properties.getData().getImportData().getCsvPath());
            mergedInput.transferTo(targetPath.toFile());
            savedFiles.add(buildFileInfo("merged_input", mergedInput, targetPath));
            log.info("已更新进口数据文件: {}", targetPath);
        }

        if (mergedOutput != null && !mergedOutput.isEmpty()) {
            Path targetPath = resolveAndPreparePath(properties.getData().getExportData().getCsvPath());
            mergedOutput.transferTo(targetPath.toFile());
            savedFiles.add(buildFileInfo("merged_output", mergedOutput, targetPath));
            log.info("已更新出口数据文件: {}", targetPath);
        }

        return savedFiles;
    }

    private Path resolveAndPreparePath(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            throw new IllegalStateException("数据文件路径未配置");
        }
        Path target = Paths.get(path);
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        return target;
    }

    private Map<String, Object> buildFileInfo(String alias, MultipartFile source, Path targetPath) {
        Map<String, Object> info = new HashMap<>();
        info.put("alias", alias);
        info.put("originalFilename", source.getOriginalFilename());
        info.put("size", source.getSize());
        info.put("savedPath", targetPath.toAbsolutePath().toString());
        return info;
    }
}


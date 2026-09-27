package com.trade.controller;

import com.trade.service.DataUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据文件上传接口
 */
@Slf4j
@RestController
@RequestMapping("/data")
public class DataUploadController {

    @Autowired
    private DataUploadService dataUploadService;

    /**
     * 上传并替换 merged_input / merged_output 数据文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDataFiles(
            @RequestPart(value = "mergedInput", required = false) MultipartFile mergedInput,
            @RequestPart(value = "mergedOutput", required = false) MultipartFile mergedOutput) throws Exception {

        List<Map<String, Object>> savedFiles = dataUploadService.saveDataFiles(mergedInput, mergedOutput);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "数据文件上传成功");
        response.put("files", savedFiles);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}


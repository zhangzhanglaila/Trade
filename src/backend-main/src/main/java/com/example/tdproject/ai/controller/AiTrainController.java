package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.service.TrainService;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai/train")
@RequiredArgsConstructor
public class AiTrainController {

    private final TrainService trainService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestPart(value = "mergedInput", required = false) MultipartFile mergedInput,
            @RequestPart(value = "mergedOutput", required = false) MultipartFile mergedOutput
    ) {
        try {
            return Result.build(trainService.upload(mergedInput, mergedOutput));
        } catch (IllegalArgumentException e) {
            return Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("/ai/train/upload 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @PostMapping("/start")
    public Result<Map<String, Object>> start() {
        try {
            return Result.build(trainService.start());
        } catch (Exception e) {
            log.error("/ai/train/start 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> status(@PathVariable String taskId) {
        try {
            return Result.build(trainService.status(taskId));
        } catch (Exception e) {
            log.error("/ai/train/status/{} 异常", taskId, e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @GetMapping("/status")
    public Result<List<Map<String, Object>>> allStatus() {
        try {
            return Result.build(trainService.allStatus());
        } catch (Exception e) {
            log.error("/ai/train/status 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }
}

package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.dto.PredictResponse;
import com.example.tdproject.ai.predict.PredictionService;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping("/predict")
    public Result<PredictResponse> predict(@RequestBody PredictRequest req) {
        try {
            return Result.build(predictionService.predict(req));
        } catch (IllegalArgumentException e) {
            return Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("/ai/predict 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    @GetMapping("/predict/health")
    public Result<Object> health() {
        try {
            return Result.build(predictionService.health());
        } catch (Exception e) {
            log.error("/ai/predict/health 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }
}

package com.example.tdproject.ai.service;

import com.example.tdproject.ai.http.TradeSpringBootClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrainService {

    private final TradeSpringBootClient tradeSpringBootClient;

    public Map<String, Object> upload(MultipartFile mergedInput, MultipartFile mergedOutput) {
        return tradeSpringBootClient.upload(mergedInput, mergedOutput);
    }

    public Map<String, Object> start() {
        return tradeSpringBootClient.startTraining();
    }

    public Map<String, Object> status(String taskId) {
        return tradeSpringBootClient.getTaskStatus(taskId);
    }

    public List<Map<String, Object>> allStatus() {
        return tradeSpringBootClient.getAllTaskStatus();
    }
}

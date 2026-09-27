package com.example.tdproject.ai.http;

import com.example.tdproject.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSpringBootClient {

    private final AiProperties props;
    private final RestTemplate restTemplate;

    public Map<String, Object> upload(MultipartFile mergedInput, MultipartFile mergedOutput) {
        String baseUrl = props.getTradeSpringBoot().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("TradeSpringBoot baseUrl 未配置：ai.tradeSpringBoot.base-url");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (mergedInput != null && !mergedInput.isEmpty()) {
            body.add("mergedInput", new MultipartFileResource(mergedInput));
        }
        if (mergedOutput != null && !mergedOutput.isEmpty()) {
            body.add("mergedOutput", new MultipartFileResource(mergedOutput));
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                baseUrl + "/data/upload",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<>() {}
        );

        return resp.getBody();
    }

    public Map<String, Object> startTraining() {
        String baseUrl = props.getTradeSpringBoot().getBaseUrl();
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                baseUrl + "/training/start",
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return resp.getBody();
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        String baseUrl = props.getTradeSpringBoot().getBaseUrl();
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                baseUrl + "/training/status/" + taskId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        return resp.getBody();
    }

    public List<Map<String, Object>> getAllTaskStatus() {
        String baseUrl = props.getTradeSpringBoot().getBaseUrl();
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                baseUrl + "/training/status",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        return resp.getBody();
    }
}

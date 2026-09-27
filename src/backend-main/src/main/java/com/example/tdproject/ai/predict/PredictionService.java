package com.example.tdproject.ai.predict;

import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.dto.PredictResponse;
import com.example.tdproject.ai.http.FlaskPredictionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final FlaskPredictionClient flaskPredictionClient;

    public PredictResponse predict(PredictRequest req) {
        validate(req);
        return flaskPredictionClient.predict(req);
    }

    public Object health() {
        return flaskPredictionClient.health();
    }

    private void validate(PredictRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("PredictRequest 不能为空");
        }
        if (isBlank(req.getTradeType()) || isBlank(req.getTarget())) {
            throw new IllegalArgumentException("tradeType/target 不能为空");
        }
        if (req.getYear() == null || req.getMonth() == null) {
            throw new IllegalArgumentException("year/month 不能为空");
        }
        if (isBlank(req.getTradePartnerName()) || isBlank(req.getProductName()) || isBlank(req.getTradeMode()) || isBlank(req.getRegisterName())) {
            throw new IllegalArgumentException("预测槽位不能为空：tradePartnerName/productName/tradeMode/registerName");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

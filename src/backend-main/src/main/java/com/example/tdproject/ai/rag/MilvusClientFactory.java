package com.example.tdproject.ai.rag;

import com.example.tdproject.ai.config.AiProperties;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusClientFactory {

    private final AiProperties props;

    public MilvusClient client() {
        AiProperties.Milvus m = props.getMilvus();
        if (m.getHost() == null || m.getHost().isBlank() || m.getPort() == null) {
            throw new IllegalStateException("Milvus 配置不完整：ai.milvus.host/port");
        }

        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(m.getHost())
                .withPort(m.getPort())
                .build();

        return new MilvusServiceClient(connectParam);
    }
}

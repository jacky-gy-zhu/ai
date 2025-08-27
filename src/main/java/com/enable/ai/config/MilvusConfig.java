package com.enable.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.username:}")
    private String username;

    @Value("${milvus.password:}")
    private String password;

    @Bean
    public MilvusServiceClient milvusClient() {
        ConnectParam.Builder connectParamBuilder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port);

        if (!username.isEmpty() && !password.isEmpty()) {
            connectParamBuilder.withAuthorization(username, password);
        }

        ConnectParam connectParam = connectParamBuilder.build();
        MilvusServiceClient client = new MilvusServiceClient(connectParam);
        
        log.info("Connected to Milvus at {}:{}", host, port);
        return client;
    }
}

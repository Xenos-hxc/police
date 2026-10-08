package com.railway.security.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Local-only blocking inference with bounded waiting and no HTTP redirects. */
@Configuration
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class LocalAiConfiguration {
    @Bean
    // 模型客户端集中设置超时与本地地址；Spring AI 提供集成抽象，权限、证据校验和失败治理仍由业务实现。
    OllamaApi localOllamaApi(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${app.ai.model-timeout-seconds:120}") int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 180)
            throw new IllegalArgumentException("AI timeout must be 1..180 seconds");
        var client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();
    }
}

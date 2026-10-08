package com.railway.security.ai;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/** Runs before Spring AI clients are constructed, so an invalid public endpoint is never dialed. */
// 启动时限制私有模型地址并检查解析结果，配合禁重定向；完整出网防护仍依赖网络策略，不能只靠 URL 字符串判断。
public class PrivateAiEndpointGuard implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("app.ai.enabled", Boolean.class, false)) return;
        validate(
                environment.getProperty("spring.ai.ollama.base-url", "http://127.0.0.1:11434"),
                environment.getProperty("spring.ai.vectorstore.qdrant.host", "127.0.0.1"));
    }

    void validate(String modelUrl, String vectorHost) {
        URI uri = URI.create(modelUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException("AI 模型地址必须是内网 HTTP(S) 地址");
        }
        requirePrivate(uri.getHost());
        requirePrivate(vectorHost);
    }

    private void requirePrivate(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!address.isLoopbackAddress() && !address.isSiteLocalAddress()) {
                    throw new IllegalStateException("AI 服务不得连接公网地址: " + host);
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("AI 服务地址无法解析: " + host, exception);
        }
    }
}

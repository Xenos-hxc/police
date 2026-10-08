package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class PrivateAiEndpointGuardTest {
    @Test
    void rejectsPublicModelEndpoint() {
        assertThrows(
                IllegalStateException.class,
                () -> new PrivateAiEndpointGuard().validate("https://example.com", "127.0.0.1"));
    }

    @Test
    void rejectsPublicVectorEndpoint() {
        assertThrows(
                IllegalStateException.class,
                () -> new PrivateAiEndpointGuard().validate("http://127.0.0.1:11434", "8.8.8.8"));
    }

    @Test
    void allowsLoopbackEndpoints() {
        assertDoesNotThrow(
                () -> new PrivateAiEndpointGuard().validate("http://127.0.0.1:11434", "127.0.0.1"));
    }

    @Test
    void guardIsRegisteredBeforeClientAutoConfiguration() throws Exception {
        try (var resource =
                PrivateAiEndpointGuard.class
                        .getClassLoader()
                        .getResourceAsStream("META-INF/spring.factories")) {
            var properties = new Properties();
            properties.load(resource);
            org.junit.jupiter.api.Assertions.assertTrue(
                    properties
                            .getProperty("org.springframework.boot.env.EnvironmentPostProcessor")
                            .contains(PrivateAiEndpointGuard.class.getName()));
        }
    }
}

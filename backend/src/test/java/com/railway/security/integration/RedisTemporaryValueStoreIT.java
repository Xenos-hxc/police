package com.railway.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.railway.security.shared.cache.RedisTemporaryValueStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisTemporaryValueStoreIT {
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Test
    void oneTimeTicketConsumptionIsAtomicAcrossCallers() throws Exception {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            var store = new RedisTemporaryValueStore(template);
            store.put("p1:ticket", "attachment-1", Duration.ofMinutes(1));
            var pool = Executors.newFixedThreadPool(2);
            try {
                var results =
                        pool.invokeAll(
                                List.of(
                                        () -> store.getAndDelete("p1:ticket"),
                                        () -> store.getAndDelete("p1:ticket")));
                int valid = 0;
                for (var result : results) {
                    if ("attachment-1".equals(result.get())) valid++;
                }
                assertEquals(1, valid);
                assertNull(store.get("p1:ticket"));
            } finally {
                pool.shutdownNow();
            }
            assertEquals(1L, store.increment("p1:counter", Duration.ofMinutes(1)));
            assertEquals(2L, store.increment("p1:counter", Duration.ofMinutes(1)));
        } finally {
            factory.destroy();
        }
    }
}

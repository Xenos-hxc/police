package com.railway.security.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.security.shared.cache.TemporaryValueStore;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatisticsCacheService {
    private static final String VERSION_KEY = "stats:version";
    private static final Duration VERSION_TTL = Duration.ofDays(365);
    private final TemporaryValueStore store;
    private final ObjectMapper objectMapper;
    private final Object[] locks = new Object[128];

    @Value("${app.statistics.cache.ttl-seconds:300}")
    private long ttlSeconds;

    // 版本化缓存键配合分段锁和二次检查抑制单进程击穿；跨实例互斥与分布式锁不是本地 synchronized 的能力。
    public <T> T get(String key, TypeReference<T> type, Supplier<T> supplier) {
        String version = store.get(VERSION_KEY);
        String cacheKey = "stats:" + (version == null ? "0" : version) + ":" + key;
        T cached = read(store.get(cacheKey), type);
        if (cached != null) return cached;
        synchronized (lock(cacheKey)) {
            cached = read(store.get(cacheKey), type);
            if (cached != null) return cached;
            T value = supplier.get();
            long ttl = Math.max(30, ttlSeconds);
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, ttl / 10));
            store.put(cacheKey, encode(value), Duration.ofSeconds(ttl + jitter));
            return value;
        }
    }

    // 递增命名空间版本使旧缓存逻辑失效；当前调用时机仍需考虑事务提交竞态，不能把它描述为已经实现 afterCommit。
    public void clear() {
        store.increment(VERSION_KEY, VERSION_TTL);
    }

    private Object lock(String key) {
        int index = Math.floorMod(key.hashCode(), locks.length);
        synchronized (locks) {
            if (locks[index] == null) locks[index] = new Object();
            return locks[index];
        }
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize statistics cache", ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize statistics cache", ex);
        }
    }
}

package com.railway.security.shared.cache;

import java.time.Duration;

/** Short-lived values used by authentication, download tickets and statistics. */
// 临时状态抽象统一 TTL、计数和一次性消费语义；本地与 Redis 实现需保持契约一致，不能只保证方法名相同。
public interface TemporaryValueStore {
    void put(String key, String value, Duration ttl);

    String get(String key);

    String getAndDelete(String key);

    long increment(String key, Duration ttl);

    void delete(String key);
}

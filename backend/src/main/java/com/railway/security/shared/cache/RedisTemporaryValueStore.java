package com.railway.security.shared.cache;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
// 计数与首次过期设置通过原子脚本执行；Redis 故障不能静默降级成本地鉴权状态，否则会破坏跨实例一致性。
public class RedisTemporaryValueStore implements TemporaryValueStore {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end; return n",
                    Long.class);

    private final StringRedisTemplate redis;

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public String getAndDelete(String key) {
        return redis.opsForValue().getAndDelete(key);
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long result =
                redis.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(ttl.toMillis()));
        if (result == null) throw new IllegalStateException("Redis counter returned no value");
        return result;
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}

package com.railway.security.shared.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalTemporaryValueStore implements TemporaryValueStore {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, System.nanoTime() + ttl.toNanos()));
        cleanupPeriodically();
    }

    @Override
    public String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAtNanos() - System.nanoTime() <= 0) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    @Override
    public String getAndDelete(String key) {
        Entry entry = entries.remove(key);
        return entry != null && entry.expiresAtNanos() - System.nanoTime() > 0
                ? entry.value()
                : null;
    }

    @Override
    public synchronized long increment(String key, Duration ttl) {
        String previous = get(key);
        long next = previous == null ? 1 : Long.parseLong(previous) + 1;
        long expiry =
                previous == null
                        ? System.nanoTime() + ttl.toNanos()
                        : entries.get(key).expiresAtNanos();
        entries.put(key, new Entry(Long.toString(next), expiry));
        cleanupPeriodically();
        return next;
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    private void cleanupPeriodically() {
        if ((writes.incrementAndGet() & 255) != 0) return;
        long now = System.nanoTime();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() - now <= 0);
    }

    private record Entry(String value, long expiresAtNanos) {}
}

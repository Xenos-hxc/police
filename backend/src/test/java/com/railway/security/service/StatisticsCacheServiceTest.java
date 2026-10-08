package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.security.shared.cache.LocalTemporaryValueStore;
import com.railway.security.statistics.StatisticsCacheService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StatisticsCacheServiceTest {
    @Test
    void clearInvalidatesPreviousVersion() {
        var cache =
                new StatisticsCacheService(
                        new LocalTemporaryValueStore(),
                        new ObjectMapper().findAndRegisterModules());
        var calls = new AtomicInteger();
        TypeReference<Integer> type = new TypeReference<>() {};
        assertEquals(1, cache.get("overview:2026", type, calls::incrementAndGet));
        assertEquals(1, cache.get("overview:2026", type, calls::incrementAndGet));
        cache.clear();
        assertEquals(2, cache.get("overview:2026", type, calls::incrementAndGet));
    }
}

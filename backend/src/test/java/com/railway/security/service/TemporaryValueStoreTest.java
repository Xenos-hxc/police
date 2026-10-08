package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.railway.security.shared.cache.LocalTemporaryValueStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TemporaryValueStoreTest {
    private final LocalTemporaryValueStore store = new LocalTemporaryValueStore();

    @Test
    void consumeIsAtomicAndOneTime() {
        store.put("ticket", "attachment-1", Duration.ofMinutes(1));
        assertEquals("attachment-1", store.getAndDelete("ticket"));
        assertNull(store.getAndDelete("ticket"));
    }

    @Test
    void expiredValueCannotBeConsumed() {
        store.put("captcha", "1234", Duration.ZERO);
        assertNull(store.getAndDelete("captcha"));
    }

    @Test
    void counterRetainsItsOriginalExpiryWindow() {
        assertEquals(1, store.increment("failures", Duration.ZERO));
        assertEquals(1, store.increment("failures", Duration.ofMinutes(1)));
    }
}

package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railway.security.auth.LoginThrottleService;
import com.railway.security.shared.cache.LocalTemporaryValueStore;
import com.railway.security.shared.web.BusinessException;
import org.junit.jupiter.api.Test;

class LoginThrottleServiceTest {
    @Test
    void fiveFailuresBlockUsernameAcrossDifferentCase() {
        var throttle = new LoginThrottleService(new LocalTemporaryValueStore());
        for (int i = 0; i < 5; i++) throttle.recordFailure("Admin");
        assertThrows(BusinessException.class, () -> throttle.assertAllowed(" admin "));
        throttle.clear("ADMIN");
        assertDoesNotThrow(() -> throttle.assertAllowed("admin"));
    }
}

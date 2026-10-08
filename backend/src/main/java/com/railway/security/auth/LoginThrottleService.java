package com.railway.security.auth;

import com.railway.security.shared.cache.TemporaryValueStore;
import com.railway.security.shared.web.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginThrottleService {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 5;
    private final TemporaryValueStore store;

    public void assertAllowed(String username) {
        String value = store.get(key(username));
        if (value != null && Long.parseLong(value) >= MAX_FAILURES) {
            throw new BusinessException(429, "登录失败次数过多，请 15 分钟后重试");
        }
    }

    public void recordFailure(String username) {
        store.increment(key(username), WINDOW);
    }

    public void clear(String username) {
        store.delete(key(username));
    }

    private String key(String username) {
        try {
            String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "auth:failure:" + HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute login throttle key", ex);
        }
    }
}

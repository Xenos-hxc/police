package com.railway.security.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.ConfigMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final ConfigMapper configMapper;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expire-minutes}") long accessMinutes,
            @Value("${app.jwt.refresh-expire-days}") long refreshDays,
            ConfigMapper configMapper) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
        this.configMapper = configMapper;
    }

    public TokenPair createTokenPair(LoginUser user, String sessionId) {
        Duration currentAccessTtl =
                Duration.ofMinutes(configLong("token.expire.minutes", accessTtl.toMinutes()));
        IssuedToken access = create(user, sessionId, currentAccessTtl, "access");
        IssuedToken refresh = create(user, sessionId, refreshTtl, "refresh");
        return new TokenPair(
                access.value(), refresh.value(), access.expiresAt(), refresh.expiresAt());
    }

    private IssuedToken create(LoginUser user, String sessionId, Duration ttl, String type) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String value =
                Jwts.builder()
                        .claims(
                                Map.of(
                                        "uid",
                                        user.getUserId(),
                                        "deptId",
                                        user.getDeptId(),
                                        "type",
                                        type,
                                        "sid",
                                        sessionId,
                                        "ver",
                                        user.getTokenVersion()))
                        .subject(user.getUsername())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .signWith(key)
                        .compact();
        return new IssuedToken(value, expiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private long configLong(String key, long fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) return fallback;
        try {
            return Math.max(1, Long.parseLong(config.getConfigValue()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record IssuedToken(String value, Instant expiresAt) {}

    public record TokenPair(
            String accessToken,
            String refreshToken,
            Instant accessExpiresAt,
            Instant refreshExpiresAt) {}
}

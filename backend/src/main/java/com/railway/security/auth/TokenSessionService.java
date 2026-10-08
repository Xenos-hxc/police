package com.railway.security.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.railway.security.persistence.entity.AuthSession;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.mapper.AuthSessionMapper;
import com.railway.security.shared.web.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenSessionService {
    private final AuthSessionMapper sessionMapper;
    private final JwtService jwtService;

    @Transactional
    public JwtService.TokenPair create(LoginUser user, HttpServletRequest request) {
        String sessionId = UUID.randomUUID().toString();
        var pair = jwtService.createTokenPair(user, sessionId);
        var session = new AuthSession();
        session.setSessionId(sessionId);
        session.setUserId(user.getUserId());
        session.setRefreshTokenHash(hash(pair.refreshToken()));
        session.setExpiresAt(local(pair.refreshExpiresAt()));
        session.setLastUsedAt(LocalDateTime.now());
        session.setClientIp(clientIp(request));
        session.setUserAgent(limit(request.getHeader("User-Agent"), 500));
        sessionMapper.insert(session);
        return pair;
    }

    // 刷新凭证轮换使用哈希比较和条件更新；重放时撤销会话必须提交，因此指定业务异常不回滚，需防止并发刷新误用。
    @Transactional(noRollbackFor = BusinessException.class)
    public JwtService.TokenPair rotate(
            LoginUser user, String refreshToken, Claims claims, HttpServletRequest request) {
        assertClaims(user, claims, "refresh");
        String sessionId = claims.get("sid", String.class);
        var session = activeSession(sessionId, user.getUserId());
        String oldHash = hash(refreshToken);
        if (!MessageDigest.isEqual(
                oldHash.getBytes(StandardCharsets.US_ASCII),
                session.getRefreshTokenHash().getBytes(StandardCharsets.US_ASCII))) {
            revoke(sessionId);
            throw new BusinessException(401, "刷新令牌已被使用或已失效");
        }
        var pair = jwtService.createTokenPair(user, sessionId);
        LocalDateTime now = LocalDateTime.now();
        int updated =
                sessionMapper.rotate(
                        session.getId(),
                        oldHash,
                        hash(pair.refreshToken()),
                        local(pair.refreshExpiresAt()),
                        now,
                        clientIp(request),
                        limit(request.getHeader("User-Agent"), 500));
        if (updated != 1) {
            revoke(sessionId);
            throw new BusinessException(401, "刷新令牌已被使用或已失效");
        }
        return pair;
    }

    // JWT 验签之外继续校验账号版本和服务端会话，使撤销与封禁可及时生效；这不是完全无状态的认证。
    public void validateAccess(LoginUser user, Claims claims) {
        assertClaims(user, claims, "access");
        activeSession(claims.get("sid", String.class), user.getUserId());
    }

    public void revoke(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        sessionMapper.update(
                null,
                new LambdaUpdateWrapper<AuthSession>()
                        .eq(AuthSession::getSessionId, sessionId)
                        .isNull(AuthSession::getRevokedAt)
                        .set(AuthSession::getRevokedAt, LocalDateTime.now()));
    }

    public void revokeAll(Long userId) {
        if (userId == null) return;
        sessionMapper.update(
                null,
                new LambdaUpdateWrapper<AuthSession>()
                        .eq(AuthSession::getUserId, userId)
                        .isNull(AuthSession::getRevokedAt)
                        .set(AuthSession::getRevokedAt, LocalDateTime.now()));
    }

    private AuthSession activeSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) throw new BusinessException(401, "登录会话无效");
        var session =
                sessionMapper.selectOne(
                        new LambdaQueryWrapper<AuthSession>()
                                .eq(AuthSession::getSessionId, sessionId)
                                .eq(AuthSession::getUserId, userId)
                                .isNull(AuthSession::getRevokedAt)
                                .gt(AuthSession::getExpiresAt, LocalDateTime.now()));
        if (session == null) throw new BusinessException(401, "登录会话已失效");
        return session;
    }

    private void assertClaims(LoginUser user, Claims claims, String type) {
        Number version = claims.get("ver", Number.class);
        Long uid = claims.get("uid", Long.class);
        if (!type.equals(claims.get("type", String.class))
                || !user.getUserId().equals(uid)
                || version == null
                || version.intValue() != user.getTokenVersion()) {
            throw new BusinessException(401, "登录凭据已失效");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算令牌摘要", ex);
        }
    }

    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

package com.railway.security.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.railway.security.auth.AuthDtos.AccessTokenResponse;
import com.railway.security.auth.AuthDtos.CaptchaResponse;
import com.railway.security.auth.AuthDtos.CurrentUserResponse;
import com.railway.security.auth.AuthDtos.LoginRequest;
import com.railway.security.auth.AuthDtos.SessionTokens;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.shared.cache.TemporaryValueStore;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final ConfigMapper configMapper;
    private final TokenSessionService tokenSessionService;
    private final AccountSecurityService accountSecurityService;
    private final LoginAuditService loginAuditService;
    private final LoginThrottleService loginThrottleService;
    private final TemporaryValueStore temporaryValueStore;

    public SessionTokens login(LoginRequest request, HttpServletRequest servletRequest) {
        try {
            loginThrottleService.assertAllowed(request.username());
            verifyCaptcha(request);
            var authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.username(), request.password()));
            var user = (LoginUser) authentication.getPrincipal();
            userMapper.update(
                    null,
                    new LambdaUpdateWrapper<SysUser>()
                            .eq(SysUser::getId, user.getUserId())
                            .set(SysUser::getLastLoginTime, LocalDateTime.now())
                            .set(SysUser::getLastLoginIp, servletRequest.getRemoteAddr()));
            loginAuditService.record(
                    request.username(),
                    servletRequest.getRemoteAddr(),
                    servletRequest.getHeader("User-Agent"),
                    1,
                    "登录成功");
            loginThrottleService.clear(request.username());
            return session(tokenSessionService.create(user, servletRequest));
        } catch (AuthenticationException exception) {
            loginThrottleService.recordFailure(request.username());
            loginAuditService.record(
                    request.username(),
                    servletRequest.getRemoteAddr(),
                    servletRequest.getHeader("User-Agent"),
                    0,
                    "账号或密码错误");
            throw exception;
        } catch (BusinessException exception) {
            loginAuditService.record(
                    request.username(),
                    servletRequest.getRemoteAddr(),
                    servletRequest.getHeader("User-Agent"),
                    0,
                    exception.getMessage());
            throw exception;
        }
    }

    public SessionTokens refresh(String refreshToken, HttpServletRequest request) {
        if (refreshToken == null) {
            throw new BusinessException(401, "刷新令牌不存在");
        }
        try {
            var claims = jwtService.parse(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new BusinessException(401, "刷新令牌无效");
            }
            var user = userDetailsService.loadById(claims.get("uid", Long.class));
            return session(tokenSessionService.rotate(user, refreshToken, claims, request));
        } catch (RuntimeException exception) {
            throw new BusinessException(401, "刷新令牌已失效");
        }
    }

    public CaptchaResponse captcha() {
        if (!configBoolean("captcha.enabled", false)) {
            return CaptchaResponse.disabled();
        }
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
        String id = UUID.randomUUID().toString();
        temporaryValueStore.put("auth:captcha:" + id, code, Duration.ofMinutes(5));
        return new CaptchaResponse(
                true,
                id,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(captchaImage(code)));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        var user = SecurityUtils.currentUser();
        var department = deptMapper.selectById(user.getDeptId());
        return new CurrentUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                department == null ? "" : department.getDeptName(),
                user.getRoles(),
                user.getPermissions());
    }

    public void logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = jwtService.parse(header.substring(7));
                tokenSessionService.revoke(claims.get("sid", String.class));
            } catch (RuntimeException ignored) {
                // Logout remains idempotent when the access token has already expired.
            }
        }
        var user = SecurityUtils.currentUser();
        loginAuditService.record(
                user.getUsername(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                1,
                "退出登录");
    }

    public void changePassword(String oldPassword, String newPassword) {
        accountSecurityService.changePassword(oldPassword, newPassword);
    }

    private SessionTokens session(JwtService.TokenPair pair) {
        return new SessionTokens(
                new AccessTokenResponse(
                        pair.accessToken(), "Bearer", pair.accessExpiresAt().toString()),
                pair.refreshToken(),
                pair.refreshExpiresAt());
    }

    private void verifyCaptcha(LoginRequest request) {
        if (!configBoolean("captcha.enabled", false)) {
            return;
        }
        if (request.captchaId() == null || request.captcha() == null) {
            throw new BusinessException("请输入验证码");
        }
        String code = temporaryValueStore.getAndDelete("auth:captcha:" + request.captchaId());
        if (code == null || !code.equalsIgnoreCase(request.captcha().trim())) {
            throw new BusinessException("验证码错误或已失效");
        }
    }

    private boolean configBoolean(String key, boolean fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config == null ? fallback : Boolean.parseBoolean(config.getConfigValue());
    }

    private byte[] captchaImage(String code) {
        try {
            var image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(235, 243, 252));
            graphics.fillRect(0, 0, 120, 40);
            graphics.setColor(new Color(20, 73, 128));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            graphics.drawString(code, 28, 29);
            graphics.setColor(new Color(92, 135, 180));
            for (int index = 0; index < 5; index++) {
                int y = ThreadLocalRandom.current().nextInt(4, 37);
                graphics.drawLine(0, y, 120, ThreadLocalRandom.current().nextInt(4, 37));
            }
            graphics.dispose();
            try (var output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                return output.toByteArray();
            }
        } catch (Exception exception) {
            throw new BusinessException("验证码生成失败");
        }
    }
}

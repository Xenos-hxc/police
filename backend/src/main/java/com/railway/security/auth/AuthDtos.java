package com.railway.security.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "请输入账号") String username,
            @NotBlank(message = "请输入密码") String password,
            String captcha,
            String captchaId) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码") @Size(max = 32, message = "新密码长度必须为1-32位")
                    String newPassword) {}

    public record AccessTokenResponse(String accessToken, String tokenType, String expiresAt) {}

    public record CaptchaResponse(boolean enabled, String captchaId, String image) {
        public static CaptchaResponse disabled() {
            return new CaptchaResponse(false, null, null);
        }
    }

    public record CurrentUserResponse(
            Long id,
            String username,
            String realName,
            Long deptId,
            String deptName,
            Set<String> roles,
            Set<String> permissions) {}

    public record SessionTokens(
            AccessTokenResponse access, String refreshToken, Instant refreshExpiresAt) {}
}

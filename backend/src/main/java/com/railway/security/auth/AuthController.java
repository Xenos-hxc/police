package com.railway.security.auth;

import com.railway.security.auth.AuthDtos.AccessTokenResponse;
import com.railway.security.auth.AuthDtos.CaptchaResponse;
import com.railway.security.auth.AuthDtos.ChangePasswordRequest;
import com.railway.security.auth.AuthDtos.CurrentUserResponse;
import com.railway.security.auth.AuthDtos.LoginRequest;
import com.railway.security.auth.AuthDtos.SessionTokens;
import com.railway.security.shared.web.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_COOKIE = "RAILWAY_REFRESH_TOKEN";

    private final AuthenticationService authenticationService;

    @Value("${app.jwt.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @PostMapping("/login")
    public ApiResponse<AccessTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        SessionTokens tokens = authenticationService.login(request, servletRequest);
        setRefreshCookie(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        return ApiResponse.ok(tokens.access());
    }

    @PostMapping("/refresh-token")
    public ApiResponse<AccessTokenResponse> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        try {
            SessionTokens tokens =
                    authenticationService.refresh(cookie(request, REFRESH_COOKIE), request);
            setRefreshCookie(response, tokens.refreshToken(), tokens.refreshExpiresAt());
            return ApiResponse.ok(tokens.access());
        } catch (RuntimeException exception) {
            clearRefreshCookie(response);
            throw exception;
        }
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.ok(authenticationService.captcha());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok(authenticationService.currentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(request);
        clearRefreshCookie(response);
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletResponse response) {
        authenticationService.changePassword(request.oldPassword(), request.newPassword());
        clearRefreshCookie(response);
        return ApiResponse.ok();
    }

    private void setRefreshCookie(HttpServletResponse response, String token, Instant expiresAt) {
        long maxAge = Math.max(1, Duration.between(Instant.now(), expiresAt).toSeconds());
        ResponseCookie cookie =
                ResponseCookie.from(REFRESH_COOKIE, token)
                        .httpOnly(true)
                        .secure(refreshCookieSecure)
                        .sameSite("Strict")
                        .path("/api/auth")
                        .maxAge(maxAge)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(REFRESH_COOKIE, "")
                        .httpOnly(true)
                        .secure(refreshCookieSecure)
                        .sameSite("Strict")
                        .path("/api/auth")
                        .maxAge(0)
                        .build()
                        .toString());
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

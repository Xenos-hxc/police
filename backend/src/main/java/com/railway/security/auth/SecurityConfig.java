package com.railway.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.OperationLogFilter;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final OperationLogFilter operationLogFilter;
    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/auth/login",
                                                "/api/auth/refresh-token",
                                                "/api/auth/captcha",
                                                "/internal/actuator/health",
                                                "/internal/actuator/health/**",
                                                "/internal/actuator/prometheus",
                                                "/doc.html",
                                                "/webjars/**",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/files/*/download")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        handler ->
                                handler.authenticationEntryPoint(
                                                (request, response, ex) ->
                                                        write(response, 401, "请先登录"))
                                        .accessDeniedHandler(
                                                (request, response, ex) ->
                                                        write(response, 403, "无权执行该操作")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(operationLogFilter, JwtAuthenticationFilter.class)
                .build();
    }

    private void write(jakarta.servlet.http.HttpServletResponse response, int code, String message)
            throws java.io.IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(objectMapper.writeValueAsString(ApiResponse.fail(code, message)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}

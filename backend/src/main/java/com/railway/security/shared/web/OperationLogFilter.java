package com.railway.security.shared.web;

import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysOperLog;
import com.railway.security.persistence.mapper.OperLogMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class OperationLogFilter extends OncePerRequestFilter {
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private final OperLogMapper operLogMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            if (WRITE_METHODS.contains(request.getMethod())
                    && request.getRequestURI().startsWith("/api/")) {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                var log = new SysOperLog();
                if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
                    log.setUserId(user.getUserId());
                    log.setUsername(user.getUsername());
                    log.setDeptId(user.getDeptId());
                }
                log.setOperationType(request.getMethod());
                log.setModule(module(request.getRequestURI()));
                log.setContent(request.getRequestURI());
                log.setRequestMethod(request.getMethod());
                log.setRequestUri(request.getRequestURI());
                log.setRequestIp(request.getRemoteAddr());
                log.setResult(response.getStatus() < 400 ? 1 : 0);
                log.setCostTime(System.currentTimeMillis() - start);
                try {
                    operLogMapper.insert(log);
                } catch (RuntimeException ignored) {
                    // Logging must not change the business response.
                }
            }
        }
    }

    private String module(String uri) {
        String[] parts = uri.split("/");
        return parts.length > 2 ? parts[2] : "system";
    }
}

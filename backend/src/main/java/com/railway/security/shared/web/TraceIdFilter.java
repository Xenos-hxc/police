package com.railway.security.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
// 请求 Trace 贯穿响应和日志；线程复用必须清理 MDC，异步任务需要显式传递上下文而非假定 ThreadLocal 自动传播。
public class TraceIdFilter extends OncePerRequestFilter {
    private static final String TRACE_HEADER = "X-Trace-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_HEADER);
        String traceId =
                incoming != null && incoming.matches("[a-fA-F0-9-]{36}")
                        ? incoming
                        : UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}

package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.dto.SystemDtos.LoginLogResponse;
import com.railway.security.system.dto.SystemDtos.OperationLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {
    private final AuditLogQueryService auditLogService;

    @GetMapping("/operation")
    public ApiResponse<PageResult<OperationLogResponse>> operationLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer resultStatus) {
        return ApiResponse.ok(
                auditLogService.operationLogs(page, size, username, module, resultStatus));
    }

    @GetMapping("/login")
    public ApiResponse<PageResult<LoginLogResponse>> loginLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(auditLogService.loginLogs(page, size, username, status));
    }
}

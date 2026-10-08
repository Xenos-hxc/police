package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.persistence.entity.SysLoginLog;
import com.railway.security.persistence.entity.SysOperLog;
import com.railway.security.persistence.mapper.LoginLogMapper;
import com.railway.security.persistence.mapper.OperLogMapper;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.dto.SystemDtos.LoginLogResponse;
import com.railway.security.system.dto.SystemDtos.OperationLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
    private final OperLogMapper operLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final SystemDtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public PageResult<OperationLogResponse> operationLogs(
            long page, long size, String username, String module, Integer resultStatus) {
        Page<SysOperLog> result =
                operLogMapper.selectPage(
                        new Page<>(Math.max(1, page), boundedSize(size)),
                        new LambdaQueryWrapper<SysOperLog>()
                                .like(hasText(username), SysOperLog::getUsername, username)
                                .like(hasText(module), SysOperLog::getModule, module)
                                .eq(resultStatus != null, SysOperLog::getResult, resultStatus)
                                .orderByDesc(SysOperLog::getCreateTime));
        return new PageResult<>(
                result.getTotal(),
                result.getRecords().stream().map(dtoMapper::toOperationLogResponse).toList());
    }

    @Transactional(readOnly = true)
    public PageResult<LoginLogResponse> loginLogs(
            long page, long size, String username, Integer status) {
        Page<SysLoginLog> result =
                loginLogMapper.selectPage(
                        new Page<>(Math.max(1, page), boundedSize(size)),
                        new LambdaQueryWrapper<SysLoginLog>()
                                .like(hasText(username), SysLoginLog::getUsername, username)
                                .eq(status != null, SysLoginLog::getStatus, status)
                                .orderByDesc(SysLoginLog::getLoginTime));
        return new PageResult<>(
                result.getTotal(),
                result.getRecords().stream().map(dtoMapper::toLoginLogResponse).toList());
    }

    private long boundedSize(long size) {
        return Math.max(1, Math.min(size, 200));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

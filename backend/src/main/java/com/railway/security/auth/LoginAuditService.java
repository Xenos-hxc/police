package com.railway.security.auth;

import com.railway.security.persistence.entity.SysLoginLog;
import com.railway.security.persistence.mapper.LoginLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAuditService {
    private final LoginLogMapper loginLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String username, String remoteAddress, String userAgent, int status, String message) {
        var log = new SysLoginLog();
        log.setUsername(username);
        log.setLoginIp(remoteAddress);
        log.setBrowser(userAgent);
        log.setOs("unknown");
        log.setStatus(status);
        log.setMessage(message);
        log.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }
}

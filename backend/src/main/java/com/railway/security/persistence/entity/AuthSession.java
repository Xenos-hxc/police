package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_session")
public class AuthSession extends BaseEntity {
    private String sessionId;
    private Long userId;
    private String refreshTokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;
    private String clientIp;
    private String userAgent;
}

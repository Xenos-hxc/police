package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {
    private String username;
    private String loginIp;
    private String browser;
    private String os;
    private Integer status;
    private String message;
    private LocalDateTime loginTime;
}

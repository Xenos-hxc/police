package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private Long deptId;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private Integer status;
    private String dataScope;
    private Integer forceChangePassword;
    private Integer tokenVersion;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
}

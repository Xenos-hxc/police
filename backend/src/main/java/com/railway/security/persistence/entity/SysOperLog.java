package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {
    private Long userId;
    private String username;
    private Long deptId;
    private String operationType;
    private String module;
    private String content;
    private String requestMethod;
    private String requestUri;
    private String requestIp;
    private Integer result;
    private String failureReason;
    private Long costTime;
}

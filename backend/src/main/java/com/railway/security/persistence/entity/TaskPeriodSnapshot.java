package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_period_snapshot")
public class TaskPeriodSnapshot extends BaseEntity {
    private Long executorDeptId;
    private String executorDeptType;
    private String targetType;
    private Integer taskYear;
    private Integer quarter;
    private Integer totalCount;
    private Integer requiredCount;
    private Integer coveragePercent;
    private String snapshotStatus;
    private LocalDateTime initializedAt;
    private LocalDateTime sealedAt;
}

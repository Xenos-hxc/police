package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hidden_danger")
public class HiddenDanger extends BaseEntity {
    private String dangerNo;
    private Long taskId;
    private String taskNo;
    private Integer taskYear;
    private Integer quarter;
    private Long executorDeptId;
    private Long targetId;
    private String targetType;
    private String targetName;
    private String dangerDetail;
    private String rectificationType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate rectificationDeadline;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rectificationCheckTime;

    private String rectificationInspectors;
    private String rectificationRemark;
    private Long rectificationSubmittedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rectificationSubmittedTime;
}

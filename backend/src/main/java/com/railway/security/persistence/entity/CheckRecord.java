package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_record")
public class CheckRecord extends BaseEntity {
    private Long taskId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;

    private String inspectors;
    private Integer hasDanger;
    private String rectificationType;
    private String dangerDetail;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate rectificationDeadline;

    private Long submittedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedTime;

    private Integer complete;
}

package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_task")
// 任务关联执行部门、受检对象及季度；数据库唯一约束兜底业务去重，逻辑删除字段是否参与唯一键影响再次创建。
public class CheckTask extends BaseEntity {
    private String taskNo;
    private String taskName;
    private String checkType;
    private String taskCategory;
    private String creationMode;
    private Long initiatorDeptId;
    private Long executorDeptId;
    private Long targetId;
    private String targetType;
    private String targetName;
    private Integer taskYear;
    private Integer quarter;
    private Integer halfYear;
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    private String status;
    private Integer overdue;
    private Integer overdueSubmitted;
    private Integer countCoverage;
    @Version private Integer version;
}

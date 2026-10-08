package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_target_jurisdiction")
public class TargetJurisdiction extends BaseEntity {
    private Long stationDeptId;
    private String targetType;
    private Long targetId;
}

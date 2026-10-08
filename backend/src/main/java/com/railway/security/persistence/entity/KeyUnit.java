package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_key_unit")
public class KeyUnit extends BaseEntity {
    private String unitCode;
    private String unitName;
    private String unitType;
    private LocalDate establishedDate;
    private String leader;
    private String contactPerson;
    private String phone;
    private String address;
    private String bureauName;
    private String jurisdictionText;
    private LocalDateTime lastCheckTime;
    private String lastCheckResult;
    private Integer status;
    private Integer archived;

    @TableField(exist = false)
    private List<Long> stationDeptIds;

    @TableField(exist = false)
    private List<String> stationNames;
}

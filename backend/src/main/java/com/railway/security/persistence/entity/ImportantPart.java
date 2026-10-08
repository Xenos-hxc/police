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
@TableName("base_important_part")
public class ImportantPart extends BaseEntity {
    private String partCode;
    private String partName;
    private String partType;
    private LocalDate establishedDate;
    private LocalDate removedDate;
    private String guardStatus;
    private String lengthDescription;
    private String railwayLine;
    private String kilometerMark;
    private String location;
    private String responsibleUnit;
    private String workshop;
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

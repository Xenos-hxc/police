package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_police_station")
public class PoliceStation extends BaseEntity {
    private Long deptId;
    private String stationCode;
    private String stationName;
    private String leader;
    private String phone;
    private String address;
    private String jurisdiction;
    private Integer status;
    private Integer archived;
}

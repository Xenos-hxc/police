package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    private Long parentId;
    private String ancestors;
    private String deptName;
    private String deptType;
    private String leader;
    private String phone;
    private String address;
    private String jurisdiction;
    private Integer sortNo;
    private Integer status;
    private Integer archived;
}

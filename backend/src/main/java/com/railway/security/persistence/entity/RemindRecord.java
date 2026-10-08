package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.railway.security.shared.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("remind_record")
public class RemindRecord extends BaseEntity {
    private Long taskId;
    private Long receiverUserId;
    private Long receiverDeptId;
    private String remindType;
    private String remindContent;
    private LocalDateTime remindTime;
    private Integer readFlag;
}

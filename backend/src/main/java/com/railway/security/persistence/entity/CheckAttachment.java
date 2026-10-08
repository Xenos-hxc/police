package com.railway.security.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.railway.security.shared.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_attachment")
public class CheckAttachment extends BaseEntity {
    private Long taskId;
    private Long recordId;
    private String attachmentType;
    private String originalName;
    @JsonIgnore private String storedName;
    @JsonIgnore private String storagePath;
    private Long fileSize;
    private String contentType;
    private String extension;
    private String sha256;
    private String scanStatus;
    private Integer scanAttempts;
    private java.time.LocalDateTime scanNextAttemptAt;
    private java.time.LocalDateTime scanStartedAt;
    private String scanError;
    private String storageStatus;
    private java.time.LocalDateTime purgedAt;
}

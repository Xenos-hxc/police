package com.railway.security.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.shared.scheduling.MysqlJobLock;
import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedFileRetentionTask {
    private final CheckAttachmentMapper attachmentMapper;
    private final ConfigMapper configMapper;
    private final MysqlJobLock jobLock;
    private final AttachmentStorage storage;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${UPLOAD_PATH:}")
    private String environmentUploadPath;

    @Scheduled(cron = "0 35 2 * * ?")
    public void purge() {
        jobLock.runIfLeader("railway:deleted-file-purge", this::purgeUnlocked);
    }

    private void purgeUnlocked() {
        int retentionDays = configInt("upload.deleted.retention.days", 30);
        Path root =
                Path.of(
                                environmentUploadPath == null || environmentUploadPath.isBlank()
                                        ? configString("upload.path", uploadPath)
                                        : environmentUploadPath)
                        .toAbsolutePath()
                        .normalize();
        for (var attachment :
                attachmentMapper.selectDeletedForPurge(
                        LocalDateTime.now().minusDays(retentionDays), 100)) {
            try {
                storage.delete(attachment, root);
                attachmentMapper.markPurged(attachment.getId());
            } catch (Exception ex) {
                log.warn("清理已逻辑删除附件失败：{}", attachment.getId(), ex);
            }
        }
    }

    private int configInt(String key, int fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) return fallback;
        try {
            return Math.max(1, Integer.parseInt(config.getConfigValue()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String configString(String key, String fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config == null
                        || config.getConfigValue() == null
                        || config.getConfigValue().isBlank()
                ? fallback
                : config.getConfigValue();
    }
}

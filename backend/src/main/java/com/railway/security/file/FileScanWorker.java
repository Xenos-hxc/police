package com.railway.security.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.upload.async-scan.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FileScanWorker {
    private static final int MAX_ATTEMPTS = 5;
    private final CheckAttachmentMapper attachments;
    private final FileSecurityService security;
    private final MeterRegistry metrics;
    private final AttachmentStorage storage;
    private final ConfigMapper configMapper;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${UPLOAD_PATH:}")
    private String environmentUploadPath;

    @Value("${app.upload.queue.enabled:false}")
    private boolean queueEnabled;

    @Scheduled(fixedDelayString = "${app.upload.async-scan.interval-ms:5000}")
    // 扫描由有界工作线程处理，数据库条件认领防重复执行；多实例容量不能只看单进程线程池大小。
    public void scanPending() {
        int recovered = attachments.recoverAbandonedScans();
        if (recovered > 0) log.warn("恢复 {} 个中断的附件检测任务", recovered);
        if (queueEnabled) return;
        for (Long id : attachments.pendingScanIds(10)) {
            if (attachments.claimScan(id) != 1) continue;
            scanClaimed(id);
        }
    }

    // 隔离文件先扫描再发布；非恶意失败采用有上限的指数退避，当前没有随机抖动，重试仍需幂等和恢复策略。
    public void scanClaimed(Long id) {
        var attachment = attachments.selectById(id);
        if (attachment == null) return;
        Path scanFile = null;
        try {
            Path root = root();
            scanFile = storage.materializePending(attachment, root);
            if (!Files.isRegularFile(scanFile)) throw new IOException("待检测文件不存在");
            String result = security.scan(scanFile);
            String sha256 = security.sha256(scanFile);
            if (attachment.getSha256() != null
                    && !attachment.getSha256().equalsIgnoreCase(sha256)) {
                throw new IOException("隔离文件完整性校验失败");
            }
            storage.promote(attachment, root);
            if (attachments.finishScan(id, result, sha256) == 1) {
                storage.deletePending(
                        attachment.getStoragePath(), attachment.getStoredName(), root);
                count(result.toLowerCase());
            }
        } catch (MalwareDetectedException exception) {
            attachments.failScan(id, "REJECTED", null, "检测到恶意文件");
            deleteRejectedFiles(attachment);
            count("rejected");
            log.warn("附件 {} 被安全检测拒绝", id);
        } catch (Exception exception) {
            int attempts = attachment.getScanAttempts() == null ? 1 : attachment.getScanAttempts();
            boolean exhausted = attempts >= MAX_ATTEMPTS;
            int delay = Math.min(3600, 30 * (1 << Math.min(attempts - 1, 6)));
            attachments.failScan(
                    id, exhausted ? "FAILED" : "PENDING", exhausted ? null : delay, "安全检测服务暂不可用");
            if (exhausted) deleteRejectedFiles(attachment);
            count(exhausted ? "failed" : "retry");
            log.warn("附件 {} 安全检测失败，尝试次数 {}", id, attempts, exception);
        } finally {
            if (storage.isObject(attachment) && scanFile != null) {
                try {
                    Files.deleteIfExists(scanFile);
                } catch (IOException exception) {
                    log.warn("扫描临时文件清理失败：{}", id, exception);
                }
            }
        }
    }

    private Path root() {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>()
                                .eq(SysConfig::getConfigKey, "upload.path"));
        String databasePath =
                config == null
                                || config.getConfigValue() == null
                                || config.getConfigValue().isBlank()
                        ? uploadPath
                        : config.getConfigValue();
        String configured =
                environmentUploadPath == null || environmentUploadPath.isBlank()
                        ? databasePath
                        : environmentUploadPath;
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private void deleteRejectedFiles(
            com.railway.security.persistence.entity.CheckAttachment attachment) {
        try {
            storage.delete(attachment, root());
        } catch (IOException exception) {
            log.warn("清理未通过检测的附件文件失败：{}", attachment.getId(), exception);
        }
    }

    private void count(String status) {
        Counter.builder("railway.file.scan.total")
                .tag("result", status)
                .register(metrics)
                .increment();
    }
}

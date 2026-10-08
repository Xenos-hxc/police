package com.railway.security.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.inspection.TaskService;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.ScanOutboxMapper;
import com.railway.security.rectification.HiddenDangerService;
import com.railway.security.shared.web.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileApplicationService {
    private static final List<String> PREVIEW_EXTENSIONS = List.of("jpg", "jpeg", "png", "pdf");
    private static final List<String> RECTIFICATION_TYPES =
            List.of("RECTIFICATION_VIDEO", "RECTIFICATION_ATTACHMENT");

    private final CheckAttachmentMapper attachmentMapper;
    private final CheckTaskMapper taskMapper;
    private final TaskService taskService;
    private final ConfigMapper configMapper;
    private final HiddenDangerService hiddenDangerService;
    private final FileSecurityService fileSecurityService;
    private final FileDownloadTicketService downloadTicketService;
    private final AttachmentStorage storage;
    private final ScanOutboxMapper scanOutbox;

    @Value("${app.upload.queue.enabled:false}")
    private boolean queueEnabled;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${UPLOAD_PATH:}")
    private String environmentUploadPath;

    @Value("${app.upload.allowed-extensions}")
    private String allowedExtensions;

    @Value("${app.upload.async-scan.enabled:true}")
    private boolean asyncScanEnabled;

    @Transactional(rollbackFor = IOException.class)
    // 附件元数据与扫描 outbox 同事务落库，隔离文件是外部副作用；回滚回调之外仍需处理进程崩溃产生的孤儿文件。
    public CheckAttachment upload(Long taskId, String attachmentType, MultipartFile file)
            throws IOException {
        String normalizedType =
                attachmentType == null ? "OTHER" : attachmentType.toUpperCase(Locale.ROOT);
        if (isRectificationType(normalizedType)) {
            hiddenDangerService.assertUploadAllowed(taskId, normalizedType);
        } else {
            taskService.assertEditable(taskMapper.selectById(taskId));
        }
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extension(original);
        Set<String> allowed =
                Arrays.stream(allowedExtensions.toLowerCase(Locale.ROOT).split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toSet());
        if (!allowed.contains(extension)) {
            throw new BusinessException("不支持的文件类型");
        }
        validateAttachmentType(normalizedType, extension);
        if (file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        validateSignature(file, extension);
        long maxMb =
                List.of("VIDEO", "RECTIFICATION_VIDEO").contains(normalizedType)
                        ? configLong("upload.video.max.mb", 5120L)
                        : configLong("upload.file.max.mb", 5120L);
        if (file.getSize() > maxMb * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 " + maxMb + "MB");
        }

        Path root = uploadRoot();
        Path day = root.resolve(LocalDate.now().toString()).normalize();
        Path quarantine = root.resolve(".quarantine").normalize();
        if (!day.startsWith(root) || !quarantine.startsWith(root)) {
            throw new BusinessException("非法文件路径");
        }
        Files.createDirectories(day);
        Files.createDirectories(quarantine);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = day.resolve(storedName).normalize();
        Path temporary = Files.createTempFile(quarantine, "upload-", ".tmp");
        if (!target.startsWith(root)) {
            throw new BusinessException("非法文件路径");
        }
        try {
            file.transferTo(temporary);
            String scanStatus;
            String sha256 = null;
            String storageStatus;
            String storagePath;
            if (asyncScanEnabled) {
                sha256 = fileSecurityService.sha256(temporary);
                storagePath = storage.putPending(temporary, target, storedName, file.getSize());
                scanStatus = "PENDING";
                storageStatus = "QUARANTINED";
            } else {
                try {
                    scanStatus = fileSecurityService.scan(temporary);
                } catch (MalwareDetectedException exception) {
                    throw new BusinessException("文件安全检测未通过，禁止上传");
                }
                sha256 = fileSecurityService.sha256(temporary);
                moveIntoStorage(temporary, target);
                storagePath = target.toString();
                storageStatus = "ACTIVE";
            }
            registerRollbackCleanup(storagePath, storedName, root);

            var attachment = new CheckAttachment();
            attachment.setTaskId(taskId);
            attachment.setAttachmentType(normalizedType);
            attachment.setOriginalName(original);
            attachment.setStoredName(storedName);
            attachment.setStoragePath(storagePath);
            attachment.setFileSize(file.getSize());
            attachment.setContentType(fileSecurityService.trustedContentType(extension));
            attachment.setExtension(extension);
            attachment.setSha256(sha256);
            attachment.setScanStatus(scanStatus);
            attachment.setStorageStatus(storageStatus);
            if (attachmentMapper.insert(attachment) != 1) {
                throw new BusinessException("附件元数据保存失败");
            }
            if (queueEnabled && asyncScanEnabled) {
                scanOutbox.enqueue(attachment.getId());
            }
            return attachment;
        } finally {
            deleteQuietly(temporary);
        }
    }

    @Transactional(readOnly = true)
    public List<CheckAttachment> list(Long taskId) {
        taskService.assertTaskMaterialReadAccess(taskMapper.selectById(taskId));
        return attachmentMapper.selectList(
                new LambdaQueryWrapper<CheckAttachment>()
                        .eq(CheckAttachment::getTaskId, taskId)
                        .orderByDesc(CheckAttachment::getCreateTime));
    }

    @Transactional(readOnly = true)
    public CheckAttachment detail(Long id) {
        return checked(id);
    }

    @Transactional(readOnly = true)
    public CheckAttachment preview(Long id) {
        var attachment = checked(id);
        assertReleased(attachment);
        String extension = String.valueOf(attachment.getExtension()).toLowerCase(Locale.ROOT);
        if (!PREVIEW_EXTENSIONS.contains(extension)) {
            throw new BusinessException("该文件类型不允许在线预览，请下载后查看");
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public DownloadTicketResponse issueDownloadTicket(Long id) {
        assertReleased(checked(id));
        String ticket = downloadTicketService.issue(id);
        return new DownloadTicketResponse("/api/files/" + id + "/download?ticket=" + ticket);
    }

    @Transactional(readOnly = true)
    public CheckAttachment download(Long id, String ticket) {
        downloadTicketService.validate(ticket, id);
        var attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        assertReleased(attachment);
        return attachment;
    }

    @Transactional
    public void delete(Long id) {
        var attachment = checked(id);
        var task = taskMapper.selectForUpdate(attachment.getTaskId());
        if (isRectificationType(attachment.getAttachmentType())) {
            hiddenDangerService.assertAttachmentDeleteAllowed(attachment);
        } else {
            taskService.assertEditable(task);
        }
        if (List.of("VIDEO", "RECORD").contains(attachment.getAttachmentType())
                && "APPROVED".equals(task.getStatus())
                && "ACTIVE".equals(attachment.getStorageStatus())
                && List.of("CLEAN", "SKIPPED").contains(attachment.getScanStatus())
                && attachmentMapper.selectCount(
                                new LambdaQueryWrapper<CheckAttachment>()
                                        .eq(CheckAttachment::getTaskId, task.getId())
                                        .eq(
                                                CheckAttachment::getAttachmentType,
                                                attachment.getAttachmentType())
                                        .eq(CheckAttachment::getStorageStatus, "ACTIVE")
                                        .in(CheckAttachment::getScanStatus, "CLEAN", "SKIPPED"))
                        <= 1) {
            throw new BusinessException("已完成任务必须保留检查视频和检查笔录，请先上传替换材料");
        }
        attachment.setStorageStatus("DELETED");
        if (attachmentMapper.updateById(attachment) != 1 || attachmentMapper.deleteById(id) != 1) {
            throw new BusinessException("附件删除失败，请刷新后重试");
        }
    }

    public String contentType(CheckAttachment attachment) {
        return fileSecurityService.trustedContentType(attachment.getExtension());
    }

    public InputStream open(CheckAttachment attachment) throws IOException {
        return storage.open(attachment, uploadRoot());
    }

    private CheckAttachment checked(Long id) {
        var attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        taskService.assertTaskMaterialReadAccess(taskMapper.selectById(attachment.getTaskId()));
        return attachment;
    }

    private void assertReleased(CheckAttachment attachment) {
        if (!"ACTIVE".equals(attachment.getStorageStatus())
                || !List.of("CLEAN", "SKIPPED").contains(attachment.getScanStatus())) {
            throw new BusinessException(409, "附件尚未通过安全检测，暂不可访问");
        }
    }

    private Path uploadRoot() {
        String configuredPath =
                environmentUploadPath == null || environmentUploadPath.isBlank()
                        ? configString("upload.path", uploadPath)
                        : environmentUploadPath;
        return Path.of(configuredPath).toAbsolutePath().normalize();
    }

    private void moveIntoStorage(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void registerRollbackCleanup(String target, String storedName, Path root) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            if (target.startsWith("s3://") || asyncScanEnabled) {
                                storage.deletePending(target, storedName, root);
                            } else {
                                deleteQuietly(Path.of(target));
                            }
                        }
                    }
                });
    }

    private long configLong(String key, long fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            return fallback;
        }
        try {
            return Long.parseLong(config.getConfigValue());
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

    private void validateSignature(MultipartFile file, String extension) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        }
        boolean valid =
                switch (extension) {
                    case "jpg", "jpeg" ->
                            header.length >= 2
                                    && unsigned(header[0]) == 0xFF
                                    && unsigned(header[1]) == 0xD8;
                    case "png" ->
                            header.length >= 8
                                    && unsigned(header[0]) == 0x89
                                    && header[1] == 0x50
                                    && header[2] == 0x4E
                                    && header[3] == 0x47;
                    case "pdf" -> ascii(header, 0, "%PDF");
                    case "mp4", "mov" -> header.length >= 8 && ascii(header, 4, "ftyp");
                    case "avi" ->
                            header.length >= 12
                                    && ascii(header, 0, "RIFF")
                                    && ascii(header, 8, "AVI");
                    case "doc", "xls", "ppt" ->
                            header.length >= 8
                                    && unsigned(header[0]) == 0xD0
                                    && unsigned(header[1]) == 0xCF
                                    && unsigned(header[2]) == 0x11
                                    && unsigned(header[3]) == 0xE0;
                    case "docx", "xlsx", "pptx", "zip" -> zipHeader(header);
                    case "rar" -> ascii(header, 0, "Rar!");
                    case "7z" ->
                            header.length >= 6
                                    && unsigned(header[0]) == 0x37
                                    && unsigned(header[1]) == 0x7A
                                    && unsigned(header[2]) == 0xBC
                                    && unsigned(header[3]) == 0xAF
                                    && unsigned(header[4]) == 0x27
                                    && unsigned(header[5]) == 0x1C;
                    case "txt" ->
                            java.util.stream.IntStream.range(0, header.length)
                                    .noneMatch(index -> header[index] == 0);
                    default -> false;
                };
        if (!valid) {
            throw new BusinessException("文件内容与扩展名不匹配");
        }
    }

    private void validateAttachmentType(String attachmentType, String extension) {
        List<String> materialExtensions =
                List.of(
                        "jpg", "jpeg", "png", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                        "txt", "zip", "rar", "7z");
        boolean valid =
                switch (attachmentType) {
                    case "VIDEO", "RECTIFICATION_VIDEO" ->
                            List.of("mp4", "mov", "avi").contains(extension);
                    case "RECORD",
                                    "HAZARD_MATERIAL",
                                    "NOTICE",
                                    "RECTIFICATION_ATTACHMENT",
                                    "PHOTO",
                                    "OTHER" ->
                            materialExtensions.contains(extension);
                    default -> false;
                };
        if (!valid) {
            throw new BusinessException(
                    switch (attachmentType) {
                        case "VIDEO" -> "视频上传框仅支持 mp4、mov、avi";
                        case "RECTIFICATION_VIDEO" -> "整改视频上传框仅支持 mp4、mov、avi";
                        case "RECORD",
                                        "HAZARD_MATERIAL",
                                        "NOTICE",
                                        "RECTIFICATION_ATTACHMENT",
                                        "PHOTO",
                                        "OTHER" ->
                                "材料上传框支持图片、常用办公文档和 zip、rar、7z 压缩包";
                        default -> "未知附件类型";
                    });
        }
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private boolean zipHeader(byte[] header) {
        return header.length >= 4
                && header[0] == 0x50
                && header[1] == 0x4B
                && ((header[2] == 0x03 && header[3] == 0x04)
                        || (header[2] == 0x05 && header[3] == 0x06)
                        || (header[2] == 0x07 && header[3] == 0x08));
    }

    private boolean isRectificationType(String type) {
        return RECTIFICATION_TYPES.contains(type);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeOriginalName(String value) {
        String candidate = value == null ? "file" : value.replace('\\', '/');
        int slash = candidate.lastIndexOf('/');
        if (slash >= 0) {
            candidate = candidate.substring(slash + 1);
        }
        candidate = candidate.replaceAll("[\\x00-\\x1F\\x7F]", "_").trim();
        if (candidate.isEmpty() || ".".equals(candidate) || "..".equals(candidate)) {
            candidate = "file";
        }
        if (candidate.length() > 255) {
            String suffix = extension(candidate);
            int suffixLength = suffix.isEmpty() ? 0 : suffix.length() + 1;
            candidate =
                    candidate.substring(0, Math.max(1, 255 - suffixLength))
                            + (suffix.isEmpty() ? "" : "." + suffix);
        }
        return candidate;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A scheduled retention task retries cleanup.
        }
    }

    public record DownloadTicketResponse(String downloadUrl) {}
}

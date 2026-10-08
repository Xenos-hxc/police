package com.railway.security.file;

import com.railway.security.persistence.entity.CheckAttachment;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AttachmentStorage {
    private final boolean objectEnabled;
    private final String bucket;
    private final MinioClient client;

    public AttachmentStorage(
            @Value("${app.upload.object-store.enabled:false}") boolean objectEnabled,
            @Value("${app.upload.object-store.endpoint}") String endpoint,
            @Value("${app.upload.object-store.access-key:}") String accessKey,
            @Value("${app.upload.object-store.secret-key:}") String secretKey,
            @Value("${app.upload.object-store.bucket}") String bucket,
            @Value("${app.upload.async-scan.enabled:true}") boolean asyncScanEnabled) {
        if (objectEnabled && (!asyncScanEnabled || accessKey.isBlank() || secretKey.isBlank())) {
            throw new IllegalStateException("对象存储需要异步扫描及非空 S3 凭据");
        }
        this.objectEnabled = objectEnabled;
        this.bucket = bucket;
        this.client =
                objectEnabled
                        ? MinioClient.builder()
                                .endpoint(endpoint)
                                .credentials(accessKey, secretKey)
                                .build()
                        : null;
    }

    public String putPending(Path source, Path localTarget, String storedName, long size)
            throws IOException {
        if (!objectEnabled) {
            Path quarantine = localTarget.getParent().getParent().resolve(".quarantine");
            Files.createDirectories(quarantine);
            move(source, quarantine.resolve(storedName + ".pending"));
            return localTarget.toString();
        }
        String key = "active/" + localTarget.getParent().getFileName() + "/" + storedName;
        String pending = pendingKey(key);
        try (InputStream input = Files.newInputStream(source)) {
            client.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(pending).stream(input, size, -1)
                            .contentType("application/octet-stream")
                            .build());
            return "s3://" + bucket + "/" + key;
        } catch (Exception exception) {
            throw new IOException("隔离文件写入对象存储失败", exception);
        }
    }

    public Path materializePending(CheckAttachment attachment, Path workDir) throws IOException {
        if (!isObject(attachment)) {
            Path target = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
            Path quarantine = workDir.resolve(".quarantine").normalize();
            Path pending = quarantine.resolve(attachment.getStoredName() + ".pending").normalize();
            if (!target.startsWith(workDir)
                    || target.startsWith(quarantine)
                    || !pending.startsWith(quarantine)) {
                throw new IOException("非法文件路径");
            }
            return Files.exists(pending) ? pending : target;
        }
        if (client == null) throw new IOException("对象存储未启用");
        String key = objectKey(attachment.getStoragePath());
        Path scanWork = workDir.resolve(".scan-work").normalize();
        Files.createDirectories(scanWork);
        Path temporary = Files.createTempFile(scanWork, "railway-scan-", ".tmp");
        try (InputStream input =
                client.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(pendingKey(key)).build())) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            return temporary;
        } catch (Exception exception) {
            Files.deleteIfExists(temporary);
            throw new IOException("读取隔离对象失败", exception);
        }
    }

    // 隔离对象发布涉及存储和数据库两个系统，使用可重试步骤而非假设一个本地事务覆盖对象存储；需考虑部分成功。
    public void promote(CheckAttachment attachment, Path workDir) throws IOException {
        if (!isObject(attachment)) {
            Path source =
                    workDir.resolve(".quarantine")
                            .resolve(attachment.getStoredName() + ".pending")
                            .normalize();
            Path target = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
            if (!source.startsWith(workDir.resolve(".quarantine")) || !target.startsWith(workDir)) {
                throw new IOException("非法文件路径");
            }
            if (Files.exists(source)) {
                Files.createDirectories(target.getParent());
                move(source, target);
            }
            return;
        }
        if (client == null) throw new IOException("对象存储未启用");
        String key = objectKey(attachment.getStoragePath());
        try {
            client.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .source(
                                    CopySource.builder()
                                            .bucket(bucket)
                                            .object(pendingKey(key))
                                            .build())
                            .build());
        } catch (Exception exception) {
            throw new IOException("发布安全文件失败", exception);
        }
    }

    public InputStream open(CheckAttachment attachment, Path localRoot) throws IOException {
        if (!isObject(attachment)) {
            Path path = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
            if (!path.startsWith(localRoot)
                    || path.startsWith(localRoot.resolve(".quarantine"))
                    || !Files.isRegularFile(path)) {
                throw new IOException("非法或不存在的附件路径");
            }
            return Files.newInputStream(path);
        }
        if (client == null) throw new IOException("对象存储未启用");
        try {
            return client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey(attachment.getStoragePath()))
                            .build());
        } catch (Exception exception) {
            throw new IOException("读取对象失败", exception);
        }
    }

    public void delete(CheckAttachment attachment, Path localRoot) throws IOException {
        if (!isObject(attachment)) {
            Path target = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
            Path pending =
                    localRoot
                            .resolve(".quarantine")
                            .resolve(attachment.getStoredName() + ".pending")
                            .normalize();
            if (!target.startsWith(localRoot)
                    || target.equals(localRoot)
                    || target.startsWith(localRoot.resolve(".quarantine"))
                    || !pending.startsWith(localRoot.resolve(".quarantine"))) {
                throw new IOException("非法文件路径");
            }
            Files.deleteIfExists(target);
            Files.deleteIfExists(pending);
            return;
        }
        if (client == null) throw new IOException("对象存储未启用");
        String key = objectKey(attachment.getStoragePath());
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            client.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(pendingKey(key)).build());
        } catch (Exception exception) {
            throw new IOException("清理对象失败", exception);
        }
    }

    public void deletePending(String storagePath, String storedName, Path root) {
        try {
            if (storagePath.startsWith("s3://")) {
                String key = objectKey(storagePath);
                client.removeObject(
                        RemoveObjectArgs.builder().bucket(bucket).object(pendingKey(key)).build());
            } else {
                Path pending =
                        root.resolve(".quarantine").resolve(storedName + ".pending").normalize();
                if (pending.startsWith(root.resolve(".quarantine"))) Files.deleteIfExists(pending);
            }
        } catch (Exception exception) {
            log.warn("事务回滚后清理隔离文件失败", exception);
        }
    }

    public boolean isObject(CheckAttachment attachment) {
        return attachment.getStoragePath() != null
                && attachment.getStoragePath().startsWith("s3://");
    }

    private String objectKey(String path) throws IOException {
        String prefix = "s3://" + bucket + "/";
        if (!path.startsWith(prefix)
                || !path.substring(prefix.length())
                        .matches("active/[0-9]{4}-[0-9]{2}-[0-9]{2}/[a-f0-9]{32}\\.[a-z0-9]+")) {
            throw new IOException("非法对象路径");
        }
        return path.substring(prefix.length());
    }

    private String pendingKey(String key) {
        return "quarantine/" + key.substring("active/".length()) + ".pending";
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

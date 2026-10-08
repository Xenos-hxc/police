package com.railway.security.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class FileScanWorkerTest {
    @TempDir Path directory;

    @Test
    void cleanFileMovesOutOfQuarantineOnlyAfterScan() throws Exception {
        var mapper = mock(CheckAttachmentMapper.class);
        var security = mock(FileSecurityService.class);
        var worker =
                new FileScanWorker(
                        mapper,
                        security,
                        new SimpleMeterRegistry(),
                        new AttachmentStorage(
                                false, "http://127.0.0.1:9000", "", "", "unused", true),
                        mock(ConfigMapper.class));
        ReflectionTestUtils.setField(worker, "uploadPath", directory.toString());
        ReflectionTestUtils.setField(worker, "environmentUploadPath", "");
        Path quarantine = directory.resolve(".quarantine");
        Files.createDirectories(quarantine);
        Path source = quarantine.resolve("safe.pdf.pending");
        Files.writeString(source, "test-file");
        Path target = directory.resolve("2026-09-30/safe.pdf");
        var attachment = attachment(target);

        when(mapper.pendingScanIds(10)).thenReturn(List.of(1L));
        when(mapper.claimScan(1L)).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(attachment);
        when(mapper.finishScan(1L, "CLEAN", "digest")).thenReturn(1);
        when(security.scan(source)).thenReturn("CLEAN");
        when(security.sha256(source)).thenReturn("digest");

        worker.scanPending();

        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target));
        verify(mapper).finishScan(1L, "CLEAN", "digest");
    }

    @Test
    void malwareIsNeverReleased() throws Exception {
        var mapper = mock(CheckAttachmentMapper.class);
        var security = mock(FileSecurityService.class);
        var worker =
                new FileScanWorker(
                        mapper,
                        security,
                        new SimpleMeterRegistry(),
                        new AttachmentStorage(
                                false, "http://127.0.0.1:9000", "", "", "unused", true),
                        mock(ConfigMapper.class));
        ReflectionTestUtils.setField(worker, "uploadPath", directory.toString());
        ReflectionTestUtils.setField(worker, "environmentUploadPath", "");
        Path quarantine = directory.resolve(".quarantine");
        Files.createDirectories(quarantine);
        Path source = quarantine.resolve("safe.pdf.pending");
        Files.writeString(source, "test-file");
        Path target = directory.resolve("2026-09-30/safe.pdf");

        when(mapper.pendingScanIds(10)).thenReturn(List.of(1L));
        when(mapper.claimScan(1L)).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(attachment(target));
        when(security.scan(source)).thenThrow(new MalwareDetectedException());

        worker.scanPending();

        assertFalse(Files.exists(source));
        assertFalse(Files.exists(target));
        verify(mapper).failScan(eq(1L), eq("REJECTED"), eq(null), any());
    }

    @Test
    void changedQuarantineContentIsNeverPublished() throws Exception {
        var mapper = mock(CheckAttachmentMapper.class);
        var security = mock(FileSecurityService.class);
        var worker =
                new FileScanWorker(
                        mapper,
                        security,
                        new SimpleMeterRegistry(),
                        new AttachmentStorage(
                                false, "http://127.0.0.1:9000", "", "", "unused", true),
                        mock(ConfigMapper.class));
        ReflectionTestUtils.setField(worker, "uploadPath", directory.toString());
        ReflectionTestUtils.setField(worker, "environmentUploadPath", "");
        Path quarantine = directory.resolve(".quarantine");
        Files.createDirectories(quarantine);
        Path source = Files.writeString(quarantine.resolve("safe.pdf.pending"), "changed");
        Path target = directory.resolve("2026-09-30/safe.pdf");
        var attachment = attachment(target);
        attachment.setSha256("original-digest");
        when(mapper.pendingScanIds(10)).thenReturn(List.of(1L));
        when(mapper.claimScan(1L)).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(attachment);
        when(security.scan(source)).thenReturn("CLEAN");
        when(security.sha256(source)).thenReturn("changed-digest");

        worker.scanPending();

        assertTrue(Files.exists(source));
        assertFalse(Files.exists(target));
        verify(mapper).failScan(eq(1L), eq("PENDING"), any(), any());
    }

    private CheckAttachment attachment(Path target) {
        var attachment = new CheckAttachment();
        attachment.setId(1L);
        attachment.setStoredName("safe.pdf");
        attachment.setStoragePath(target.toString());
        attachment.setScanAttempts(1);
        return attachment;
    }
}

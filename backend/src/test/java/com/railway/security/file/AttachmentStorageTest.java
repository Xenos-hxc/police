package com.railway.security.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.railway.security.persistence.entity.CheckAttachment;
import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class AttachmentStorageTest {
    @TempDir Path root;

    @Test
    void quarantinedFileIsNotReadableUntilPromotion() throws Exception {
        var storage = new AttachmentStorage(false, "http://127.0.0.1:9000", "", "", "test", true);
        Path upload = Files.writeString(root.resolve("upload.tmp"), "test-content");
        Path target = root.resolve("2026-09-30/safe.pdf");
        var attachment = new CheckAttachment();
        attachment.setStoredName("safe.pdf");
        attachment.setStoragePath(storage.putPending(upload, target, "safe.pdf", 12));

        assertFalse(Files.exists(target));
        assertEquals(
                "test-content", Files.readString(storage.materializePending(attachment, root)));
        storage.promote(attachment, root);
        assertTrue(Files.exists(target));
        try (var input = storage.open(attachment, root)) {
            assertEquals("test-content", new String(input.readAllBytes()));
        }
        storage.delete(attachment, root);
        assertFalse(Files.exists(target));
    }

    @Test
    void refusesPathsOutsideUploadRoot() throws Exception {
        var storage = new AttachmentStorage(false, "http://127.0.0.1:9000", "", "", "test", true);
        var attachment = new CheckAttachment();
        attachment.setStoredName("safe.pdf");
        attachment.setStoragePath(root.getParent().resolve("outside.pdf").toString());
        assertThrows(IOException.class, () -> storage.delete(attachment, root));
    }

    @Test
    void objectUploadStaysInQuarantineUntilPromotion() throws Exception {
        var storage =
                new AttachmentStorage(true, "http://127.0.0.1:9000", "key", "secret", "test", true);
        var client = mock(MinioClient.class);
        ReflectionTestUtils.setField(storage, "client", client);
        Path upload = Files.writeString(root.resolve("upload.tmp"), "test-content");
        String storedName = "0123456789abcdef0123456789abcdef.pdf";
        Path target = root.resolve("2026-09-30").resolve(storedName);
        var attachment = new CheckAttachment();
        attachment.setStoragePath(storage.putPending(upload, target, storedName, 12));
        attachment.setStoredName(storedName);

        assertEquals("s3://test/active/2026-09-30/" + storedName, attachment.getStoragePath());
        verify(client).putObject(any(PutObjectArgs.class));
        storage.promote(attachment, root);
        verify(client).copyObject(any(CopyObjectArgs.class));
        storage.delete(attachment, root);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(2))
                .removeObject(any(RemoveObjectArgs.class));
    }
}

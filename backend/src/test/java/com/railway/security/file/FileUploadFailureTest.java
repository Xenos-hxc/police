package com.railway.security.file;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.railway.security.shared.web.GlobalExceptionHandler;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

class FileUploadFailureTest {
    @Test
    void operatingSystemDenialHasSafeActionableResponse() throws Exception {
        assertStorageFailure(new AccessDeniedException("D:/private/quarantine/secret.tmp"));
    }

    @Test
    void storageIOExceptionHasSafeActionableResponse() throws Exception {
        assertStorageFailure(new IOException("D:/private disk full"));
    }

    @Test
    void checkedStorageFailuresRollBackUploadTransaction() throws Exception {
        var transaction =
                FileApplicationService.class
                        .getMethod("upload", Long.class, String.class, MultipartFile.class)
                        .getAnnotation(Transactional.class);
        assertTrue(Arrays.asList(transaction.rollbackFor()).contains(IOException.class));
    }

    private void assertStorageFailure(IOException failure) throws Exception {
        var service = mock(FileApplicationService.class);
        when(service.upload(eq(1L), eq("RECORD"), any())).thenThrow(failure);
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new FileController(service, mock(FileScanEvents.class)))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(
                        multipart("/api/files/upload")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "record.txt",
                                                "text/plain",
                                                new byte[] {65}))
                                .param("taskId", "1")
                                .param("attachmentType", "RECORD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value(503))
                .andExpect(jsonPath("message").value(containsString("请勿关闭安全防护")))
                .andExpect(content().string(not(containsString("D:/private"))));
    }
}

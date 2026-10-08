package com.railway.security.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.railway.security.file.FileController;
import com.railway.security.inspection.TaskController;
import com.railway.security.rectification.HiddenDangerController;
import com.railway.security.statistics.DashboardController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminReadOnlyAuthorizationTest {
    @Test
    void administratorCanReadStatisticsButNotDashboardTasksOrDangers() {
        assertTrue(authorization(DashboardController.class, "overview").contains("ADMIN"));
        assertTrue(authorization(DashboardController.class, "ranking").contains("ADMIN"));
        assertFalse(authorization(DashboardController.class, "summary").contains("ADMIN"));
        assertFalse(classAuthorization(TaskController.class).contains("ADMIN"));
        assertFalse(classAuthorization(HiddenDangerController.class).contains("ADMIN"));
    }

    @Test
    void administratorCanReadAttachmentsButCannotUploadOrDeleteThem() {
        assertTrue(authorization(FileController.class, "detail").contains("ADMIN"));
        assertTrue(authorization(FileController.class, "preview").contains("ADMIN"));
        assertTrue(authorization(FileController.class, "downloadTicket").contains("ADMIN"));
        assertFalse(authorization(FileController.class, "upload").contains("ADMIN"));
        assertFalse(authorization(FileController.class, "delete").contains("ADMIN"));
    }

    private String authorization(Class<?> type, String methodName) {
        Method method =
                Arrays.stream(type.getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        return annotation == null ? "" : annotation.value();
    }

    private String classAuthorization(Class<?> type) {
        PreAuthorize annotation = type.getAnnotation(PreAuthorize.class);
        return annotation == null ? "" : annotation.value();
    }
}

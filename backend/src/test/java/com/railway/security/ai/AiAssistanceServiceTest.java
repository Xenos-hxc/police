package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.security.file.FileApplicationService;
import com.railway.security.inspection.TaskService;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
// AI 测试覆盖部门隔离、引用、人工审核和失败路径；Mock 断言验证工程契约，不等于真实模型质量评测。
class AiAssistanceServiceTest {
    @Mock ChatModel model;
    @Mock VectorStore vectors;
    @Mock JdbcTemplate jdbc;
    @Mock CheckTaskMapper tasks;
    @Mock CheckRecordMapper records;
    @Mock TaskService taskService;
    @Mock DataScopeService scope;
    @Mock FileApplicationService files;
    @Mock MaterialTextExtractor extractor;
    AiAssistanceService service;

    @BeforeEach
    void setup() {
        service =
                new AiAssistanceService(
                        model,
                        vectors,
                        jdbc,
                        tasks,
                        records,
                        taskService,
                        scope,
                        files,
                        extractor,
                        new ObjectMapper(),
                        new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxBytes", 1024);
    }

    @Test
    void expiredRunsBecomeReplayableWithoutTouchingBusinessTables() {
        when(jdbc.update(any(String.class))).thenReturn(2);
        service.recoverExpiredRuns();
        var update = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(update.capture());
        assertTrue(update.getValue().startsWith("UPDATE ai_assistance_run"));
        assertTrue(update.getValue().contains("status='RUNNING'"));
        assertTrue(update.getValue().contains("INTERVAL 30 MINUTE"));
    }

    @Test
    void disabledFeatureNeverRecoversRuns() {
        ReflectionTestUtils.setField(service, "enabled", false);
        service.recoverExpiredRuns();
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void noDepartmentEvidenceMustAbstainWithoutCallingModel() {
        var task = new CheckTask();
        task.setId(7L);
        task.setExecutorDeptId(42L);
        when(tasks.selectById(7L)).thenReturn(task);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var answer = service.answer(7L, "整改期限的依据是什么？");
        assertTrue(answer.insufficientEvidence());
        assertTrue(answer.citations().isEmpty());
        ArgumentCaptor<SearchRequest> search = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectors).similaritySearch(search.capture());
        assertTrue(search.getValue().getFilterExpression().toString().contains("42"));
        assertEquals(
                new org.springframework.ai.vectorstore.filter.Filter.Value("42"),
                search.getValue().getFilterExpression().right());
        verify(model, never()).call(any(String.class));
    }

    @Test
    void crossDepartmentVectorHitNeverEntersPrompt() {
        var task = new CheckTask();
        task.setId(7L);
        task.setExecutorDeptId(42L);
        when(tasks.selectById(7L)).thenReturn(task);
        when(vectors.similaritySearch(any(SearchRequest.class)))
                .thenReturn(
                        List.of(
                                Document.builder()
                                        .text("其他部门内部规定")
                                        .metadata(Map.of("deptId", 99L, "policyId", 4L))
                                        .build()));
        var answer = service.answer(7L, "如何整改？");
        assertTrue(answer.insufficientEvidence());
        verify(model, never()).call(any(String.class));
        verify(jdbc, never())
                .query(
                        any(String.class),
                        any(org.springframework.jdbc.core.RowMapper.class),
                        any());
    }

    @Test
    void taskAuthorizationRunsBeforeVectorSearch() {
        var task = new CheckTask();
        when(tasks.selectById(7L)).thenReturn(task);
        org.mockito.Mockito.doThrow(new BusinessException(403, "无权访问"))
                .when(taskService)
                .assertTaskAccess(task);
        assertThrows(BusinessException.class, () -> service.answer(7L, "什么依据？"));
        verify(vectors, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void quarantinedAttachmentCannotReachExtractorOrModel() throws Exception {
        var attachment = new CheckAttachment();
        attachment.setId(9L);
        attachment.setTaskId(7L);
        attachment.setStorageStatus("QUARANTINED");
        attachment.setScanStatus("PENDING");
        when(files.detail(9L)).thenReturn(attachment);
        var task = new CheckTask();
        task.setId(7L);
        when(tasks.selectById(7L)).thenReturn(task);
        assertEquals(
                409, assertThrows(BusinessException.class, () -> service.quality(9L)).getCode());
        verify(extractor, never()).extract(any(), any());
        verify(model, never()).call(any(String.class));
    }
}

package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.inspection.TaskApplicationService;
import com.railway.security.inspection.TaskExcelService;
import com.railway.security.inspection.TaskSelectionService;
import com.railway.security.inspection.TaskService;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TaskApplicationServiceTest {
    @Mock private CheckTaskMapper taskMapper;
    @Mock private TaskService taskService;
    @Mock private CheckRecordMapper recordMapper;
    @Mock private CheckAttachmentMapper attachmentMapper;
    @Mock private TaskExcelService taskExcelService;
    @Mock private TaskSelectionService taskSelectionService;
    @Mock private StatisticsCacheService statisticsCacheService;
    @Mock private SystemPeriodService systemPeriodService;
    @InjectMocks private TaskApplicationService service;

    @BeforeEach
    void authenticate() {
        SysUser user = new SysUser();
        user.setId(3L);
        user.setDeptId(101L);
        user.setUsername("station");
        user.setPassword("encoded");
        user.setRealName("测试派出所");
        user.setDataScope("DEPT");
        user.setStatus(1);
        LoginUser principal = new LoginUser(user, List.of("STATION"), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listBoundsPaginationAndReturnsMapperPage() {
        Page<CheckTask> mapperPage = new Page<>(1, 100);
        mapperPage.setRecords(List.of(task(1L, "PENDING")));
        mapperPage.setTotal(1);
        when(taskMapper.selectPage(any(), any())).thenReturn(mapperPage);

        var result = service.list(criteria(0, 999, "UNFINISHED"));

        assertEquals(1, result.total());
        assertEquals(1L, result.records().get(0).getId());
    }

    @Test
    void summaryUsesRequirementsAndClampsExtraCompletedRows() {
        when(taskMapper.selectCount(any())).thenReturn(3L);
        when(taskSelectionService.statuses(2026, 3))
                .thenReturn(
                        List.of(
                                new TaskSelectionService.SelectionStatus(
                                        "KEY_UNIT", "重点单位", 2, 0, 0, 2, false, true, true)));

        var summary = service.summary(criteria(1, 10, ""));

        assertEquals(2, summary.totalCount());
        assertEquals(2, summary.completedCount());
        assertEquals(0, summary.unfinishedCount());
        assertEquals(100D, summary.completionRate());
        verify(taskMapper).selectCount(any());
    }

    @Test
    void detailFullDetailExportAndOverduePageUseApplicationBoundary() {
        CheckTask task = task(8L, "PENDING");
        CheckRecord record = new CheckRecord();
        record.setId(9L);
        CheckAttachment attachment = new CheckAttachment();
        attachment.setId(10L);
        when(taskMapper.selectById(8L)).thenReturn(task);
        when(recordMapper.selectOne(any())).thenReturn(record);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(taskExcelService.export(List.of(task))).thenReturn(new byte[] {1, 2, 3});
        Page<CheckTask> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(task));
        mapperPage.setTotal(1);
        when(taskMapper.selectPage(any(), any())).thenReturn(mapperPage);

        assertEquals(task, service.detail(8L));
        assertEquals(record, service.fullDetail(8L).record());
        assertEquals(attachment, service.fullDetail(8L).attachments().get(0));
        assertArrayEquals(new byte[] {1, 2, 3}, service.export(criteria(1, 10, "")));
        assertEquals(1, service.overdueReminders(-1, 500).total());
    }

    @Test
    void updateRejectsFrozenFieldsAndClearsCacheAfterSuccess() {
        CheckTask persisted = task(8L, "PENDING");
        persisted.setStartDate(LocalDate.of(2026, 7, 1));
        persisted.setDeadline(LocalDateTime.of(2026, 9, 30, 23, 59));
        when(taskMapper.selectById(8L)).thenReturn(persisted);

        CheckTask invalid = new CheckTask();
        invalid.setDeadline(persisted.getDeadline().plusDays(1));
        assertThrows(BusinessException.class, () -> service.update(8L, invalid));

        CheckTask update = new CheckTask();
        update.setDeadline(persisted.getDeadline());
        update.setStartDate(persisted.getStartDate());
        update.setRemark("现场复核");
        when(taskMapper.updateById(persisted)).thenReturn(1);

        service.update(8L, update);

        assertEquals("现场复核", persisted.getRemark());
        verify(statisticsCacheService).clear();
    }

    private TaskApplicationService.TaskSearchCriteria criteria(
            long page, long size, String completion) {
        return new TaskApplicationService.TaskSearchCriteria(
                page,
                size,
                "",
                "INTERNAL_SECURITY",
                "",
                2026,
                3,
                null,
                null,
                "KEY_UNIT",
                "",
                completion,
                "SYSTEM");
    }

    private CheckTask task(Long id, String status) {
        CheckTask task = new CheckTask();
        task.setId(id);
        task.setExecutorDeptId(101L);
        task.setTaskYear(2026);
        task.setQuarter(3);
        task.setTargetType("KEY_UNIT");
        task.setStatus(status);
        task.setCountCoverage(1);
        return task;
    }
}

package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.railway.security.inspection.TaskSelectionService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.statistics.DashboardService;
import com.railway.security.system.SystemPeriodService;
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
class DashboardServiceTest {
    @Mock private CheckTaskMapper taskMapper;
    @Mock private TaskSelectionService taskSelectionService;
    @Mock private SystemPeriodService systemPeriodService;
    @InjectMocks private DashboardService service;

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
    void summaryUsesConfiguredRequirementsAndCompletedTasks() {
        stubPeriodAndRequirements();
        when(taskMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                task(1L, "KEY_UNIT", "APPROVED", 1),
                                task(2L, "KEY_UNIT", "PENDING", 1),
                                task(3L, "IMPORTANT_PART", "OVERDUE_SUBMITTED", 1),
                                task(4L, "IMPORTANT_PART", "PENDING", 0)));

        var summary = service.summary();

        assertEquals(2, summary.coveredCount());
        assertEquals(6, summary.uncoveredCount());
        assertEquals(25D, summary.coverageRate());
        assertEquals(1, summary.pendingUploadCount());
        assertEquals(2026, summary.year());
        assertEquals(3, summary.quarter());
    }

    @Test
    void chartsAndTodoReturnStableCompletionAndPagination() {
        stubPeriodAndRequirements();
        when(taskMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                task(1L, "KEY_UNIT", "APPROVED", 1),
                                task(2L, "KEY_UNIT", "PENDING", 1),
                                task(3L, "IMPORTANT_PART", "OVERDUE", 1)));

        var charts = service.charts();
        var todo = service.todo(0, 1);

        assertEquals(1, charts.targetCompletion().get("KEY_UNIT").completed());
        assertEquals(2, charts.targetCompletion().get("KEY_UNIT").unfinished());
        assertEquals(5, charts.targetCompletion().get("IMPORTANT_PART").unfinished());
        assertEquals(2, todo.total());
        assertEquals(1, todo.records().size());
    }

    @Test
    void warningsMergeSelectionWarningsWithDueAndOverdueTasks() {
        when(systemPeriodService.effectivePeriod())
                .thenReturn(new SystemPeriodService.Period(2026, 3));
        CheckTask selectionWarning = task(10L, "KEY_UNIT", "PENDING", 1);
        selectionWarning.setDeadline(null);
        when(taskSelectionService.selectionWarningTasks(2026, 3))
                .thenReturn(List.of(selectionWarning));
        CheckTask overdue = task(11L, "IMPORTANT_PART", "OVERDUE", 1);
        overdue.setDeadline(LocalDateTime.now().minusDays(1));
        CheckTask distant = task(12L, "IMPORTANT_PART", "PENDING", 1);
        distant.setDeadline(LocalDateTime.now().plusDays(20));
        when(taskMapper.selectList(any())).thenReturn(List.of(overdue, distant));

        var result = service.warnings(1, 10);

        assertEquals(2, result.total());
        assertEquals(11L, result.records().get(0).getId());
    }

    private void stubPeriodAndRequirements() {
        when(systemPeriodService.effectivePeriod())
                .thenReturn(new SystemPeriodService.Period(2026, 3));
        when(taskSelectionService.statuses(2026, 3))
                .thenReturn(List.of(status("KEY_UNIT", 3), status("IMPORTANT_PART", 5)));
    }

    private TaskSelectionService.SelectionStatus status(String type, int required) {
        return new TaskSelectionService.SelectionStatus(
                type, type, required, 0, 0, required, false, true, true);
    }

    private CheckTask task(Long id, String type, String status, int countCoverage) {
        CheckTask task = new CheckTask();
        task.setId(id);
        task.setExecutorDeptId(101L);
        task.setTargetType(type);
        task.setStatus(status);
        task.setCountCoverage(countCoverage);
        task.setDeadline(LocalDateTime.now().plusDays(id));
        return task;
    }
}

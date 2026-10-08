package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.inspection.TaskSelectionService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.entity.TaskPeriodSnapshot;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.persistence.mapper.TaskPeriodSnapshotMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TaskSelectionServiceTest {
    @Mock private CheckTaskMapper taskMapper;
    @Mock private TaskPeriodSnapshotMapper snapshotMapper;
    @Mock private PoliceStationMapper stationMapper;
    @Mock private KeyUnitMapper unitMapper;
    @Mock private ImportantPartMapper partMapper;
    @Mock private TargetJurisdictionMapper jurisdictionMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private ConfigMapper configMapper;
    @Mock private StatisticsCacheService statisticsCacheService;
    @Mock private DataScopeService dataScopeService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void randomSavePersistsExactlyConfirmedTargetIds() {
        loginBureau();
        LocalDate now = LocalDate.now();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        TaskPeriodSnapshot snapshot = snapshot(now.getYear(), quarter, "OPEN");
        List<CheckTask> tasks = List.of(task(11L, "甲"), task(12L, "乙"), task(13L, "丙"));
        when(deptMapper.selectById(1L)).thenReturn(bureau());
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(taskMapper.selectList(any())).thenReturn(tasks, List.of(), tasks, List.of());
        when(taskMapper.updateById(any(CheckTask.class))).thenReturn(1);

        TaskSelectionService.SelectionPool result =
                service()
                        .save(
                                new TaskSelectionService.SelectionSaveRequest(
                                        "KEY_UNIT",
                                        now.getYear(),
                                        quarter,
                                        "RANDOM",
                                        List.of(11L, 13L)));

        List<Long> selectedIds =
                result.targets().stream()
                        .filter(TaskSelectionService.SelectionTarget::selected)
                        .map(TaskSelectionService.SelectionTarget::targetId)
                        .sorted()
                        .toList();
        assertEquals(List.of(11L, 13L), selectedIds);
        assertEquals("RANDOM", tasks.get(0).getCreationMode());
        assertEquals(0, tasks.get(1).getCountCoverage());
        assertEquals("RANDOM", tasks.get(2).getCreationMode());
    }

    @Test
    void sealedHistoricalSnapshotCannotBeReselected() {
        loginBureau();
        when(deptMapper.selectById(1L)).thenReturn(bureau());
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot(2026, 3, "SEALED"));

        assertThrows(
                BusinessException.class,
                () ->
                        service()
                                .save(
                                        new TaskSelectionService.SelectionSaveRequest(
                                                "KEY_UNIT", 2026, 3, "MANUAL", List.of())));
        verify(taskMapper, never()).updateById(any(CheckTask.class));
    }

    @Test
    void startupInitializationDoesNotCreateBusinessRowsWithoutTargets() {
        when(deptMapper.selectList(any())).thenReturn(List.of(bureau()));

        service().initializeCurrentPeriodForAll();

        verify(snapshotMapper, never()).insertIgnore(any(TaskPeriodSnapshot.class));
        verify(taskMapper, never()).insert(any(CheckTask.class));
    }

    private TaskSelectionService service() {
        return new TaskSelectionService(
                taskMapper,
                snapshotMapper,
                stationMapper,
                unitMapper,
                partMapper,
                jurisdictionMapper,
                deptMapper,
                configMapper,
                statisticsCacheService,
                dataScopeService,
                new SystemPeriodService(configMapper));
    }

    private TaskPeriodSnapshot snapshot(int year, int quarter, String status) {
        TaskPeriodSnapshot snapshot = new TaskPeriodSnapshot();
        snapshot.setExecutorDeptId(1L);
        snapshot.setExecutorDeptType("BUREAU");
        snapshot.setTargetType("KEY_UNIT");
        snapshot.setTaskYear(year);
        snapshot.setQuarter(quarter);
        snapshot.setTotalCount(3);
        snapshot.setRequiredCount(2);
        snapshot.setCoveragePercent(25);
        snapshot.setSnapshotStatus(status);
        return snapshot;
    }

    private CheckTask task(Long targetId, String name) {
        CheckTask task = new CheckTask();
        task.setId(100L + targetId);
        task.setTargetId(targetId);
        task.setTargetType("KEY_UNIT");
        task.setTargetName(name);
        task.setStatus("PENDING");
        task.setCountCoverage(0);
        task.setVersion(0);
        return task;
    }

    private SysDept bureau() {
        SysDept dept = new SysDept();
        dept.setId(1L);
        dept.setDeptType("BUREAU");
        return dept;
    }

    private void loginBureau() {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setDeptId(1L);
        user.setUsername("bureau-test");
        user.setPassword("unused");
        user.setRealName("test");
        user.setStatus(1);
        user.setDataScope("ALL");
        user.setForceChangePassword(0);
        user.setTokenVersion(0);
        LoginUser principal = new LoginUser(user, List.of("BUREAU"), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }
}

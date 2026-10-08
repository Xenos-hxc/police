package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.railway.security.inspection.StationInspectionService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.TaskPeriodSnapshot;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.TaskPeriodSnapshotMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StationInspectionServiceTest {
    @Mock private DeptMapper deptMapper;
    @Mock private TaskPeriodSnapshotMapper snapshotMapper;
    @Mock private CheckTaskMapper taskMapper;
    @Mock private CheckRecordMapper recordMapper;
    @Mock private CheckAttachmentMapper attachmentMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private SystemPeriodService systemPeriodService;

    @Test
    void progressUsesQuarterSnapshotRequirementsInsteadOfExistingTaskCount() {
        SysDept station = station();
        when(deptMapper.selectList(any())).thenReturn(List.of(station));
        when(deptMapper.selectBatchIds(List.of(101L))).thenReturn(List.of(station));
        when(snapshotMapper.selectList(any()))
                .thenReturn(List.of(snapshot("KEY_UNIT", 3), snapshot("IMPORTANT_PART", 5)));
        when(taskMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                task(1L, "KEY_UNIT", "APPROVED"),
                                task(2L, "KEY_UNIT", "OVERDUE_SUBMITTED"),
                                task(3L, "IMPORTANT_PART", "APPROVED")));

        var result = service().progress(2026, 1, null, null, 1, 10);
        var quarter = result.records().get(0).progress();

        assertEquals(8, quarter.requiredCount());
        assertEquals(3, quarter.completedCount());
        assertEquals(5, quarter.unfinishedCount());
        assertEquals(2, quarter.keyUnit().completedCount());
        assertEquals(1, quarter.importantPart().completedCount());
        assertEquals(37.5D, quarter.completionRate());
    }

    @Test
    void progressSortsAllStationsByCompletionRateBeforePaging() {
        SysDept first = station(101L, "甲派出所");
        SysDept second = station(102L, "乙派出所");
        when(deptMapper.selectList(any())).thenReturn(List.of(first, second));
        when(deptMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(first, second));
        when(snapshotMapper.selectList(any()))
                .thenReturn(List.of(snapshot(101L, "KEY_UNIT", 4), snapshot(102L, "KEY_UNIT", 4)));
        when(taskMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                task(101L, 1L, "KEY_UNIT", "APPROVED"),
                                task(102L, 2L, "KEY_UNIT", "APPROVED"),
                                task(102L, 3L, "KEY_UNIT", "APPROVED"),
                                task(102L, 4L, "KEY_UNIT", "APPROVED")));

        var result = service().progress(2026, 1, null, "DESC", 1, 1);

        assertEquals(2, result.total());
        assertEquals(1, result.records().size());
        assertEquals(102L, result.records().get(0).deptId());
        assertEquals(75D, result.records().get(0).progress().completionRate());
    }

    @Test
    void progressRejectsFutureQuarter() {
        assertThrows(RuntimeException.class, () -> service().progress(2100, 1, null, null, 1, 10));
    }

    private StationInspectionService service() {
        when(systemPeriodService.effectivePeriod())
                .thenReturn(new SystemPeriodService.Period(2026, 3));
        return new StationInspectionService(
                deptMapper,
                snapshotMapper,
                taskMapper,
                recordMapper,
                attachmentMapper,
                dataScopeService,
                systemPeriodService);
    }

    private SysDept station() {
        return station(101L, "示例车站派出所");
    }

    private SysDept station(Long id, String name) {
        SysDept station = new SysDept();
        station.setId(id);
        station.setDeptName(name);
        station.setDeptType("STATION");
        station.setStatus(1);
        station.setArchived(0);
        return station;
    }

    private TaskPeriodSnapshot snapshot(String targetType, int requiredCount) {
        return snapshot(101L, targetType, requiredCount);
    }

    private TaskPeriodSnapshot snapshot(Long executorDeptId, String targetType, int requiredCount) {
        TaskPeriodSnapshot snapshot = new TaskPeriodSnapshot();
        snapshot.setExecutorDeptId(executorDeptId);
        snapshot.setExecutorDeptType("STATION");
        snapshot.setTargetType(targetType);
        snapshot.setTaskYear(2026);
        snapshot.setQuarter(1);
        snapshot.setRequiredCount(requiredCount);
        return snapshot;
    }

    private CheckTask task(Long targetId, String targetType, String status) {
        return task(101L, targetId, targetType, status);
    }

    private CheckTask task(Long executorDeptId, Long targetId, String targetType, String status) {
        CheckTask task = new CheckTask();
        task.setId(targetId);
        task.setExecutorDeptId(executorDeptId);
        task.setTargetId(targetId);
        task.setTargetType(targetType);
        task.setTaskYear(2026);
        task.setQuarter(1);
        task.setStatus(status);
        task.setCountCoverage(1);
        return task;
    }
}

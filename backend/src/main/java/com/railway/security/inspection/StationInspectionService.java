package com.railway.security.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.TaskPeriodSnapshot;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.TaskPeriodSnapshotMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.SystemPeriodService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StationInspectionService {
    private static final String CHECK_TYPE = "INTERNAL_SECURITY";
    private static final List<String> TARGET_TYPES = List.of("KEY_UNIT", "IMPORTANT_PART");
    private static final Set<String> STATUSES =
            Set.of("PENDING", "APPROVED", "OVERDUE", "OVERDUE_SUBMITTED");

    private final DeptMapper deptMapper;
    private final TaskPeriodSnapshotMapper snapshotMapper;
    private final CheckTaskMapper taskMapper;
    private final CheckRecordMapper recordMapper;
    private final CheckAttachmentMapper attachmentMapper;
    private final DataScopeService dataScopeService;
    private final SystemPeriodService systemPeriodService;

    public PageResult<StationProgress> progress(
            Integer year,
            Integer quarter,
            String stationName,
            String completionOrder,
            int page,
            int size) {
        int taskYear = safeYear(year);
        int taskQuarter = safeQuarter(quarter);
        checkViewablePeriod(taskYear, taskQuarter);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        var stations =
                deptMapper.selectList(
                        new LambdaQueryWrapper<SysDept>()
                                .eq(SysDept::getDeptType, "STATION")
                                .eq(SysDept::getStatus, 1)
                                .eq(SysDept::getArchived, 0)
                                .like(hasText(stationName), SysDept::getDeptName, stationName)
                                .orderByAsc(SysDept::getSortNo)
                                .orderByAsc(SysDept::getId));
        Map<Long, QuarterProgress> progress =
                quarterProgressByStation(
                        stations.stream().map(SysDept::getId).toList(), taskYear, taskQuarter);
        List<StationProgress> allRecords =
                new ArrayList<>(
                        stations.stream()
                                .map(
                                        station ->
                                                new StationProgress(
                                                        station.getId(),
                                                        station.getDeptName(),
                                                        taskYear,
                                                        taskQuarter,
                                                        progress.getOrDefault(
                                                                station.getId(),
                                                                emptyQuarter(taskQuarter))))
                                .toList());
        sortByCompletionRate(allRecords, completionOrder);
        int from = Math.min((safePage - 1) * safeSize, allRecords.size());
        int to = Math.min(from + safeSize, allRecords.size());
        return new PageResult<>(allRecords.size(), allRecords.subList(from, to));
    }

    public StationDetail detail(
            Long stationDeptId,
            Integer year,
            Integer quarter,
            String targetType,
            String targetName,
            String status,
            int page,
            int size) {
        int taskYear = safeYear(year);
        int taskQuarter = safeQuarter(quarter);
        checkViewablePeriod(taskYear, taskQuarter);
        String normalizedTargetType = targetType(targetType);
        String normalizedStatus = status(status);
        SysDept station = requiredStation(stationDeptId);
        dataScopeService.checkDept(stationDeptId);

        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getExecutorDeptId, stationDeptId)
                        .eq(CheckTask::getTaskYear, taskYear)
                        .eq(CheckTask::getQuarter, taskQuarter)
                        .eq(CheckTask::getCheckType, CHECK_TYPE)
                        .eq(CheckTask::getTargetType, normalizedTargetType)
                        .like(hasText(targetName), CheckTask::getTargetName, targetName)
                        .eq(hasText(normalizedStatus), CheckTask::getStatus, normalizedStatus)
                        .and(
                                w ->
                                        w.eq(CheckTask::getCountCoverage, 1)
                                                .or()
                                                .in(
                                                        CheckTask::getStatus,
                                                        List.of("APPROVED", "OVERDUE_SUBMITTED")))
                        .last(
                                "ORDER BY CASE status WHEN 'PENDING' THEN 0 WHEN 'OVERDUE' THEN 1 "
                                        + "WHEN 'OVERDUE_SUBMITTED' THEN 2 WHEN 'APPROVED' THEN 3 ELSE 4 END, target_name ASC");
        var taskPage =
                taskMapper.selectPage(
                        new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 100))), wrapper);
        List<Long> taskIds = taskPage.getRecords().stream().map(CheckTask::getId).toList();

        Map<Long, CheckRecord> records =
                taskIds.isEmpty()
                        ? Map.of()
                        : recordMapper
                                .selectList(
                                        new LambdaQueryWrapper<CheckRecord>()
                                                .in(CheckRecord::getTaskId, taskIds))
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                CheckRecord::getTaskId,
                                                Function.identity(),
                                                (a, b) -> a));
        Map<Long, Long> attachmentCounts =
                taskIds.isEmpty()
                        ? Map.of()
                        : attachmentMapper
                                .selectList(
                                        new LambdaQueryWrapper<CheckAttachment>()
                                                .in(CheckAttachment::getTaskId, taskIds))
                                .stream()
                                .collect(
                                        Collectors.groupingBy(
                                                CheckAttachment::getTaskId, Collectors.counting()));
        List<InspectionItem> items =
                taskPage.getRecords().stream()
                        .map(
                                task ->
                                        new InspectionItem(
                                                task,
                                                records.get(task.getId()),
                                                attachmentCounts.getOrDefault(task.getId(), 0L)))
                        .toList();

        QuarterProgress quarterProgress =
                quarterProgressByStation(List.of(stationDeptId), taskYear, taskQuarter)
                        .getOrDefault(stationDeptId, emptyQuarter(taskQuarter));
        return new StationDetail(
                new StationInfo(station.getId(), station.getDeptName()),
                quarterProgress,
                new PageResult<>(taskPage.getTotal(), items));
    }

    private Map<Long, QuarterProgress> quarterProgressByStation(
            List<Long> stationDeptIds, int year, int quarter) {
        if (stationDeptIds.isEmpty()) return Map.of();
        List<TaskPeriodSnapshot> snapshots =
                snapshotMapper.selectList(
                        new LambdaQueryWrapper<TaskPeriodSnapshot>()
                                .in(TaskPeriodSnapshot::getExecutorDeptId, stationDeptIds)
                                .eq(TaskPeriodSnapshot::getExecutorDeptType, "STATION")
                                .eq(TaskPeriodSnapshot::getTaskYear, year)
                                .eq(TaskPeriodSnapshot::getQuarter, quarter)
                                .in(TaskPeriodSnapshot::getTargetType, TARGET_TYPES));
        List<CheckTask> tasks =
                taskMapper.selectList(
                        new LambdaQueryWrapper<CheckTask>()
                                .in(CheckTask::getExecutorDeptId, stationDeptIds)
                                .eq(CheckTask::getTaskYear, year)
                                .eq(CheckTask::getQuarter, quarter)
                                .eq(CheckTask::getCheckType, CHECK_TYPE)
                                .in(CheckTask::getTargetType, TARGET_TYPES)
                                .and(
                                        w ->
                                                w.eq(CheckTask::getCountCoverage, 1)
                                                        .or()
                                                        .in(
                                                                CheckTask::getStatus,
                                                                List.of(
                                                                        "APPROVED",
                                                                        "OVERDUE_SUBMITTED"))));
        Map<ProgressKey, TaskPeriodSnapshot> snapshotMap =
                snapshots.stream()
                        .collect(
                                Collectors.toMap(
                                        value ->
                                                new ProgressKey(
                                                        value.getExecutorDeptId(),
                                                        value.getTargetType(),
                                                        value.getQuarter()),
                                        Function.identity(),
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        Map<ProgressKey, List<CheckTask>> taskMap =
                tasks.stream()
                        .collect(
                                Collectors.groupingBy(
                                        value ->
                                                new ProgressKey(
                                                        value.getExecutorDeptId(),
                                                        value.getTargetType(),
                                                        value.getQuarter()),
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        Map<Long, SysDept> stationMap =
                deptMapper.selectBatchIds(stationDeptIds).stream()
                        .collect(
                                Collectors.toMap(SysDept::getId, Function.identity(), (a, b) -> a));

        Map<Long, QuarterProgress> result = new LinkedHashMap<>();
        for (Long stationDeptId : stationDeptIds) {
            if (!stationMap.containsKey(stationDeptId)) continue;
            TargetProgress unit =
                    targetProgress(
                            snapshotMap,
                            taskMap,
                            new ProgressKey(stationDeptId, "KEY_UNIT", quarter));
            TargetProgress part =
                    targetProgress(
                            snapshotMap,
                            taskMap,
                            new ProgressKey(stationDeptId, "IMPORTANT_PART", quarter));
            result.put(stationDeptId, quarterProgress(quarter, unit, part));
        }
        return result;
    }

    private void sortByCompletionRate(List<StationProgress> records, String completionOrder) {
        if (!hasText(completionOrder)) return;
        String order = completionOrder.trim().toUpperCase();
        Comparator<StationProgress> byRate =
                Comparator.comparingDouble(item -> item.progress().completionRate());
        if ("DESC".equals(order)) {
            byRate = byRate.reversed();
        } else if (!"ASC".equals(order)) {
            throw new BusinessException("完成率排序方式无效");
        }
        records.sort(byRate.thenComparing(StationProgress::stationName));
    }

    private TargetProgress targetProgress(
            Map<ProgressKey, TaskPeriodSnapshot> snapshots,
            Map<ProgressKey, List<CheckTask>> tasks,
            ProgressKey key) {
        TaskPeriodSnapshot snapshot = snapshots.get(key);
        List<CheckTask> rows = tasks.getOrDefault(key, List.of());
        long selected =
                rows.stream()
                        .filter(
                                task ->
                                        Integer.valueOf(1).equals(task.getCountCoverage())
                                                || isCompleted(task))
                        .map(CheckTask::getTargetId)
                        .distinct()
                        .count();
        long completedRaw =
                rows.stream()
                        .filter(this::isCompleted)
                        .map(CheckTask::getTargetId)
                        .distinct()
                        .count();
        long required =
                snapshot == null
                        ? selected
                        : Math.max(
                                0,
                                snapshot.getRequiredCount() == null
                                        ? 0
                                        : snapshot.getRequiredCount());
        if (required == 0 && completedRaw > 0) required = completedRaw;
        long completed = Math.min(required, completedRaw);
        long overdue =
                rows.stream()
                        .filter(task -> "OVERDUE".equals(task.getStatus()))
                        .map(CheckTask::getTargetId)
                        .distinct()
                        .count();
        return new TargetProgress(
                snapshot != null || !rows.isEmpty(),
                required,
                completed,
                Math.max(0, required - completed),
                overdue,
                percent(completed, required));
    }

    private QuarterProgress quarterProgress(int quarter, TargetProgress unit, TargetProgress part) {
        long required = unit.requiredCount() + part.requiredCount();
        long completed = unit.completedCount() + part.completedCount();
        long overdue = unit.overdueCount() + part.overdueCount();
        return new QuarterProgress(
                quarter,
                unit.initialized() || part.initialized(),
                unit,
                part,
                required,
                completed,
                Math.max(0, required - completed),
                overdue,
                percent(completed, required));
    }

    private QuarterProgress emptyQuarter(int quarter) {
        var empty = new TargetProgress(false, 0, 0, 0, 0, 0D);
        return quarterProgress(quarter, empty, empty);
    }

    private SysDept requiredStation(Long deptId) {
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null || !"STATION".equals(dept.getDeptType())) {
            throw new BusinessException("派出所不存在");
        }
        return dept;
    }

    private int safeYear(Integer year) {
        int value = year == null ? systemPeriodService.effectivePeriod().year() : year;
        if (value < 2024 || value > 2100) throw new BusinessException("年度范围无效");
        return value;
    }

    private int safeQuarter(Integer quarter) {
        int value = quarter == null ? systemPeriodService.effectivePeriod().quarter() : quarter;
        if (value < 1 || value > 4) throw new BusinessException("季度范围无效");
        return value;
    }

    private void checkViewablePeriod(int year, int quarter) {
        systemPeriodService.assertIncluded(year, quarter);
        SystemPeriodService.Period maximum = systemPeriodService.effectivePeriod();
        if (new SystemPeriodService.Period(year, quarter).compareTo(maximum) > 0) {
            throw new BusinessException("只能查看当前季度及之前的季度");
        }
    }

    private String targetType(String value) {
        String type = hasText(value) ? value.trim().toUpperCase() : "KEY_UNIT";
        if (!TARGET_TYPES.contains(type)) throw new BusinessException("检查对象类型无效");
        return type;
    }

    private String status(String value) {
        if (!hasText(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) throw new BusinessException("任务状态无效");
        return normalized;
    }

    private boolean isCompleted(CheckTask task) {
        return "APPROVED".equals(task.getStatus()) || "OVERDUE_SUBMITTED".equals(task.getStatus());
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0D : Math.round(numerator * 10000D / denominator) / 100D;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record StationInfo(Long deptId, String stationName) {}

    public record TargetProgress(
            boolean initialized,
            long requiredCount,
            long completedCount,
            long unfinishedCount,
            long overdueCount,
            double completionRate) {}

    public record QuarterProgress(
            int quarter,
            boolean initialized,
            TargetProgress keyUnit,
            TargetProgress importantPart,
            long requiredCount,
            long completedCount,
            long unfinishedCount,
            long overdueCount,
            double completionRate) {}

    public record StationProgress(
            Long deptId, String stationName, int year, int quarter, QuarterProgress progress) {}

    public record InspectionItem(CheckTask task, CheckRecord record, long attachmentCount) {}

    public record StationDetail(
            StationInfo station, QuarterProgress progress, PageResult<InspectionItem> tasks) {}

    private record ProgressKey(Long stationDeptId, String targetType, Integer quarter) {}
}

package com.railway.security.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.inspection.TaskSelectionService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final CheckTaskMapper taskMapper;
    private final TaskSelectionService taskSelectionService;
    private final SystemPeriodService systemPeriodService;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        var tasks = coverageTasks();
        long required =
                currentQuarterRequirements().stream()
                        .mapToLong(TaskSelectionService.SelectionStatus::requiredCount)
                        .sum();
        long completed = tasks.stream().filter(this::isSubmittedStatus).count();
        long overdue =
                tasks.stream().filter(task -> Integer.valueOf(1).equals(task.getOverdue())).count();
        long pendingUpload =
                tasks.stream()
                        .filter(task -> List.of("PENDING", "OVERDUE").contains(task.getStatus()))
                        .count();
        var period = systemPeriodService.effectivePeriod();
        return new DashboardSummary(
                percent(completed, required),
                percent(completed, required),
                overdue,
                pendingUpload,
                completed,
                Math.max(0, required - completed),
                completed,
                Math.max(0, required - completed),
                period.year(),
                period.quarter());
    }

    @Transactional(readOnly = true)
    public DashboardCharts charts() {
        var tasks = coverageTasks();
        var requirements = currentQuarterRequirements();
        Map<String, Long> status =
                new LinkedHashMap<>(
                        tasks.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                CheckTask::getStatus, Collectors.counting())));
        long required =
                requirements.stream()
                        .mapToLong(TaskSelectionService.SelectionStatus::requiredCount)
                        .sum();
        status.merge("PENDING", Math.max(0, required - tasks.size()), Long::sum);
        Map<String, TargetCompletion> targetCompletion = new LinkedHashMap<>();
        for (var requirement : requirements) {
            long completed =
                    tasks.stream()
                            .filter(task -> requirement.targetType().equals(task.getTargetType()))
                            .filter(this::isSubmittedStatus)
                            .count();
            targetCompletion.put(
                    requirement.targetType(),
                    new TargetCompletion(
                            completed, Math.max(0, requirement.requiredCount() - completed)));
        }
        return new DashboardCharts(status, targetCompletion);
    }

    @Transactional(readOnly = true)
    public PageResult<CheckTask> todo(int page, int size) {
        var rows =
                coverageTasks().stream()
                        .filter(task -> List.of("PENDING", "OVERDUE").contains(task.getStatus()))
                        .sorted(
                                Comparator.comparing(
                                        CheckTask::getDeadline,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
        return page(rows, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<CheckTask> warnings(int page, int size) {
        var now = LocalDateTime.now();
        var until = now.plusDays(7);
        var period = systemPeriodService.effectivePeriod();
        var rows =
                new ArrayList<>(
                        taskSelectionService.selectionWarningTasks(
                                period.year(), period.quarter()));
        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getExecutorDeptId, SecurityUtils.currentUser().getDeptId())
                        .eq(CheckTask::getCheckType, "INTERNAL_SECURITY")
                        .eq(CheckTask::getCountCoverage, 1)
                        .in(CheckTask::getStatus, List.of("PENDING", "OVERDUE"));
        systemPeriodService.applyTaskBoundary(wrapper);
        rows.addAll(
                taskMapper.selectList(wrapper).stream()
                        .filter(task -> task.getDeadline() != null)
                        .filter(
                                task ->
                                        "OVERDUE".equals(task.getStatus())
                                                || task.getDeadline().isBefore(now)
                                                || (task.getDeadline().isAfter(now)
                                                        && task.getDeadline().isBefore(until)))
                        .toList());
        rows.sort(
                Comparator.comparing(
                        CheckTask::getDeadline, Comparator.nullsLast(Comparator.naturalOrder())));
        return page(rows, page, size);
    }

    private List<CheckTask> coverageTasks() {
        return currentQuarterExecutableTasks().stream()
                .filter(task -> Integer.valueOf(1).equals(task.getCountCoverage()))
                .toList();
    }

    private List<CheckTask> currentQuarterExecutableTasks() {
        Long deptId = SecurityUtils.currentUser().getDeptId();
        var period = systemPeriodService.effectivePeriod();
        return taskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getExecutorDeptId, deptId)
                        .eq(CheckTask::getTaskYear, period.year())
                        .eq(CheckTask::getQuarter, period.quarter())
                        .eq(CheckTask::getCheckType, "INTERNAL_SECURITY"));
    }

    private List<TaskSelectionService.SelectionStatus> currentQuarterRequirements() {
        var period = systemPeriodService.effectivePeriod();
        return taskSelectionService.statuses(period.year(), period.quarter());
    }

    private PageResult<CheckTask> page(List<CheckTask> rows, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        return new PageResult<>(rows.size(), rows.subList(from, to));
    }

    private boolean isSubmittedStatus(CheckTask task) {
        return "APPROVED".equals(task.getStatus()) || "OVERDUE_SUBMITTED".equals(task.getStatus());
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 10000D / denominator) / 100D;
    }

    public record DashboardSummary(
            double coverageRate,
            double submissionRate,
            long overdueCount,
            long pendingUploadCount,
            long coveredCount,
            long uncoveredCount,
            long completedCount,
            long unfinishedCount,
            int year,
            int quarter) {}

    public record DashboardCharts(
            Map<String, Long> status, Map<String, TargetCompletion> targetCompletion) {}

    public record TargetCompletion(long completed, long unfinished) {}
}

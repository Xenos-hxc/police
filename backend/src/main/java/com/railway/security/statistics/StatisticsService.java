package com.railway.security.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.railway.security.inspection.TaskSelectionService;
import com.railway.security.inspection.TaskService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 统计必须说明季度、部门、分母和逻辑删除口径；大聚合要看执行计划和扫描量，不能把缓存命中当成 SQL 本身更快。
public class StatisticsService {
    private final CheckTaskMapper taskMapper;
    private final DeptMapper deptMapper;
    private final DataScopeService dataScopeService;
    private final TaskSelectionService taskSelectionService;
    private final TaskService taskService;
    private final StatisticsCacheService statisticsCacheService;
    private final SystemPeriodService systemPeriodService;

    public Overview overview(StatisticsQuery query) {
        return statisticsCacheService.get(
                cacheKey("overview", query),
                new TypeReference<Overview>() {},
                () -> doOverview(query));
    }

    private Overview doOverview(StatisticsQuery query) {
        int year = effectiveYear(query);
        List<Integer> quarters = effectiveQuarters(query);
        List<Requirement> requirements = requirements(query, year, quarters);
        List<CheckTask> selectedTasks = scopedTasks(query, year, quarters);
        long required = requirements.stream().mapToLong(Requirement::required).sum();
        long covered =
                selectedTasks.stream()
                        .filter(this::isSubmittedStatus)
                        .map(this::taskIdentity)
                        .distinct()
                        .count();
        long submitted = covered;
        long overdue =
                selectedTasks.stream()
                        .filter(task -> Integer.valueOf(1).equals(task.getOverdue()))
                        .filter(task -> !"OVERDUE_SUBMITTED".equals(task.getStatus()))
                        .count();
        return new Overview(
                required,
                covered,
                Math.max(0, required - covered),
                required,
                submitted,
                Math.max(0, required - submitted),
                overdue,
                0,
                percent(covered, required),
                percent(submitted, required),
                percent(overdue, required));
    }

    public Map<String, Object> charts(StatisticsQuery query) {
        return statisticsCacheService.get(
                cacheKey("charts", query),
                new TypeReference<Map<String, Object>>() {},
                () -> doCharts(query));
    }

    private Map<String, Object> doCharts(StatisticsQuery query) {
        int year = effectiveYear(query);
        List<Integer> quarters = effectiveQuarters(query);
        List<Requirement> requirements = requirements(query, year, quarters);
        List<CheckTask> tasks = scopedTasks(query, year, quarters);
        Map<Long, SysDept> deptMap =
                deptMap(requirements.stream().map(Requirement::executorDeptId).toList());
        Map<RequirementKey, List<CheckTask>> tasksByKey =
                tasks.stream().collect(Collectors.groupingBy(RequirementKey::from));
        long requiredTotal = requirements.stream().mapToLong(Requirement::required).sum();

        Map<String, Long> status =
                new LinkedHashMap<>(
                        tasks.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                CheckTask::getStatus,
                                                LinkedHashMap::new,
                                                Collectors.counting())));
        Map<RequirementKey, Long> actualByRequirement =
                tasks.stream()
                        .collect(
                                Collectors.groupingBy(RequirementKey::from, Collectors.counting()));
        for (Requirement requirement : requirements) {
            long actual = actualByRequirement.getOrDefault(requirement.key(), 0L);
            long missing = Math.max(0, requirement.required() - actual);
            if (missing > 0) {
                status.merge(
                        isElapsedQuarter(year, requirement.quarter()) ? "OVERDUE" : "PENDING",
                        missing,
                        Long::sum);
            }
        }

        Map<String, Long> requiredByType =
                requirements.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Requirement::targetType,
                                        LinkedHashMap::new,
                                        Collectors.summingLong(Requirement::required)));
        Map<String, Object> targetCompletion = new LinkedHashMap<>();
        for (var entry : requiredByType.entrySet()) {
            long completed =
                    tasks.stream()
                            .filter(task -> entry.getKey().equals(task.getTargetType()))
                            .filter(this::isSubmittedStatus)
                            .count();
            targetCompletion.put(
                    entry.getKey(),
                    Map.of(
                            "completed",
                            completed,
                            "unfinished",
                            Math.max(0, entry.getValue() - completed)));
        }

        Map<String, Object> scopeCompletion = new LinkedHashMap<>();
        var scopeGroups =
                requirements.stream()
                        .collect(
                                Collectors.groupingBy(
                                        requirement ->
                                                scopeLabel(
                                                        requirement.executorDeptId(),
                                                        requirement.targetType(),
                                                        deptMap),
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        for (var entry : scopeGroups.entrySet()) {
            long required = entry.getValue().stream().mapToLong(Requirement::required).sum();
            Set<RequirementKey> keys =
                    entry.getValue().stream().map(Requirement::key).collect(Collectors.toSet());
            List<CheckTask> scoped =
                    keys.stream()
                            .flatMap(key -> tasksByKey.getOrDefault(key, List.of()).stream())
                            .toList();
            long completed = scoped.stream().filter(this::isSubmittedStatus).count();
            long overdue =
                    scoped.stream()
                            .filter(task -> Integer.valueOf(1).equals(task.getOverdue()))
                            .filter(task -> !"OVERDUE_SUBMITTED".equals(task.getStatus()))
                            .count();
            scopeCompletion.put(
                    entry.getKey(),
                    Map.of(
                            "required",
                            required,
                            "completed",
                            completed,
                            "unfinished",
                            Math.max(0, required - completed),
                            "overdue",
                            overdue));
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int quarter = 1; quarter <= 4; quarter++) {
            if (!systemPeriodService.includes(year, quarter)) continue;
            List<Requirement> quarterRequirements = requirements(query, year, List.of(quarter));
            List<CheckTask> quarterTasks = scopedTasks(query, year, List.of(quarter));
            long required = quarterRequirements.stream().mapToLong(Requirement::required).sum();
            long completed = quarterTasks.stream().filter(this::isSubmittedStatus).count();
            trend.add(
                    Map.of(
                            "quarter",
                            quarter,
                            "label",
                            quarterLabel(quarter),
                            "required",
                            required,
                            "completed",
                            completed,
                            "unfinished",
                            Math.max(0, required - completed)));
        }
        return Map.of(
                "status",
                status,
                "checkType",
                requiredByType,
                "targetCompletion",
                targetCompletion,
                "scopeCompletion",
                scopeCompletion,
                "trend",
                trend);
    }

    public PageResult<RankingItem> ranking(StatisticsQuery query) {
        return statisticsCacheService.get(
                cacheKey("ranking", query),
                new TypeReference<PageResult<RankingItem>>() {},
                () -> doRanking(query));
    }

    private PageResult<RankingItem> doRanking(StatisticsQuery query) {
        validateRankingScope(query.rankDeptType());
        String rankDeptType = effectiveRankDeptType(query.rankDeptType());
        int year = effectiveYear(query);
        List<Integer> quarters = effectiveQuarters(query);
        List<Requirement> requirements = requirements(query, year, quarters);
        List<CheckTask> tasks = scopedTasks(query, year, quarters);
        Map<Long, SysDept> deptMap =
                deptMap(requirements.stream().map(Requirement::executorDeptId).toList());
        Map<RequirementKey, List<CheckTask>> tasksByKey =
                tasks.stream().collect(Collectors.groupingBy(RequirementKey::from));
        List<RankingItem> all =
                requirements.stream()
                        .collect(Collectors.groupingBy(Requirement::executorDeptId))
                        .entrySet()
                        .stream()
                        .map(
                                entry -> {
                                    SysDept dept = deptMap.get(entry.getKey());
                                    long total =
                                            entry.getValue().stream()
                                                    .mapToLong(Requirement::required)
                                                    .sum();
                                    Set<RequirementKey> keys =
                                            entry.getValue().stream()
                                                    .map(Requirement::key)
                                                    .collect(Collectors.toSet());
                                    List<CheckTask> deptTasks =
                                            keys.stream()
                                                    .flatMap(
                                                            key ->
                                                                    tasksByKey
                                                                            .getOrDefault(
                                                                                    key, List.of())
                                                                            .stream())
                                                    .toList();
                                    long completed =
                                            deptTasks.stream()
                                                    .filter(this::isSubmittedStatus)
                                                    .count();
                                    long overdue =
                                            deptTasks.stream()
                                                    .filter(
                                                            task ->
                                                                    Integer.valueOf(1)
                                                                            .equals(
                                                                                    task
                                                                                            .getOverdue()))
                                                    .filter(
                                                            task ->
                                                                    !"OVERDUE_SUBMITTED"
                                                                            .equals(
                                                                                    task
                                                                                            .getStatus()))
                                                    .count();
                                    return new RankingItem(
                                            entry.getKey(),
                                            dept == null ? "未知部门" : dept.getDeptName(),
                                            dept == null ? "" : dept.getDeptType(),
                                            total,
                                            completed,
                                            overdue,
                                            percent(completed, total),
                                            percent(overdue, total));
                                })
                        .filter(
                                item ->
                                        rankDeptType == null
                                                || rankDeptType.equals(item.deptType()))
                        .sorted(
                                Comparator.comparingDouble(RankingItem::completionRate)
                                        .reversed()
                                        .thenComparing(RankingItem::overdueRate)
                                        .thenComparing(RankingItem::deptId))
                        .toList();
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? 10 : Math.min(query.size(), 100);
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.size(), all.subList(from, to));
    }

    public List<CheckTask> scopedTasks(StatisticsQuery query, int year, int quarter) {
        requirements(query, year, List.of(quarter));
        return scopedTasks(query, year, List.of(quarter));
    }

    private List<CheckTask> scopedTasks(StatisticsQuery query, int year, List<Integer> quarters) {
        quarters = systemPeriodService.includedQuarters(year, quarters);
        if (quarters.isEmpty()) return List.of();
        List<Long> deptIds = statisticDeptIds(query);
        if (deptIds.isEmpty()) return List.of();
        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .in(CheckTask::getExecutorDeptId, deptIds)
                        .eq(CheckTask::getTaskYear, year)
                        .in(CheckTask::getQuarter, quarters)
                        .eq(CheckTask::getCheckType, "INTERNAL_SECURITY")
                        .eq(
                                hasText(query.targetType()),
                                CheckTask::getTargetType,
                                query.targetType())
                        .eq(query.overdue() != null, CheckTask::getOverdue, query.overdue())
                        .eq(CheckTask::getCountCoverage, 1);
        systemPeriodService.applyTaskBoundary(wrapper);
        return taskMapper.selectList(wrapper);
    }

    private List<Requirement> requirements(
            StatisticsQuery query, int year, List<Integer> quarters) {
        quarters = systemPeriodService.includedQuarters(year, quarters);
        if (quarters.isEmpty()) return List.of();
        List<Long> deptIds = statisticDeptIds(query);
        List<Requirement> result = new ArrayList<>();
        for (int quarter : quarters) {
            for (Long deptId : deptIds) {
                for (var status : taskSelectionService.statusesForDept(deptId, year, quarter)) {
                    if (hasText(query.targetType())
                            && !query.targetType().equals(status.targetType())) continue;
                    result.add(
                            new Requirement(
                                    deptId, status.targetType(), quarter, status.requiredCount()));
                }
            }
        }
        return result;
    }

    private List<Long> statisticDeptIds(StatisticsQuery query) {
        List<Long> permitted = dataScopeService.permittedDeptIds();
        if (query.deptId() != null) {
            dataScopeService.checkDept(query.deptId());
            permitted = List.of(query.deptId());
        }
        if (hasText(query.executorDeptType())) {
            return deptMapper
                    .selectList(
                            new LambdaQueryWrapper<SysDept>()
                                    .in(SysDept::getId, safeIds(permitted))
                                    .eq(SysDept::getDeptType, query.executorDeptType())
                                    .eq(SysDept::getStatus, 1))
                    .stream()
                    .map(SysDept::getId)
                    .toList();
        }
        return deptMapper
                .selectList(
                        new LambdaQueryWrapper<SysDept>()
                                .in(SysDept::getId, safeIds(permitted))
                                .in(SysDept::getDeptType, List.of("BUREAU", "STATION"))
                                .eq(SysDept::getStatus, 1))
                .stream()
                .map(SysDept::getId)
                .toList();
    }

    private void validateRankingScope(String rankDeptType) {
        if (hasText(rankDeptType) && !"STATION".equals(rankDeptType)) {
            throw new BusinessException("排名部门类型无效");
        }
        var roles = SecurityUtils.currentUser().getRoles();
        if (!roles.contains("ADMIN") && !roles.contains("BUREAU")) {
            throw new BusinessException(403, "仅管理员或公安处账号可以查询各所完成率排名");
        }
    }

    private String effectiveRankDeptType(String requested) {
        return "STATION";
    }

    private int effectiveYear(StatisticsQuery query) {
        return query.year() == null ? systemPeriodService.effectivePeriod().year() : query.year();
    }

    private List<Integer> effectiveQuarters(StatisticsQuery query) {
        int year = effectiveYear(query);
        List<Integer> requested;
        if (query.quarter() != null) requested = List.of(query.quarter());
        else if (Integer.valueOf(1).equals(query.halfYear())) requested = List.of(1, 2);
        else if (Integer.valueOf(2).equals(query.halfYear())) requested = List.of(3, 4);
        else requested = List.of(systemPeriodService.effectivePeriod().quarter());
        return systemPeriodService.includedQuarters(year, requested);
    }

    private Map<Long, SysDept> deptMap(Collection<Long> deptIds) {
        List<Long> ids = deptIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return deptMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysDept::getId, value -> value, (a, b) -> a));
    }

    private String scopeLabel(Long executorDeptId, String targetType, Map<Long, SysDept> deptMap) {
        SysDept dept = deptMap.get(executorDeptId);
        String executor =
                dept == null
                        ? "当前部门"
                        : switch (dept.getDeptType()) {
                            case "BUREAU" -> "公安处";
                            case "STATION" -> "派出所";
                            default -> "当前部门";
                        };
        String target =
                switch (targetType) {
                    case "STATION" -> "派出所";
                    case "KEY_UNIT" -> "重点单位";
                    case "IMPORTANT_PART" -> "重要部位";
                    default -> targetType;
                };
        return executor + "检查" + target;
    }

    private String taskIdentity(CheckTask task) {
        return task.getExecutorDeptId()
                + ":"
                + task.getTargetType()
                + ":"
                + task.getTargetId()
                + ":"
                + task.getTaskYear()
                + ":"
                + task.getQuarter();
    }

    private String cacheKey(String scope, StatisticsQuery query) {
        var user = SecurityUtils.currentUser();
        return String.join(
                "|",
                scope,
                String.valueOf(user.getUserId()),
                String.valueOf(user.getDeptId()),
                String.valueOf(query.year()),
                String.valueOf(query.quarter()),
                String.valueOf(query.halfYear()),
                String.valueOf(query.deptId()),
                String.valueOf(query.targetType()),
                String.valueOf(query.executorDeptType()),
                String.valueOf(query.rankDeptType()),
                String.valueOf(query.page()),
                String.valueOf(query.size()));
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0D : Math.round(numerator * 10000D / denominator) / 100D;
    }

    private boolean isSubmittedStatus(CheckTask task) {
        return "APPROVED".equals(task.getStatus()) || "OVERDUE_SUBMITTED".equals(task.getStatus());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isElapsedQuarter(int year, int quarter) {
        return LocalDate.now()
                .isAfter(LocalDate.of(year, quarter * 3, 1).plusMonths(1).minusDays(1));
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }

    private String quarterLabel(int quarter) {
        return switch (quarter) {
            case 1 -> "第一季度";
            case 2 -> "第二季度";
            case 3 -> "第三季度";
            case 4 -> "第四季度";
            default -> "";
        };
    }

    public record StatisticsQuery(
            Integer year,
            Integer quarter,
            Integer halfYear,
            Long deptId,
            String checkType,
            Integer overdue,
            String targetType,
            String executorDeptType,
            String rankDeptType,
            Integer page,
            Integer size) {}

    public record Overview(
            long requiredCount,
            long coveredCount,
            long uncoveredCount,
            long taskCount,
            long submittedCount,
            long unsubmittedCount,
            long overdueCount,
            long rejectedCount,
            double coverageRate,
            double submissionRate,
            double overdueRate) {}

    public record RankingItem(
            Long deptId,
            String deptName,
            String deptType,
            long taskCount,
            long completedCount,
            long overdueCount,
            double completionRate,
            double overdueRate) {}

    private record RequirementKey(Long executorDeptId, String targetType, int quarter) {
        private static RequirementKey from(CheckTask task) {
            return new RequirementKey(
                    task.getExecutorDeptId(),
                    task.getTargetType(),
                    task.getQuarter() == null ? 0 : task.getQuarter());
        }
    }

    private record Requirement(Long executorDeptId, String targetType, int quarter, long required) {
        private RequirementKey key() {
            return new RequirementKey(executorDeptId, targetType, quarter);
        }
    }
}

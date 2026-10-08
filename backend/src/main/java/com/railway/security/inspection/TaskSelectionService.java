package com.railway.security.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.TargetJurisdiction;
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
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskSelectionService {
    private static final String CHECK_TYPE = "INTERNAL_SECURITY";
    private static final String TASK_NAME = "内部治安保卫工作监督检查";
    private static final Set<String> TARGET_TYPES = Set.of("STATION", "KEY_UNIT", "IMPORTANT_PART");

    private final CheckTaskMapper taskMapper;
    private final TaskPeriodSnapshotMapper snapshotMapper;
    private final PoliceStationMapper stationMapper;
    private final KeyUnitMapper unitMapper;
    private final ImportantPartMapper partMapper;
    private final TargetJurisdictionMapper jurisdictionMapper;
    private final DeptMapper deptMapper;
    private final ConfigMapper configMapper;
    private final StatisticsCacheService statisticsCacheService;
    private final DataScopeService dataScopeService;
    private final SystemPeriodService systemPeriodService;

    public List<SelectionStatus> statuses(Integer year, Integer quarter) {
        return statusesForDept(
                SecurityUtils.currentUser().getDeptId(), safeYear(year), safeQuarter(quarter));
    }

    public List<SelectionStatus> statusesForDept(
            Long executorDeptId, Integer year, Integer quarter) {
        dataScopeService.checkDept(executorDeptId);
        int taskYear = safeYear(year);
        int taskQuarter = safeQuarter(quarter);
        if (!systemPeriodService.includes(taskYear, taskQuarter)) return List.of();
        SysDept dept = requiredExecutorDept(executorDeptId);
        return targetTypesForDept(dept.getDeptType()).stream()
                .map(
                        type ->
                                status(
                                        executorDeptId,
                                        dept.getDeptType(),
                                        type,
                                        taskYear,
                                        taskQuarter))
                .toList();
    }

    @Transactional
    // 季度初始化固定覆盖分母与任务快照，拒绝用当前档案伪造过去期间；幂等性还需唯一约束和并发验证。
    public List<SelectionStatus> initializePeriod(Integer year, Integer quarter) {
        int taskYear = safeYear(year);
        int taskQuarter = safeQuarter(quarter);
        systemPeriodService.assertIncluded(taskYear, taskQuarter);
        if (quarterEnd(taskYear, taskQuarter).isBefore(LocalDateTime.now())) {
            throw new BusinessException("历史季度未初始化时不能按当前档案补建任务");
        }
        Long deptId = SecurityUtils.currentUser().getDeptId();
        SysDept dept = requiredExecutorDept(deptId);
        for (String targetType : targetTypesForDept(dept.getDeptType())) {
            initializeSnapshot(
                    deptId,
                    dept.getDeptType(),
                    targetType,
                    taskYear,
                    taskQuarter,
                    SecurityUtils.currentUserIdOrZero());
        }
        return statusesForDept(deptId, taskYear, taskQuarter);
    }

    @Transactional
    public void initializeCurrentPeriodForAll() {
        SystemPeriodService.Period period = systemPeriodService.effectivePeriod();
        int year = period.year();
        int quarter = period.quarter();
        for (SysDept dept :
                deptMapper.selectList(
                        new LambdaQueryWrapper<SysDept>()
                                .in(SysDept::getDeptType, List.of("BUREAU", "STATION"))
                                .eq(SysDept::getStatus, 1)
                                .eq(SysDept::getArchived, 0))) {
            for (String targetType : targetTypesForDept(dept.getDeptType())) {
                initializeSnapshot(dept.getId(), dept.getDeptType(), targetType, year, quarter, 0L);
            }
        }
    }

    @Transactional
    public void sealElapsedSnapshots() {
        for (TaskPeriodSnapshot snapshot :
                snapshotMapper.selectList(
                        new LambdaQueryWrapper<TaskPeriodSnapshot>()
                                .eq(TaskPeriodSnapshot::getSnapshotStatus, "OPEN"))) {
            if (quarterEnd(snapshot.getTaskYear(), snapshot.getQuarter())
                    .isBefore(LocalDateTime.now())) {
                snapshot.setSnapshotStatus("SEALED");
                snapshot.setSealedAt(LocalDateTime.now());
                snapshotMapper.updateById(snapshot);
            }
        }
    }

    public SelectionPool pool(String targetType, Integer year, Integer quarter) {
        int taskYear = safeYear(year);
        int taskQuarter = safeQuarter(quarter);
        systemPeriodService.assertIncluded(taskYear, taskQuarter);
        String type = normalizeTargetType(targetType);
        Long executorDeptId = SecurityUtils.currentUser().getDeptId();
        SysDept dept = requiredExecutorDept(executorDeptId);
        assertTargetTypeAllowed(dept.getDeptType(), type);
        TaskPeriodSnapshot snapshot = requiredSnapshot(executorDeptId, type, taskYear, taskQuarter);
        List<CheckTask> tasks = currentTasks(executorDeptId, type, taskYear, taskQuarter);
        Set<Long> excluded =
                otherQuarterSelectedTargetIds(executorDeptId, type, taskYear, taskQuarter);
        List<CheckTask> eligible = eligibleTasks(tasks, excluded);
        int submitted =
                (int)
                        tasks.stream()
                                .filter(this::isSubmitted)
                                .filter(task -> Integer.valueOf(1).equals(task.getCountCoverage()))
                                .count();
        int remainingNeed = Math.max(0, snapshot.getRequiredCount() - submitted);
        List<SelectionTarget> targets =
                eligible.stream()
                        .filter(task -> !isSubmitted(task))
                        .sorted(Comparator.comparing(CheckTask::getTargetName))
                        .map(
                                task ->
                                        new SelectionTarget(
                                                task.getId(),
                                                task.getTargetId(),
                                                task.getTargetName(),
                                                Integer.valueOf(1).equals(task.getCountCoverage())))
                        .toList();
        int selected = (int) targets.stream().filter(SelectionTarget::selected).count();
        return new SelectionPool(
                type,
                targetLabel(type),
                snapshot.getRequiredCount(),
                submitted,
                selected,
                remainingNeed,
                targets.size(),
                targets);
    }

    @Transactional
    public SelectionPool save(SelectionSaveRequest request) {
        String type = normalizeTargetType(request.targetType());
        int taskYear = safeYear(request.year());
        int taskQuarter = safeQuarter(request.quarter());
        systemPeriodService.assertIncluded(taskYear, taskQuarter);
        Long executorDeptId = SecurityUtils.currentUser().getDeptId();
        SysDept dept = requiredExecutorDept(executorDeptId);
        assertTargetTypeAllowed(dept.getDeptType(), type);
        TaskPeriodSnapshot snapshot = requiredSnapshot(executorDeptId, type, taskYear, taskQuarter);
        assertSelectionEditable(snapshot, taskYear, taskQuarter);
        if (snapshot.getCoveragePercent() >= 100) {
            throw new BusinessException("季度覆盖率为100%的任务不需要筛选");
        }

        List<CheckTask> tasks = currentTasks(executorDeptId, type, taskYear, taskQuarter);
        Set<Long> excluded =
                otherQuarterSelectedTargetIds(executorDeptId, type, taskYear, taskQuarter);
        List<CheckTask> candidates =
                eligibleTasks(tasks, excluded).stream().filter(task -> !isSubmitted(task)).toList();
        int submitted =
                (int)
                        tasks.stream()
                                .filter(this::isSubmitted)
                                .filter(task -> Integer.valueOf(1).equals(task.getCountCoverage()))
                                .count();
        int remainingNeed = Math.max(0, snapshot.getRequiredCount() - submitted);
        Set<Long> selectedTargetIds = selectedTargetIds(request, candidates, remainingNeed);
        String mode = normalizeMode(request.mode());

        for (CheckTask task : tasks) {
            if (isSubmitted(task)) task.setCountCoverage(1);
            else if (excluded.contains(task.getTargetId())) task.setCountCoverage(0);
            else {
                task.setCountCoverage(selectedTargetIds.contains(task.getTargetId()) ? 1 : 0);
                task.setCreationMode(mode);
            }
            updateTask(task);
        }
        statisticsCacheService.clear();
        return pool(type, taskYear, taskQuarter);
    }

    @Transactional
    public SelectionPool clear(SelectionClearRequest request) {
        String type = normalizeTargetType(request.targetType());
        int taskYear = safeYear(request.year());
        int taskQuarter = safeQuarter(request.quarter());
        systemPeriodService.assertIncluded(taskYear, taskQuarter);
        Long executorDeptId = SecurityUtils.currentUser().getDeptId();
        SysDept dept = requiredExecutorDept(executorDeptId);
        assertTargetTypeAllowed(dept.getDeptType(), type);
        TaskPeriodSnapshot snapshot = requiredSnapshot(executorDeptId, type, taskYear, taskQuarter);
        assertSelectionEditable(snapshot, taskYear, taskQuarter);
        if (snapshot.getCoveragePercent() >= 100) {
            throw new BusinessException("季度覆盖率为100%的任务不需要筛选，不能清空");
        }
        boolean changed = false;
        for (CheckTask task : currentTasks(executorDeptId, type, taskYear, taskQuarter)) {
            if (!isSubmitted(task) && Integer.valueOf(1).equals(task.getCountCoverage())) {
                task.setCountCoverage(0);
                task.setCreationMode("SYSTEM");
                updateTask(task);
                changed = true;
            }
        }
        if (changed) statisticsCacheService.clear();
        return pool(type, taskYear, taskQuarter);
    }

    public List<String> accessibleTargetTypes() {
        return targetTypesForDept(
                requiredExecutorDept(SecurityUtils.currentUser().getDeptId()).getDeptType());
    }

    public List<CheckTask> selectionWarningTasks(Integer year, Integer quarter) {
        int taskYear = safeYear(year);
        int taskQuarter = safeQuarter(quarter);
        return statuses(taskYear, taskQuarter).stream()
                .filter(SelectionStatus::needSelection)
                .map(
                        status -> {
                            var task = new CheckTask();
                            task.setTaskName(
                                    status.initialized() ? "本季度监督检查任务尚未筛选" : "本季度监督检查任务尚未初始化");
                            task.setTargetType(status.targetType());
                            task.setTargetName(
                                    status.targetLabel()
                                            + (status.initialized() ? "任务未筛选" : "任务未初始化"));
                            task.setTaskYear(taskYear);
                            task.setQuarter(taskQuarter);
                            task.setStatus("PENDING");
                            task.setOverdue(0);
                            task.setDeadline(quarterEnd(taskYear, taskQuarter));
                            return task;
                        })
                .toList();
    }

    private SelectionStatus status(
            Long executorDeptId,
            String executorDeptType,
            String targetType,
            int year,
            int quarter) {
        TaskPeriodSnapshot snapshot = findSnapshot(executorDeptId, targetType, year, quarter);
        boolean fullCoverage = coveragePercent(executorDeptType, targetType) >= 100;
        if (snapshot == null) {
            boolean hasTargets =
                    !targetSeeds(executorDeptId, executorDeptType, targetType).isEmpty();
            return new SelectionStatus(
                    targetType,
                    targetLabel(targetType),
                    0,
                    0,
                    0,
                    0,
                    fullCoverage,
                    hasTargets,
                    false);
        }
        List<CheckTask> tasks = currentTasks(executorDeptId, targetType, year, quarter);
        int submitted =
                (int)
                        tasks.stream()
                                .filter(this::isSubmitted)
                                .filter(task -> Integer.valueOf(1).equals(task.getCountCoverage()))
                                .count();
        int selected =
                (int)
                        tasks.stream()
                                .filter(task -> !isSubmitted(task))
                                .filter(task -> Integer.valueOf(1).equals(task.getCountCoverage()))
                                .count();
        int remainingNeed = Math.max(0, snapshot.getRequiredCount() - submitted);
        boolean needSelection = snapshot.getCoveragePercent() < 100 && selected < remainingNeed;
        return new SelectionStatus(
                targetType,
                targetLabel(targetType),
                snapshot.getRequiredCount(),
                submitted,
                selected,
                remainingNeed,
                snapshot.getCoveragePercent() >= 100,
                needSelection,
                true);
    }

    private void initializeSnapshot(
            Long executorDeptId,
            String executorDeptType,
            String targetType,
            int year,
            int quarter,
            Long createBy) {
        if (findSnapshot(executorDeptId, targetType, year, quarter) != null) return;
        List<TargetSeed> seeds = targetSeeds(executorDeptId, executorDeptType, targetType);
        if (seeds.isEmpty()) return;
        int percent = coveragePercent(executorDeptType, targetType);
        var snapshot = new TaskPeriodSnapshot();
        snapshot.setExecutorDeptId(executorDeptId);
        snapshot.setExecutorDeptType(executorDeptType);
        snapshot.setTargetType(targetType);
        snapshot.setTaskYear(year);
        snapshot.setQuarter(quarter);
        snapshot.setTotalCount(seeds.size());
        snapshot.setRequiredCount(requiredCount(seeds.size(), percent, targetType, quarter));
        snapshot.setCoveragePercent(percent);
        snapshot.setSnapshotStatus("OPEN");
        snapshot.setInitializedAt(LocalDateTime.now());
        snapshot.setCreateBy(createBy == null ? 0L : createBy);
        if (snapshotMapper.insertIgnore(snapshot) != 1) return;

        boolean fullCoverage = percent >= 100;
        for (TargetSeed seed : seeds) {
            var task = new CheckTask();
            task.setTaskNo(taskNo(year, quarter, executorDeptId, targetType, seed.id()));
            task.setTaskName(TASK_NAME);
            task.setCheckType(CHECK_TYPE);
            task.setTaskCategory("COVERAGE");
            task.setCreationMode("SYSTEM");
            task.setInitiatorDeptId(executorDeptId);
            task.setExecutorDeptId(executorDeptId);
            task.setTargetId(seed.id());
            task.setTargetType(targetType);
            task.setTargetName(seed.name());
            task.setTaskYear(year);
            task.setQuarter(quarter);
            task.setHalfYear(quarter <= 2 ? 1 : 2);
            task.setStartDate(quarterStart(year, quarter));
            task.setDeadline(quarterEnd(year, quarter));
            task.setStatus("PENDING");
            task.setOverdue(0);
            task.setOverdueSubmitted(0);
            task.setCountCoverage(fullCoverage ? 1 : 0);
            task.setCreateBy(createBy == null ? 0L : createBy);
            taskMapper.insert(task);
        }
        statisticsCacheService.clear();
    }

    private TaskPeriodSnapshot findSnapshot(Long deptId, String targetType, int year, int quarter) {
        return snapshotMapper.selectOne(
                new LambdaQueryWrapper<TaskPeriodSnapshot>()
                        .eq(TaskPeriodSnapshot::getExecutorDeptId, deptId)
                        .eq(TaskPeriodSnapshot::getTargetType, targetType)
                        .eq(TaskPeriodSnapshot::getTaskYear, year)
                        .eq(TaskPeriodSnapshot::getQuarter, quarter));
    }

    private TaskPeriodSnapshot requiredSnapshot(
            Long deptId, String targetType, int year, int quarter) {
        TaskPeriodSnapshot snapshot = findSnapshot(deptId, targetType, year, quarter);
        if (snapshot == null) throw new BusinessException("该季度任务尚未初始化，请刷新任务基线");
        return snapshot;
    }

    private void assertSelectionEditable(TaskPeriodSnapshot snapshot, int year, int quarter) {
        if (!"OPEN".equals(snapshot.getSnapshotStatus())
                || quarterEnd(year, quarter).isBefore(LocalDateTime.now())) {
            throw new BusinessException("历史季度检查对象已固化，不能重新筛选或清空");
        }
    }

    private List<TargetSeed> targetSeeds(
            Long executorDeptId, String executorDeptType, String targetType) {
        if ("BUREAU".equals(executorDeptType)) return allTargets(targetType);
        if (!"STATION".equals(executorDeptType) || "STATION".equals(targetType)) return List.of();
        List<Long> targetIds =
                jurisdictionMapper
                        .selectList(
                                new LambdaQueryWrapper<TargetJurisdiction>()
                                        .eq(TargetJurisdiction::getStationDeptId, executorDeptId)
                                        .eq(TargetJurisdiction::getTargetType, targetType))
                        .stream()
                        .map(TargetJurisdiction::getTargetId)
                        .distinct()
                        .toList();
        if (targetIds.isEmpty()) return List.of();
        return switch (targetType) {
            case "KEY_UNIT" ->
                    unitMapper
                            .selectList(
                                    new LambdaQueryWrapper<KeyUnit>()
                                            .in(KeyUnit::getId, targetIds)
                                            .eq(KeyUnit::getStatus, 1)
                                            .eq(KeyUnit::getArchived, 0))
                            .stream()
                            .map(item -> new TargetSeed(item.getId(), item.getUnitName()))
                            .toList();
            case "IMPORTANT_PART" ->
                    partMapper
                            .selectList(
                                    new LambdaQueryWrapper<ImportantPart>()
                                            .in(ImportantPart::getId, targetIds)
                                            .eq(ImportantPart::getStatus, 1)
                                            .eq(ImportantPart::getArchived, 0))
                            .stream()
                            .map(item -> new TargetSeed(item.getId(), item.getPartName()))
                            .toList();
            default -> List.of();
        };
    }

    private List<TargetSeed> allTargets(String targetType) {
        return switch (targetType) {
            case "STATION" ->
                    stationMapper
                            .selectList(
                                    new LambdaQueryWrapper<PoliceStation>()
                                            .eq(PoliceStation::getStatus, 1)
                                            .eq(PoliceStation::getArchived, 0))
                            .stream()
                            .map(item -> new TargetSeed(item.getId(), item.getStationName()))
                            .toList();
            case "KEY_UNIT" ->
                    unitMapper
                            .selectList(
                                    new LambdaQueryWrapper<KeyUnit>()
                                            .eq(KeyUnit::getStatus, 1)
                                            .eq(KeyUnit::getArchived, 0))
                            .stream()
                            .map(item -> new TargetSeed(item.getId(), item.getUnitName()))
                            .toList();
            case "IMPORTANT_PART" ->
                    partMapper
                            .selectList(
                                    new LambdaQueryWrapper<ImportantPart>()
                                            .eq(ImportantPart::getStatus, 1)
                                            .eq(ImportantPart::getArchived, 0))
                            .stream()
                            .map(item -> new TargetSeed(item.getId(), item.getPartName()))
                            .toList();
            default -> List.of();
        };
    }

    private Set<Long> selectedTargetIds(
            SelectionSaveRequest request, List<CheckTask> candidates, int remainingNeed) {
        Set<Long> selected =
                new HashSet<>(request.targetIds() == null ? List.of() : request.targetIds());
        Set<Long> allowed =
                candidates.stream().map(CheckTask::getTargetId).collect(Collectors.toSet());
        if (!allowed.containsAll(selected)) throw new BusinessException("存在不允许筛选的对象");
        if (selected.size() != remainingNeed)
            throw new BusinessException("本季度还需筛选 " + remainingNeed + " 个对象");
        return selected;
    }

    private void updateTask(CheckTask task) {
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessException("任务筛选结果已被其他用户修改，请刷新后重试");
        }
    }

    private Set<Long> otherQuarterSelectedTargetIds(
            Long executorDeptId, String targetType, int year, int quarter) {
        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getExecutorDeptId, executorDeptId)
                        .eq(CheckTask::getCheckType, CHECK_TYPE)
                        .eq(CheckTask::getTaskYear, year)
                        .eq(CheckTask::getTargetType, targetType)
                        .ge(CheckTask::getQuarter, cycleStartQuarter(targetType, quarter))
                        .le(CheckTask::getQuarter, cycleEndQuarter(targetType, quarter))
                        .ne(CheckTask::getQuarter, quarter)
                        .eq(CheckTask::getCountCoverage, 1);
        systemPeriodService.applyTaskBoundary(wrapper);
        return taskMapper.selectList(wrapper).stream()
                .map(CheckTask::getTargetId)
                .collect(Collectors.toSet());
    }

    private List<CheckTask> currentTasks(
            Long executorDeptId, String targetType, int year, int quarter) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getExecutorDeptId, executorDeptId)
                        .eq(CheckTask::getCheckType, CHECK_TYPE)
                        .eq(CheckTask::getTaskYear, year)
                        .eq(CheckTask::getQuarter, quarter)
                        .eq(CheckTask::getTargetType, targetType));
    }

    private List<CheckTask> eligibleTasks(List<CheckTask> tasks, Set<Long> excludedTargetIds) {
        if (excludedTargetIds.isEmpty()) return tasks;
        return tasks.stream()
                .filter(task -> !excludedTargetIds.contains(task.getTargetId()))
                .toList();
    }

    private int requiredCount(int total, int percent, String targetType, int quarter) {
        if (percent >= 100) return total;
        int position = "STATION".equals(targetType) ? (quarter - 1) % 2 + 1 : quarter;
        double ratio = percent / 100D;
        int cumulative = Math.min(total, (int) Math.ceil(total * ratio * position));
        int previous = Math.min(total, (int) Math.ceil(total * ratio * (position - 1)));
        return Math.max(0, cumulative - previous);
    }

    private int coveragePercent(String executorDeptType, String targetType) {
        if ("BUREAU".equals(executorDeptType) && "STATION".equals(targetType))
            return configPercent("coverage.bureau.station.quarter", 50);
        if ("BUREAU".equals(executorDeptType) && "KEY_UNIT".equals(targetType))
            return configPercent("coverage.bureau.unit.quarter", 25);
        if ("BUREAU".equals(executorDeptType) && "IMPORTANT_PART".equals(targetType))
            return configPercent("coverage.bureau.part.quarter", 25);
        if ("STATION".equals(executorDeptType) && "KEY_UNIT".equals(targetType))
            return configPercent("coverage.station.unit.quarter", 100);
        if ("STATION".equals(executorDeptType) && "IMPORTANT_PART".equals(targetType))
            return configPercent("coverage.station.part.quarter", 100);
        return 0;
    }

    private int configPercent(String key, int fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) return fallback;
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(config.getConfigValue())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> targetTypesForDept(String deptType) {
        return switch (String.valueOf(deptType)) {
            case "BUREAU" -> List.of("STATION", "KEY_UNIT", "IMPORTANT_PART");
            case "STATION" -> List.of("KEY_UNIT", "IMPORTANT_PART");
            default -> List.of();
        };
    }

    private void assertTargetTypeAllowed(String deptType, String targetType) {
        if (!targetTypesForDept(deptType).contains(targetType))
            throw new BusinessException(403, "当前账号不能检查该对象类型");
    }

    private SysDept requiredExecutorDept(Long deptId) {
        var dept = deptMapper.selectById(deptId);
        if (dept == null || !List.of("BUREAU", "STATION").contains(dept.getDeptType()))
            throw new BusinessException("当前部门不能执行检查任务");
        return dept;
    }

    private String normalizeTargetType(String targetType) {
        String type = targetType == null ? "" : targetType.toUpperCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(type)) throw new BusinessException("未知检查对象类型");
        return type;
    }

    private String normalizeMode(String mode) {
        String value = mode == null ? "MANUAL" : mode.toUpperCase(Locale.ROOT);
        if (!List.of("MANUAL", "RANDOM").contains(value)) throw new BusinessException("筛选方式无效");
        return value;
    }

    private String targetLabel(String targetType) {
        return switch (targetType) {
            case "STATION" -> "派出所";
            case "KEY_UNIT" -> "重点单位";
            case "IMPORTANT_PART" -> "重要部位";
            default -> targetType;
        };
    }

    private int cycleStartQuarter(String targetType, int quarter) {
        return "STATION".equals(targetType) ? (quarter <= 2 ? 1 : 3) : 1;
    }

    private int cycleEndQuarter(String targetType, int quarter) {
        return "STATION".equals(targetType) ? (quarter <= 2 ? 2 : 4) : 4;
    }

    private boolean isSubmitted(CheckTask task) {
        return "APPROVED".equals(task.getStatus()) || "OVERDUE_SUBMITTED".equals(task.getStatus());
    }

    private String taskNo(
            int year, int quarter, Long executorDeptId, String targetType, Long targetId) {
        String code =
                switch (targetType) {
                    case "STATION" -> "S";
                    case "KEY_UNIT" -> "U";
                    case "IMPORTANT_PART" -> "P";
                    default -> "X";
                };
        return "NB" + year + "Q" + quarter + "-" + executorDeptId + "-" + code + "-" + targetId;
    }

    private int safeYear(Integer year) {
        int value = year == null ? systemPeriodService.effectivePeriod().year() : year;
        if (value < 2020 || value > LocalDate.now().getYear() + 2)
            throw new BusinessException("年度范围无效");
        return value;
    }

    private int safeQuarter(Integer quarter) {
        int value = quarter == null ? systemPeriodService.effectivePeriod().quarter() : quarter;
        if (value < 1 || value > 4) throw new BusinessException("季度必须为1至4");
        return value;
    }

    private LocalDate quarterStart(int year, int quarter) {
        return LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
    }

    private LocalDateTime quarterEnd(int year, int quarter) {
        return quarterStart(year, quarter)
                .plusMonths(3)
                .minusDays(1)
                .atTime(LocalTime.of(23, 59, 59));
    }

    private record TargetSeed(Long id, String name) {}

    public record SelectionStatus(
            String targetType,
            String targetLabel,
            int requiredCount,
            int submittedCount,
            int selectedCount,
            int remainingNeed,
            boolean fullCoverage,
            boolean needSelection,
            boolean initialized) {}

    public record SelectionTarget(
            Long taskId, Long targetId, String targetName, boolean selected) {}

    public record SelectionPool(
            String targetType,
            String targetLabel,
            int requiredCount,
            int submittedCount,
            int selectedCount,
            int remainingNeed,
            int selectableCount,
            List<SelectionTarget> targets) {}

    public record SelectionSaveRequest(
            String targetType, Integer year, Integer quarter, String mode, List<Long> targetIds) {}

    public record SelectionClearRequest(String targetType, Integer year, Integer quarter) {}
}

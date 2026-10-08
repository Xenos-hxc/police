package com.railway.security.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.archive.ArchiveDtos.HistoryCriteria;
import com.railway.security.archive.ArchiveDtos.HistoryDetailResponse;
import com.railway.security.archive.ArchiveDtos.HistoryItem;
import com.railway.security.archive.ArchiveDtos.HistoryProgress;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveHistoryService {
    private final ArchiveCatalogueQueryService catalogueQueryService;
    private final CheckTaskMapper taskMapper;
    private final CheckRecordMapper recordMapper;
    private final CheckAttachmentMapper attachmentMapper;
    private final DeptMapper deptMapper;
    private final SystemPeriodService systemPeriodService;

    @Transactional(readOnly = true)
    public List<CheckTask> history(String type, Long id, HistoryCriteria criteria) {
        loadArchive(type, id);
        return historyTasks(type, id, criteria);
    }

    @Transactional(readOnly = true)
    public HistoryDetailResponse detail(String type, Long id, HistoryCriteria criteria) {
        Object archive = loadArchive(type, id);
        var effectivePeriod = systemPeriodService.effectivePeriod();
        int statYear = criteria.year() == null ? effectivePeriod.year() : criteria.year();
        int statQuarter =
                criteria.quarter() == null ? effectivePeriod.quarter() : criteria.quarter();
        var tasks = historyTasks(type, id, criteria);
        var currentTasks =
                tasks.stream()
                        .filter(
                                task ->
                                        Objects.equals(task.getTaskYear(), statYear)
                                                && Objects.equals(task.getQuarter(), statQuarter))
                        .toList();
        long completed = currentTasks.stream().filter(this::isSubmitted).count();
        long overdue =
                currentTasks.stream().filter(task -> "OVERDUE".equals(task.getStatus())).count();
        long overdueCompleted =
                currentTasks.stream()
                        .filter(task -> "OVERDUE_SUBMITTED".equals(task.getStatus()))
                        .count();

        var taskIds = tasks.stream().map(CheckTask::getId).toList();
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
                                                value -> value,
                                                (first, ignored) -> first));
        Map<Long, List<CheckAttachment>> attachments =
                taskIds.isEmpty()
                        ? Map.of()
                        : attachmentMapper
                                .selectList(
                                        new LambdaQueryWrapper<CheckAttachment>()
                                                .in(CheckAttachment::getTaskId, taskIds)
                                                .orderByDesc(CheckAttachment::getCreateTime))
                                .stream()
                                .collect(Collectors.groupingBy(CheckAttachment::getTaskId));
        var deptIds =
                tasks.stream()
                        .map(CheckTask::getExecutorDeptId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, SysDept> departments =
                deptIds.isEmpty()
                        ? Map.of()
                        : deptMapper.selectBatchIds(deptIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                SysDept::getId,
                                                value -> value,
                                                (first, ignored) -> first));
        var items =
                tasks.stream()
                        .map(
                                task -> {
                                    SysDept department = departments.get(task.getExecutorDeptId());
                                    return new HistoryItem(
                                            task,
                                            department == null
                                                    ? "未知检查部门"
                                                    : department.getDeptName(),
                                            department == null ? "" : department.getDeptType(),
                                            records.get(task.getId()),
                                            attachments.getOrDefault(task.getId(), List.of()));
                                })
                        .toList();
        var progress =
                new HistoryProgress(
                        statYear,
                        statQuarter,
                        currentTasks.size(),
                        completed,
                        Math.max(0, currentTasks.size() - completed),
                        completed,
                        overdue,
                        overdueCompleted,
                        currentTasks.isEmpty()
                                ? 0D
                                : Math.round(completed * 10000D / currentTasks.size()) / 100D);
        return new HistoryDetailResponse(archive, progress, items, true);
    }

    private List<CheckTask> historyTasks(String type, Long id, HistoryCriteria criteria) {
        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getTargetId, id)
                        .eq(CheckTask::getTargetType, targetType(type))
                        .eq(criteria.year() != null, CheckTask::getTaskYear, criteria.year())
                        .eq(criteria.quarter() != null, CheckTask::getQuarter, criteria.quarter())
                        .ge(
                                criteria.startDate() != null,
                                CheckTask::getStartDate,
                                criteria.startDate())
                        .le(criteria.endDate() != null, CheckTask::getStartDate, criteria.endDate())
                        .and(
                                query ->
                                        query.eq(CheckTask::getCountCoverage, 1)
                                                .or()
                                                .in(
                                                        CheckTask::getStatus,
                                                        List.of("APPROVED", "OVERDUE_SUBMITTED")))
                        .orderByDesc(CheckTask::getTaskYear)
                        .orderByDesc(CheckTask::getQuarter)
                        .orderByDesc(CheckTask::getCreateTime);
        systemPeriodService.applyTaskBoundary(wrapper);
        var user = SecurityUtils.currentUser();
        if (user.getRoles().contains("STATION")) {
            var bureauDeptIds =
                    deptMapper
                            .selectList(
                                    new LambdaQueryWrapper<SysDept>()
                                            .eq(SysDept::getDeptType, "BUREAU"))
                            .stream()
                            .map(SysDept::getId)
                            .toList();
            wrapper.and(
                    query ->
                            query.eq(CheckTask::getExecutorDeptId, user.getDeptId())
                                    .or()
                                    .in(
                                            !bureauDeptIds.isEmpty(),
                                            CheckTask::getExecutorDeptId,
                                            bureauDeptIds));
        }
        return taskMapper.selectList(wrapper);
    }

    private Object loadArchive(String type, Long id) {
        return switch (type) {
            case "police-stations" -> catalogueQueryService.station(id);
            case "key-units" -> catalogueQueryService.unit(id);
            case "important-parts" -> catalogueQueryService.part(id);
            default -> throw new com.railway.security.shared.web.BusinessException("不支持的档案类型");
        };
    }

    private String targetType(String type) {
        return switch (type) {
            case "police-stations" -> "STATION";
            case "key-units" -> "KEY_UNIT";
            case "important-parts" -> "IMPORTANT_PART";
            default -> throw new com.railway.security.shared.web.BusinessException("不支持的档案类型");
        };
    }

    private boolean isSubmitted(CheckTask task) {
        return "APPROVED".equals(task.getStatus()) || "OVERDUE_SUBMITTED".equals(task.getStatus());
    }
}

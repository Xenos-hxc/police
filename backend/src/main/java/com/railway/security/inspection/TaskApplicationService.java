package com.railway.security.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskApplicationService {
    private static final List<String> COMPLETED_STATUSES = List.of("APPROVED", "OVERDUE_SUBMITTED");

    private final CheckTaskMapper taskMapper;
    private final TaskService taskService;
    private final CheckRecordMapper recordMapper;
    private final CheckAttachmentMapper attachmentMapper;
    private final TaskExcelService taskExcelService;
    private final TaskSelectionService taskSelectionService;
    private final StatisticsCacheService statisticsCacheService;
    private final SystemPeriodService systemPeriodService;

    @Transactional(readOnly = true)
    // 分页查询先固定数据范围与排序，再批量补充展示信息；复合索引需匹配过滤和排序，避免 N+1 与逐行相关子查询。
    public PageResult<CheckTask> list(TaskSearchCriteria criteria) {
        var wrapper =
                commonWrapper(criteria)
                        .in(
                                "COMPLETED".equalsIgnoreCase(criteria.completion()),
                                CheckTask::getStatus,
                                COMPLETED_STATUSES)
                        .in(
                                "UNFINISHED".equalsIgnoreCase(criteria.completion()),
                                CheckTask::getStatus,
                                List.of("PENDING", "OVERDUE"))
                        .like(
                                hasText(criteria.creationMode()),
                                CheckTask::getCreationMode,
                                criteria.creationMode())
                        .last("ORDER BY status_sort ASC, create_time DESC, id DESC");
        applyExecutableScope(wrapper);
        var result =
                taskMapper.selectPage(
                        new Page<>(safePage(criteria.page()), safeSize(criteria.size())), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional(readOnly = true)
    public TaskListSummary summary(TaskSearchCriteria criteria) {
        var wrapper = commonWrapper(criteria).in(CheckTask::getStatus, COMPLETED_STATUSES);
        applyExecutableScope(wrapper);
        long completed = taskMapper.selectCount(wrapper);
        var statuses =
                taskSelectionService.statuses(criteria.year(), criteria.quarter()).stream()
                        .filter(
                                status ->
                                        !hasText(criteria.targetType())
                                                || criteria.targetType()
                                                        .equals(status.targetType()))
                        .toList();
        long required =
                statuses.stream()
                        .mapToLong(TaskSelectionService.SelectionStatus::requiredCount)
                        .sum();
        completed = Math.min(completed, required);
        long unfinished = Math.max(0, required - completed);
        double rate = required == 0 ? 0D : Math.round(completed * 10000D / required) / 100D;
        return new TaskListSummary(required, completed, unfinished, rate);
    }

    @Transactional(readOnly = true)
    public PageResult<CheckTask> overdueReminders(long page, long size) {
        var result =
                taskMapper.selectPage(
                        new Page<>(safePage(page), safeSize(size)), overdueReminderWrapper());
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional(readOnly = true)
    public CheckTask detail(Long id) {
        var task = taskMapper.selectById(id);
        taskService.assertTaskAccess(task);
        return task;
    }

    @Transactional(readOnly = true)
    public byte[] export(TaskSearchCriteria criteria) {
        var wrapper =
                commonWrapper(criteria).last("ORDER BY status_sort ASC, create_time DESC, id DESC");
        applyExecutableScope(wrapper);
        return taskExcelService.export(taskMapper.selectList(wrapper));
    }

    @Transactional(readOnly = true)
    public TaskFullDetail fullDetail(Long id) {
        var task = taskMapper.selectById(id);
        taskService.assertTaskAccess(task);
        var record =
                recordMapper.selectOne(
                        new LambdaQueryWrapper<CheckRecord>().eq(CheckRecord::getTaskId, id));
        var attachments =
                attachmentMapper.selectList(
                        new LambdaQueryWrapper<CheckAttachment>()
                                .eq(CheckAttachment::getTaskId, id)
                                .orderByDesc(CheckAttachment::getCreateTime));
        return new TaskFullDetail(task, record, attachments);
    }

    @Transactional
    public void update(Long id, CheckTask update) {
        var task = taskMapper.selectById(id);
        taskService.assertEditable(task);
        if (update.getDeadline() != null && !update.getDeadline().equals(task.getDeadline())) {
            throw new BusinessException("截止日期已固化，业务账号不能修改");
        }
        if (update.getStartDate() != null && !update.getStartDate().equals(task.getStartDate())) {
            throw new BusinessException("任务开始日期已固化，不能修改");
        }
        task.setRemark(update.getRemark());
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessException("任务已被其他操作更新，请刷新后重试");
        }
        statisticsCacheService.clear();
    }

    private LambdaQueryWrapper<CheckTask> commonWrapper(TaskSearchCriteria criteria) {
        return new LambdaQueryWrapper<CheckTask>()
                .like(hasText(criteria.status()), CheckTask::getStatus, criteria.status())
                .like(hasText(criteria.checkType()), CheckTask::getCheckType, criteria.checkType())
                .eq(hasText(criteria.targetType()), CheckTask::getTargetType, criteria.targetType())
                .like(
                        hasText(criteria.targetName()),
                        CheckTask::getTargetName,
                        criteria.targetName())
                .and(
                        hasText(criteria.keyword()),
                        wrapper ->
                                wrapper.like(CheckTask::getTaskNo, criteria.keyword())
                                        .or()
                                        .like(CheckTask::getTargetName, criteria.keyword()))
                .eq(criteria.year() != null, CheckTask::getTaskYear, criteria.year())
                .eq(criteria.quarter() != null, CheckTask::getQuarter, criteria.quarter())
                .eq(criteria.halfYear() != null, CheckTask::getHalfYear, criteria.halfYear())
                .eq(criteria.overdue() != null, CheckTask::getOverdue, criteria.overdue())
                .and(
                        wrapper ->
                                wrapper.eq(CheckTask::getCountCoverage, 1)
                                        .or()
                                        .in(CheckTask::getStatus, COMPLETED_STATUSES));
    }

    private LambdaQueryWrapper<CheckTask> overdueReminderWrapper() {
        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getExecutorDeptId, SecurityUtils.currentUser().getDeptId())
                        .eq(CheckTask::getCheckType, "INTERNAL_SECURITY")
                        .eq(CheckTask::getStatus, "OVERDUE")
                        .eq(CheckTask::getCountCoverage, 1)
                        .orderByAsc(CheckTask::getDeadline)
                        .orderByAsc(CheckTask::getTaskNo);
        systemPeriodService.applyTaskBoundary(wrapper);
        return wrapper;
    }

    private void applyExecutableScope(LambdaQueryWrapper<CheckTask> wrapper) {
        wrapper.eq(CheckTask::getExecutorDeptId, SecurityUtils.currentUser().getDeptId());
        systemPeriodService.applyTaskBoundary(wrapper);
    }

    private long safePage(long page) {
        return Math.max(1, page);
    }

    private long safeSize(long size) {
        return Math.max(1, Math.min(size, 100));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record TaskSearchCriteria(
            long page,
            long size,
            String status,
            String checkType,
            String keyword,
            Integer year,
            Integer quarter,
            Integer halfYear,
            Integer overdue,
            String targetType,
            String targetName,
            String completion,
            String creationMode) {}

    public record TaskListSummary(
            long totalCount, long completedCount, long unfinishedCount, double completionRate) {}

    public record TaskFullDetail(
            CheckTask task, CheckRecord record, List<CheckAttachment> attachments) {}
}

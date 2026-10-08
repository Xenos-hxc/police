package com.railway.security.rectification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.RemindRecord;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.RemindRecordMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReminderService {
    private final RemindRecordMapper remindMapper;
    private final CheckTaskMapper taskMapper;
    private final DataScopeService dataScopeService;
    private final SystemPeriodService systemPeriodService;

    @Transactional(readOnly = true)
    public PageResult<RemindRecord> list(long page, long size, Integer readFlag) {
        var result =
                remindMapper.selectPage(
                        new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 100))),
                        visibleWrapper()
                                .eq(readFlag != null, RemindRecord::getReadFlag, readFlag)
                                .orderByDesc(RemindRecord::getRemindTime));
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return remindMapper.selectCount(visibleWrapper().eq(RemindRecord::getReadFlag, 0));
    }

    @Transactional
    public void markRead(Long id) {
        var record = remindMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("提醒不存在");
        }
        dataScopeService.checkDept(record.getReceiverDeptId());
        if (!visibleTaskIds().contains(record.getTaskId())) {
            throw new BusinessException("提醒不存在");
        }
        record.setReadFlag(1);
        if (remindMapper.updateById(record) != 1) {
            throw new BusinessException("提醒状态更新失败");
        }
    }

    @Transactional
    public void markAllRead() {
        remindMapper.update(
                null,
                new LambdaUpdateWrapper<RemindRecord>()
                        .in(
                                RemindRecord::getReceiverDeptId,
                                safeIds(dataScopeService.permittedDeptIds()))
                        .in(RemindRecord::getTaskId, visibleTaskIds())
                        .eq(RemindRecord::getReadFlag, 0)
                        .set(RemindRecord::getReadFlag, 1));
    }

    private LambdaQueryWrapper<RemindRecord> visibleWrapper() {
        return new LambdaQueryWrapper<RemindRecord>()
                .in(RemindRecord::getReceiverDeptId, safeIds(dataScopeService.permittedDeptIds()))
                .in(RemindRecord::getTaskId, visibleTaskIds());
    }

    private List<Long> visibleTaskIds() {
        var deptIds = dataScopeService.permittedDeptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of(-1L);
        }
        var wrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .select(CheckTask::getId)
                        .in(CheckTask::getExecutorDeptId, deptIds);
        systemPeriodService.applyTaskBoundary(wrapper);
        return safeIds(taskMapper.selectList(wrapper).stream().map(CheckTask::getId).toList());
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }
}

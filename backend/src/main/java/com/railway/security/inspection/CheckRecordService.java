package com.railway.security.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.rectification.HiddenDangerService;
import com.railway.security.shared.web.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckRecordService {
    private final CheckRecordMapper recordMapper;
    private final CheckTaskMapper taskMapper;
    private final TaskService taskService;
    private final HiddenDangerService hiddenDangerService;

    @Transactional(readOnly = true)
    public CheckRecord byTask(Long taskId) {
        taskService.assertTaskMaterialReadAccess(taskMapper.selectById(taskId));
        return recordMapper.selectOne(
                new LambdaQueryWrapper<CheckRecord>().eq(CheckRecord::getTaskId, taskId));
    }

    @Transactional
    public CheckRecord create(CheckRecord record) {
        var task = taskMapper.selectForUpdate(record.getTaskId());
        taskService.assertEditable(task);
        if (recordMapper.selectCount(
                        new LambdaQueryWrapper<CheckRecord>()
                                .eq(CheckRecord::getTaskId, record.getTaskId()))
                > 0) {
            throw new BusinessException("该任务已存在检查笔录");
        }
        record.setSubmittedBy(null);
        record.setSubmittedTime(null);
        record.setComplete(0);
        record.setHasDanger(Integer.valueOf(1).equals(record.getHasDanger()) ? 1 : 0);
        if (recordMapper.insert(record) != 1) {
            throw new BusinessException("检查笔录保存失败");
        }
        return record;
    }

    @Transactional
    public void update(Long id, CheckRecord update) {
        var old = recordMapper.selectById(id);
        if (old == null) {
            throw new BusinessException("检查笔录不存在");
        }
        var task = taskMapper.selectForUpdate(old.getTaskId());
        taskService.assertEditable(task);
        copyEditable(update, old);
        if ("APPROVED".equals(task.getStatus()) || "OVERDUE_SUBMITTED".equals(task.getStatus())) {
            if (!isComplete(old)) {
                throw new BusinessException("已完成任务的检查时间和检查人员不能为空");
            }
            hiddenDangerService.syncFromInspection(task, old);
            old.setComplete(1);
        } else {
            old.setComplete(0);
            old.setSubmittedBy(null);
            old.setSubmittedTime(null);
        }
        if (recordMapper.updateById(old) != 1) {
            throw new BusinessException("检查笔录已被其他操作更新，请刷新后重试");
        }
    }

    @Transactional(readOnly = true)
    public CheckRecord printable(Long id) {
        var record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("检查笔录不存在");
        }
        taskService.assertTaskMaterialReadAccess(taskMapper.selectById(record.getTaskId()));
        return record;
    }

    private boolean isComplete(CheckRecord record) {
        return record.getCheckTime() != null && hasText(record.getInspectors());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void copyEditable(CheckRecord source, CheckRecord target) {
        target.setCheckTime(source.getCheckTime());
        target.setInspectors(source.getInspectors());
        target.setHasDanger(Integer.valueOf(1).equals(source.getHasDanger()) ? 1 : 0);
        target.setRectificationType(source.getRectificationType());
        target.setDangerDetail(source.getDangerDetail());
        target.setRectificationDeadline(source.getRectificationDeadline());
        target.setRemark(source.getRemark());
    }
}

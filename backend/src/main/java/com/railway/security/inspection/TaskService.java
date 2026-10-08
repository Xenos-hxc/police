package com.railway.security.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.HiddenDangerMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final CheckTaskMapper taskMapper;
    private final CheckRecordMapper recordMapper;
    private final CheckAttachmentMapper attachmentMapper;
    private final HiddenDangerMapper hiddenDangerMapper;
    private final DataScopeService dataScopeService;
    private final ConfigMapper configMapper;
    private final StatisticsCacheService statisticsCacheService;

    // 角色权限与数据范围取交集；此处 ADMIN 显式拒绝优先于 BUREAU 放行，不等同于材料读取入口的权限政策。
    public void assertTaskAccess(CheckTask task) {
        if (task == null) throw new BusinessException("任务不存在");
        var user = SecurityUtils.currentUser();
        if (user.getRoles().contains("ADMIN")) {
            throw new BusinessException(403, "系统管理员不能访问检查任务");
        }
        if (user.getRoles().contains("BUREAU")) return;
        if (user.getRoles().contains("STATION")
                && user.getDeptId().equals(task.getExecutorDeptId())) return;
        throw new BusinessException(403, "无权访问该任务");
    }

    public void assertTaskMaterialReadAccess(CheckTask task) {
        if (task == null) throw new BusinessException("任务不存在");
        var user = SecurityUtils.currentUser();
        if (user.getRoles().contains("ADMIN")
                || user.getRoles().contains("BUREAU")
                || user.getDeptId().equals(task.getExecutorDeptId())) return;
        var executor = dataScopeService.dept(task.getExecutorDeptId());
        if (user.getRoles().contains("STATION")
                && executor != null
                && "BUREAU".equals(executor.getDeptType())
                && dataScopeService
                        .permittedTargetIds(task.getTargetType())
                        .contains(task.getTargetId())) return;
        throw new BusinessException(403, "无权访问该检查材料");
    }

    // 状态转换统一校验期间、执行部门和可编辑条件；APPROVED 在本业务中不能直接解释为独立人工审批完成。
    public void assertEditable(CheckTask task) {
        assertTaskAccess(task);
        if (!Integer.valueOf(1).equals(task.getCountCoverage())) {
            throw new BusinessException("该任务尚未纳入本季度检查范围，不能提交或修改检查材料");
        }
        if (isFuturePeriod(task)) {
            throw new BusinessException("未到该任务所属季度，不能提交或修改检查材料");
        }
        var user = SecurityUtils.currentUser();
        if (!user.getDeptId().equals(task.getExecutorDeptId())) {
            throw new BusinessException(403, "仅任务执行部门可以修改或提交检查材料");
        }
        boolean beforeDeadline = !LocalDateTime.now().isAfter(task.getDeadline());
        boolean overdueEditable =
                List.of("PENDING", "OVERDUE").contains(task.getStatus())
                        && !beforeDeadline
                        && configBoolean("task.allow.overdue.submit", true);
        boolean editableStatus =
                ("PENDING".equals(task.getStatus()) && beforeDeadline)
                        || overdueEditable
                        || ("APPROVED".equals(task.getStatus()) && beforeDeadline);
        if (!editableStatus) {
            throw new BusinessException("当前任务状态不允许修改");
        }
    }

    @Transactional
    // 事务内对任务加行锁并校验材料，再更新状态；实体版本号是另一层并发保护，不能替代一致的锁顺序。
    public void submit(Long taskId) {
        var task = taskMapper.selectForUpdate(taskId);
        assertEditable(task);
        if (LocalDateTime.now().isAfter(task.getDeadline())
                && !configBoolean("task.allow.overdue.submit", true)) {
            throw new BusinessException("系统配置不允许逾期补交");
        }
        var record =
                recordMapper.selectOne(
                        new LambdaQueryWrapper<CheckRecord>()
                                .eq(CheckRecord::getTaskId, taskId)
                                .eq(CheckRecord::getComplete, 1));
        if (record == null) throw new BusinessException("请先完整填写检查笔录");
        long videoCount =
                attachmentMapper.selectCount(
                        new LambdaQueryWrapper<CheckAttachment>()
                                .eq(CheckAttachment::getTaskId, taskId)
                                .eq(CheckAttachment::getAttachmentType, "VIDEO")
                                .eq(CheckAttachment::getStorageStatus, "ACTIVE")
                                .in(CheckAttachment::getScanStatus, "CLEAN", "SKIPPED"));
        if (videoCount == 0) throw new BusinessException("请至少上传一个检查视频");
        long recordAttachmentCount =
                attachmentMapper.selectCount(
                        new LambdaQueryWrapper<CheckAttachment>()
                                .eq(CheckAttachment::getTaskId, taskId)
                                .eq(CheckAttachment::getAttachmentType, "RECORD")
                                .eq(CheckAttachment::getStorageStatus, "ACTIVE")
                                .in(CheckAttachment::getScanStatus, "CLEAN", "SKIPPED"));
        if (recordAttachmentCount == 0) throw new BusinessException("请至少上传一份检查笔录");
        boolean overdueSubmit =
                LocalDateTime.now().isAfter(task.getDeadline())
                        || Integer.valueOf(1).equals(task.getOverdue());
        task.setStatus(overdueSubmit ? "OVERDUE_SUBMITTED" : "APPROVED");
        task.setOverdueSubmitted(overdueSubmit ? 1 : 0);
        if (taskMapper.updateById(task) != 1) throw new BusinessException("任务已被其他操作更新，请刷新后重试");
        statisticsCacheService.clear();
    }

    public boolean isFuturePeriod(CheckTask task) {
        if (task == null) {
            return false;
        }
        var start = quarterStart(task.getTaskYear(), task.getQuarter());
        return start != null && LocalDateTime.now().isBefore(start);
    }

    private LocalDateTime quarterStart(Integer year, Integer quarter) {
        if (year == null || quarter == null || quarter < 1 || quarter > 4) {
            return null;
        }
        return LocalDate.of(year, (quarter - 1) * 3 + 1, 1).atStartOfDay();
    }

    private LocalDateTime quarterEnd(Integer year, Integer quarter) {
        var start = quarterStart(year, quarter);
        if (start == null) {
            return null;
        }
        return start.toLocalDate().plusMonths(3).minusDays(1).atTime(LocalTime.of(23, 59, 59));
    }

    public boolean configBoolean(String key, boolean fallback) {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config == null ? fallback : Boolean.parseBoolean(config.getConfigValue());
    }
}

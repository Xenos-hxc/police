package com.railway.security.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.RemindRecord;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.RemindRecordMapper;
import com.railway.security.rectification.HiddenDangerService;
import com.railway.security.shared.scheduling.MysqlJobLock;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OverdueScanTask {
    private final CheckTaskMapper taskMapper;
    private final RemindRecordMapper remindMapper;
    private final ConfigMapper configMapper;
    private final StatisticsCacheService statisticsCacheService;
    private final HiddenDangerService hiddenDangerService;
    private final TaskSelectionService taskSelectionService;
    private final SystemPeriodService systemPeriodService;
    private final MysqlJobLock jobLock;

    @Scheduled(cron = "0 5 1 * * ?")
    @EventListener(ApplicationReadyEvent.class)
    public void scan() {
        jobLock.runIfLeader("railway:overdue-scan", this::scanUnlocked);
    }

    private void scanUnlocked() {
        taskSelectionService.initializeCurrentPeriodForAll();
        var now = LocalDateTime.now();
        var configuredRemindDays = remindDays();
        var taskWrapper =
                new LambdaQueryWrapper<CheckTask>()
                        .in(CheckTask::getStatus, List.of("PENDING", "OVERDUE"))
                        .eq(CheckTask::getCountCoverage, 1);
        systemPeriodService.applyTaskBoundary(taskWrapper);
        var tasks = taskMapper.selectList(taskWrapper);
        boolean changed = false;
        for (var task : tasks) {
            long days =
                    ChronoUnit.DAYS.between(now.toLocalDate(), task.getDeadline().toLocalDate());
            if (task.getDeadline().isBefore(now)) {
                if (!"OVERDUE".equals(task.getStatus())
                        || !Integer.valueOf(1).equals(task.getOverdue())) {
                    task.setOverdue(1);
                    task.setStatus("OVERDUE");
                    taskMapper.updateById(task);
                    changed = true;
                }
                createReminder(task, "OVERDUE", "任务已逾期，请尽快补交检查材料");
            } else if (configuredRemindDays.contains(days)) {
                createReminder(task, "DUE_SOON", "任务将在 " + days + " 天后截止");
            }
        }
        if (changed) {
            statisticsCacheService.clear();
        }
        taskSelectionService.sealElapsedSnapshots();
        hiddenDangerService.refreshOverdue();
    }

    private List<Long> remindDays() {
        var config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>()
                                .eq(SysConfig::getConfigKey, "task.remind.days"));
        if (config == null || config.getConfigValue() == null) return List.of(7L, 3L, 1L, 0L);
        try {
            return java.util.Arrays.stream(config.getConfigValue().split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException ignored) {
            return List.of(7L, 3L, 1L, 0L);
        }
    }

    private void createReminder(CheckTask task, String type, String content) {
        var todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        if (remindMapper.selectCount(
                        new LambdaQueryWrapper<RemindRecord>()
                                .eq(RemindRecord::getTaskId, task.getId())
                                .eq(RemindRecord::getRemindType, type)
                                .ge(RemindRecord::getRemindTime, todayStart))
                > 0) return;
        var remind = new RemindRecord();
        remind.setTaskId(task.getId());
        remind.setReceiverDeptId(task.getExecutorDeptId());
        remind.setRemindType(type);
        remind.setRemindContent(content);
        remind.setRemindTime(LocalDateTime.now());
        remind.setReadFlag(0);
        remindMapper.insert(remind);
    }
}

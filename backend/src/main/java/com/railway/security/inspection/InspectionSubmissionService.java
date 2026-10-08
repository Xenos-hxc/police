package com.railway.security.inspection;

import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.rectification.HiddenDangerService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionSubmissionService {
    private final CheckRecordMapper recordMapper;
    private final CheckTaskMapper taskMapper;
    private final TaskService taskService;
    private final HiddenDangerService hiddenDangerService;

    @Transactional
    // 外层事务串联笔录、隐患与任务提交；跨 Bean 代理调用与本类自调用语义不同，外部文件和缓存不会随数据库回滚。
    public void submit(Long recordId) {
        CheckRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException("检查笔录不存在");
        if (record.getCheckTime() == null
                || record.getInspectors() == null
                || record.getInspectors().isBlank()) {
            throw new BusinessException("检查时间和检查人员不能为空");
        }
        var task = taskMapper.selectById(record.getTaskId());
        taskService.assertEditable(task);
        hiddenDangerService.syncFromInspection(task, record);
        record.setComplete(1);
        record.setSubmittedBy(SecurityUtils.currentUser().getUserId());
        record.setSubmittedTime(LocalDateTime.now());
        recordMapper.updateById(record);
        taskService.submit(task.getId());
    }
}

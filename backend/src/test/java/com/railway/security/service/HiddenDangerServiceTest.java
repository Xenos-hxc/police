package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.HiddenDanger;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.HiddenDangerMapper;
import com.railway.security.rectification.HiddenDangerService;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class HiddenDangerServiceTest {
    @Mock private HiddenDangerMapper dangerMapper;
    @Mock private CheckAttachmentMapper attachmentMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private ConfigMapper configMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void firstDeadlineDangerCreatesPendingTask() {
        var service = service();
        when(dangerMapper.selectOne(any())).thenReturn(null);
        when(attachmentMapper.selectCount(any())).thenReturn(1L);

        CheckTask task = task();
        CheckRecord record = new CheckRecord();
        record.setHasDanger(1);
        record.setRectificationType(HiddenDangerService.DEADLINE);
        record.setDangerDetail("消防通道堆放杂物");
        record.setRectificationDeadline(LocalDate.now().plusDays(7));

        service.syncFromInspection(task, record);

        ArgumentCaptor<HiddenDanger> captor = ArgumentCaptor.forClass(HiddenDanger.class);
        verify(dangerMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(task.getId(), captor.getValue().getTaskId());
        assertEquals(
                record.getRectificationDeadline(), captor.getValue().getRectificationDeadline());
    }

    @Test
    void changingPendingDangerToNoDangerCancelsRectification() {
        var service = service();
        HiddenDanger existing = new HiddenDanger();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setRectificationType(HiddenDangerService.DEADLINE);
        when(dangerMapper.selectOne(any())).thenReturn(existing);

        CheckRecord record = new CheckRecord();
        record.setHasDanger(0);
        record.setRectificationType(HiddenDangerService.DEADLINE);
        record.setDangerDetail("旧隐患");
        record.setRectificationDeadline(LocalDate.now().plusDays(7));

        service.syncFromInspection(task(), record);

        assertEquals("CANCELLED", existing.getStatus());
        assertEquals(0, record.getHasDanger());
        assertNull(record.getRectificationType());
        assertNull(record.getDangerDetail());
        assertNull(record.getRectificationDeadline());
        verify(dangerMapper).updateById(existing);
    }

    @Test
    void overdueCompletedDangerCanBeEditedAndResubmitted() {
        var service = service();
        HiddenDanger existing = new HiddenDanger();
        existing.setId(12L);
        existing.setTaskId(8L);
        existing.setTaskYear(2026);
        existing.setQuarter(3);
        existing.setExecutorDeptId(101L);
        existing.setRectificationType(HiddenDangerService.DEADLINE);
        existing.setRectificationDeadline(LocalDate.now().minusDays(2));
        existing.setStatus("OVERDUE_COMPLETED");
        when(dangerMapper.selectById(12L)).thenReturn(existing);
        when(dataScopeService.permittedDeptIds()).thenReturn(List.of(101L));
        when(attachmentMapper.selectCount(any())).thenReturn(1L);
        authenticateStation();

        LocalDateTime checkTime = LocalDateTime.now().withNano(0);
        var request = new HiddenDangerService.RectificationRequest(checkTime, "复查民警", "补充整改说明");
        service.saveRectificationDraft(12L, request);
        service.submitRectification(12L, request);

        assertEquals("OVERDUE_COMPLETED", existing.getStatus());
        assertEquals(checkTime, existing.getRectificationCheckTime());
        assertEquals("复查民警", existing.getRectificationInspectors());
        assertEquals("补充整改说明", existing.getRectificationRemark());
        verify(dangerMapper, times(2)).updateById(existing);
    }

    private HiddenDangerService service() {
        return new HiddenDangerService(
                dangerMapper,
                attachmentMapper,
                deptMapper,
                dataScopeService,
                configMapper,
                new SystemPeriodService(configMapper));
    }

    private CheckTask task() {
        CheckTask task = new CheckTask();
        task.setId(8L);
        task.setTaskNo("NB2026Q3-101-U-1001");
        task.setTaskYear(2026);
        task.setQuarter(3);
        task.setExecutorDeptId(101L);
        task.setTargetId(1001L);
        task.setTargetType("KEY_UNIT");
        task.setTargetName("测试重点单位");
        return task;
    }

    private void authenticateStation() {
        SysUser user = new SysUser();
        user.setId(3L);
        user.setDeptId(101L);
        user.setUsername("station");
        user.setPassword("Demo-Only-Change-Me!2026");
        user.setRealName("测试派出所");
        user.setDataScope("DEPT");
        user.setForceChangePassword(0);
        user.setStatus(1);
        LoginUser loginUser = new LoginUser(user, List.of("STATION"), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                loginUser, null, loginUser.getAuthorities()));
    }
}

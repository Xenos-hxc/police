package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.railway.security.inspection.TaskService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.HiddenDangerMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TaskServiceAccessTest {
    @Mock private CheckTaskMapper taskMapper;
    @Mock private CheckRecordMapper recordMapper;
    @Mock private CheckAttachmentMapper attachmentMapper;
    @Mock private HiddenDangerMapper hiddenDangerMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private ConfigMapper configMapper;
    @Mock private StatisticsCacheService statisticsCacheService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stationCannotOpenPeerTaskEvenWhenTargetIsShared() {
        login(101L, "STATION");
        CheckTask own = task(101L, 9001L);
        CheckTask peer = task(102L, 9001L);

        assertDoesNotThrow(() -> service().assertTaskAccess(own));
        assertThrows(BusinessException.class, () -> service().assertTaskAccess(peer));
    }

    @Test
    void stationCanReadBureauMaterialForJurisdictionButNotPeerMaterial() {
        login(101L, "STATION");
        SysDept bureau = new SysDept();
        bureau.setId(1L);
        bureau.setDeptType("BUREAU");
        SysDept peer = new SysDept();
        peer.setId(102L);
        peer.setDeptType("STATION");
        when(dataScopeService.dept(1L)).thenReturn(bureau);
        when(dataScopeService.dept(102L)).thenReturn(peer);
        when(dataScopeService.permittedTargetIds("IMPORTANT_PART")).thenReturn(List.of(9001L));

        assertDoesNotThrow(() -> service().assertTaskMaterialReadAccess(task(1L, 9001L)));
        assertThrows(
                BusinessException.class,
                () -> service().assertTaskMaterialReadAccess(task(102L, 9001L)));
    }

    @Test
    void administratorCannotAccessBusinessTaskButCanReadArchivedMaterial() {
        login(1L, "ADMIN");
        assertThrows(BusinessException.class, () -> service().assertTaskAccess(task(101L, 9001L)));
        assertDoesNotThrow(() -> service().assertTaskMaterialReadAccess(task(101L, 9001L)));
    }

    @Test
    void bureauCanAccessAllTaskLevels() {
        login(1L, "BUREAU");
        assertDoesNotThrow(() -> service().assertTaskAccess(task(120L, 9001L)));
        assertDoesNotThrow(() -> service().assertTaskMaterialReadAccess(task(120L, 9001L)));
    }

    @Test
    void unselectedPresetTaskCannotBeEdited() {
        login(1L, "BUREAU");
        CheckTask task = task(1L, 9001L);
        LocalDate now = LocalDate.now();
        task.setTaskYear(now.getYear());
        task.setQuarter((now.getMonthValue() - 1) / 3 + 1);
        task.setDeadline(LocalDateTime.now().plusDays(1));
        task.setStatus("PENDING");
        task.setCountCoverage(0);

        assertThrows(BusinessException.class, () -> service().assertEditable(task));
        task.setCountCoverage(1);
        assertDoesNotThrow(() -> service().assertEditable(task));
    }

    private TaskService service() {
        return new TaskService(
                taskMapper,
                recordMapper,
                attachmentMapper,
                hiddenDangerMapper,
                dataScopeService,
                configMapper,
                statisticsCacheService);
    }

    private CheckTask task(Long executorDeptId, Long targetId) {
        CheckTask task = new CheckTask();
        task.setId(88L);
        task.setExecutorDeptId(executorDeptId);
        task.setTargetType("IMPORTANT_PART");
        task.setTargetId(targetId);
        return task;
    }

    private void login(Long deptId, String role) {
        SysUser user = new SysUser();
        user.setId(99L);
        user.setDeptId(deptId);
        user.setUsername("test");
        user.setPassword("unused");
        user.setRealName("test");
        user.setStatus(1);
        user.setDataScope("DEPT");
        user.setForceChangePassword(0);
        user.setTokenVersion(0);
        LoginUser principal = new LoginUser(user, List.of(role), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }
}

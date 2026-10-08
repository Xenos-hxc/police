package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.railway.security.inspection.TaskSelectionService;
import com.railway.security.inspection.TaskService;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.statistics.StatisticsService;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceAccessTest {
    @Mock private CheckTaskMapper taskMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private TaskSelectionService taskSelectionService;
    @Mock private TaskService taskService;
    @Mock private ConfigMapper configMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorCanQueryStationRanking() {
        login("ADMIN");
        when(dataScopeService.permittedDeptIds()).thenReturn(List.of(1L));
        when(deptMapper.selectList(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service().ranking(query()));
    }

    @Test
    void stationCannotQueryUpperLevelRanking() {
        login("STATION");
        assertThrows(BusinessException.class, () -> service().ranking(query()));
    }

    private StatisticsService service() {
        return new StatisticsService(
                taskMapper,
                deptMapper,
                dataScopeService,
                taskSelectionService,
                taskService,
                new StatisticsCacheService(
                        new com.railway.security.shared.cache.LocalTemporaryValueStore(),
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()),
                new SystemPeriodService(configMapper));
    }

    private StatisticsService.StatisticsQuery query() {
        return new StatisticsService.StatisticsQuery(
                2026, 3, null, null, null, null, null, null, "STATION", 1, 10);
    }

    private void login(String role) {
        SysUser user = new SysUser();
        user.setId("ADMIN".equals(role) ? 1L : 101L);
        user.setDeptId("ADMIN".equals(role) ? 1L : 101L);
        user.setUsername("test");
        user.setPassword("unused");
        user.setRealName("测试用户");
        user.setStatus(1);
        user.setDataScope("ADMIN".equals(role) ? "ALL" : "DEPT");
        LoginUser principal = new LoginUser(user, List.of(role), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }
}

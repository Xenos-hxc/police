package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.shared.security.DataScopeService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {
    @Mock private DeptMapper deptMapper;
    @Mock private PoliceStationMapper stationMapper;
    @Mock private KeyUnitMapper unitMapper;
    @Mock private ImportantPartMapper partMapper;
    @Mock private TargetJurisdictionMapper jurisdictionMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void globalManagerRetainsAccessToDisabledDepartments() {
        authenticateAdmin();
        SysDept enabled = department(1L, 1);
        SysDept disabled = department(101L, 0);
        when(deptMapper.selectList(any())).thenReturn(List.of(enabled, disabled));

        assertEquals(List.of(1L, 101L), service().permittedDeptIds());
    }

    private void authenticateAdmin() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setDeptId(1L);
        user.setUsername("admin");
        user.setPassword("password");
        user.setRealName("系统管理员");
        user.setStatus(1);
        user.setDataScope("ALL");
        LoginUser principal = new LoginUser(user, List.of("ADMIN"), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private SysDept department(Long id, int status) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setStatus(status);
        return dept;
    }

    private DataScopeService service() {
        return new DataScopeService(
                deptMapper, stationMapper, unitMapper, partMapper, jurisdictionMapper);
    }
}

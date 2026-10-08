package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.railway.security.auth.AccountManagementService;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysRole;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.RoleMapper;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.shared.web.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {
    @Mock private DeptMapper deptMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserMapper userMapper;
    @InjectMocks private AccountManagementService service;

    @Test
    void initialPasswordAllowsOneToThirtyTwoCharacters() {
        assertDoesNotThrow(() -> service.validateInitialPassword("1"));
        assertDoesNotThrow(() -> service.validateInitialPassword("x".repeat(32)));
        assertThrows(BusinessException.class, () -> service.validateInitialPassword(""));
        assertThrows(
                BusinessException.class, () -> service.validateInitialPassword("x".repeat(33)));
    }

    @Test
    void stationAccountSelectsBureauAsParentAndBindsActualStation() {
        SysDept bureau = dept(1L, 0L, "BUREAU");
        SysDept station = dept(101L, 1L, "STATION");
        when(deptMapper.selectById(101L)).thenReturn(station);
        when(deptMapper.selectById(1L)).thenReturn(bureau);
        when(roleMapper.selectBatchIds(List.of(3L))).thenReturn(List.of(role(3L, "STATION")));
        when(userMapper.selectList(any())).thenReturn(List.of());

        var policy = service.validatePolicy(101L, 1L, List.of(3L), null, true);

        assertEquals("STATION", policy.roleCode());
        assertEquals("DEPT", policy.dataScope());
    }

    @Test
    void stationCannotBeSelectedAsItsOwnParentDepartment() {
        SysDept station = dept(101L, 1L, "STATION");
        when(deptMapper.selectById(101L)).thenReturn(station);
        when(roleMapper.selectBatchIds(List.of(3L))).thenReturn(List.of(role(3L, "STATION")));

        assertThrows(
                BusinessException.class,
                () -> service.validatePolicy(101L, 101L, List.of(3L), null, true));
    }

    @Test
    void newlyCreatedAccountCannotUseAdministratorRole() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "BUREAU"));
        when(roleMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(role(1L, "ADMIN")));

        assertThrows(
                BusinessException.class,
                () -> service.validatePolicy(1L, null, List.of(1L), null, true));
    }

    private SysDept dept(Long id, Long parentId, String type) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptType(type);
        return dept;
    }

    private SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }
}

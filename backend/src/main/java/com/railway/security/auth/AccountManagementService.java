package com.railway.security.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysRole;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.RoleMapper;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.shared.web.BusinessException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountManagementService {
    private final DeptMapper deptMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    public void validateInitialPassword(String password) {
        if (password == null || password.isBlank() || password.length() > 32) {
            throw new BusinessException("初始密码长度必须为1-32位");
        }
    }

    public AccountPolicy validatePolicy(
            Long deptId,
            Long parentDeptId,
            List<Long> roleIds,
            Long currentUserId,
            boolean creating) {
        if (deptId == null) throw new BusinessException("账号对应部门不能为空");
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) throw new BusinessException("账号对应部门不存在");
        if (roleIds == null || roleIds.isEmpty()) throw new BusinessException("请选择账号类型");

        List<SysRole> assignedRoles = roleMapper.selectBatchIds(roleIds);
        Set<String> roleCodes =
                assignedRoles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        if (roleCodes.size() != 1
                || assignedRoles.size() != roleIds.stream().distinct().count()
                || !List.of("ADMIN", "BUREAU", "STATION").containsAll(roleCodes)) {
            throw new BusinessException("账号必须且只能选择系统管理员、公安处账号、派出所账号中的一个角色");
        }

        String roleCode = roleCodes.iterator().next();
        if (creating && "ADMIN".equals(roleCode)) {
            throw new BusinessException("新增账号只能选择公安处账号或派出所账号");
        }
        if ("ADMIN".equals(roleCode)) {
            if (!"BUREAU".equals(dept.getDeptType())) {
                throw new BusinessException("系统管理员必须绑定公安处");
            }
            return new AccountPolicy(roleCode, "ALL");
        }

        String expectedRole =
                switch (dept.getDeptType()) {
                    case "BUREAU" -> "BUREAU";
                    case "STATION" -> "STATION";
                    default -> throw new BusinessException("该部门类型不能绑定业务账号");
                };
        if (!roleCode.equals(expectedRole)) {
            throw new BusinessException("账号类型必须与账号对应部门一致");
        }
        if ("STATION".equals(roleCode)) {
            if (parentDeptId == null) throw new BusinessException("请选择所属公安处");
            SysDept parent = deptMapper.selectById(parentDeptId);
            if (parent == null || !"BUREAU".equals(parent.getDeptType())) {
                throw new BusinessException("派出所账号的所属部门必须是公安处");
            }
            if (!parentDeptId.equals(dept.getParentId())) {
                throw new BusinessException("所选派出所不属于该公安处");
            }
        }

        boolean existing =
                userMapper
                        .selectList(
                                new LambdaQueryWrapper<SysUser>()
                                        .eq(SysUser::getDeptId, dept.getId())
                                        .ne(currentUserId != null, SysUser::getId, currentUserId))
                        .stream()
                        .anyMatch(
                                user ->
                                        userMapper
                                                .selectRoleCodes(user.getId())
                                                .contains(expectedRole));
        if (existing) throw new BusinessException("公安处和每个派出所只能维护一个对应的业务账号");
        return new AccountPolicy(roleCode, "BUREAU".equals(roleCode) ? "ALL" : "DEPT");
    }

    public record AccountPolicy(String roleCode, String dataScope) {}
}

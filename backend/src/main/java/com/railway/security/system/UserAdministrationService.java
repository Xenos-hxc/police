package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.auth.AccountManagementService;
import com.railway.security.auth.TokenSessionService;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysRole;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.entity.SysUserRole;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.RoleMapper;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.persistence.mapper.UserRoleCodeProjection;
import com.railway.security.persistence.mapper.UserRoleMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.dto.SystemDtos.UserFormResponse;
import com.railway.security.system.dto.SystemDtos.UserResponse;
import com.railway.security.system.dto.SystemDtos.UserSaveRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {
    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;
    private final TokenSessionService tokenSessionService;
    private final AccountManagementService accountManagementService;

    @Transactional(readOnly = true)
    public PageResult<UserResponse> page(
            long page, long size, String keyword, Integer status, Long deptId, String deptType) {
        List<Long> permitted = dataScopeService.permittedDeptIds();
        if (hasText(deptType)) {
            permitted =
                    deptMapper
                            .selectList(
                                    new LambdaQueryWrapper<SysDept>()
                                            .in(SysDept::getId, safeIds(permitted))
                                            .eq(SysDept::getDeptType, deptType))
                            .stream()
                            .map(SysDept::getId)
                            .toList();
        }
        List<Long> keywordDepartmentIds =
                !hasText(keyword)
                        ? List.of()
                        : deptMapper
                                .selectList(
                                        new LambdaQueryWrapper<SysDept>()
                                                .like(SysDept::getDeptName, keyword.trim()))
                                .stream()
                                .map(SysDept::getId)
                                .toList();
        LambdaQueryWrapper<SysUser> wrapper =
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, safeIds(permitted))
                        .and(
                                hasText(keyword),
                                query -> {
                                    query.like(SysUser::getUsername, keyword.trim());
                                    if (!keywordDepartmentIds.isEmpty()) {
                                        query.or().in(SysUser::getDeptId, keywordDepartmentIds);
                                    }
                                })
                        .eq(status != null, SysUser::getStatus, status)
                        .eq(deptId != null, SysUser::getDeptId, deptId)
                        .orderByDesc(SysUser::getId);
        Page<SysUser> result =
                userMapper.selectPage(new Page<>(Math.max(1, page), boundedSize(size)), wrapper);
        List<SysUser> users = result.getRecords();
        Map<Long, SysDept> departments = loadDepartments(users);
        Map<Long, List<String>> roles = loadRoles(users);
        List<UserResponse> records =
                users.stream()
                        .map(
                                user ->
                                        toResponse(
                                                user,
                                                departments.get(user.getDeptId()),
                                                roles.getOrDefault(user.getId(), List.of())))
                        .toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Transactional(readOnly = true)
    public UserFormResponse get(Long id) {
        SysUser user = requiredUser(id);
        dataScopeService.checkDept(user.getDeptId());
        List<Long> roleIds =
                userRoleMapper
                        .selectList(
                                new LambdaQueryWrapper<SysUserRole>()
                                        .eq(SysUserRole::getUserId, id))
                        .stream()
                        .map(SysUserRole::getRoleId)
                        .toList();
        SysDept department = deptMapper.selectById(user.getDeptId());
        String accountType = userMapper.selectRoleCodes(id).stream().findFirst().orElse("");
        return new UserFormResponse(
                user.getId(),
                user.getDeptId(),
                user.getUsername(),
                user.getStatus(),
                user.getDataScope(),
                roleIds,
                accountType,
                department == null ? null : department.getParentId());
    }

    @Transactional
    public UserResponse create(UserSaveRequest request) {
        validate(request, true);
        dataScopeService.checkDept(request.deptId());
        SysDept department = requiredDepartment(request.deptId());
        String dataScope =
                accountManagementService
                        .validatePolicy(
                                request.deptId(),
                                request.parentDeptId(),
                                request.roleIds(),
                                null,
                                true)
                        .dataScope();
        if (userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getUsername, request.username().trim()))
                > 0) {
            throw new BusinessException("账号已存在");
        }
        SysUser user = new SysUser();
        user.setDeptId(request.deptId());
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(department.getDeptName());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setDataScope(dataScope);
        user.setForceChangePassword(0);
        user.setTokenVersion(0);
        userMapper.insert(user);
        replaceRoles(user.getId(), request.roleIds());
        List<String> roles =
                roleMapper.selectBatchIds(request.roleIds()).stream()
                        .map(SysRole::getRoleCode)
                        .toList();
        return toResponse(user, department, roles);
    }

    @Transactional
    public void update(Long id, UserSaveRequest request) {
        validate(request, false);
        SysUser user = requiredUser(id);
        dataScopeService.checkDept(user.getDeptId());
        dataScopeService.checkDept(request.deptId());
        SysDept department = requiredDepartment(request.deptId());
        String dataScope =
                accountManagementService
                        .validatePolicy(
                                request.deptId(),
                                request.parentDeptId(),
                                request.roleIds(),
                                id,
                                false)
                        .dataScope();
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            Set<String> roleCodes =
                    roleMapper.selectBatchIds(request.roleIds()).stream()
                            .map(SysRole::getRoleCode)
                            .collect(Collectors.toSet());
            if (!Long.valueOf(1L).equals(request.deptId())
                    || !roleCodes.equals(Set.of("ADMIN"))
                    || Integer.valueOf(0).equals(request.status())) {
                throw new BusinessException("内置管理员必须保持启用并绑定公安处和系统管理员角色");
            }
        }
        user.setDeptId(request.deptId());
        user.setRealName(department.getDeptName());
        user.setStatus(request.status() == null ? user.getStatus() : request.status());
        user.setDataScope(dataScope);
        user.setTokenVersion(nextTokenVersion(user));
        userMapper.updateById(user);
        replaceRoles(id, request.roleIds());
        tokenSessionService.revokeAll(id);
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = requiredUser(id);
        if (id.equals(SecurityUtils.currentUser().getUserId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("内置管理员账号不能删除");
        }
        dataScopeService.checkDept(user.getDeptId());
        userRoleMapper.physicalDeleteByUserId(id);
        String originalUsername = user.getUsername();
        user.setUsername("deleted_" + id + "_" + System.currentTimeMillis());
        String deletionRemark = "删除前账号：" + originalUsername;
        user.setRemark(
                !hasText(user.getRemark())
                        ? deletionRemark
                        : user.getRemark() + "；" + deletionRemark);
        user.setTokenVersion(nextTokenVersion(user));
        userMapper.updateById(user);
        tokenSessionService.revokeAll(id);
        userMapper.deleteById(id);
    }

    @Transactional
    public void resetPassword(Long id, String requestedPassword) {
        SysUser user = requiredUser(id);
        dataScopeService.checkDept(user.getDeptId());
        String password =
                hasText(requestedPassword) ? requestedPassword : "Demo-Only-Change-Me!2026";
        accountManagementService.validateInitialPassword(password);
        user.setPassword(passwordEncoder.encode(password));
        user.setForceChangePassword(0);
        user.setTokenVersion(nextTokenVersion(user));
        userMapper.updateById(user);
        tokenSessionService.revokeAll(id);
    }

    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (!List.of(0, 1).contains(status)) {
            throw new BusinessException("账号状态无效");
        }
        SysUser user = requiredUser(id);
        dataScopeService.checkDept(user.getDeptId());
        if (id.equals(SecurityUtils.currentUser().getUserId())
                && Integer.valueOf(0).equals(status)) {
            throw new BusinessException("不能停用当前登录账号");
        }
        if ("admin".equalsIgnoreCase(user.getUsername()) && Integer.valueOf(0).equals(status)) {
            throw new BusinessException("内置管理员账号不能停用");
        }
        user.setStatus(status);
        user.setTokenVersion(nextTokenVersion(user));
        userMapper.updateById(user);
        tokenSessionService.revokeAll(id);
    }

    private Map<Long, SysDept> loadDepartments(List<SysUser> users) {
        List<Long> ids = users.stream().map(SysUser::getDeptId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return deptMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysDept::getId, value -> value));
    }

    private Map<Long, List<String>> loadRoles(List<SysUser> users) {
        List<Long> ids = users.stream().map(SysUser::getId).toList();
        return userMapper.selectRoleCodesByUserIds(ids).stream()
                .collect(
                        Collectors.groupingBy(
                                UserRoleCodeProjection::userId,
                                Collectors.mapping(
                                        UserRoleCodeProjection::roleCode, Collectors.toList())));
    }

    private UserResponse toResponse(SysUser user, SysDept department, List<String> roles) {
        return new UserResponse(
                user.getId(),
                user.getDeptId(),
                department == null ? "" : department.getDeptName(),
                user.getUsername(),
                user.getStatus(),
                user.getDataScope(),
                roles,
                user.getLastLoginTime(),
                user.getCreateTime());
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.physicalDeleteByUserId(userId);
        roleIds.stream()
                .distinct()
                .forEach(
                        roleId -> {
                            if (roleMapper.selectById(roleId) == null) {
                                throw new BusinessException("角色不存在");
                            }
                            SysUserRole link = new SysUserRole();
                            link.setUserId(userId);
                            link.setRoleId(roleId);
                            userRoleMapper.insert(link);
                        });
    }

    private void validate(UserSaveRequest request, boolean creating) {
        if (creating && !hasText(request.username())) {
            throw new BusinessException("账号不能为空");
        }
        if (creating) {
            accountManagementService.validateInitialPassword(request.password());
        }
        if (request.roleIds() == null || request.roleIds().isEmpty()) {
            throw new BusinessException("请至少分配一个角色");
        }
        if (hasText(request.dataScope())
                && !List.of("ALL", "DEPT_AND_CHILD", "DEPT").contains(request.dataScope())) {
            throw new BusinessException("数据范围无效");
        }
    }

    private SysUser requiredUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private SysDept requiredDepartment(Long id) {
        SysDept department = deptMapper.selectById(id);
        if (department == null) {
            throw new BusinessException("部门不存在");
        }
        return department;
    }

    private int nextTokenVersion(SysUser user) {
        return (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
    }

    private long boundedSize(long size) {
        return Math.max(1, Math.min(size, 200));
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

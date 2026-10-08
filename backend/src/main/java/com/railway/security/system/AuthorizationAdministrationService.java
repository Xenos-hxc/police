package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.SysMenu;
import com.railway.security.persistence.entity.SysRole;
import com.railway.security.persistence.entity.SysRoleMenu;
import com.railway.security.persistence.entity.SysUserRole;
import com.railway.security.persistence.mapper.MenuMapper;
import com.railway.security.persistence.mapper.RoleMapper;
import com.railway.security.persistence.mapper.RoleMenuMapper;
import com.railway.security.persistence.mapper.UserRoleMapper;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.system.dto.SystemDtos.MenuResponse;
import com.railway.security.system.dto.SystemDtos.MenuSaveRequest;
import com.railway.security.system.dto.SystemDtos.RoleDetailResponse;
import com.railway.security.system.dto.SystemDtos.RoleResponse;
import com.railway.security.system.dto.SystemDtos.RoleSaveRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorizationAdministrationService {
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final SystemDtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roleMapper
                .selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId))
                .stream()
                .map(dtoMapper::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDetailResponse role(Long id) {
        SysRole role = requiredRole(id);
        List<Long> menuIds =
                roleMenuMapper
                        .selectList(
                                new LambdaQueryWrapper<SysRoleMenu>()
                                        .eq(SysRoleMenu::getRoleId, id))
                        .stream()
                        .map(SysRoleMenu::getMenuId)
                        .toList();
        return new RoleDetailResponse(dtoMapper.toRoleResponse(role), menuIds);
    }

    @Transactional
    public RoleResponse createRole(RoleSaveRequest request) {
        validateRole(request);
        if (roleMapper.selectCount(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getRoleCode, request.roleCode().trim()))
                > 0) {
            throw new BusinessException("角色编码已存在");
        }
        SysRole role = new SysRole();
        copyRole(request, role);
        roleMapper.insert(role);
        replaceRoleMenus(role.getId(), request.menuIds());
        return dtoMapper.toRoleResponse(role);
    }

    @Transactional
    public void updateRole(Long id, RoleSaveRequest request) {
        validateRole(request);
        SysRole role = requiredRole(id);
        if (roleMapper.selectCount(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getRoleCode, request.roleCode().trim())
                                .ne(SysRole::getId, id))
                > 0) {
            throw new BusinessException("角色编码已存在");
        }
        copyRole(request, role);
        roleMapper.updateById(role);
        replaceRoleMenus(id, request.menuIds());
    }

    @Transactional
    public void deleteRole(Long id) {
        requiredRole(id);
        if (userRoleMapper.selectCount(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id))
                > 0) {
            throw new BusinessException("角色已分配给用户，不能删除");
        }
        roleMenuMapper.physicalDeleteByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> menus() {
        return menuMapper
                .selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortNo))
                .stream()
                .map(dtoMapper::toMenuResponse)
                .toList();
    }

    @Transactional
    public MenuResponse createMenu(MenuSaveRequest request) {
        validateMenu(request, null);
        SysMenu menu = new SysMenu();
        copyMenu(request, menu);
        menuMapper.insert(menu);
        return dtoMapper.toMenuResponse(menu);
    }

    @Transactional
    public void updateMenu(Long id, MenuSaveRequest request) {
        SysMenu menu = requiredMenu(id);
        validateMenu(request, id);
        copyMenu(request, menu);
        menuMapper.updateById(menu);
    }

    @Transactional
    public void deleteMenu(Long id) {
        requiredMenu(id);
        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id))
                > 0) {
            throw new BusinessException("存在下级菜单，不能删除");
        }
        roleMenuMapper.physicalDeleteByMenuId(id);
        menuMapper.deleteById(id);
    }

    private void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.physicalDeleteByRoleId(roleId);
        if (menuIds == null) {
            return;
        }
        menuIds.stream()
                .distinct()
                .forEach(
                        menuId -> {
                            requiredMenu(menuId);
                            SysRoleMenu link = new SysRoleMenu();
                            link.setRoleId(roleId);
                            link.setMenuId(menuId);
                            roleMenuMapper.insert(link);
                        });
    }

    private void copyRole(RoleSaveRequest request, SysRole role) {
        role.setRoleName(request.roleName().trim());
        role.setRoleCode(request.roleCode().trim());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setRemark(request.remark());
    }

    private void copyMenu(MenuSaveRequest request, SysMenu menu) {
        menu.setParentId(request.parentId() == null ? 0L : request.parentId());
        menu.setMenuName(request.menuName().trim());
        menu.setMenuType(request.menuType());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setPermission(request.permission());
        menu.setIcon(request.icon());
        menu.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
        menu.setStatus(request.status() == null ? 1 : request.status());
        menu.setRemark(request.remark());
    }

    private void validateRole(RoleSaveRequest request) {
        if (request.dataScope() != null
                && !List.of("ALL", "DEPT_AND_CHILD", "DEPT").contains(request.dataScope())) {
            throw new BusinessException("角色数据范围无效");
        }
    }

    private void validateMenu(MenuSaveRequest request, Long currentId) {
        if (!List.of("M", "C", "F").contains(request.menuType())) {
            throw new BusinessException("菜单类型无效");
        }
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        if (currentId != null && currentId.equals(parentId)) {
            throw new BusinessException("上级菜单不能选择自身");
        }
        if (parentId != 0 && menuMapper.selectById(parentId) == null) {
            throw new BusinessException("上级菜单不存在");
        }
    }

    private SysRole requiredRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private SysMenu requiredMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        return menu;
    }
}

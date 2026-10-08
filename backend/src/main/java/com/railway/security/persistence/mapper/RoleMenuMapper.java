package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface RoleMenuMapper extends BaseMapper<SysRoleMenu> {
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int physicalDeleteByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int physicalDeleteByMenuId(@Param("menuId") Long menuId);
}

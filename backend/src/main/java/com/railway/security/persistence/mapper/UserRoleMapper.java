package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface UserRoleMapper extends BaseMapper<SysUserRole> {
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}

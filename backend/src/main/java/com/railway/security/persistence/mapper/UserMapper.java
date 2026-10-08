package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<SysUser> {
    @Select(
            """
        SELECT r.role_code FROM sys_role r
        JOIN sys_user_role ur ON ur.role_id=r.id AND ur.deleted=0
        WHERE ur.user_id=#{userId} AND r.deleted=0 AND r.status=1
        """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select(
            """
        SELECT DISTINCT m.permission FROM sys_menu m
        JOIN sys_role_menu rm ON rm.menu_id=m.id AND rm.deleted=0
        JOIN sys_user_role ur ON ur.role_id=rm.role_id AND ur.deleted=0
        JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=1
        WHERE ur.user_id=#{userId} AND m.deleted=0 AND m.status=1
          AND m.permission IS NOT NULL AND m.permission<>''
        """)
    List<String> selectPermissions(@Param("userId") Long userId);

    @Select(
            """
        <script>
        SELECT ur.user_id AS userId, r.role_code AS roleCode
        FROM sys_user_role ur
        JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=1
        WHERE ur.deleted=0
        <if test="userIds != null and !userIds.isEmpty()">
          AND ur.user_id IN
          <foreach collection="userIds" item="userId" open="(" separator="," close=")">
            #{userId}
          </foreach>
        </if>
        <if test="userIds == null or userIds.isEmpty()">
          AND 1=0
        </if>
        ORDER BY ur.user_id, r.id
        </script>
        """)
    List<UserRoleCodeProjection> selectRoleCodesByUserIds(@Param("userIds") List<Long> userIds);
}

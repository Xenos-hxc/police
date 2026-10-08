package com.railway.security.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;
    private final DeptMapper deptMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("账号或密码错误");
        }
        assertDeptEnabled(user);
        return loginUser(user);
    }

    public LoginUser loadById(Long userId) {
        var user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        assertDeptEnabled(user);
        return loginUser(user);
    }

    private void assertDeptEnabled(SysUser user) {
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new UsernameNotFoundException("账号已停用");
        }
        var dept = deptMapper.selectById(user.getDeptId());
        if (dept == null
                || !Integer.valueOf(1).equals(dept.getStatus())
                || Integer.valueOf(1).equals(dept.getArchived())) {
            throw new UsernameNotFoundException("所属部门已停用");
        }
    }

    private LoginUser loginUser(SysUser user) {
        var roles = userMapper.selectRoleCodes(user.getId());
        if (roles.isEmpty()) throw new UsernameNotFoundException("账号未分配有效角色");
        return new LoginUser(user, roles, userMapper.selectPermissions(user.getId()));
    }
}

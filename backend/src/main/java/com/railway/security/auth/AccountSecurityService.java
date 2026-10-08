package com.railway.security.auth;

import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountSecurityService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService tokenSessionService;

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException("请输入原密码");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() > 32) {
            throw new BusinessException("新密码长度必须为1-32位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        Long userId = SecurityUtils.currentUser().getUserId();
        var user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("账号不存在");
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForceChangePassword(0);
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException("密码修改失败，请重试");
        }
        tokenSessionService.revokeAll(userId);
    }
}

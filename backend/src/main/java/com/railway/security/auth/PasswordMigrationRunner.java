package com.railway.security.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordMigrationRunner implements ApplicationRunner {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService tokenSessionService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int migrated = 0;
        for (SysUser user : userMapper.selectList(new LambdaQueryWrapper<>())) {
            if (isBcrypt(user.getPassword())) continue;
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setForceChangePassword(0);
            user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
            userMapper.updateById(user);
            tokenSessionService.revokeAll(user.getId());
            migrated++;
        }
        if (migrated > 0) log.warn("已将 {} 个历史明文密码迁移为 BCrypt", migrated);
    }

    private boolean isBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}

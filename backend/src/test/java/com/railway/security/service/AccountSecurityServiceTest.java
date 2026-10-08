package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.auth.AccountSecurityService;
import com.railway.security.auth.TokenSessionService;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.shared.web.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenSessionService tokenSessionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changePasswordEncodesPasswordAndRevokesAllSessions() {
        login();
        SysUser user = user();
        user.setPassword("encoded-old");
        user.setTokenVersion(2);
        when(userMapper.selectById(101L)).thenReturn(user);
        when(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");
        when(userMapper.updateById(user)).thenReturn(1);

        service().changePassword("old-password", "new-password");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("encoded-new", captor.getValue().getPassword());
        assertEquals(3, captor.getValue().getTokenVersion());
        assertEquals(0, captor.getValue().getForceChangePassword());
        verify(tokenSessionService).revokeAll(101L);
    }

    @Test
    void wrongOldPasswordDoesNotUpdateAccount() {
        login();
        SysUser user = user();
        user.setPassword("encoded-old");
        when(userMapper.selectById(101L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThrows(
                BusinessException.class, () -> service().changePassword("wrong", "new-password"));

        verify(userMapper, never()).updateById(user);
        verify(tokenSessionService, never()).revokeAll(101L);
    }

    private AccountSecurityService service() {
        return new AccountSecurityService(userMapper, passwordEncoder, tokenSessionService);
    }

    private void login() {
        SysUser user = user();
        LoginUser principal = new LoginUser(user, List.of("STATION"), List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(101L);
        user.setDeptId(101L);
        user.setUsername("station");
        user.setRealName("测试派出所");
        user.setStatus(1);
        user.setDataScope("DEPT");
        return user;
    }
}

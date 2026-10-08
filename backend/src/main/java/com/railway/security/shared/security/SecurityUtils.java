package com.railway.security.shared.security;

import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BusinessException(401, "登录状态已失效");
        }
        return user;
    }

    public static Long currentUserIdOrZero() {
        try {
            return currentUser().getUserId();
        } catch (BusinessException ignored) {
            return 0L;
        }
    }

    public static boolean isAdminOrBureau() {
        var roles = currentUser().getRoles();
        return roles.contains("ADMIN") || roles.contains("BUREAU");
    }
}

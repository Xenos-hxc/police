package com.railway.security.persistence.entity;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class LoginUser implements UserDetails {
    private final Long userId;
    private final Long deptId;
    private final String realName;
    private final String dataScope;
    private final Integer tokenVersion;
    private final String username;
    private final String password;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public LoginUser(SysUser user, List<String> roles, List<String> permissions) {
        this.userId = user.getId();
        this.deptId = user.getDeptId();
        this.realName = user.getRealName();
        this.dataScope = user.getDataScope();
        this.tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.roles = new LinkedHashSet<>(roles);
        this.permissions = new LinkedHashSet<>(permissions);
        var values = new LinkedHashSet<GrantedAuthority>();
        roles.forEach(role -> values.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(permission -> values.add(new SimpleGrantedAuthority(permission)));
        this.authorities = values;
        this.enabled = Integer.valueOf(1).equals(user.getStatus());
    }
}

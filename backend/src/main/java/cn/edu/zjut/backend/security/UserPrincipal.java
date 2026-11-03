package cn.edu.zjut.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体。携带租户（部门）与角色，供鉴权与数据隔离使用。
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final Long deptId;
    private final String username;
    private final String password;
    private final String role;
    private final String realName;
    private final boolean enabled;

    public UserPrincipal(Long id, Long deptId, String username, String password,
                         String role, String realName, boolean enabled) {
        this.id = id;
        this.deptId = deptId;
        this.username = username;
        this.password = password;
        this.role = role;
        this.realName = realName;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getRole() {
        return role;
    }

    public String getRealName() {
        return realName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

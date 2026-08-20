package org.example.blog.service;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

/**
 * 自定义 UserDetails，携带用户 UUID，供 SpEL 表达式和 @AuthenticationPrincipal 使用
 */
public class CustomUserDetails implements UserDetails {

    @Getter
    private final UUID id;
    private final String username;
    private final String password;
    private final boolean enabled;
    @Getter
    private final boolean admin;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(UUID id, String username, String password,
                             boolean enabled, boolean admin,
                             Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.admin = admin;
        this.authorities = authorities;
    }

    @Override
    @NullMarked
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    @NullMarked
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // 以下均返回 true，账户过期/锁定等策略暂不实现
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
}

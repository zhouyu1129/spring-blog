package org.example.blog.service;

import org.example.blog.cache.RolePermissionCache;
import org.example.blog.dao.Permission;
import org.example.blog.dao.Role;
import org.example.blog.dao.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;
    private final RolePermissionCache cache;

    public CustomUserDetailsService(UserService userService, RolePermissionCache cache) {
        this.userService = userService;
        this.cache = cache;
    }

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String userfield) throws UsernameNotFoundException {
        User user = userService.getByUniqueField(userfield);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + userfield);
        }

        if (!user.getIsEnabled()) {
            throw new UsernameNotFoundException("账号已被禁用: " + userfield);
        }

        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                true,
                user.getIsAdmin() != null && user.getIsAdmin(),
                getAuthorities(user)
        );
    }

    /**
     * 构建用户的权限集合
     * 从缓存获取角色和权限的单例对象，不重复查库
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 基于 isAdmin 字段添加 ROLE_ADMIN 权限
        if (user.getIsAdmin() != null && user.getIsAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        // 基于 is_staff 字段添加 ROLE_STAFF 权限（管理员后端只读访问）
        if (user.getIsStaff() != null && user.getIsStaff()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
        }

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                String roleName = role.getRoleName();
                if (!roleName.startsWith("ROLE_")) {
                    roleName = "ROLE_" + roleName;
                }
                // 跳过 ROLE_ADMIN / ROLE_STAFF，已由 isAdmin / isStaff 字段控制
                if ("ROLE_ADMIN".equals(roleName) || "ROLE_STAFF".equals(roleName)) {
                    continue;
                }
                authorities.add(new SimpleGrantedAuthority(roleName));

                // 从缓存获取该角色的权限（单例引用，不查库）
                List<Permission> permissions = cache.getPermissionsByRoleId(role.getId());
                for (Permission perm : permissions) {
                    authorities.add(new SimpleGrantedAuthority(perm.getPermName()));
                }
            }
        }

        return authorities;
    }
}

package org.example.blog.service;

import lombok.RequiredArgsConstructor;
import org.example.blog.cache.RolePermissionCache;
import org.example.blog.dao.Permission;
import org.example.blog.mapper.PermissionMapper;
import org.example.blog.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 负面权限名前缀：'!article:create' 表示禁止 article:create */
    public static final String NEGATIVE_PREFIX = "!";

    // 权限名常量（与 schema.sql 预置的权限字典一致）
    public static final String ARTICLE_CREATE = "article:create";
    public static final String ARTICLE_UPDATE_OWN = "article:update:own";
    public static final String ARTICLE_UPDATE_ANY = "article:update:any";
    public static final String ARTICLE_VIEW_HIDDEN = "article:view:hidden";
    public static final String COMMENT_CREATE = "comment:create";
    public static final String COMMENT_UPDATE_OWN = "comment:update:own";
    public static final String COMMENT_UPDATE_ANY = "comment:update:any";
    public static final String COMMENT_VIEW_HIDDEN = "comment:view:hidden";

    private final PermissionMapper permissionMapper;
    private final RolePermissionCache cache;
    private final UserMapper userMapper;

    // ========== 权限判定 ==========

    /**
     * 判定用户是否拥有某权限：
     * <ul>
     *   <li>管理员直通（ bypass 一切权限与负面权限）</li>
     *   <li>否则必须「拥有对应的正面权限 且 没有对应的负面权限」</li>
     * </ul>
     * 权限来自用户当前有效的角色（expires_at 为空表示永久，或晚于当前时间），
     * 实时查库计算，角色过期后权限立即失效。
     */
    public boolean hasPermission(UUID userId, boolean isAdmin, String permission) {
        if (isAdmin) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        List<String> perms = userMapper.selectEffectivePermissions(userId);
        return perms.contains(permission) && !perms.contains(NEGATIVE_PREFIX + permission);
    }

    /** 根据 ID 查询权限 */
    public Permission getById(Integer id) {
        return permissionMapper.selectById(id);
    }

    /** 根据权限名查询权限 */
    public Permission getByPermName(String permName) {
        return permissionMapper.selectByPermName(permName);
    }

    /** 查询所有权限 */
    public List<Permission> getAll() {
        return permissionMapper.selectAll();
    }

    /** 创建权限 */
    @Transactional
    public Permission create(Permission permission) {
        permissionMapper.insert(permission);
        cache.refresh();
        return permission;
    }

    /** 更新权限 */
    @Transactional
    public Permission update(Permission permission) {
        permissionMapper.update(permission);
        cache.refresh();
        return permission;
    }

    /** 删除权限 */
    @Transactional
    public void delete(Integer id) {
        permissionMapper.deleteById(id);
        cache.refresh();
    }
}

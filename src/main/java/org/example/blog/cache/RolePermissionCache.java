package org.example.blog.cache;

import org.example.blog.dao.Permission;
import org.example.blog.dao.Role;
import org.example.blog.mapper.PermissionMapper;
import org.example.blog.mapper.RoleMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 角色-权限缓存（单例）
 * <p>
 * 启动时将所有 Role、Permission 加载到内存，后续认证直接引用缓存对象，
 * 避免每次登录都查库、避免重复创建对象。
 * <p>
 * 使用 AtomicReference 持有不可变的缓存快照，refresh 时整体替换，
 * 读操作无需加锁，不会读到半刷新状态。
 */
@Component
public class RolePermissionCache {

    /**
     * 不可变缓存快照
     */
        private record CacheSnapshot(Map<Integer, Role> roleById, Map<String, Role> roleByName,
                                     Map<Integer, Permission> permById, Map<String, Permission> permByName,
                                     Map<Integer, List<Permission>> permsByRoleId) {
            private CacheSnapshot(Map<Integer, Role> roleById, Map<String, Role> roleByName,
                                  Map<Integer, Permission> permById, Map<String, Permission> permByName,
                                  Map<Integer, List<Permission>> permsByRoleId) {
                this.roleById = Collections.unmodifiableMap(roleById);
                this.roleByName = Collections.unmodifiableMap(roleByName);
                this.permById = Collections.unmodifiableMap(permById);
                this.permByName = Collections.unmodifiableMap(permByName);
                this.permsByRoleId = Collections.unmodifiableMap(permsByRoleId);
            }
        }

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    private final AtomicReference<CacheSnapshot> snapshotRef = new AtomicReference<>(
            new CacheSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));

    public RolePermissionCache(RoleMapper roleMapper, PermissionMapper permissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    /** 重新从数据库加载全部角色和权限到缓存（原子替换） */
    public void refresh() {
        Map<Integer, Permission> permById = new HashMap<>();
        Map<String, Permission> permByName = new HashMap<>();

        // 1. 加载所有 Permission
        for (Permission perm : permissionMapper.selectAll()) {
            permById.put(perm.getId(), perm);
            permByName.put(perm.getPermName(), perm);
        }

        Map<Integer, Role> roleById = new HashMap<>();
        Map<String, Role> roleByName = new HashMap<>();
        Map<Integer, List<Permission>> permsByRoleId = new HashMap<>();

        // 2. 加载所有 Role
        for (Role role : roleMapper.selectAll()) {
            roleById.put(role.getId(), role);
            roleByName.put(role.getRoleName(), role);
        }

        // 3. 为每个 Role 关联 Permission
        for (Role role : roleById.values()) {
            List<Permission> perms = roleMapper.selectPermissionsByRoleId(role.getId());
            List<Permission> cachedPerms = new ArrayList<>();
            for (Permission perm : perms) {
                Permission cached = permById.get(perm.getId());
                cachedPerms.add(cached != null ? cached : perm);
            }
            List<Permission> immutablePerms = Collections.unmodifiableList(cachedPerms);
            permsByRoleId.put(role.getId(), immutablePerms);
            role.setPermissions(cachedPerms);
        }

        // 原子替换整个快照
        snapshotRef.set(new CacheSnapshot(roleById, roleByName, permById, permByName, permsByRoleId));
    }

    private CacheSnapshot snapshot() {
        return snapshotRef.get();
    }

    /** 根据 ID 获取 Role 单例 */
    public Role getRoleById(Integer roleId) {
        return snapshot().roleById.get(roleId);
    }

    /** 根据角色名获取 Role 单例 */
    public Role getRoleByName(String roleName) {
        return snapshot().roleByName.get(roleName);
    }

    /** 根据 ID 获取 Permission 单例 */
    public Permission getPermissionById(Integer permId) {
        return snapshot().permById.get(permId);
    }

    /** 根据权限名获取 Permission 单例 */
    public Permission getPermissionByName(String permName) {
        return snapshot().permByName.get(permName);
    }

    /** 获取角色对应的 Permission 列表（单例引用） */
    public List<Permission> getPermissionsByRoleId(Integer roleId) {
        return snapshot().permsByRoleId.getOrDefault(roleId, Collections.emptyList());
    }

    /** 获取所有 Role（单例引用） */
    public Collection<Role> getAllRoles() {
        return snapshot().roleById.values();
    }

    /** 获取所有 Permission（单例引用） */
    public Collection<Permission> getAllPermissions() {
        return snapshot().permById.values();
    }
}

package org.example.blog.service;

import lombok.RequiredArgsConstructor;
import org.example.blog.cache.RolePermissionCache;
import org.example.blog.dao.Permission;
import org.example.blog.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionCache cache;

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

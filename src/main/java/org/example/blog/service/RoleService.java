package org.example.blog.service;

import lombok.RequiredArgsConstructor;
import org.example.blog.cache.RolePermissionCache;
import org.example.blog.dao.Permission;
import org.example.blog.dao.Role;
import org.example.blog.mapper.RoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionCache cache;

    /** 根据 ID 查询角色（含权限） */
    public Role getById(Integer id) {
        return roleMapper.selectById(id);
    }

    /** 根据角色名查询角色 */
    public Role getByRoleName(String roleName) {
        return roleMapper.selectByRoleName(roleName);
    }

    /** 查询所有角色 */
    public List<Role> getAll() {
        return roleMapper.selectAll();
    }

    /** 创建角色 */
    @Transactional
    public Role create(Role role) {
        roleMapper.insert(role);
        cache.refresh();
        return role;
    }

    /** 更新角色 */
    @Transactional
    public Role update(Role role) {
        roleMapper.update(role);
        cache.refresh();
        return role;
    }

    /** 删除角色 */
    @Transactional
    public void delete(Integer id) {
        roleMapper.deleteById(id);
        cache.refresh();
    }

    /** 给角色分配权限 */
    @Transactional
    public void assignPermission(Integer roleId, Integer permissionId) {
        roleMapper.insertRolePermission(roleId, permissionId);
        cache.refresh();
    }

    /** 移除角色的权限 */
    @Transactional
    public void removePermission(Integer roleId, Integer permissionId) {
        roleMapper.deleteRolePermission(roleId, permissionId);
        cache.refresh();
    }

    /** 查询角色的权限列表 */
    public List<Permission> getRolePermissions(Integer roleId) {
        return roleMapper.selectPermissionsByRoleId(roleId);
    }
}

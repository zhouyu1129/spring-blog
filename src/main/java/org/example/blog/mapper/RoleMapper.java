package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.Permission;
import org.example.blog.dao.Role;

import java.util.List;

@Mapper
public interface RoleMapper {

    /** 根据 ID 查询角色（含权限列表） */
    Role selectById(Integer id);

    /** 根据角色名查询角色 */
    Role selectByRoleName(String roleName);

    /** 查询所有角色 */
    List<Role> selectAll();

    /** 插入角色 */
    int insert(Role role);

    /** 更新角色 */
    int update(Role role);

    /** 删除角色 */
    int deleteById(Integer id);

    /** 查询角色的权限列表 */
    List<Permission> selectPermissionsByRoleId(Integer roleId);

    /** 给角色分配权限 */
    int insertRolePermission(Integer roleId, Integer permissionId);

    /** 移除角色的某个权限 */
    int deleteRolePermission(Integer roleId, Integer permissionId);

    /** 移除角色的全部权限（重新分配前清空） */
    int deleteAllRolePermissions(Integer roleId);
}

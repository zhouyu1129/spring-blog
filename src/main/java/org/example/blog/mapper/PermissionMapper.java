package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.Permission;

import java.util.List;

@Mapper
public interface PermissionMapper {

    /** 根据 ID 查询权限 */
    Permission selectById(Integer id);

    /** 根据权限名查询权限 */
    Permission selectByPermName(String permName);

    /** 查询所有权限 */
    List<Permission> selectAll();

    /** 插入权限 */
    int insert(Permission permission);

    /** 更新权限 */
    int update(Permission permission);

    /** 删除权限 */
    int deleteById(Integer id);
}

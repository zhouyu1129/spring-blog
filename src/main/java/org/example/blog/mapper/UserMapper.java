package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.Role;
import org.example.blog.dao.User;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UserMapper {

    /** 根据用户名查询用户（含角色列表） */
    User selectByUsername(String username);

    /** 根据 Email 查询用户（含角色列表） */
    User selectByEmail(String email);

    /** 根据 Student Number 查询用户（含角色列表） */
    User selectByStudentNumber(String studentNumber);

    /** 根据 ID 查询用户（含角色列表） */
    User selectById(UUID id);

    /** 查询所有用户 */
    List<User> selectAll();

    /** 插入用户 */
    int insert(User user);

    /** 更新用户基本信息 */
    int update(User user);

    /** 删除用户 */
    int deleteById(UUID id);

    /** 查询用户的角色列表 */
    List<Role> selectRolesByUserId(UUID userId);

    /** 给用户分配角色 */
    int insertUserRole(UUID userId, Integer roleId);

    /** 移除用户的某个角色 */
    int deleteUserRole(UUID userId, Integer roleId);
}

package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.blog.dao.Role;
import org.example.blog.dao.User;

import java.time.LocalDateTime;
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

    /**
     * 分页查询用户（管理员后端），按 created_at 降序。
     * keyword 为 null 或空时不过滤，否则按用户名/昵称/邮箱/学号模糊匹配
     */
    List<User> selectPage(@Param("keyword") String keyword,
                          @Param("limit") int limit,
                          @Param("offset") int offset);

    /** 统计用户总数（过滤条件与 selectPage 一致） */
    long countByKeyword(@Param("keyword") String keyword);

    /** 插入用户 */
    int insert(User user);

    /** 更新用户基本信息 */
    int update(User user);

    /** 删除用户 */
    int deleteById(UUID id);

    /** 查询用户的角色列表 */
    List<Role> selectRolesByUserId(UUID userId);

    /** 给用户分配角色（expiresAt 为有效期，null 表示永久） */
    int insertUserRole(@Param("userId") UUID userId, @Param("roleId") Integer roleId,
                       @Param("expiresAt") LocalDateTime expiresAt);

    /** 移除用户的某个角色 */
    int deleteUserRole(UUID userId, Integer roleId);

    /** 移除用户的全部角色（重新分配前清空） */
    int deleteAllUserRoles(@Param("userId") UUID userId);

    /**
     * 查询用户当前「有效」的全部权限名（含负面权限）：
     * 只统计 expires_at 为空（永久）或晚于当前时间的角色，过期角色的权限不参与计算
     */
    List<String> selectEffectivePermissions(@Param("userId") UUID userId);
}

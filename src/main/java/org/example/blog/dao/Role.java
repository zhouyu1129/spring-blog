package org.example.blog.dao;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Role {
    private Integer id;
    private String roleName;
    private String description;
    /** 系统预置角色：不可删除、不可改名 */
    private Boolean isSystem;
    /** 拥有该角色的用户数（仅角色列表查询填充） */
    private Long userCount;

    // 用于查询角色的权限列表
    private List<Permission> permissions;

    /** 该角色对某用户的有效期（仅从 user_roles 关联查询时填充，NULL 表示永久），非角色本身的属性 */
    private LocalDateTime expiresAt;
}

package org.example.blog.dao;

import lombok.Data;
import java.util.List;

@Data
public class Role {
    private Integer id;
    private String roleName;
    private String description;

    // 用于查询角色的权限列表
    private List<Permission> permissions;
}
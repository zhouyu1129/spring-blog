package org.example.blog.dao;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class User {
    private UUID id;
    private String username;
    private String password;
    private String nickname;
    private String realName;
    private String gender;
    private String email;
    private Boolean emailVerified;
    private String mobile;
    private String studentNumber;
    private Boolean isStaff;
    private Boolean isAdmin;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoggedAt;

    // 关联属性 (非数据库字段，用于业务逻辑)
    private List<Role> roles;
}

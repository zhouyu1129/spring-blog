package org.example.blog.controller;

import org.example.blog.dao.User;
import org.example.blog.service.CustomUserDetails;
import org.example.blog.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 查询所有用户
     * GET /users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> list() {
        return ResponseEntity.ok(userService.getAll());
    }

    /**
     * 根据 ID 查询用户
     * GET /users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<User> getById(@PathVariable UUID id) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    /**
     * 更新用户信息
     * PUT /users/{id}
     * 非管理员只能修改安全字段（nickname, realName, gender, mobile, email），
     * 敏感字段（isAdmin, isStaff, isEnabled）只能由管理员修改。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                       @RequestBody User body,
                                       @AuthenticationPrincipal CustomUserDetails currentUser) {
        body.setId(id);

        User existing = userService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = currentUser.isAdmin();

        // 非管理员的字段限制
        if (!isAdmin) {
            // 用户名格式校验
            if (body.getUsername() != null && !UserService.isUserNameValid(body.getUsername())) {
                return ResponseEntity.badRequest().body(Map.of("message", "用户名不合法"));
            }
            // 学号不可修改
            body.setStudentNumber(existing.getStudentNumber());
            // 敏感字段不可修改
            body.setIsAdmin(existing.getIsAdmin());
            body.setIsStaff(existing.getIsStaff());
            body.setIsEnabled(existing.getIsEnabled());
        }

        // 邮箱格式校验
        if (body.getEmail() != null && !UserService.isEmailValid(body.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不合法"));
        }

        // 密码不应通过此接口修改，强制保留原密码
        body.setPassword(null);

        userService.update(body);
        return ResponseEntity.ok(userService.getById(id));
    }

    /**
     * 删除用户
     * DELETE /users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 给用户分配角色
     * POST /users/{id}/roles
     * Body: { "roleId": 1 }
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(@PathVariable UUID id, @RequestBody Map<String, Integer> body) {
        userService.assignRole(id, body.get("roleId"));
        return ResponseEntity.ok().build();
    }

    /**
     * 移除用户的角色
     * DELETE /users/{id}/roles/{roleId}
     */
    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeRole(@PathVariable UUID id, @PathVariable Integer roleId) {
        userService.removeRole(id, roleId);
        return ResponseEntity.ok().build();
    }
}

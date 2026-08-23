package org.example.blog.controller;

import lombok.RequiredArgsConstructor;
import org.example.blog.service.AdminService;
import org.example.blog.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 管理员后端 API（RESTful 风格，分页用查询参数）。
 * <p>
 * 访问控制（见 SecurityConfig）：
 * <ul>
 *   <li>查询（GET /api/admin/**）：需 is_staff 或 is_admin（ROLE_STAFF / ROLE_ADMIN）</li>
 *   <li>修改（POST / PATCH / DELETE）：仅 is_admin（ROLE_ADMIN）</li>
 * </ul>
 * 业务约束（格式、唯一性、自我保护等）见 {@link AdminService}。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ApiAdminController {

    private final AdminService adminService;

    // ========== 用户管理 ==========

    /** 用户列表（分页 + 搜索用户名/昵称/邮箱/学号） */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(adminService.listUsers(search, page));
    }

    /** 用户详情 */
    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    /** 创建用户 */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.createUser(body));
    }

    /** 编辑用户（部分更新：username/nickname/real_name/mobile/gender/email/student_number/password/email_verified/is_staff/is_admin/is_enabled） */
    @PatchMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateUser(currentUser.getId(), id, body));
    }

    /** 删除用户（物理删除，连同其文章与评论） */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(adminService.deleteUser(currentUser.getId(), id));
    }

    // ========== 文章管理 ==========

    /** 文章列表（分页，含已删除/已隐藏；deleted/hidden 查询参数为 true/false 时只保留对应状态，缺省为全部） */
    @GetMapping("/articles")
    public ResponseEntity<Map<String, Object>> listArticles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(adminService.listArticles(search, deleted, hidden, page));
    }

    /** 文章详情（含已删除/已隐藏的最新版本，附图片/文件列表） */
    @GetMapping("/articles/{indexId}")
    public ResponseEntity<Map<String, Object>> getArticle(@PathVariable Integer indexId) {
        return ResponseEntity.ok(adminService.getArticle(indexId));
    }

    /** 编辑文章（部分更新：title/content 生成新版本，is_hidden/is_deleted 修改状态，可用于隐藏/恢复/软删除） */
    @PatchMapping("/articles/{indexId}")
    public ResponseEntity<Map<String, Object>> updateArticle(
            @PathVariable Integer indexId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateArticle(indexId, body));
    }

    // ========== 评论管理 ==========

    /** 评论列表（分页，含已删除/已隐藏；deleted/hidden 查询参数为 true/false 时只保留对应状态，缺省为全部） */
    @GetMapping("/comments")
    public ResponseEntity<Map<String, Object>> listComments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(adminService.listComments(search, deleted, hidden, page));
    }

    /** 编辑评论（部分更新：content 生成新版本，is_hidden/is_deleted 修改状态，可用于隐藏/恢复/软删除） */
    @PatchMapping("/comments/{commentIndexId}")
    public ResponseEntity<Map<String, Object>> updateComment(
            @PathVariable Integer commentIndexId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateComment(commentIndexId, body));
    }
}

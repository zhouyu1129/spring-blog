package org.example.blog.controller;

import org.example.blog.service.CommentService;
import org.example.blog.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 前端 API 评论控制器
 * 所有路径以 /api/comment 开头，与前端 api/index.ts 中 commentApi 定义对应
 */
@RestController
@RequestMapping("/api/comment")
public class ApiCommentController {

    private final CommentService commentService;

    public ApiCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // ========== 3.1 评论列表（无需登录，可见性与文章详情一致） ==========

    @GetMapping("/{articleIndexId}/{page}/")
    public ResponseEntity<?> list(@AuthenticationPrincipal CustomUserDetails currentUser,
                                  @PathVariable Integer articleIndexId,
                                  @PathVariable int page) {
        Map<String, Object> result = commentService.list(articleIndexId, page,
                currentUserId(currentUser), isAdmin(currentUser));
        if (result == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", "文章不存在"));
        }
        return ResponseEntity.ok(result);
    }

    // ========== 3.2 创建评论 ==========

    @PostMapping("/{articleIndexId}/create/")
    public ResponseEntity<?> create(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer articleIndexId,
                                    @RequestBody Map<String, String> body) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return badRequest("评论内容不能为空");
        }
        commentService.create(currentUser.getId(), isAdmin(currentUser), articleIndexId, content);
        return ResponseEntity.ok(Map.of("status", "success", "message", "评论发布成功"));
    }

    // ========== 3.3 修改评论（作者/版主/管理员） ==========
    @PostMapping("/update/{commentIndexId}/")
    public ResponseEntity<?> update(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer commentIndexId,
                                    @RequestBody Map<String, String> body) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return badRequest("评论内容不能为空");
        }
        commentService.edit(currentUser.getId(), isAdmin(currentUser), commentIndexId, content);
        return ResponseEntity.ok(Map.of("status", "success", "message", "评论修改成功"));
    }

    // ========== 3.4 删除评论（仅作者，软删除） ==========

    @PostMapping("/delete/{commentIndexId}/")
    public ResponseEntity<?> delete(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer commentIndexId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        commentService.delete(currentUser.getId(), commentIndexId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "评论已删除"));
    }

    // ========== 3.5 隐藏评论（仅作者） ==========

    @PostMapping("/hide/{commentIndexId}/")
    public ResponseEntity<?> hide(@AuthenticationPrincipal CustomUserDetails currentUser,
                                  @PathVariable Integer commentIndexId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        commentService.setHidden(currentUser.getId(), commentIndexId, true);
        return ResponseEntity.ok(Map.of("status", "success", "message", "评论已隐藏"));
    }

    // ========== 3.6 取消隐藏评论（仅作者） ==========

    @PostMapping("/unhide/{commentIndexId}/")
    public ResponseEntity<?> unhide(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer commentIndexId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        commentService.setHidden(currentUser.getId(), commentIndexId, false);
        return ResponseEntity.ok(Map.of("status", "success", "message", "评论已取消隐藏"));
    }

    // ========== 辅助方法 ==========

    private UUID currentUserId(CustomUserDetails currentUser) {
        return currentUser != null ? currentUser.getId() : null;
    }

    private boolean isAdmin(CustomUserDetails currentUser) {
        return currentUser != null && currentUser.isAdmin();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message));
    }
}

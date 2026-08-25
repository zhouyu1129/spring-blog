package org.example.blog.controller;

import org.example.blog.dao.File;
import org.example.blog.service.ArticleService;
import org.example.blog.service.CustomUserDetails;
import org.example.blog.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 前端 API 文章控制器
 * 所有路径以 /api/article 开头，与前端 api/index.ts 中 articleApi 定义对应
 */
@RestController
@RequestMapping("/api/article")
public class ApiArticleController {

    private final ArticleService articleService;
    private final StorageService storageService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ApiArticleController(ArticleService articleService, StorageService storageService) {
        this.articleService = articleService;
        this.storageService = storageService;
    }

    // ========== 2.1 文章列表（无需登录，已隐藏文章仅管理员和作者本人可见） ==========

    @GetMapping("/")
    public ResponseEntity<?> list(@AuthenticationPrincipal CustomUserDetails currentUser,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(articleService.list(search, page, currentUserId(currentUser), isAdmin(currentUser)));
    }

    // ========== 2.2 文章详情（无需登录，可见性同列表） ==========

    @GetMapping("/{indexId}/")
    public ResponseEntity<?> detail(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer indexId) {
        Map<String, Object> detail = articleService.getDetail(indexId, currentUserId(currentUser), isAdmin(currentUser));
        if (detail == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", "文章不存在"));
        }
        return ResponseEntity.ok(detail);
    }

    // ========== 2.3 创建文章 ==========

    @PostMapping("/create/")
    public ResponseEntity<?> create(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @RequestParam String title,
                                    @RequestParam String content,
                                    @RequestParam(name = "images", required = false) List<MultipartFile> images,
                                    @RequestParam(name = "image_id_mapping", required = false) String imageIdMapping,
                                    @RequestParam(name = "selected_files", required = false) List<String> selectedFiles) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return badRequest("标题和内容不能为空");
        }
        List<Integer> mapping = parseImageIdMapping(imageIdMapping);
        if (mapping == null) {
            return badRequest("image_id_mapping 格式错误");
        }

        articleService.create(currentUser.getId(), isAdmin(currentUser), title, content, images, mapping, selectedFiles);
        return ResponseEntity.ok(Map.of("status", "success", "message", "文章创建成功"));
    }

    // ========== 2.4 编辑文章（仅作者） ==========

    @PostMapping("/{indexId}/edit/")
    public ResponseEntity<?> edit(@AuthenticationPrincipal CustomUserDetails currentUser,
                                  @PathVariable Integer indexId,
                                  @RequestParam String title,
                                  @RequestParam String content,
                                  @RequestParam(name = "images", required = false) List<MultipartFile> images,
                                  @RequestParam(name = "image_id_mapping", required = false) String imageIdMapping,
                                  @RequestParam(name = "keep_images", required = false) List<String> keepImages,
                                  @RequestParam(name = "keep_files", required = false) List<String> keepFiles,
                                  @RequestParam(name = "selected_files", required = false) List<String> selectedFiles) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return badRequest("标题和内容不能为空");
        }
        List<Integer> mapping = parseImageIdMapping(imageIdMapping);
        if (mapping == null) {
            return badRequest("image_id_mapping 格式错误");
        }

        articleService.edit(currentUser.getId(), isAdmin(currentUser), indexId, title, content,
                images, mapping, keepImages, keepFiles, selectedFiles);
        return ResponseEntity.ok(Map.of("status", "success", "message", "文章修改成功"));
    }

    // ========== 2.5 删除文章（作者或管理员） ==========

    @PostMapping("/{indexId}/delete/")
    public ResponseEntity<?> delete(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer indexId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        articleService.delete(currentUser.getId(), isAdmin(currentUser), indexId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "文章已删除"));
    }

    // ========== 2.6 上传文件 ==========

    @PostMapping("/upload-file/")
    public ResponseEntity<?> uploadFile(@AuthenticationPrincipal CustomUserDetails currentUser,
                                        @RequestParam("file") MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "文件上传失败"));
        }
        try {
            File record = articleService.uploadTempFile(currentUser.getId(), file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "file_id", record.getId().toString(),
                    "filename", record.getTitle(),
                    "file_size", file.getSize(),
                    "file_url", storageService.fileUrl(record.getPath())));
        } catch (StorageService.StorageException e) {
            return ResponseEntity.ok(Map.of("success", false, "error", "文件上传失败"));
        }
    }

    // ========== 2.7 删除临时文件 ==========

    @PostMapping("/delete-temp-file/{fileId}/")
    public ResponseEntity<?> deleteTempFile(@AuthenticationPrincipal CustomUserDetails currentUser,
                                            @PathVariable String fileId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        articleService.deleteTempFile(currentUser.getId(), fileId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ========== 2.8 获取临时文件列表 ==========

    @GetMapping("/get-temp-files/")
    public ResponseEntity<?> getTempFiles(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        return ResponseEntity.ok(Map.of("success", true,
                "files", articleService.getTempFiles(currentUser.getId())));
    }

    // ========== 2.9 隐藏文章（作者或管理员） ==========

    @PostMapping("/{indexId}/hide/")
    public ResponseEntity<?> hide(@AuthenticationPrincipal CustomUserDetails currentUser,
                                  @PathVariable Integer indexId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        articleService.setHidden(currentUser.getId(), isAdmin(currentUser), indexId, true);
        return ResponseEntity.ok(Map.of("status", "success", "message", "文章已隐藏"));
    }

    // ========== 2.10 取消隐藏文章（作者或管理员） ==========

    @PostMapping("/{indexId}/unhide/")
    public ResponseEntity<?> unhide(@AuthenticationPrincipal CustomUserDetails currentUser,
                                    @PathVariable Integer indexId) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        articleService.setHidden(currentUser.getId(), isAdmin(currentUser), indexId, false);
        return ResponseEntity.ok(Map.of("status", "success", "message", "文章已取消隐藏"));
    }

    // ========== 辅助方法 ==========

    private java.util.UUID currentUserId(CustomUserDetails currentUser) {
        return currentUser != null ? currentUser.getId() : null;
    }

    private boolean isAdmin(CustomUserDetails currentUser) {
        return currentUser != null && currentUser.isAdmin();
    }

    /** 解析 image_id_mapping（JSON 数组字符串，如 "[1,2,3]"），格式错误返回 null */
    private List<Integer> parseImageIdMapping(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message));
    }
}

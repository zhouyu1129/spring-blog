package org.example.blog.service;

import lombok.RequiredArgsConstructor;
import org.example.blog.dao.Article;
import org.example.blog.dao.Comment;
import org.example.blog.dao.File;
import org.example.blog.dao.Image;
import org.example.blog.dao.User;
import org.example.blog.mapper.ArticleMapper;
import org.example.blog.mapper.CommentMapper;
import org.example.blog.mapper.FileMapper;
import org.example.blog.mapper.ImageMapper;
import org.example.blog.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员后端业务逻辑。
 * <p>
 * 访问控制由 SecurityConfig 完成：查询（GET /api/admin/**）需 ROLE_STAFF 或 ROLE_ADMIN，
 * 修改（写操作）需 ROLE_ADMIN。本类只负责业务约束：
 * <ul>
 *   <li>用户名/邮箱/学号的格式与唯一性校验（创建和修改时）</li>
 *   <li>自我保护：管理员不能取消自己的管理员权限、禁用或删除自己的账号</li>
 *   <li>文章/评论的软删除与隐藏状态按「最新版本」读写，内容修改生成新版本且不改变作者</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int PAGE_SIZE = 10;

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final ImageMapper imageMapper;
    private final FileMapper fileMapper;
    private final PasswordEncoder passwordEncoder;

    // ========== 用户管理 ==========

    /** 用户列表（分页 + 关键词搜索用户名/昵称/邮箱/学号） */
    public Map<String, Object> listUsers(String search, int page) {
        String keyword = normalizeKeyword(search);
        long total = userMapper.countByKeyword(keyword);
        int currentPage = safePage(page, total);
        List<Map<String, Object>> objectList = userMapper
                .selectPage(keyword, PAGE_SIZE, offset(currentPage))
                .stream().map(this::toAdminUserMap).toList();
        return Map.of("page_obj", buildPageObj(currentPage, total, objectList));
    }

    /** 用户详情 */
    public Map<String, Object> getUser(UUID id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw notFound("用户不存在");
        }
        return Map.of("user", toAdminUserMap(user));
    }

    /** 创建用户（必填 username/email/student_number/password，其余可选） */
    @Transactional
    public Map<String, Object> createUser(Map<String, Object> body) {
        String username = requiredString(body, "username");
        String email = requiredString(body, "email");
        String studentNumber = requiredString(body, "student_number");
        String password = requiredString(body, "password");

        if (!UserService.isUserNameValid(username)) {
            throw badRequest("用户名格式不合法（3-40 位，仅字母数字下划线连字符，且不能为纯数字）");
        }
        if (!UserService.isEmailValid(email)) {
            throw badRequest("邮箱格式不合法");
        }
        if (!UserService.isStudentNumberValid(studentNumber)) {
            throw badRequest("学号格式不合法（10 位数字）");
        }
        if (!UserService.isPasswordValid(password)) {
            throw badRequest("密码长度需为 6-128 位");
        }
        requireUniqueUser(username, email, studentNumber, null);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(optionalString(body, "nickname", username));
        user.setRealName(optionalString(body, "real_name", null));
        user.setGender(optionalString(body, "gender", null));
        user.setMobile(optionalString(body, "mobile", null));
        user.setEmail(email);
        user.setStudentNumber(studentNumber);
        user.setEmailVerified(optionalBoolean(body, "email_verified", false));
        user.setIsStaff(optionalBoolean(body, "is_staff", false));
        user.setIsAdmin(optionalBoolean(body, "is_admin", false));
        user.setIsEnabled(optionalBoolean(body, "is_enabled", true));
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return Map.of("user", toAdminUserMap(userMapper.selectById(user.getId())));
    }

    /**
     * 编辑用户（部分更新）。
     * 约束：不能取消自己的管理员权限、不能禁用自己的账号（防止锁死）
     */
    @Transactional
    public Map<String, Object> updateUser(UUID operatorId, UUID targetId, Map<String, Object> body) {
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw notFound("用户不存在");
        }

        // 自我保护
        if (targetId.equals(operatorId)) {
            if (Boolean.FALSE.equals(body.get("is_admin"))) {
                throw badRequest("不能取消自己的管理员权限");
            }
            if (Boolean.FALSE.equals(body.get("is_enabled"))) {
                throw badRequest("不能禁用自己的账号");
            }
        }

        User update = new User();
        update.setId(targetId);

        if (body.containsKey("username")) {
            String username = requiredString(body, "username");
            if (!UserService.isUserNameValid(username)) {
                throw badRequest("用户名格式不合法（3-40 位，仅字母数字下划线连字符，且不能为纯数字）");
            }
            requireUsernameUnique(username, targetId);
            update.setUsername(username);
        }
        if (body.containsKey("nickname")) {
            update.setNickname(stringOrNull(body.get("nickname")));
        }
        if (body.containsKey("real_name")) {
            update.setRealName(stringOrNull(body.get("real_name")));
        }
        if (body.containsKey("gender")) {
            update.setGender(stringOrNull(body.get("gender")));
        }
        if (body.containsKey("mobile")) {
            update.setMobile(stringOrNull(body.get("mobile")));
        }
        if (body.containsKey("email")) {
            String email = requiredString(body, "email");
            if (!UserService.isEmailValid(email)) {
                throw badRequest("邮箱格式不合法");
            }
            User existing = userMapper.selectByEmail(email);
            if (existing != null && !existing.getId().equals(targetId)) {
                throw badRequest("邮箱已被占用");
            }
            update.setEmail(email);
        }
        if (body.containsKey("student_number")) {
            String studentNumber = requiredString(body, "student_number");
            if (!UserService.isStudentNumberValid(studentNumber)) {
                throw badRequest("学号格式不合法（10 位数字）");
            }
            User existing = userMapper.selectByStudentNumber(studentNumber);
            if (existing != null && !existing.getId().equals(targetId)) {
                throw badRequest("学号已被占用");
            }
            update.setStudentNumber(studentNumber);
        }
        if (body.containsKey("password")) {
            String password = requiredString(body, "password");
            if (!UserService.isPasswordValid(password)) {
                throw badRequest("密码长度需为 6-128 位");
            }
            update.setPassword(passwordEncoder.encode(password));
        }
        if (body.containsKey("email_verified")) {
            update.setEmailVerified(optionalBoolean(body, "email_verified", false));
        }
        if (body.containsKey("is_staff")) {
            update.setIsStaff(optionalBoolean(body, "is_staff", false));
        }
        if (body.containsKey("is_admin")) {
            update.setIsAdmin(optionalBoolean(body, "is_admin", false));
        }
        if (body.containsKey("is_enabled")) {
            update.setIsEnabled(optionalBoolean(body, "is_enabled", true));
        }

        if (body.isEmpty()) {
            throw badRequest("请求体不包含可修改的字段");
        }

        userMapper.update(update);
        return Map.of("user", toAdminUserMap(userMapper.selectById(targetId)));
    }

    /**
     * 删除用户（物理删除）。
     * 约束：不能删除自己；其文章由外键级联删除，其评论先物理删除
     * （comments.author_id 外键无级联），磁盘上的图片/文件记录随文章级联删除。
     */
    @Transactional
    public Map<String, Object> deleteUser(UUID operatorId, UUID targetId) {
        if (targetId.equals(operatorId)) {
            throw badRequest("不能删除自己的账号");
        }
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw notFound("用户不存在");
        }
        commentMapper.deleteByAuthorId(targetId);
        userMapper.deleteById(targetId);
        return Map.of("status", "success", "message", "用户及其文章、评论已删除");
    }

    // ========== 文章管理 ==========

    /** 文章列表（分页，含已删除/已隐藏，可按状态过滤） */
    public Map<String, Object> listArticles(String search, Boolean deleted, Boolean hidden, int page) {
        String keyword = normalizeKeyword(search);
        long total = articleMapper.countAdmin(keyword, deleted, hidden);
        int currentPage = safePage(page, total);
        List<Map<String, Object>> objectList = articleMapper
                .selectAdminPage(keyword, deleted, hidden, PAGE_SIZE, offset(currentPage))
                .stream().map(this::toAdminArticleMap).toList();
        return Map.of("page_obj", buildPageObj(currentPage, total, objectList));
    }

    /** 文章详情（含已删除/已隐藏的最新版本，附图片/文件列表） */
    public Map<String, Object> getArticle(Integer indexId) {
        Article article = articleMapper.selectByIndexId(indexId);
        if (article == null) {
            throw notFound("文章不存在");
        }
        Map<String, Object> map = toAdminArticleMap(article);
        map.put("content", article.getContent());
        List<Map<String, Object>> images = new ArrayList<>();
        for (Image image : imageMapper.selectByArticleId(article.getId())) {
            images.add(Map.of("id", image.getId().toString(), "title", image.getTitle(), "path", image.getPath()));
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (File file : fileMapper.selectByArticleId(article.getId())) {
            files.add(Map.of("id", file.getId().toString(), "title", file.getTitle(), "path", file.getPath()));
        }
        map.put("images", images);
        map.put("files", files);
        return Map.of("article", map);
    }

    /**
     * 编辑文章（部分更新）：title/content 生成新版本（作者不变，图片/文件关联完整复制）；
     * is_hidden/is_deleted 修改「最新版本」的状态标志，可用于隐藏/恢复。
     * 未显式指定的状态在内容修改生成新版本后仍保持编辑前的取值。
     */
    @Transactional
    public Map<String, Object> updateArticle(Integer indexId, Map<String, Object> body) {
        Article current = articleMapper.selectByIndexId(indexId);
        if (current == null) {
            throw notFound("文章不存在");
        }

        String title = stringOrNull(body.get("title"));
        String content = stringOrNull(body.get("content"));
        Boolean hiddenParam = booleanOrNull(body.get("is_hidden"));
        Boolean deletedParam = booleanOrNull(body.get("is_deleted"));

        if (title == null && content == null && hiddenParam == null && deletedParam == null) {
            throw badRequest("请求体不包含可修改的字段（title / content / is_hidden / is_deleted）");
        }

        // 目标状态：显式指定则用指定值，否则保持编辑前状态（新版本不丢状态）
        boolean desiredHidden = hiddenParam != null ? hiddenParam : Boolean.TRUE.equals(current.getIsHidden());
        boolean desiredDeleted = deletedParam != null ? deletedParam : Boolean.TRUE.equals(current.getIsDeleted());

        // 1. 标题/内容 → 插入新版本（沿用 indexId 与作者，复制图片/文件关联）
        if (title != null || content != null) {
            String newTitle = title != null ? title : current.getTitle();
            String newContent = content != null ? content : current.getContent();
            if (newTitle.isBlank() || newContent.isBlank()) {
                throw badRequest("标题和内容不能为空");
            }
            Article version = new Article();
            version.setId(UUID.randomUUID());
            version.setIndexId(indexId);
            version.setTitle(newTitle);
            version.setContent(newContent);
            version.setAuthorId(current.getAuthorId());
            version.setUpdatedAt(LocalDateTime.now());
            articleMapper.insert(version);
            for (Image image : imageMapper.selectByArticleId(current.getId())) {
                imageMapper.insertQuote(version.getId(), image.getId());
            }
            for (File file : fileMapper.selectByArticleId(current.getId())) {
                fileMapper.insertQuote(version.getId(), file.getId());
            }
            current = articleMapper.selectByIndexId(indexId);
        }

        // 2. 状态标志 → 作用于最新版本
        if (Boolean.TRUE.equals(current.getIsHidden()) != desiredHidden) {
            if (desiredHidden) {
                articleMapper.setHidden(current);
            } else {
                articleMapper.setNotHidden(current);
            }
        }
        if (Boolean.TRUE.equals(current.getIsDeleted()) != desiredDeleted) {
            if (desiredDeleted) {
                articleMapper.setDeleted(current);
            } else {
                articleMapper.setNotDeleted(current);
            }
        }

        Article latest = articleMapper.selectByIndexId(indexId);
        Map<String, Object> map = toAdminArticleMap(latest);
        map.put("content", latest.getContent());
        return Map.of("article", map);
    }

    // ========== 评论管理 ==========

    /** 评论列表（分页，含已删除/已隐藏，可按状态过滤） */
    public Map<String, Object> listComments(String search, Boolean deleted, Boolean hidden, int page) {
        String keyword = normalizeKeyword(search);
        long total = commentMapper.countAdmin(keyword, deleted, hidden);
        int currentPage = safePage(page, total);
        List<Map<String, Object>> objectList = commentMapper
                .selectAdminPage(keyword, deleted, hidden, PAGE_SIZE, offset(currentPage))
                .stream().map(this::toAdminCommentMap).toList();
        return Map.of("page_obj", buildPageObj(currentPage, total, objectList));
    }

    /**
     * 编辑评论（部分更新）：content 生成新版本（作者不变）；
     * is_hidden/is_deleted 修改「最新版本」的状态标志，可用于隐藏/恢复。
     */
    @Transactional
    public Map<String, Object> updateComment(Integer commentIndexId, Map<String, Object> body) {
        Comment current = commentMapper.selectByIndexId(commentIndexId);
        if (current == null) {
            throw notFound("评论不存在");
        }

        String content = stringOrNull(body.get("content"));
        Boolean hiddenParam = booleanOrNull(body.get("is_hidden"));
        Boolean deletedParam = booleanOrNull(body.get("is_deleted"));

        if (content == null && hiddenParam == null && deletedParam == null) {
            throw badRequest("请求体不包含可修改的字段（content / is_hidden / is_deleted）");
        }

        boolean desiredHidden = hiddenParam != null ? hiddenParam : Boolean.TRUE.equals(current.getIsHidden());
        boolean desiredDeleted = deletedParam != null ? deletedParam : Boolean.TRUE.equals(current.getIsDeleted());

        // 1. 内容 → 插入新版本（沿用 indexId、作者与所属文章）
        if (content != null) {
            if (content.isBlank()) {
                throw badRequest("评论内容不能为空");
            }
            Comment version = new Comment();
            version.setId(UUID.randomUUID());
            version.setIndexId(commentIndexId);
            version.setContent(content);
            version.setAuthorId(current.getAuthorId());
            version.setArticleIndexId(current.getArticleIndexId());
            version.setUpdatedAt(LocalDateTime.now());
            commentMapper.insert(version);
            current = commentMapper.selectByIndexId(commentIndexId);
        }

        // 2. 状态标志 → 作用于最新版本
        if (Boolean.TRUE.equals(current.getIsHidden()) != desiredHidden) {
            if (desiredHidden) {
                commentMapper.setHidden(current);
            } else {
                commentMapper.setNotHidden(current);
            }
        }
        if (Boolean.TRUE.equals(current.getIsDeleted()) != desiredDeleted) {
            if (desiredDeleted) {
                commentMapper.setDeleted(current);
            } else {
                commentMapper.setNotDeleted(current);
            }
        }

        return Map.of("comment", toAdminCommentMap(commentMapper.selectByIndexId(commentIndexId)));
    }

    // ========== 辅助方法 ==========

    private void requireUniqueUser(String username, String email, String studentNumber, UUID excludeId) {
        requireUsernameUnique(username, excludeId);
        User byEmail = userMapper.selectByEmail(email);
        if (byEmail != null && !byEmail.getId().equals(excludeId)) {
            throw badRequest("邮箱已被占用");
        }
        User byStudentNumber = userMapper.selectByStudentNumber(studentNumber);
        if (byStudentNumber != null && !byStudentNumber.getId().equals(excludeId)) {
            throw badRequest("学号已被占用");
        }
    }

    private void requireUsernameUnique(String username, UUID excludeId) {
        User byUsername = userMapper.selectByUsername(username);
        if (byUsername != null && !byUsername.getId().equals(excludeId)) {
            throw badRequest("用户名已被占用");
        }
    }

    /** User → 管理员视角的用户结构（不含密码） */
    private Map<String, Object> toAdminUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("real_name", user.getRealName());
        map.put("gender", user.getGender());
        map.put("email", user.getEmail());
        map.put("email_verified", user.getEmailVerified());
        map.put("mobile", user.getMobile());
        map.put("student_number", user.getStudentNumber());
        map.put("is_staff", user.getIsStaff());
        map.put("is_admin", user.getIsAdmin());
        map.put("is_enabled", user.getIsEnabled());
        map.put("created_at", user.getCreatedAt());
        map.put("last_logged_at", user.getLastLoggedAt());
        List<String> roles = new ArrayList<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> roles.add(role.getRoleName()));
        }
        map.put("roles", roles);
        return map;
    }

    /** Article → 管理员视角的文章结构（含状态标志与作者摘要，不含正文） */
    private Map<String, Object> toAdminArticleMap(Article article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index_id", article.getIndexId());
        map.put("title", article.getTitle());
        map.put("author", toAuthorSummary(article.getAuthorId()));
        map.put("is_deleted", article.getIsDeleted());
        map.put("is_hidden", article.getIsHidden());
        map.put("created_at", article.getCreatedAt());
        map.put("updated_at", article.getUpdatedAt());
        return map;
    }

    /** Comment → 管理员视角的评论结构（含状态标志、作者摘要与所属文章标题） */
    private Map<String, Object> toAdminCommentMap(Comment comment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index_id", comment.getIndexId());
        map.put("content", comment.getContent());
        map.put("author", toAuthorSummary(comment.getAuthorId()));
        map.put("article_index_id", comment.getArticleIndexId());
        map.put("article_title", comment.getArticleTitle());
        map.put("is_deleted", comment.getIsDeleted());
        map.put("is_hidden", comment.getIsHidden());
        map.put("created_at", comment.getCreatedAt());
        map.put("updated_at", comment.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toAuthorSummary(UUID authorId) {
        if (authorId == null) {
            return null;
        }
        User author = userMapper.selectById(authorId);
        if (author == null) {
            return Map.of("id", authorId.toString());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", author.getId().toString());
        map.put("username", author.getUsername());
        map.put("nickname", author.getNickname());
        return map;
    }

    /** 构造前端约定的 page_obj 分页结构 */
    private Map<String, Object> buildPageObj(int currentPage, long total, List<Map<String, Object>> objectList) {
        int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        Map<String, Object> pageObj = new LinkedHashMap<>();
        pageObj.put("number", currentPage);
        pageObj.put("paginator", Map.of("num_pages", totalPages));
        pageObj.put("object_list", objectList);
        return pageObj;
    }

    private int safePage(int page, long total) {
        int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        return Math.clamp(page, 1, totalPages);
    }

    private int offset(int currentPage) {
        return (currentPage - 1) * PAGE_SIZE;
    }

    private String normalizeKeyword(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw badRequest("缺少必填字段或格式不合法: " + key);
        }
        return s.trim();
    }

    private String optionalString(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            return defaultValue;
        }
        return s.trim();
    }

    private String stringOrNull(Object value) {
        return value instanceof String s ? s : null;
    }

    private boolean optionalBoolean(Map<String, Object> body, String key, boolean defaultValue) {
        Object value = body.get(key);
        return value instanceof Boolean b ? b : defaultValue;
    }

    private Boolean booleanOrNull(Object value) {
        return value instanceof Boolean b ? b : null;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}

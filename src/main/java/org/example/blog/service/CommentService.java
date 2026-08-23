package org.example.blog.service;

import org.example.blog.dao.Article;
import org.example.blog.dao.Comment;
import org.example.blog.dao.User;
import org.example.blog.mapper.ArticleMapper;
import org.example.blog.mapper.CommentMapper;
import org.springframework.http.HttpStatus;
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
 * 评论业务服务：评论的查询/创建/编辑/删除，以及用户主页的评论列表
 */
@Service
public class CommentService {

    /** 评论列表分页大小 */
    private static final int PAGE_SIZE = 10;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserService userService;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper, UserService userService) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.userService = userService;
    }

    // ========== 查询 ==========

    /**
     * 文章的评论列表（分页），返回前端约定的结构（article + page_obj）
     * <p>
     * 文章可见性：已删除文章任何人都不可见；已隐藏文章仅管理员和作者本人可见（与文章详情一致）
     * <p>
     * 评论可见性：已删除评论任何人都不可见；已隐藏评论仅管理员和评论作者本人可见
     *
     * @param page   页码，从 1 开始
     * @param userId 当前用户 ID，游客为 null
     * @param isAdmin 当前用户是否管理员
     * @return 文章不存在、已删除或无权查看时返回 null
     */
    public Map<String, Object> list(Integer articleIndexId, int page, UUID userId, boolean isAdmin) {
        Article article = articleMapper.selectByIndexId(articleIndexId);
        if (article == null || Boolean.TRUE.equals(article.getIsDeleted())) {
            return null;
        }
        if (Boolean.TRUE.equals(article.getIsHidden()) && !canViewHidden(article, userId, isAdmin)) {
            return null;
        }

        // 过滤已删除评论；已隐藏评论仅管理员和评论作者本人可见
        List<Comment> visible = new ArrayList<>();
        for (Comment comment : commentMapper.selectByArticleIndexId(articleIndexId)) {
            if (Boolean.TRUE.equals(comment.getIsDeleted())) {
                continue;
            }
            if (Boolean.TRUE.equals(comment.getIsHidden()) && !canViewHiddenComment(comment, userId, isAdmin)) {
                continue;
            }
            visible.add(comment);
        }

        // 内存分页
        int totalPages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.clamp(page, 1, totalPages);
        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, visible.size());

        List<Map<String, Object>> objectList = new ArrayList<>();
        for (Comment comment : visible.subList(fromIndex, toIndex)) {
            objectList.add(toCommentMap(comment));
        }

        Map<String, Object> pageObj = new LinkedHashMap<>();
        pageObj.put("number", currentPage);
        pageObj.put("paginator", Map.of("num_pages", totalPages));
        pageObj.put("object_list", objectList);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("article", toArticleMap(article));
        result.put("page_obj", pageObj);
        return result;
    }

    /**
     * 用户的评论列表（分页，用户主页使用），返回前端约定的 page_obj 结构；
     * 已删除评论不展示；已隐藏评论仅管理员和评论作者本人可见；
     * 所属文章已删除或已隐藏的评论不展示（与该接口文章列表的处理一致）
     *
     * @param authorId      被查看主页的用户 ID（评论作者）
     * @param viewerId      当前查看者 ID，未登录为 null
     * @param viewerIsAdmin 当前查看者是否管理员
     */
    public Map<String, Object> listByAuthor(UUID authorId, int page, UUID viewerId, boolean viewerIsAdmin) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Comment comment : commentMapper.selectByAuthorId(authorId)) {
            if (Boolean.TRUE.equals(comment.getIsDeleted())) {
                continue;
            }
            if (Boolean.TRUE.equals(comment.getIsHidden()) && !canViewHiddenComment(comment, viewerId, viewerIsAdmin)) {
                continue;
            }
            Article article = comment.getArticleIndexId() == null
                    ? null : articleMapper.selectByIndexId(comment.getArticleIndexId());
            if (article == null || Boolean.TRUE.equals(article.getIsDeleted())
                    || Boolean.TRUE.equals(article.getIsHidden())) {
                continue;
            }
            visible.add(toProfileCommentMap(comment, article));
        }

        // 内存分页
        int totalPages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.clamp(page, 1, totalPages);
        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, visible.size());

        Map<String, Object> pageObj = new LinkedHashMap<>();
        pageObj.put("number", currentPage);
        pageObj.put("paginator", Map.of("num_pages", totalPages));
        pageObj.put("object_list", new ArrayList<>(visible.subList(fromIndex, toIndex)));
        return pageObj;
    }

    // ========== 创建 / 编辑 / 删除 ==========

    /** 创建评论。文章必须存在且当前用户可见（未删除；已隐藏文章仅作者本人和管理员可评论） */
    @Transactional
    public void create(UUID userId, boolean isAdmin, Integer articleIndexId, String content) {
        Article article = articleMapper.selectByIndexId(articleIndexId);
        if (article == null || Boolean.TRUE.equals(article.getIsDeleted())
                || (Boolean.TRUE.equals(article.getIsHidden()) && !canViewHidden(article, userId, isAdmin))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        }

        // indexId 为 null，由数据库序列自动分配
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setContent(content);
        comment.setAuthorId(userId);
        comment.setArticleIndexId(articleIndexId);
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
    }

    /** 编辑评论（仅作者本人）：插入一条沿用同一 indexId 的新版本记录，原版本保留，隐藏状态沿用到新版本 */
    @Transactional
    public void edit(UUID userId, Integer commentIndexId, String content) {
        Comment current = requireOwnedComment(userId, commentIndexId);

        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setIndexId(commentIndexId);
        comment.setContent(content);
        comment.setAuthorId(current.getAuthorId());
        comment.setArticleIndexId(current.getArticleIndexId());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
    }

    /** 删除评论（仅作者本人）：软删除，标记最新版本记录的 is_deleted，删除后任何人都不可见 */
    @Transactional
    public void delete(UUID userId, Integer commentIndexId) {
        Comment comment = requireOwnedComment(userId, commentIndexId);
        commentMapper.setDeleted(comment);
    }

    /** 隐藏/取消隐藏评论（仅作者本人）：隐藏后仅管理员和评论作者本人可见 */
    @Transactional
    public void setHidden(UUID userId, Integer commentIndexId, boolean hidden) {
        Comment comment = requireOwnedComment(userId, commentIndexId);
        if (hidden) {
            commentMapper.setHidden(comment);
        } else {
            commentMapper.setNotHidden(comment);
        }
    }

    // ========== 内部方法 ==========

    /** 查询评论并校验：存在、未删除且当前用户是作者，否则抛出 404/403 */
    private Comment requireOwnedComment(UUID userId, Integer commentIndexId) {
        Comment comment = commentMapper.selectByIndexId(commentIndexId);
        if (comment == null || Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评论不存在");
        }
        if (!userId.equals(comment.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有评论作者可以操作此评论");
        }
        return comment;
    }

    /** 已隐藏文章是否对当前用户可见（管理员或作者本人） */
    private boolean canViewHidden(Article article, UUID userId, boolean isAdmin) {
        return isAdmin || (userId != null && userId.equals(article.getAuthorId()));
    }

    /** 已隐藏评论是否对当前用户可见（管理员或评论作者本人） */
    private boolean canViewHiddenComment(Comment comment, UUID userId, boolean isAdmin) {
        return isAdmin || (userId != null && userId.equals(comment.getAuthorId()));
    }

    /** Comment → 前端评论结构（top 字段数据库暂未实现，固定为 false） */
    private Map<String, Object> toCommentMap(Comment comment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index_id", comment.getIndexId());
        map.put("content", comment.getContent());
        map.put("create_time", comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null);
        map.put("update_time", comment.getUpdatedAt() != null ? comment.getUpdatedAt().toString() : null);
        map.put("is_hidden", Boolean.TRUE.equals(comment.getIsHidden()));
        map.put("top", false);
        map.put("author", toAuthorMap(comment.getAuthorId()));
        return map;
    }

    /** Comment → 用户主页评论结构（比评论列表条目多一个 article 字段） */
    private Map<String, Object> toProfileCommentMap(Comment comment, Article article) {
        Map<String, Object> map = toCommentMap(comment);
        Map<String, Object> articleMap = new LinkedHashMap<>();
        articleMap.put("index_id", article.getIndexId());
        articleMap.put("title", article.getTitle());
        map.put("article", articleMap);
        return map;
    }

    /** 文章信息（评论列表顶部的 article 字段） */
    private Map<String, Object> toArticleMap(Article article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index_id", article.getIndexId());
        map.put("title", article.getTitle());
        map.put("author_id", toAuthorMap(article.getAuthorId()));
        map.put("created_at", article.getCreatedAt() != null ? article.getCreatedAt().toString() : null);
        return map;
    }

    /** 作者信息（id 为字符串，前端按字符串处理用户 ID） */
    private Map<String, Object> toAuthorMap(UUID authorId) {
        if (authorId == null) {
            return null;
        }
        User author = userService.getById(authorId);
        if (author == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", author.getId().toString());
        map.put("nickname", author.getNickname());
        map.put("username", author.getUsername());
        return map;
    }
}

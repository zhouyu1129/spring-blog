package org.example.blog.service;

import org.example.blog.dao.Article;
import org.example.blog.dao.File;
import org.example.blog.dao.Image;
import org.example.blog.dao.User;
import org.example.blog.mapper.ArticleMapper;
import org.example.blog.mapper.FileMapper;
import org.example.blog.mapper.ImageMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章业务服务：文章的查询/创建/编辑/删除，以及文章图片和附件的管理
 */
@Service
public class ArticleService {

    /** 文章列表分页大小 */
    private static final int PAGE_SIZE = 10;

    /** 正文中的图片占位符，如 [[img_id=3]] */
    private static final Pattern IMAGE_REF_PATTERN = Pattern.compile("\\[\\[img_id=(\\d+)\\]\\]");

    private final ArticleMapper articleMapper;
    private final ImageMapper imageMapper;
    private final FileMapper fileMapper;
    private final StorageService storageService;
    private final UserService userService;

    public ArticleService(ArticleMapper articleMapper, ImageMapper imageMapper,
                          FileMapper fileMapper, StorageService storageService,
                          UserService userService) {
        this.articleMapper = articleMapper;
        this.imageMapper = imageMapper;
        this.fileMapper = fileMapper;
        this.storageService = storageService;
        this.userService = userService;
    }

    // ========== 查询 ==========

    /**
     * 文章列表（分页 + 标题搜索），返回前端约定的 page_obj 结构。
     * 可见性过滤与分页在 SQL 层完成
     * <p>
     * 可见性：已删除文章任何人都不可见；已隐藏文章仅管理员和作者本人可见，其余文章所有人可见
     *
     * @param search   搜索关键词（搜索标题），可为空
     * @param page     页码，从 1 开始
     * @param userId   当前用户 ID，游客为 null
     * @param isAdmin  当前用户是否管理员
     */
    public Map<String, Object> list(String search, int page, UUID userId, boolean isAdmin) {
        String keyword = (search == null || search.isBlank()) ? null : search.trim();

        long total = articleMapper.countVisible(keyword, userId, isAdmin);
        int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.clamp(page, 1, totalPages);
        int offset = (currentPage - 1) * PAGE_SIZE;

        List<Map<String, Object>> objectList = new ArrayList<>();
        for (Article article : articleMapper.selectVisiblePage(keyword, userId, isAdmin, PAGE_SIZE, offset)) {
            objectList.add(toArticleMap(article, userId, isAdmin));
        }

        Map<String, Object> pageObj = new LinkedHashMap<>();
        pageObj.put("number", currentPage);
        pageObj.put("paginator", Map.of("num_pages", totalPages));
        pageObj.put("object_list", objectList);
        return Map.of("page_obj", pageObj);
    }

    /**
     * 文章详情（含关联的图片和文件），可见性规则同 list
     *
     * @return 前端约定的详情结构；文章不存在、已删除或无权查看时返回 null
     */
    public Map<String, Object> getDetail(Integer indexId, UUID userId, boolean isAdmin) {
        Article article = articleMapper.selectByIndexId(indexId);
        if (article == null || Boolean.TRUE.equals(article.getIsDeleted())) {
            return null;
        }
        if (Boolean.TRUE.equals(article.getIsHidden()) && !canViewHidden(article, userId, isAdmin)) {
            return null;
        }

        List<Map<String, Object>> images = new ArrayList<>();
        for (Image image : imageMapper.selectByArticleId(article.getId())) {
            images.add(toImageMap(image));
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (File file : fileMapper.selectByArticleId(article.getId())) {
            files.add(toFileMap(file));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("article", toArticleMap(article, userId, isAdmin));
        result.put("files", files);
        result.put("images", images);
        return result;
    }

    // ========== 创建 / 编辑 / 删除 ==========

    /**
     * 创建文章：保存被引用的图片，把正文中的 [[img_id=N]] 占位符替换为标准 Markdown 图片语法，
     * 插入文章记录并建立与图片、附件的关联
     *
     * @param imageIdMapping  与 imageFiles 一一对应的临时图片 ID 列表
     * @param selectedFileIds 要关联的临时文件 ID 列表（之前通过 upload-file 接口上传）
     */
    @Transactional
    public void create(UUID authorId, String title, String content,
                       List<MultipartFile> imageFiles, List<Integer> imageIdMapping,
                       List<String> selectedFileIds) {
        // 1. 保存图片，记录 临时ID → Image 的对应关系
        Map<Integer, Image> uploadedImages = saveImages(authorId, imageFiles, imageIdMapping);

        // 2. 替换正文中的图片占位符
        String finalContent = replaceImageRefs(content, uploadedImages);

        // 3. 插入文章（indexId 为 null，由数据库序列自动分配）
        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setTitle(title);
        article.setContent(finalContent);
        article.setAuthorId(authorId);
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(article);

        // 4. 建立文章与图片、附件的关联
        quoteImagesAndFiles(article.getId(), uploadedImages.values(), selectedFileIds);
    }

    /**
     * 编辑文章：插入一条沿用同一 indexId 的新版本记录；
     * 保留的图片/文件关联到新版本，未保留的仅不关联新版本（记录和磁盘文件保留，供历史版本预览），
     * 新上传的图片/文件关联到新版本
     * <p>
     * 权限：作者本人或管理员，且文章未删除
     */
    @Transactional
    public void edit(UUID userId, boolean isAdmin, Integer indexId, String title, String content,
                     List<MultipartFile> imageFiles, List<Integer> imageIdMapping,
                     List<String> keepImageIds, List<String> keepFileIds,
                     List<String> selectedFileIds) {
        Article current = requireOperableArticle(userId, isAdmin, indexId);

        // 1. 保存新上传的图片（编辑时 [[img_id=N]] 只指向新图片，旧图片在正文中已是标准 Markdown）
        Map<Integer, Image> newImages = saveImages(userId, imageFiles, imageIdMapping);
        String finalContent = replaceImageRefs(content, newImages);

        // 2. 插入新版本：沿用 indexId，created_at 由 SQL 自动继承首版时间
        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setIndexId(indexId);
        article.setTitle(title);
        article.setContent(finalContent);
        article.setAuthorId(current.getAuthorId());
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(article);

        // 3. 处理已有图片：保留的关联到新版本；未保留的不关联新版本，
        //    但不删除图片记录和磁盘文件（旧版本仍引用它们，后台预览历史版本时不受影响）
        Set<UUID> keepImages = toUuidSet(keepImageIds);
        for (Image image : imageMapper.selectByArticleId(current.getId())) {
            if (keepImages.contains(image.getId())) {
                imageMapper.insertQuote(article.getId(), image.getId());
            }
        }

        // 4. 处理已有文件，逻辑同上
        Set<UUID> keepFiles = toUuidSet(keepFileIds);
        for (File file : fileMapper.selectByArticleId(current.getId())) {
            if (keepFiles.contains(file.getId())) {
                fileMapper.insertQuote(article.getId(), file.getId());
            }
        }

        // 5. 关联新图片与新增的临时文件
        quoteImagesAndFiles(article.getId(), newImages.values(), selectedFileIds);
    }

    /** 删除文章（软删除，关联的图片和文件保留；删除后任何人在现有前端都无法访问）。权限：作者或管理员 */
    @Transactional
    public void delete(UUID userId, boolean isAdmin, Integer indexId) {
        Article article = requireOperableArticle(userId, isAdmin, indexId);
        articleMapper.setDeleted(article);
    }

    /** 隐藏/取消隐藏文章。权限：作者或管理员，且文章未删除 */
    @Transactional
    public void setHidden(UUID userId, boolean isAdmin, Integer indexId, boolean hidden) {
        Article article = requireOperableArticle(userId, isAdmin, indexId);
        if (hidden) {
            articleMapper.setHidden(article);
        } else {
            articleMapper.setNotHidden(article);
        }
    }

    // ========== 临时文件（已上传但未关联文章的文件） ==========

    /** 上传临时文件：保存到磁盘并插入 files 表，返回文件记录 */
    public File uploadTempFile(UUID authorId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        File record = new File();
        record.setId(UUID.randomUUID());
        record.setTitle(originalFilename == null || originalFilename.isBlank() ? "未命名文件" : originalFilename);
        record.setPath(storageService.storeFile(file));
        record.setAuthorId(authorId);
        record.setCreatedAt(LocalDateTime.now());
        fileMapper.insert(record);
        return record;
    }

    /** 删除临时文件（数据库记录 + 磁盘文件） */
    public void deleteTempFile(UUID userId, String fileId) {
        UUID id = parseUuid(fileId);
        File file = id == null ? null : fileMapper.selectById(id);
        if (file == null || !file.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        fileMapper.deleteById(file.getId());
        storageService.deleteFile(file.getPath());
    }

    /** 查询当前用户的临时文件列表（前端约定的 file_id/filename/file_size/file_url 结构） */
    public List<Map<String, Object>> getTempFiles(UUID authorId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (File file : fileMapper.selectTempByAuthorId(authorId)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("file_id", file.getId().toString());
            map.put("filename", file.getTitle());
            map.put("file_size", storageService.fileSize(file.getPath()));
            map.put("file_url", storageService.fileUrl(file.getPath()));
            result.add(map);
        }
        return result;
    }

    // ========== 内部方法 ==========

    /** 保存上传的图片并插入 images 表，返回 临时ID → Image 的对应关系 */
    private Map<Integer, Image> saveImages(UUID authorId, List<MultipartFile> imageFiles, List<Integer> imageIdMapping) {
        Map<Integer, Image> result = new LinkedHashMap<>();
        if (imageFiles == null) {
            return result;
        }
        for (int i = 0; i < imageFiles.size(); i++) {
            MultipartFile file = imageFiles.get(i);
            if (file == null || file.isEmpty()) {
                continue;
            }
            // 前端 image_id_mapping[i] 与 images[i] 一一对应；缺省时按 1 开始的序号
            int tempId = (imageIdMapping != null && i < imageIdMapping.size())
                    ? imageIdMapping.get(i) : i + 1;

            Image image = new Image();
            image.setId(UUID.randomUUID());
            image.setTitle(file.getOriginalFilename());
            image.setPath(storageService.storeImage(file));
            image.setAuthorId(authorId);
            image.setCreatedAt(LocalDateTime.now());
            imageMapper.insert(image);
            result.put(tempId, image);
        }
        return result;
    }

    /** 把正文中的 [[img_id=N]] 替换为标准 Markdown 图片语法，如 ![图片名](/media/images/xxx.png) */
    private String replaceImageRefs(String content, Map<Integer, Image> images) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        Matcher matcher = IMAGE_REF_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Image image = images.get(Integer.parseInt(matcher.group(1)));
            String replacement = "";
            if (image != null) {
                // alt 文本去掉 Markdown 语法字符，避免破坏语法
                String alt = image.getTitle() == null ? "" : image.getTitle().replaceAll("[\\[\\]()]", "");
                replacement = "![" + alt + "](" + storageService.imageUrl(image.getPath()) + ")";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 建立文章与图片、附件的关联 */
    private void quoteImagesAndFiles(UUID articleId, Collection<Image> images, List<String> selectedFileIds) {
        for (Image image : images) {
            imageMapper.insertQuote(articleId, image.getId());
        }
        if (selectedFileIds == null) {
            return;
        }
        for (String fileId : selectedFileIds) {
            UUID id = parseUuid(fileId);
            if (id == null) {
                continue;
            }
            File file = fileMapper.selectById(id);
            if (file != null) {
                fileMapper.insertQuote(articleId, file.getId());
            }
        }
    }

    /** 查询文章并校验：存在、未删除、当前用户是作者或管理员，否则抛出 404/403 */
    private Article requireOperableArticle(UUID userId, boolean isAdmin, Integer indexId) {
        Article article = articleMapper.selectByIndexId(indexId);
        if (article == null || Boolean.TRUE.equals(article.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        }
        if (!canOperate(article, userId, isAdmin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有作者或管理员可以操作此文章");
        }
        return article;
    }

    /** 已隐藏文章是否对当前用户可见（管理员或作者本人） */
    private boolean canViewHidden(Article article, UUID userId, boolean isAdmin) {
        return isAdmin || (userId != null && userId.equals(article.getAuthorId()));
    }

    /** 当前用户是否可操作（编辑/删除/隐藏）该文章：作者本人或管理员 */
    private boolean canOperate(Article article, UUID userId, boolean isAdmin) {
        if (userId == null) {
            return false;
        }
        return isAdmin || userId.equals(article.getAuthorId());
    }

    /** Article → 前端文章结构（含作者信息和当前用户的操作权限标志） */
    private Map<String, Object> toArticleMap(Article article, UUID userId, boolean isAdmin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index_id", article.getIndexId());
        map.put("title", article.getTitle());
        map.put("content", article.getContent());
        map.put("is_hidden", Boolean.TRUE.equals(article.getIsHidden()));
        map.put("can_edit", canOperate(article, userId, isAdmin));
        map.put("can_hide", canOperate(article, userId, isAdmin));
        map.put("created_at", article.getCreatedAt() != null ? article.getCreatedAt().toString() : null);
        map.put("updated_at", article.getUpdatedAt() != null ? article.getUpdatedAt().toString() : null);
        map.put("author_id", toAuthorMap(article.getAuthorId()));
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

    /** Image → 前端图片结构 */
    private Map<String, Object> toImageMap(Image image) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", image.getId().toString());
        map.put("title", image.getTitle());
        map.put("content", Map.of("url", storageService.imageUrl(image.getPath())));
        map.put("created_at", image.getCreatedAt() != null ? image.getCreatedAt().toString() : null);
        return map;
    }

    /** File → 前端文件结构 */
    private Map<String, Object> toFileMap(File file) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", file.getId().toString());
        map.put("title", file.getTitle());
        map.put("content", Map.of(
                "url", storageService.fileUrl(file.getPath()),
                "size", storageService.fileSize(file.getPath())));
        map.put("created_at", file.getCreatedAt() != null ? file.getCreatedAt().toString() : null);
        return map;
    }

    private Set<UUID> toUuidSet(List<String> ids) {
        Set<UUID> result = new HashSet<>();
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            UUID uuid = parseUuid(id);
            if (uuid != null) {
                result.add(uuid);
            }
        }
        return result;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package org.example.blog;

import com.jayway.jsonpath.JsonPath;
import org.example.blog.dao.Article;
import org.example.blog.dao.Image;
import org.example.blog.dao.User;
import org.example.blog.mapper.ArticleMapper;
import org.example.blog.mapper.ImageMapper;
import org.example.blog.service.CustomUserDetails;
import org.example.blog.service.StorageService;
import org.example.blog.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文章模块接口集成测试
 * <p>
 * 通过 MockMvc 走完整的过滤器链（含 Spring Security），验证：
 * 创建/列表/详情/编辑/删除/隐藏以及管理员、作者、其他用户、游客的角色权限。
 * <p>
 * {@code @Transactional} 使每个测试的数据库写入自动回滚；
 * 测试上传到磁盘的图片/文件记录后统一删除。
 * 需要本地 PostgreSQL 和 Redis 运行（与开发环境相同配置）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArticleApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ImageMapper imageMapper;

    private User author;
    private User other;
    private User adminUser;

    /** 本测试上传到磁盘的图片/文件（相对路径），@AfterEach 统一删除 */
    private final List<String> savedImages = new ArrayList<>();
    private final List<String> savedFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        author = newUser("author");
        other = newUser("other");
        adminUser = newUser("admin");
    }

    @AfterEach
    void cleanUpFiles() {
        savedImages.forEach(storageService::deleteImage);
        savedFiles.forEach(storageService::deleteFile);
        savedImages.clear();
        savedFiles.clear();
    }

    // ========== 创建 ==========

    @Test
    void createArticle_byAuthor_succeedsAndVisibleInList() throws Exception {
        String title = uniqueTitle("测试创建文章");

        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", title)
                        .param("content", "# 标题\n\n正文内容")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 列表可见，且带权限标志
        mockMvc.perform(get("/api/article/").param("search", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].title").value(title))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(false))
                .andExpect(jsonPath("$.page_obj.object_list[0].can_edit").value(false));

        // 作者是作者本人时详情带 can_edit=true
        Integer indexId = findIndexIdByTitle(title, other, false);
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.can_edit").value(true))
                .andExpect(jsonPath("$.article.author_id.username").value(author.getUsername()));
    }

    @Test
    void createArticle_withoutLogin_returns401() throws Exception {
        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", "未登录文章")
                        .param("content", "内容")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createArticle_withImage_replacesPlaceholderWithMarkdownSyntax() throws Exception {
        String title = uniqueTitle("测试图片文章");
        byte[] png = minimalPng();

        mockMvc.perform(multipart("/api/article/create/")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "images", "测试图片.png", "image/png", png))
                        .param("title", title)
                        .param("content", "开头\n\n[[img_id=1]]\n\n结尾")
                        .param("image_id_mapping", "[1]")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        Integer indexId = findIndexIdByTitle(title, author, false);
        String body = mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 占位符被替换为标准 Markdown，数据库不再出现 [[img_id=
        String content = JsonPath.read(body, "$.article.content");
        assertThat(content).contains("![测试图片.png](/media/images/");
        assertThat(content).doesNotContain("[[img_id=");

        // images 列表带访问 URL，并记录文件用于清理
        List<Map<String, Object>> images = JsonPath.read(body, "$.images");
        assertThat(images).hasSize(1);
        String url = (String) ((Map<?, ?>) images.getFirst().get("content")).get("url");
        assertThat(url).startsWith("/media/images/");
        savedImages.add(url.substring("/media/images/".length()));
    }

    @Test
    void createArticle_withInvalidImageIdMapping_returns400() throws Exception {
        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", "坏映射文章")
                        .param("content", "内容")
                        .param("image_id_mapping", "[1,")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ========== 可见性与隐藏 ==========

    @Test
    void hiddenArticle_visibleOnlyToAuthorAndAdmin() throws Exception {
        String title = uniqueTitle("测试隐藏可见性");
        Integer indexId = createArticleAs(author, false, title);

        // 作者隐藏
        mockMvc.perform(post("/api/article/{id}/hide/", indexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 游客：详情 404、列表不含
        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isNotFound());
        assertThat(findIndexIdByTitleOrNull(title, null, false)).isNull();

        // 其他用户：详情 404、列表不含
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(other, false)))
                .andExpect(status().isNotFound());
        assertThat(findIndexIdByTitleOrNull(title, other, false)).isNull();

        // 作者：可见且带隐藏标记
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.is_hidden").value(true))
                .andExpect(jsonPath("$.article.can_edit").value(true));
        assertThat(findIndexIdByTitleOrNull(title, author, false)).isNotNull();

        // 管理员：可见
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.is_hidden").value(true))
                .andExpect(jsonPath("$.article.can_edit").value(true));

        // 管理员取消隐藏后，其他用户恢复可见
        mockMvc.perform(post("/api/article/{id}/unhide/", indexId)
                        .with(auth(adminUser, true)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.is_hidden").value(false));
    }

    @Test
    void hiddenArticle_appearsInListOnlyForAuthorAndAdmin() throws Exception {
        String title = uniqueTitle("测试隐藏列表");
        Integer indexId = createArticleAs(author, false, title);

        mockMvc.perform(post("/api/article/{id}/hide/", indexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        assertThat(findIndexIdByTitleOrNull(title, null, false)).isNull();
        assertThat(findIndexIdByTitleOrNull(title, other, false)).isNull();
        assertThat(findIndexIdByTitleOrNull(title, author, false)).isEqualTo(indexId);
        assertThat(findIndexIdByTitleOrNull(title, adminUser, true)).isEqualTo(indexId);
    }

    // ========== 操作权限 ==========

    @Test
    void otherUser_cannotEditDeleteOrHideForeignArticle() throws Exception {
        String title = uniqueTitle("测试越权");
        Integer indexId = createArticleAs(author, false, title);

        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", "越权修改").param("content", "越权内容")
                        .with(auth(other, false)).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/article/{id}/delete/", indexId)
                        .with(auth(other, false)).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/article/{id}/hide/", indexId)
                        .with(auth(other, false)).with(csrf()))
                .andExpect(status().isForbidden());

        // 文章原样保留
        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(title));
    }

    @Test
    void admin_canEditAndUnhideForeignArticle() throws Exception {
        String title = uniqueTitle("测试管理员操作");
        Integer indexId = createArticleAs(author, false, title);

        // 管理员编辑他人文章
        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", title + "改").param("content", "管理员修改后的内容")
                        .with(auth(adminUser, true)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(title + "改"))
                // 作者保持不变
                .andExpect(jsonPath("$.article.author_id.username").value(author.getUsername()));

        // 管理员隐藏、取消隐藏他人文章
        mockMvc.perform(post("/api/article/{id}/hide/", indexId)
                        .with(auth(adminUser, true)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/article/{id}/unhide/", indexId)
                        .with(auth(adminUser, true)).with(csrf()))
                .andExpect(status().isOk());
    }

    // ========== 删除 ==========

    @Test
    void deletedArticle_invisibleAndInoperableToEveryone() throws Exception {
        String title = uniqueTitle("测试删除");
        Integer indexId = createArticleAs(author, false, title);

        mockMvc.perform(post("/api/article/{id}/delete/", indexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 游客、作者本人、管理员均 404
        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(author, false)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(adminUser, true)))
                .andExpect(status().isNotFound());

        // 列表不含（含管理员视角）
        assertThat(findIndexIdByTitleOrNull(title, adminUser, true)).isNull();

        // 已删除文章无法再操作（管理员 unhide/delete 均 404）
        mockMvc.perform(post("/api/article/{id}/unhide/", indexId)
                        .with(auth(adminUser, true)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/article/{id}/delete/", indexId)
                        .with(auth(adminUser, true)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ========== 编辑版本 ==========

    @Test
    void editArticle_createsNewVersionInheritingCreatedAt() throws Exception {
        String title = uniqueTitle("测试编辑版本");
        Integer indexId = createArticleAs(author, false, title);

        String before = mockMvc.perform(get("/api/article/{id}/", indexId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String createdAtBefore = JsonPath.read(before, "$.article.created_at");
        String indexIdBefore = String.valueOf((Integer) JsonPath.read(before, "$.article.index_id"));

        // 等待 updated_at 与首版区分
        Thread.sleep(10);

        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", title + "V2").param("content", "第二版内容")
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        String after = mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 同一 index_id、标题更新、created_at 继承首版、updated_at 变化
        assertThat(String.valueOf((Integer) JsonPath.read(after, "$.article.index_id"))).isEqualTo(indexIdBefore);
        assertThat((String) JsonPath.read(after, "$.article.title")).isEqualTo(title + "V2");
        assertThat((String) JsonPath.read(after, "$.article.created_at")).isEqualTo(createdAtBefore);
        assertThat((String) JsonPath.read(after, "$.article.updated_at")).isNotEqualTo(createdAtBefore);
    }

    // ========== 编辑解除图片关联 ==========

    @Test
    void editArticle_unkeptImageRecordAndOldVersionQuotePreserved() throws Exception {
        // 创建带图片的文章
        String title = uniqueTitle("测试解除图片关联");
        mockMvc.perform(multipart("/api/article/create/")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "images", "旧图.png", "image/png", minimalPng()))
                        .param("title", title)
                        .param("content", "正文\n\n[[img_id=1]]")
                        .param("image_id_mapping", "[1]")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        Integer indexId = findIndexIdByTitle(title, author, false);
        Article version1 = articleMapper.selectByIndexId(indexId);
        String body = mockMvc.perform(get("/api/article/{id}/", indexId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String imageUrl = JsonPath.read(body, "$.images[0].content.url");
        String imageId = JsonPath.read(body, "$.images[0].id");
        savedImages.add(imageUrl.substring("/media/images/".length()));

        // 编辑：正文不再引用该图片，也不保留（keep_images 为空）
        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", title)
                        .param("content", "正文已删除图片引用")
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        // 新版本详情不再返回该图片
        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(0))
                .andExpect(jsonPath("$.article.content").value("正文已删除图片引用"));

        // 但图片记录和磁盘文件保留（供历史版本预览）
        Image record = imageMapper.selectById(java.util.UUID.fromString(imageId));
        assertThat(record).as("图片数据库记录应保留").isNotNull();
        assertThat(storageService.imageSize(record.getPath())).as("磁盘文件应保留").isGreaterThan(0);

        // 旧版本与该图片的关联也保留（后台预览历史版本时图片可见）
        assertThat(imageMapper.selectByArticleId(version1.getId()))
                .as("旧版本的图片关联应保留")
                .extracting(Image::getId)
                .containsExactly(java.util.UUID.fromString(imageId));
    }

    // ========== 临时文件 ==========

    @Test
    void tempFile_lifecycle_uploadListAndDelete() throws Exception {
        byte[] content = "hello-file".getBytes(StandardCharsets.UTF_8);

        // 上传
        String uploadBody = mockMvc.perform(multipart("/api/article/upload-file/")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "文档.txt", "text/plain", content))
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.filename").value("文档.txt"))
                .andExpect(jsonPath("$.file_size").value(content.length))
                .andExpect(jsonPath("$.file_url").value(org.hamcrest.Matchers.startsWith("/media/files/")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String fileId = JsonPath.read(uploadBody, "$.file_id");
        String fileUrl = JsonPath.read(uploadBody, "$.file_url");
        savedFiles.add(fileUrl.substring("/media/files/".length()));

        // 临时文件列表包含刚上传的文件
        mockMvc.perform(get("/api/article/get-temp-files/").with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.files[?(@.file_id=='" + fileId + "')].filename").value(org.hamcrest.Matchers.hasItem("文档.txt")));

        // 其他用户看不到我的临时文件
        String otherList = mockMvc.perform(get("/api/article/get-temp-files/").with(auth(other, false)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((List<?>) JsonPath.read(otherList, "$.files[?(@.file_id=='" + fileId + "')]")).isEmpty();

        // 删除临时文件
        mockMvc.perform(post("/api/article/delete-temp-file/{fileId}/", fileId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/article/get-temp-files/").with(auth(author, false)))
                .andExpect(jsonPath("$.files[?(@.file_id=='" + fileId + "')]").value(org.hamcrest.Matchers.empty()));
    }

    // ========== 搜索 ==========

    @Test
    void list_searchByTitleKeyword() throws Exception {
        String titleA = uniqueTitle("甲苹果文章");
        String titleB = uniqueTitle("乙香蕉文章");
        createArticleAs(author, false, titleA);
        createArticleAs(author, false, titleB);

        mockMvc.perform(get("/api/article/").param("search", "苹果"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].title").value(titleA));
    }

    // ========== 辅助方法 ==========

    /** 创建测试用户（用户名/学号/邮箱带随机后缀避免唯一冲突） */
    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "_" + suffix);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname(prefix);
        user.setEmail(prefix + "_" + suffix + "@test.example.com");
        user.setStudentNumber(String.format("99%08d", Math.abs(suffix.hashCode()) % 100000000));
        user.setIsEnabled(true);
        return userService.create(user);
    }

    /** 以指定用户身份调用创建文章接口并返回 index_id */
    private Integer createArticleAs(User user, boolean admin, String title) throws Exception {
        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", title)
                        .param("content", "正文内容 " + title)
                        .with(auth(user, admin))
                        .with(csrf()))
                .andExpect(status().isOk());
        Integer indexId = findIndexIdByTitleOrNull(title, user, admin);
        assertThat(indexId).as("创建后的文章应出现在列表中").isNotNull();
        return indexId;
    }

    /** 按标题搜索并返回 index_id；找不到返回 null */
    private Integer findIndexIdByTitleOrNull(String title, User user, boolean admin) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/article/").param("search", title);
        if (user != null) {
            request = request.with(auth(user, admin));
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = JsonPath.read(body, "$.page_obj.object_list[?(@.title=='" + title + "')].index_id");
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Integer findIndexIdByTitle(String title, User user, boolean admin) throws Exception {
        Integer id = findIndexIdByTitleOrNull(title, user, admin);
        assertThat(id).isNotNull();
        return id;
    }

    /** 模拟登录用户：把 CustomUserDetails 放入请求线程的 SecurityContext */
    private RequestPostProcessor auth(User user, boolean admin) {
        CustomUserDetails details = new CustomUserDetails(
                user.getId(), user.getUsername(), user.getPassword(), true, admin, List.of());
        return SecurityMockMvcRequestPostProcessors.user(details);
    }

    /**
     * 满足 CsrfFilter 校验：CookieCsrfTokenRepository 从 XSRF-TOKEN Cookie 读取 token，
     * SpaCsrfTokenRequestHandler 对 X-XSRF-TOKEN 头按明文校验，两者同值即可通过
     */
    private RequestPostProcessor csrf() {
        return request -> {
            request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "test-csrf-token"));
            request.addHeader("X-XSRF-TOKEN", "test-csrf-token");
            return request;
        };
    }

    private String uniqueTitle(String prefix) {
        return prefix + "_" + System.nanoTime() % 100000000;
    }

    /** 最小合法 PNG 文件（1x1 像素） */
    private byte[] minimalPng() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D,
                0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0,
                0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0, 0, 0, 0x0A,
                0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0, 1, 0, 0, 5, 0, 1,
                0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0,
                0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}

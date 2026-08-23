package org.example.blog;

import com.jayway.jsonpath.JsonPath;
import org.example.blog.dao.User;
import org.example.blog.service.CustomUserDetails;
import org.example.blog.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员后端接口集成测试
 * <p>
 * 通过 MockMvc 走完整的过滤器链（含 Spring Security），验证：
 * <ul>
 *   <li>访问控制：仅 is_staff / is_admin 可查询，仅 is_admin 可修改</li>
 *   <li>用户增删改：格式/唯一性约束、自我保护（不能降级/禁用/删除自己）</li>
 *   <li>文章/评论增删查改：含已删除/已隐藏数据的过滤、软删除恢复、编辑生成新版本且作者不变</li>
 * </ul>
 * {@code @Transactional} 使每个测试的数据库写入自动回滚。
 * 需要本地 PostgreSQL 和 Redis 运行（与开发环境相同配置）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User normalUser;
    private User staffUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        normalUser = newUser("normal");
        staffUser = newUser("staff");
        adminUser = newUser("admin");
    }

    // ========== 访问控制 ==========

    @Test
    void accessControl_anonymous_cannotRead() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/articles")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/comments")).andExpect(status().isForbidden());
    }

    @Test
    void accessControl_normalUser_cannotReadOrWrite() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(auth(normalUser, false, false)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/{id}", normalUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"x\"}")
                        .with(auth(normalUser, false, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessControl_staffUser_canReadButNotWrite() throws Exception {
        // 查询允许
        mockMvc.perform(get("/api/admin/users").with(auth(staffUser, false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj").exists());
        mockMvc.perform(get("/api/admin/articles").with(auth(staffUser, false, true)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/comments").with(auth(staffUser, false, true)))
                .andExpect(status().isOk());

        // 修改拒绝（非 is_admin）
        mockMvc.perform(patch("/api/admin/articles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_hidden\":true}")
                        .with(auth(staffUser, false, true))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/{id}", normalUser.getId())
                        .with(auth(staffUser, false, true))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ========== 用户管理 ==========

    @Test
    void createUser_byAdmin_succeedsAndSearchable() throws Exception {
        String username = "created_" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\","
                                + "\"email\":\"" + username + "@test.example.com\","
                                + "\"student_number\":\"9800000001\","
                                + "\"password\":\"123456\","
                                + "\"nickname\":\"管理员创建的用户\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.is_enabled").value(true))
                .andExpect(jsonPath("$.user.is_staff").value(false));

        // 搜索可找到
        mockMvc.perform(get("/api/admin/users").param("search", username)
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].username").value(username));
    }

    @Test
    void createUser_duplicateUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + normalUser.getUsername() + "\","
                                + "\"email\":\"unique1@test.example.com\","
                                + "\"student_number\":\"9800000002\","
                                + "\"password\":\"123456\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"valid_user_1\","
                                + "\"email\":\"not-an-email\","
                                + "\"student_number\":\"9800000003\","
                                + "\"password\":\"123456\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_byAdmin_changesFields() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", normalUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"管理员改的昵称\",\"is_staff\":true}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("管理员改的昵称"))
                .andExpect(jsonPath("$.user.is_staff").value(true));

        // 详情接口确认持久化
        mockMvc.perform(get("/api/admin/users/{id}", normalUser.getId())
                        .with(auth(staffUser, false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("管理员改的昵称"))
                .andExpect(jsonPath("$.user.is_staff").value(true));
    }

    @Test
    void updateUser_emailTakenByOther_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", normalUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + staffUser.getEmail() + "\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_selfDemoteAdmin_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", adminUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_admin\":false}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_selfDisable_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", adminUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_enabled\":false}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"x\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_self_returns400() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", adminUser.getId())
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUser_byAdmin_removesUserWithArticlesAndComments() throws Exception {
        // 目标用户拥有一篇文章和一条评论
        String title = uniqueTitle("待删用户文章");
        Integer articleIndexId = createArticleAs(normalUser, title);
        createCommentAs(normalUser, articleIndexId, "待删用户的评论_" + System.nanoTime() % 100000000);

        mockMvc.perform(delete("/api/admin/users/{id}", normalUser.getId())
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 用户不存在
        mockMvc.perform(get("/api/admin/users/{id}", normalUser.getId())
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isNotFound());
        // 文章随用户级联删除（管理员全量列表也搜不到）
        assertThat(findAdminArticleIndexIdByTitle(title)).isNull();
        // 公开接口同样 404
        mockMvc.perform(get("/api/article/{id}/", articleIndexId))
                .andExpect(status().isNotFound());
    }

    // ========== 文章管理 ==========

    @Test
    void listArticles_includesDeletedAndHidden_withFilters() throws Exception {
        // 用唯一后缀搜索，避免命中本地开发库中的既有数据
        String suffix = String.valueOf(System.nanoTime() % 100000000);
        createArticleAs(normalUser, "可见文章_" + suffix);
        Integer hiddenIndexId = createArticleAs(normalUser, "隐藏文章_" + suffix);
        Integer deletedIndexId = createArticleAs(normalUser, "已删文章_" + suffix);
        hideArticleAs(normalUser, hiddenIndexId);
        deleteArticleAs(normalUser, deletedIndexId);

        // 不带过滤：三种状态都能看到
        mockMvc.perform(get("/api/admin/articles").param("search", suffix)
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(3));

        // deleted=true：只剩已删除文章
        mockMvc.perform(get("/api/admin/articles").param("search", suffix)
                        .param("deleted", "true")
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_deleted").value(true));

        // hidden=true 且 deleted=false：只剩已隐藏未删除文章
        mockMvc.perform(get("/api/admin/articles").param("search", suffix)
                        .param("deleted", "false").param("hidden", "true")
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true));
    }

    @Test
    void updateArticle_hideByAdmin_hiddenFromPublic() throws Exception {
        Integer indexId = createArticleAs(normalUser, uniqueTitle("管理员隐藏操作"));

        mockMvc.perform(patch("/api/admin/articles/{id}", indexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_hidden\":true}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.is_hidden").value(true));

        // 其他用户视角不可见
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(staffUser, false, true)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateArticle_restoreDeletedByAdmin_visibleAgain() throws Exception {
        String title = uniqueTitle("管理员恢复文章");
        Integer indexId = createArticleAs(normalUser, title);
        deleteArticleAs(normalUser, indexId);
        mockMvc.perform(get("/api/article/{id}/", indexId)).andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/articles/{id}", indexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_deleted\":false}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.is_deleted").value(false));

        // 公开恢复可见
        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(title));
    }

    @Test
    void updateArticle_editByAdmin_createsNewVersionAndKeepsAuthor() throws Exception {
        String title = uniqueTitle("管理员编辑原文");
        Integer indexId = createArticleAs(normalUser, title);
        String newTitle = uniqueTitle("管理员编辑后");

        mockMvc.perform(patch("/api/admin/articles/{id}", indexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + newTitle + "\",\"content\":\"管理员改写的内容\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(newTitle))
                .andExpect(jsonPath("$.article.author.username").value(normalUser.getUsername()));

        // 公开详情：新标题，作者仍是原作者
        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(newTitle))
                .andExpect(jsonPath("$.article.author_id.username").value(normalUser.getUsername()));

        // 详情接口返回正文与图片/文件列表
        mockMvc.perform(get("/api/admin/articles/{id}", indexId)
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.content").value("管理员改写的内容"))
                .andExpect(jsonPath("$.article.images").isArray())
                .andExpect(jsonPath("$.article.files").isArray());
    }

    @Test
    void updateArticle_editHiddenArticle_keepsHiddenState() throws Exception {
        Integer indexId = createArticleAs(normalUser, uniqueTitle("编辑隐藏文章"));
        hideArticleAs(normalUser, indexId);
        String newTitle = uniqueTitle("编辑隐藏文章后");

        // 仅改标题：隐藏状态不应丢失
        mockMvc.perform(patch("/api/admin/articles/{id}", indexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + newTitle + "\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.is_hidden").value(true))
                .andExpect(jsonPath("$.article.title").value(newTitle));
    }

    @Test
    void updateArticle_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/admin/articles/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_hidden\":true}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ========== 评论管理 ==========

    @Test
    void listComments_includesDeletedAndHidden_withFilters() throws Exception {
        // 用唯一后缀搜索，避免命中本地开发库中的既有数据
        String suffix = String.valueOf(System.nanoTime() % 100000000);
        Integer articleIndexId = createArticleAs(normalUser, uniqueTitle("管理员评论列表"));
        createCommentAs(normalUser, articleIndexId, "普通评论_" + suffix);
        Integer hiddenId = createCommentAs(normalUser, articleIndexId, "隐藏评论_" + suffix);
        Integer deletedId = createCommentAs(normalUser, articleIndexId, "删除评论_" + suffix);
        hideCommentAs(normalUser, hiddenId);
        deleteCommentAs(normalUser, deletedId);

        // 不带过滤：三种状态都能看到
        mockMvc.perform(get("/api/admin/comments").param("search", suffix)
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(3));

        // deleted=true：只剩已删除评论
        mockMvc.perform(get("/api/admin/comments").param("search", suffix)
                        .param("deleted", "true")
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_deleted").value(true));

        // hidden=true 且 deleted=false：只剩已隐藏未删除评论，且带文章标题
        mockMvc.perform(get("/api/admin/comments").param("search", suffix)
                        .param("deleted", "false").param("hidden", "true")
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true))
                .andExpect(jsonPath("$.page_obj.object_list[0].article_title").isNotEmpty());
    }

    @Test
    void updateComment_editByAdmin_createsNewVersionAndKeepsAuthor() throws Exception {
        Integer articleIndexId = createArticleAs(normalUser, uniqueTitle("管理员编辑评论"));
        String originalContent = "原始评论内容_" + System.nanoTime() % 100000000;
        Integer commentId = createCommentAs(normalUser, articleIndexId, originalContent);

        mockMvc.perform(patch("/api/admin/comments/{id}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"管理员改写的评论\"}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.content").value("管理员改写的评论"))
                .andExpect(jsonPath("$.comment.author.username").value(normalUser.getUsername()));

        // 公开列表：新内容，作者仍是原作者
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("管理员改写的评论"))
                .andExpect(jsonPath("$.page_obj.object_list[0].author.username")
                        .value(normalUser.getUsername()));
    }

    @Test
    void updateComment_hideAndUnhideByAdmin() throws Exception {
        Integer articleIndexId = createArticleAs(normalUser, uniqueTitle("管理员隐藏评论"));
        Integer commentId = createCommentAs(normalUser, articleIndexId, "将被隐藏的评论_" + System.nanoTime() % 100000000);

        // 隐藏：游客不可见
        mockMvc.perform(patch("/api/admin/comments/{id}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_hidden\":true}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.is_hidden").value(true));
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        // 取消隐藏：恢复可见
        mockMvc.perform(patch("/api/admin/comments/{id}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_hidden\":false}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.is_hidden").value(false));
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1));
    }

    @Test
    void updateComment_restoreDeletedByAdmin_visibleAgain() throws Exception {
        Integer articleIndexId = createArticleAs(normalUser, uniqueTitle("管理员恢复评论"));
        Integer commentId = createCommentAs(normalUser, articleIndexId, "将被删除的评论_" + System.nanoTime() % 100000000);
        deleteCommentAs(normalUser, commentId);
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        mockMvc.perform(patch("/api/admin/comments/{id}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_deleted\":false}")
                        .with(auth(adminUser, true, true))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.is_deleted").value(false));

        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1));
    }

    // ========== 辅助方法 ==========

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "_" + suffix);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname(prefix);
        user.setEmail(prefix + "_" + suffix + "@test.example.com");
        user.setStudentNumber(String.format("97%08d", Math.abs(suffix.hashCode()) % 100000000));
        user.setIsEnabled(true);
        return userService.create(user);
    }

    /** 以指定用户身份创建文章并返回 index_id */
    private Integer createArticleAs(User user, String title) throws Exception {
        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", title)
                        .param("content", "正文内容 " + title)
                        .with(auth(user, false, false))
                        .with(csrf()))
                .andExpect(status().isOk());
        Integer indexId = findAdminArticleIndexIdByTitle(title);
        assertThat(indexId).as("创建后的文章应出现在管理员列表中").isNotNull();
        return indexId;
    }

    /** 以指定用户身份创建评论并返回评论 index_id */
    private Integer createCommentAs(User user, Integer articleIndexId, String content) throws Exception {
        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}")
                        .with(auth(user, false, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        // 通过管理员全量列表定位该评论
        String body = mockMvc.perform(get("/api/admin/comments").param("search", content)
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = JsonPath.read(body,
                "$.page_obj.object_list[?(@.content=='" + content + "')].index_id");
        assertThat(ids).as("创建后的评论应出现在管理员列表中").isNotEmpty();
        return ids.getFirst();
    }

    private void hideArticleAs(User user, Integer articleIndexId) throws Exception {
        mockMvc.perform(post("/api/article/{id}/hide/", articleIndexId)
                        .with(auth(user, false, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    private void deleteArticleAs(User user, Integer articleIndexId) throws Exception {
        mockMvc.perform(post("/api/article/{id}/delete/", articleIndexId)
                        .with(auth(user, false, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    private void hideCommentAs(User user, Integer commentIndexId) throws Exception {
        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(user, false, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    private void deleteCommentAs(User user, Integer commentIndexId) throws Exception {
        mockMvc.perform(post("/api/comment/delete/{id}/", commentIndexId)
                        .with(auth(user, false, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    /** 在管理员文章列表中按标题查找 index_id；找不到返回 null */
    private Integer findAdminArticleIndexIdByTitle(String title) throws Exception {
        String body = mockMvc.perform(get("/api/admin/articles").param("search", title)
                        .with(auth(adminUser, true, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = JsonPath.read(body,
                "$.page_obj.object_list[?(@.title=='" + title + "')].index_id");
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** 以指定身份构造认证（角色与 is_staff / is_admin 字段的映射关系一致） */
    private RequestPostProcessor auth(User user, boolean admin, boolean staff) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (staff) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
        }
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        CustomUserDetails details = new CustomUserDetails(
                user.getId(), user.getUsername(), user.getPassword(), true, admin, authorities);
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
}

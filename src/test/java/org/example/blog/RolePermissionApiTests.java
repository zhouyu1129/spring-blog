package org.example.blog;

import org.example.blog.dao.Role;
import org.example.blog.dao.User;
import org.example.blog.mapper.RoleMapper;
import org.example.blog.mapper.UserMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色-权限体系集成测试
 * <p>
 * 验证权限判定规则「（拥有正面权限 且 无对应负面权限）或 管理员」：
 * <ul>
 *   <li>注册默认获得 user 角色（创建文章/评论、修改自己的文章/评论）</li>
 *   <li>muted 角色（负面权限）禁止创建文章/评论；角色过期后禁令失效</li>
 *   <li>observer 角色可查看任何隐藏文章/隐藏评论</li>
 *   <li>moderator 角色可修改任何文章/评论（新版本保留原作者）</li>
 *   <li>管理员直通一切权限（含负面权限场景）</li>
 * </ul>
 * {@code @Transactional} 使每个测试的数据库写入自动回滚。
 * 需要本地 PostgreSQL 和 Redis 运行（与开发环境相同配置）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RolePermissionApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User author;
    private User other;
    private User adminUser;

    @BeforeEach
    void setUp() {
        author = newUser("author");
        other = newUser("other");
        adminUser = newUser("admin");
    }

    // ========== 注册默认角色 ==========

    @Test
    void register_defaultsToUserRole_canCreateArticleAndComment() {
        // userService.create 自动分配 user 角色
        List<Role> roles = userMapper.selectRolesByUserId(author.getId());
        assertThat(roles).extracting(Role::getRoleName).contains("user");

        // user 角色自带创建文章/评论权限（走完整 HTTP 链路验证）
        try {
            String title = uniqueTitle("默认角色文章");
            mockMvc.perform(multipart("/api/article/create/")
                            .param("title", title)
                            .param("content", "正文")
                            .with(auth(author, false))
                            .with(csrf()))
                    .andExpect(status().isOk());

            Integer indexId = findAdminArticleIndexIdByTitle(title);
            mockMvc.perform(post("/api/comment/{id}/create/", indexId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"默认角色评论\"}")
                            .with(auth(author, false))
                            .with(csrf()))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 禁止发言（负面权限） ==========

    @Test
    void muted_cannotCreateArticleOrComment() throws Exception {
        assignRole(author, "muted", null);

        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", uniqueTitle("禁言文章"))
                        .param("content", "正文")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Integer indexId = createArticleAs(other, uniqueTitle("禁言目标文章"));
        mockMvc.perform(post("/api/comment/{id}/create/", indexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"禁言评论\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void muted_expired_canCreateAgain() throws Exception {
        // 已过期的 muted 角色：负面权限不参与计算，恢复创建能力
        assignRole(author, "muted", LocalDateTime.now().minusHours(1));

        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", uniqueTitle("过期禁言文章"))
                        .param("content", "正文")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void muted_expiresInFuture_stillBlocked() throws Exception {
        assignRole(author, "muted", LocalDateTime.now().plusDays(1));

        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", uniqueTitle("未来禁言文章"))
                        .param("content", "正文")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void muted_adminBypassesNegativePermission() throws Exception {
        // 管理员即使被赋予 muted 角色也直通
        assignRole(adminUser, "muted", null);

        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", uniqueTitle("管理员禁言文章"))
                        .param("content", "正文")
                        .with(auth(adminUser, true))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ========== 无角色用户 ==========

    @Test
    void userWithoutRoles_cannotCreateArticle() throws Exception {
        // 清空全部角色：没有正面权限 → 拒绝
        userMapper.deleteAllUserRoles(author.getId());

        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", uniqueTitle("无角色文章"))
                        .param("content", "正文")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ========== 观测者（查看隐藏内容） ==========

    @Test
    void observer_viewsHiddenArticle() throws Exception {
        String title = uniqueTitle("观测者隐藏文章");
        Integer indexId = createArticleAs(author, title);
        hideArticleAs(author, indexId);

        // 普通其他用户：不可见
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(other, false)))
                .andExpect(status().isNotFound());

        // 观测者：详情与列表均可见
        assignRole(other, "observer", null);
        mockMvc.perform(get("/api/article/{id}/", indexId).with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(title))
                .andExpect(jsonPath("$.article.is_hidden").value(true));

        mockMvc.perform(get("/api/article/").param("search", title).with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true));
    }

    @Test
    void observer_viewsHiddenComment() throws Exception {
        Integer indexId = createArticleAs(author, uniqueTitle("观测者隐藏评论文章"));
        String content = "待隐藏评论_" + System.nanoTime() % 100000000;
        Integer commentId = createCommentAs(author, indexId, content);
        hideCommentAs(author, commentId);

        // 普通其他用户：看不到
        mockMvc.perform(get("/api/comment/{id}/{page}/", indexId, 1).with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        // 观测者：可见
        assignRole(other, "observer", null);
        mockMvc.perform(get("/api/comment/{id}/{page}/", indexId, 1).with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true));
    }

    // ========== 版主（修改任何文章/评论） ==========

    @Test
    void moderator_editsAnyArticle_keepsOriginalAuthor() throws Exception {
        Integer indexId = createArticleAs(author, uniqueTitle("版主编辑文章"));
        String newTitle = uniqueTitle("版主编辑后");

        // 普通用户不能编辑他人文章
        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", newTitle)
                        .param("content", "版主改写内容")
                        .with(auth(other, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // 版主可以
        assignRole(other, "moderator", null);
        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", newTitle)
                        .param("content", "版主改写内容")
                        .with(auth(other, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/article/{id}/", indexId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value(newTitle))
                .andExpect(jsonPath("$.article.author_id.username").value(author.getUsername()))
                .andExpect(jsonPath("$.article.can_edit").value(false));
    }

    @Test
    void moderator_editsAnyComment_keepsOriginalAuthor() throws Exception {
        Integer indexId = createArticleAs(author, uniqueTitle("版主编辑评论文章"));
        Integer commentId = createCommentAs(author, indexId, "待版主编辑的评论");

        // 普通用户不能编辑他人评论
        mockMvc.perform(post("/api/comment/update/{id}/", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"普通用户改写\"}")
                        .with(auth(other, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // 版主可以
        assignRole(other, "moderator", null);
        mockMvc.perform(post("/api/comment/update/{id}/", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"版主改写评论\"}")
                        .with(auth(other, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/comment/{id}/{page}/", indexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("版主改写评论"))
                .andExpect(jsonPath("$.page_obj.object_list[0].author.username").value(author.getUsername()));
    }

    // ========== 用户自己的内容（own 权限） ==========

    @Test
    void user_editsOwnArticleAndComment() throws Exception {
        Integer indexId = createArticleAs(author, uniqueTitle("own 权限文章"));
        Integer commentId = createCommentAs(author, indexId, "own 权限评论");

        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", uniqueTitle("own 权限文章改"))
                        .param("content", "自己改写")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comment/update/{id}/", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"自己改写评论\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutUserRole_cannotEditOwnArticle() throws Exception {
        // 清空角色：没有 article:update:own，即使是作者也不能编辑
        Integer indexId = createArticleAs(author, uniqueTitle("清角色编辑文章"));
        userMapper.deleteAllUserRoles(author.getId());

        mockMvc.perform(multipart("/api/article/{id}/edit/", indexId)
                        .param("title", uniqueTitle("清角色编辑文章改"))
                        .param("content", "内容")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ========== 辅助方法 ==========

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "_" + suffix);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname(prefix);
        user.setEmail(prefix + "_" + suffix + "@test.example.com");
        user.setStudentNumber(String.format("96%08d", Math.abs(suffix.hashCode()) % 100000000));
        user.setIsEnabled(true);
        return userService.create(user);
    }

    /** 给用户分配角色（expiresAt 为 null 表示永久） */
    private void assignRole(User user, String roleName, LocalDateTime expiresAt) {
        Role role = roleMapper.selectByRoleName(roleName);
        assertThat(role).as("系统预置角色应存在: " + roleName).isNotNull();
        userMapper.insertUserRole(user.getId(), role.getId(), expiresAt);
    }

    /** 以指定用户身份创建文章并返回 index_id（通过管理员全量列表定位） */
    private Integer createArticleAs(User user, String title) throws Exception {
        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", title)
                        .param("content", "正文内容 " + title)
                        .with(auth(user, user.equals(adminUser)))
                        .with(csrf()))
                .andExpect(status().isOk());
        Integer indexId = findAdminArticleIndexIdByTitle(title);
        assertThat(indexId).as("创建后的文章应出现在管理员列表中").isNotNull();
        return indexId;
    }

    /** 以指定用户身份创建评论并返回评论 index_id（通过管理员全量列表定位） */
    private Integer createCommentAs(User user, Integer articleIndexId, String content) throws Exception {
        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}")
                        .with(auth(user, user.equals(adminUser)))
                        .with(csrf()))
                .andExpect(status().isOk());
        return findAdminCommentIndexIdByContent(content);
    }

    private void hideArticleAs(User user, Integer articleIndexId) throws Exception {
        mockMvc.perform(post("/api/article/{id}/hide/", articleIndexId)
                        .with(auth(user, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    private void hideCommentAs(User user, Integer commentIndexId) throws Exception {
        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(user, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    private Integer findAdminArticleIndexIdByTitle(String title) throws Exception {
        String body = mockMvc.perform(get("/api/admin/articles").param("search", title)
                        .with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body,
                "$.page_obj.object_list[?(@.title=='" + title + "')].index_id");
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Integer findAdminCommentIndexIdByContent(String content) throws Exception {
        String body = mockMvc.perform(get("/api/admin/comments").param("search", content)
                        .with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body,
                "$.page_obj.object_list[?(@.content=='" + content + "')].index_id");
        assertThat(ids).as("创建后的评论应出现在管理员列表中").isNotEmpty();
        return ids.getFirst();
    }

    /** 以指定身份构造认证 */
    private RequestPostProcessor auth(User user, boolean admin) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        CustomUserDetails details = new CustomUserDetails(
                user.getId(), user.getUsername(), user.getPassword(), true, admin, authorities);
        return SecurityMockMvcRequestPostProcessors.user(details);
    }

    /** 满足 CsrfFilter 校验：Cookie 与请求头同值即可通过 */
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

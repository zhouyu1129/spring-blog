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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论模块接口集成测试
 * <p>
 * 通过 MockMvc 走完整的过滤器链（含 Spring Security），验证：
 * 评论的创建/列表/修改（新版本机制）/删除（软删除）/隐藏（新版本继承隐藏状态）以及作者、
 * 其他用户、管理员、游客的角色权限，另覆盖评论列表分页、隐藏文章和隐藏评论的可见性、用户主页的评论列表。
 * <p>
 * {@code @Transactional} 使每个测试的数据库写入自动回滚。
 * 需要本地 PostgreSQL 和 Redis 运行（与开发环境相同配置）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

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

    // ========== 创建 ==========

    @Test
    void createComment_byLoggedInUser_succeedsAndVisibleInList() throws Exception {
        Integer articleIndexId = createArticleAs(author, "测试评论文章");

        createCommentAs(author, false, articleIndexId, "第一条评论");
        Thread.sleep(2);
        createCommentAs(author, false, articleIndexId, "第二条评论");

        // 列表返回 article + page_obj 结构，评论按时间倒序
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.index_id").value(articleIndexId))
                .andExpect(jsonPath("$.article.title").value("测试评论文章"))
                .andExpect(jsonPath("$.article.author_id.username").value(author.getUsername()))
                .andExpect(jsonPath("$.page_obj.number").value(1))
                .andExpect(jsonPath("$.page_obj.paginator.num_pages").value(1))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(2))
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("第二条评论"))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(false))
                .andExpect(jsonPath("$.page_obj.object_list[0].top").value(false))
                .andExpect(jsonPath("$.page_obj.object_list[0].author.id").value(author.getId().toString()))
                .andExpect(jsonPath("$.page_obj.object_list[0].author.username").value(author.getUsername()))
                .andExpect(jsonPath("$.page_obj.object_list[1].content").value("第一条评论"));
    }

    @Test
    void createComment_withoutLogin_returns401() throws Exception {
        Integer articleIndexId = createArticleAs(author, "未登录评论文章");

        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"游客评论\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void createComment_articleNotFound_returns404() throws Exception {
        // service 抛出的 ResponseStatusException 由容器渲染错误页，MockMvc 下只断言状态码
        mockMvc.perform(post("/api/comment/{id}/create/", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"评论\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createComment_onDeletedArticle_returns404() throws Exception {
        Integer articleIndexId = createArticleAs(author, "已删除文章");

        mockMvc.perform(post("/api/article/{id}/delete/", articleIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"评论已删除文章\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createComment_blankContent_returns400() throws Exception {
        Integer articleIndexId = createArticleAs(author, "空评论文章");

        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("评论内容不能为空"));
    }

    @Test
    void createComment_onHiddenArticle_onlyAuthorAndAdminCanComment() throws Exception {
        Integer articleIndexId = createArticleAs(author, "隐藏文章评论");
        hideArticleAs(author, articleIndexId);

        // 作者本人可评论
        createCommentAs(author, false, articleIndexId, "作者对隐藏文章的评论");

        // 其他用户不可见（404）
        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"他人评论\"}")
                        .with(auth(other, false))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // 管理员可评论
        createCommentAs(adminUser, true, articleIndexId, "管理员对隐藏文章的评论");
    }

    // ========== 列表 ==========

    @Test
    void listComments_articleNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/comment/{id}/{page}/", 999999, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("文章不存在"));
    }

    @Test
    void listComments_paginatesAtPageSize10() throws Exception {
        Integer articleIndexId = createArticleAs(author, "分页评论文章");

        for (int i = 1; i <= 11; i++) {
            createCommentAs(author, false, articleIndexId, "第 " + i + " 条评论");
            Thread.sleep(2);
        }

        // 第 1 页 10 条
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.number").value(1))
                .andExpect(jsonPath("$.page_obj.paginator.num_pages").value(2))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(10));

        // 第 2 页 1 条
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.number").value(2))
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("第 1 条评论"));
    }

    @Test
    void listComments_onHiddenArticle_visibleOnlyToAuthorAndAdmin() throws Exception {
        Integer articleIndexId = createArticleAs(author, "隐藏文章可见性");
        createCommentAs(author, false, articleIndexId, "隐藏文章里的评论");
        hideArticleAs(author, articleIndexId);

        // 游客 404
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isNotFound());

        // 其他用户 404
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(other, false)))
                .andExpect(status().isNotFound());

        // 作者可见
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("隐藏文章里的评论"));

        // 管理员可见
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1));
    }

    // ========== 修改（新版本机制） ==========

    @Test
    void updateComment_createsNewVersionPreservingCreatedTime() throws Exception {
        Integer articleIndexId = createArticleAs(author, "评论版本文章");
        createCommentAs(author, false, articleIndexId, "第一版内容");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "第一版内容");
        assertThat(commentIndexId).isNotNull();

        String before = listCommentsBody(articleIndexId, 1);
        String createTime = JsonPath.read(before, "$.page_obj.object_list[0].create_time");
        String updateTime = JsonPath.read(before, "$.page_obj.object_list[0].update_time");

        // 等待 updated_at 与首版区分
        Thread.sleep(10);

        mockMvc.perform(post("/api/comment/update/{id}/", commentIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第二版内容\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("评论修改成功"));

        String after = listCommentsBody(articleIndexId, 1);

        // 同一 index_id：内容更新、create_time 继承首版、update_time 变化
        assertThat((Integer) JsonPath.read(after, "$.page_obj.object_list[0].index_id")).isEqualTo(commentIndexId);
        assertThat((String) JsonPath.read(after, "$.page_obj.object_list[0].content")).isEqualTo("第二版内容");
        assertThat((String) JsonPath.read(after, "$.page_obj.object_list[0].create_time")).isEqualTo(createTime);
        assertThat((String) JsonPath.read(after, "$.page_obj.object_list[0].update_time")).isNotEqualTo(updateTime);

        // 旧版本被去重，列表中该评论只出现一次
        List<?> versions = JsonPath.read(after, "$.page_obj.object_list[?(@.index_id==" + commentIndexId + ")]");
        assertThat(versions).hasSize(1);
    }

    @Test
    void updateComment_withoutLogin_returns401() throws Exception {
        Integer articleIndexId = createArticleAs(author, "未登录修改文章");

        mockMvc.perform(post("/api/comment/update/{id}/", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"游客修改\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void updateComment_byNonAuthor_returns403() throws Exception {
        Integer articleIndexId = createArticleAs(author, "越权修改文章");
        createCommentAs(author, false, articleIndexId, "作者的评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "作者的评论");

        mockMvc.perform(post("/api/comment/update/{id}/", commentIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权修改内容\"}")
                        .with(auth(other, false))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // 原评论未被改动
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("作者的评论"));
    }

    @Test
    void updateComment_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/comment/update/{id}/", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"内容\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ========== 删除 ==========

    @Test
    void deleteComment_byAuthor_softDeletesAndBlocksFurtherEdit() throws Exception {
        Integer articleIndexId = createArticleAs(author, "删除评论文章");
        createCommentAs(author, false, articleIndexId, "将被删除的评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "将被删除的评论");

        mockMvc.perform(post("/api/comment/delete/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("评论已删除"));

        // 软删除后列表不可见（游客视角）
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        // 软删除后作者也无法再编辑/删除/隐藏
        mockMvc.perform(post("/api/comment/update/{id}/", commentIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"编辑已删除的评论\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/comment/delete/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteComment_withoutLogin_returns401() throws Exception {
        mockMvc.perform(post("/api/comment/delete/{id}/", 1)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void deleteComment_byNonAuthor_returns403() throws Exception {
        Integer articleIndexId = createArticleAs(author, "越权删除文章");
        createCommentAs(author, false, articleIndexId, "不能被他人删除的评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "不能被他人删除的评论");

        mockMvc.perform(post("/api/comment/delete/{id}/", commentIndexId)
                        .with(auth(other, false)).with(csrf()))
                .andExpect(status().isForbidden());

        // 评论原样保留
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("不能被他人删除的评论"));
    }

    // ========== 隐藏 / 取消隐藏 ==========

    @Test
    void hideComment_visibleOnlyToAuthorAndAdmin() throws Exception {
        Integer articleIndexId = createArticleAs(author, "隐藏评论可见性文章");
        createCommentAs(author, false, articleIndexId, "将被隐藏的评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "将被隐藏的评论");

        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("评论已隐藏"));

        // 游客不可见
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        // 其他用户不可见
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        // 作者可见且带 is_hidden 标记
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("将被隐藏的评论"))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true));

        // 管理员可见
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true));
    }

    @Test
    void hideComment_byNonAuthor_returns403() throws Exception {
        Integer articleIndexId = createArticleAs(author, "越权隐藏评论文章");
        createCommentAs(author, false, articleIndexId, "不能被他人隐藏的评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "不能被他人隐藏的评论");

        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(other, false)).with(csrf()))
                .andExpect(status().isForbidden());

        // 评论仍然公开可见
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(false));
    }

    @Test
    void hideComment_withoutLogin_returns401() throws Exception {
        mockMvc.perform(post("/api/comment/hide/{id}/", 1)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void unhideComment_visibleToAllAgain() throws Exception {
        Integer articleIndexId = createArticleAs(author, "取消隐藏评论文章");
        createCommentAs(author, false, articleIndexId, "将被取消隐藏的评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "将被取消隐藏的评论");

        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comment/unhide/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("评论已取消隐藏"));

        // 游客重新可见，is_hidden 恢复为 false
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(false));
    }

    @Test
    void editHiddenComment_newVersionInheritsHidden() throws Exception {
        Integer articleIndexId = createArticleAs(author, "隐藏评论编辑文章");
        createCommentAs(author, false, articleIndexId, "隐藏前内容");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "隐藏前内容");

        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        // 作者编辑隐藏评论：插入新版本
        mockMvc.perform(post("/api/comment/update/{id}/", commentIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"隐藏后编辑内容\"}")
                        .with(auth(author, false))
                        .with(csrf()))
                .andExpect(status().isOk());

        // 游客仍不可见（新版本继承隐藏状态）
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(0));

        // 作者可见新内容且仍为隐藏
        mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, 1)
                        .with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.page_obj.object_list[0].content").value("隐藏后编辑内容"))
                .andExpect(jsonPath("$.page_obj.object_list[0].is_hidden").value(true));
    }

    // ========== 用户主页 ==========

    @Test
    void userProfile_includesCommentsWithArticle() throws Exception {
        Integer articleIndexId = createArticleAs(author, "主页评论所属文章");
        createCommentAs(author, false, articleIndexId, "主页里展示的评论");

        mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment_page_obj.number").value(1))
                .andExpect(jsonPath("$.comment_page_obj.paginator.num_pages").value(1))
                .andExpect(jsonPath("$.comment_page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.comment_page_obj.object_list[0].content").value("主页里展示的评论"))
                .andExpect(jsonPath("$.comment_page_obj.object_list[0].top").value(false))
                .andExpect(jsonPath("$.comment_page_obj.object_list[0].author.id").value(author.getId().toString()))
                .andExpect(jsonPath("$.comment_page_obj.object_list[0].article.index_id").value(articleIndexId))
                .andExpect(jsonPath("$.comment_page_obj.object_list[0].article.title").value("主页评论所属文章"));
    }

    @Test
    void userProfile_excludesCommentsOfDeletedArticle() throws Exception {
        Integer articleIndexId = createArticleAs(author, "将被删除的主页文章");
        createCommentAs(author, false, articleIndexId, "随文章消失的评论");

        mockMvc.perform(post("/api/article/{id}/delete/", articleIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment_page_obj.object_list.length()").value(0));
    }

    @Test
    void userProfile_hiddenArticleVisibleOnlyToOwnerAndAdmin() throws Exception {
        Integer articleIndexId = createArticleAs(author, "主页隐藏可见性文章");
        createArticleAs(author, "主页正常可见文章");
        hideArticleAs(author, articleIndexId);

        // 作者本人可见：两条文章，隐藏的那条带 is_hidden 标记
        String body = mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article_page_obj.object_list.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Boolean> hiddenFlag = JsonPath.read(body, "$.article_page_obj.object_list[?(@.title=='主页隐藏可见性文章')].is_hidden");
        assertThat(hiddenFlag).containsExactly(true);
        List<Boolean> normalFlag = JsonPath.read(body, "$.article_page_obj.object_list[?(@.title=='主页正常可见文章')].is_hidden");
        assertThat(normalFlag).containsExactly(false);

        // 其他用户不可见（/api/user/** 需登录，无游客场景）
        mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article_page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.article_page_obj.object_list[0].title").value("主页正常可见文章"));

        // 管理员可见
        mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article_page_obj.object_list.length()").value(2));
    }

    @Test
    void userProfile_hiddenCommentVisibleOnlyToOwnerAndAdmin() throws Exception {
        Integer articleIndexId = createArticleAs(author, "主页隐藏评论文章");
        createCommentAs(author, false, articleIndexId, "主页中将被隐藏的评论");
        createCommentAs(author, false, articleIndexId, "主页正常评论");
        Integer commentIndexId = findCommentIndexId(articleIndexId, "主页中将被隐藏的评论");

        mockMvc.perform(post("/api/comment/hide/{id}/", commentIndexId)
                        .with(auth(author, false)).with(csrf()))
                .andExpect(status().isOk());

        // 作者本人可见两条，隐藏的那条带 is_hidden 标记
        String body = mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(author, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment_page_obj.object_list.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Boolean> hiddenFlag = JsonPath.read(body, "$.comment_page_obj.object_list[?(@.content=='主页中将被隐藏的评论')].is_hidden");
        assertThat(hiddenFlag).containsExactly(true);

        // 其他用户只见正常评论
        mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(other, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment_page_obj.object_list.length()").value(1))
                .andExpect(jsonPath("$.comment_page_obj.object_list[0].content").value("主页正常评论"));

        // 管理员可见两条
        mockMvc.perform(get("/api/user/user/{userId}", author.getId().toString())
                        .with(auth(adminUser, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment_page_obj.object_list.length()").value(2));
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
    private Integer createArticleAs(User user, String title) throws Exception {
        mockMvc.perform(multipart("/api/article/create/")
                        .param("title", title)
                        .param("content", "正文内容 " + title)
                        .with(auth(user, false))
                        .with(csrf()))
                .andExpect(status().isOk());
        Integer indexId = findArticleIndexIdByTitle(title);
        assertThat(indexId).as("创建后的文章应出现在列表中").isNotNull();
        return indexId;
    }

    /** 隐藏文章 */
    private void hideArticleAs(User user, Integer articleIndexId) throws Exception {
        mockMvc.perform(post("/api/article/{id}/hide/", articleIndexId)
                        .with(auth(user, false)).with(csrf()))
                .andExpect(status().isOk());
    }

    /** 以指定用户身份创建评论 */
    private void createCommentAs(User user, boolean admin, Integer articleIndexId, String content) throws Exception {
        mockMvc.perform(post("/api/comment/{id}/create/", articleIndexId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}")
                        .with(auth(user, admin))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("评论发布成功"));
    }

    /** 游客身份获取评论列表响应体 */
    private String listCommentsBody(Integer articleIndexId, int page) throws Exception {
        return mockMvc.perform(get("/api/comment/{id}/{page}/", articleIndexId, page))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 按内容在评论列表中查找评论的 index_id；找不到返回 null */
    private Integer findCommentIndexId(Integer articleIndexId, String content) throws Exception {
        String body = listCommentsBody(articleIndexId, 1);
        List<Integer> ids = JsonPath.read(body,
                "$.page_obj.object_list[?(@.content=='" + content + "')].index_id");
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** 按标题搜索文章并返回 index_id；找不到返回 null */
    private Integer findArticleIndexIdByTitle(String title) throws Exception {
        String body = mockMvc.perform(get("/api/article/").param("search", title))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> ids = JsonPath.read(body,
                "$.page_obj.object_list[?(@.title=='" + title + "')].index_id");
        return ids.isEmpty() ? null : ids.getFirst();
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
}

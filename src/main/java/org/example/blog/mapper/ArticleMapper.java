package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.blog.dao.Article;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ArticleMapper {

    /** 根据 ID 查询文章 */
    Article selectById(UUID id);

    /** 根据 index_id 查询文章，只保留updated_at最新的一个 */
    Article selectByIndexId(Integer indexId);

    /**
     * 分页查询全站可见文章（可见性过滤与分页在 SQL 层完成），按 created_at 降序，
     * 相同 index_id 只保留 updated_at 最新的一个版本。
     * 可见性：已删除文章不返回；已隐藏文章仅当查看者是管理员（isAdmin）或作者本人（viewerId）时返回。
     * keyword 为 null 或空时不过滤标题，否则按标题 ILIKE 模糊匹配。
     */
    List<Article> selectVisiblePage(@Param("keyword") String keyword,
                                    @Param("viewerId") UUID viewerId,
                                    @Param("isAdmin") boolean isAdmin,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    /** 统计全站可见文章总数（过滤条件与 selectVisiblePage 一致） */
    long countVisible(@Param("keyword") String keyword,
                      @Param("viewerId") UUID viewerId,
                      @Param("isAdmin") boolean isAdmin);

    /**
     * 分页查询某作者主页的可见文章（过滤与分页在 SQL 层完成），按 created_at 降序，
     * 相同 index_id 只保留 updated_at 最新的一个版本。
     * canViewHidden 为 true（作者本人或管理员查看）时包含已隐藏文章，否则不含。
     */
    List<Article> selectVisibleByAuthorPage(@Param("authorId") UUID authorId,
                                            @Param("canViewHidden") boolean canViewHidden,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    /** 统计某作者主页的可见文章总数（过滤条件与 selectVisibleByAuthorPage 一致） */
    long countVisibleByAuthor(@Param("authorId") UUID authorId,
                              @Param("canViewHidden") boolean canViewHidden);

    /**
     * 管理员后端：分页查询全部文章（含已删除/已隐藏，最新版本），按 created_at 降序。
     * keyword 非空时按标题模糊匹配；deleted/hidden 为 null 时不过滤该状态，否则只保留匹配值
     */
    List<Article> selectAdminPage(@Param("keyword") String keyword,
                                  @Param("deleted") Boolean deleted,
                                  @Param("hidden") Boolean hidden,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 管理员后端：统计全部文章总数（过滤条件与 selectAdminPage 一致） */
    long countAdmin(@Param("keyword") String keyword,
                    @Param("deleted") Boolean deleted,
                    @Param("hidden") Boolean hidden);

    /** 插入文章，自动查询index_id相同的对象并设置created_at，若没有则设置created_at为updated_at
      * 使用此接口插入时is_deleted永远为false，旧文章（indexId不为null）的is_hidden沿用上一版本，新文章使用DEFAULT
      * indexId 为 null（新文章）时由数据库序列（SERIAL）自动生成，指定时（保存新版本）沿用 */
    int insert(Article article);

    /** 设置文章为已删除 */
    int setDeleted(Article article);

    /** 设置文章为未删除 */
    int setNotDeleted(Article article);

    /** 设置文章为已隐藏 */
    int setHidden(Article article);

    /** 设置文章为未隐藏 */
    int setNotHidden(Article article);

}

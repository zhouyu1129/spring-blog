package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.blog.dao.Comment;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CommentMapper {

    /** 根据 index_id 查询评论，只保留 updated_at 最新的一个版本 */
    Comment selectByIndexId(Integer indexId);

    /**
     * 分页查询某文章的可见评论（过滤与分页在 SQL 层完成），按 created_at 降序，
     * 相同 index_id 只保留 updated_at 最新的一个版本。
     * 可见性：已删除评论不返回；已隐藏评论仅当查看者是管理员（isAdmin）或评论作者本人（viewerId）时返回。
     */
    List<Comment> selectVisiblePage(@Param("articleIndexId") Integer articleIndexId,
                                    @Param("viewerId") UUID viewerId,
                                    @Param("isAdmin") boolean isAdmin,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    /** 统计某文章的可见评论总数（过滤条件与 selectVisiblePage 一致） */
    long countVisible(@Param("articleIndexId") Integer articleIndexId,
                      @Param("viewerId") UUID viewerId,
                      @Param("isAdmin") boolean isAdmin);

    /**
     * 分页查询某作者的可见评论（用户主页使用，过滤与分页在 SQL 层完成），按 created_at 降序，
     * 相同 index_id 只保留 updated_at 最新的一个版本，结果携带所属文章标题（articleTitle）。
     * 可见性：已删除评论不返回；已隐藏评论仅管理员或评论作者本人可见；
     * 所属文章不存在、已删除或已隐藏的评论不返回（对任何人一致）。
     */
    List<Comment> selectVisibleByAuthorPage(@Param("authorId") UUID authorId,
                                            @Param("viewerId") UUID viewerId,
                                            @Param("isAdmin") boolean isAdmin,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    /** 统计某作者的可见评论总数（过滤条件与 selectVisibleByAuthorPage 一致） */
    long countVisibleByAuthor(@Param("authorId") UUID authorId,
                              @Param("viewerId") UUID viewerId,
                              @Param("isAdmin") boolean isAdmin);

    /** 插入评论，自动查询 index_id 相同的评论并继承其 created_at，若没有则 created_at 为 updated_at
      * 使用此接口插入时 is_deleted 永远为 false，旧评论（indexId 不为 null）的 is_hidden 沿用上一版本，新评论使用 DEFAULT
      * indexId 为 null（新评论）时由数据库序列（SERIAL）自动生成，指定时（修改评论）沿用 */
    int insert(Comment comment);

    /** 设置评论为已删除 */
    int setDeleted(Comment comment);

    /** 设置评论为未删除 */
    int setNotDeleted(Comment comment);

    /** 设置评论为已隐藏 */
    int setHidden(Comment comment);

    /** 设置评论为未隐藏 */
    int setNotHidden(Comment comment);

}

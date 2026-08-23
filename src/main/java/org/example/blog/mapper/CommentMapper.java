package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.Comment;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CommentMapper {

    /** 根据 index_id 查询评论，只保留 updated_at 最新的一个版本 */
    Comment selectByIndexId(Integer indexId);

    /** 查询文章的评论列表，按 created_at 降序，相同 index_id 只保留 updated_at 最新的一个版本 */
    List<Comment> selectByArticleIndexId(Integer articleIndexId);

    /** 查询 author_id 对应的评论列表，按 created_at 降序，相同 index_id 只保留 updated_at 最新的一个版本 */
    List<Comment> selectByAuthorId(UUID authorId);

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

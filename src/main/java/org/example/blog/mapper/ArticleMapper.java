package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.Article;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ArticleMapper {

    /** 根据 ID 查询文章 */
    Article selectById(UUID id);

    /** 根据 index_id 查询文章，只保留updated_at最新的一个 */
    Article selectByIndexId(Integer indexId);

    /** 查询author_id对应的文章列表，按created_at降序，相同index_id只保留updated_at最新的一个 */
    List<Article> selectByAuthorId(UUID authorId);

    /** 查询title包含keyword的文章列表，按created_at降序，相同index_id只保留updated_at最新的一个 */
    List<Article> selectByTitleContaining(String keyword);

    /** 插入文章，自动查询index_id相同的对象并设置created_at，若没有则设置created_at为updated_at
      * 使用此接口插入时is_deleted和is_hidden永远为false
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

package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.Image;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ImageMapper {

    /** 根据 ID 查询图片 */
    Image selectById(UUID id);

    /** 查询文章关联的图片列表（按上传时间倒序） */
    List<Image> selectByArticleId(UUID articleId);

    /** 查询用户的临时图片列表（已上传但未关联任何文章，按上传时间倒序） */
    List<Image> selectTempByAuthorId(UUID authorId);

    /** 插入图片 */
    int insert(Image image);

    /** 删除图片（关联关系由外键 ON DELETE CASCADE 级联清理） */
    int deleteById(UUID id);

    /** 建立文章与图片的关联（已存在则忽略） */
    int insertQuote(UUID articleId, UUID imageId);

    /** 解除文章与图片的关联 */
    int deleteQuote(UUID articleId, UUID imageId);
}

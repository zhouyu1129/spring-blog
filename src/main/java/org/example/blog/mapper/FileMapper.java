package org.example.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.blog.dao.File;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FileMapper {

    /** 根据 ID 查询文件 */
    File selectById(UUID id);

    /** 查询文章关联的文件列表（按上传时间倒序） */
    List<File> selectByArticleId(UUID articleId);

    /** 查询用户的临时文件列表（已上传但未关联任何文章，按上传时间倒序） */
    List<File> selectTempByAuthorId(UUID authorId);

    /** 插入文件 */
    int insert(File file);

    /** 删除文件（关联关系由外键 ON DELETE CASCADE 级联清理） */
    int deleteById(UUID id);

    /** 建立文章与文件的关联（已存在则忽略） */
    int insertQuote(UUID articleId, UUID fileId);

    /** 解除文章与文件的关联 */
    int deleteQuote(UUID articleId, UUID fileId);
}

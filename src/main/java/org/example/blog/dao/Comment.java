package org.example.blog.dao;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Comment {
    private UUID id;
    private Integer indexId;
    private String content;
    private UUID authorId;
    private Integer articleIndexId;
    private Boolean isDeleted;
    private Boolean isHidden;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 所属文章标题（仅用户主页评论查询的 JOIN 结果使用，非表字段） */
    private String articleTitle;
}

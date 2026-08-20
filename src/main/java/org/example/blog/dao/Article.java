package org.example.blog.dao;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Article {
    private UUID id;
    private Integer indexId;
    private String title;
    private String content;
    private UUID authorId;
    private Boolean isDeleted;
    private Boolean isHidden;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

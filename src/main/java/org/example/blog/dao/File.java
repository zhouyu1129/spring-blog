package org.example.blog.dao;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class File {
    private UUID id;
    private String title;
    private String path;
    private UUID authorId;
    private LocalDateTime createdAt;
}

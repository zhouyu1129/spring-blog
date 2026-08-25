package org.example.blog.dao;

import lombok.Data;

import java.util.UUID;

@Data
public class FileQuote {
    private UUID articleId;
    private UUID fileId;
}

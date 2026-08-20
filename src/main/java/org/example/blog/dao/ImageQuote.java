package org.example.blog.dao;

import lombok.Data;

import java.util.UUID;

@Data
public class ImageQuote {
    private UUID articleId;
    private UUID imageId;
}

package org.example.blog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地文件存储服务：负责把上传的图片/文件保存到配置目录、删除磁盘文件、拼访问 URL
 * <p>
 * 数据库 images/files 表的 path 字段只存相对保存目录的相对路径（即文件名），
 * 实际磁盘位置由 application.properties 中的 app.upload.image-dir / app.upload.file-dir 决定
 */
@Service
public class StorageService {

    private final Path imageRoot;
    private final Path fileRoot;

    public StorageService(@Value("${app.upload.image-dir}") String imageDir,
                          @Value("${app.upload.file-dir}") String fileDir) {
        this.imageRoot = Paths.get(imageDir).toAbsolutePath().normalize();
        this.fileRoot = Paths.get(fileDir).toAbsolutePath().normalize();
    }

    /** 保存图片到图片目录，返回相对路径 */
    public String storeImage(MultipartFile file) {
        return store(imageRoot, file);
    }

    /** 保存文件到文件目录，返回相对路径 */
    public String storeFile(MultipartFile file) {
        return store(fileRoot, file);
    }

    /** 删除磁盘上的图片文件 */
    public void deleteImage(String path) {
        delete(imageRoot, path);
    }

    /** 删除磁盘上的文件 */
    public void deleteFile(String path) {
        delete(fileRoot, path);
    }

    /** 图片访问 URL（由 WebMvcConfig 映射到本地图片目录） */
    public String imageUrl(String path) {
        return "/media/images/" + path;
    }

    /** 文件访问 URL（由 WebMvcConfig 映射到本地文件目录） */
    public String fileUrl(String path) {
        return "/media/files/" + path;
    }

    /** 读取图片文件大小（字节），文件不存在时返回 0 */
    public long imageSize(String path) {
        return size(imageRoot, path);
    }

    /** 读取文件大小（字节），文件不存在时返回 0 */
    public long fileSize(String path) {
        return size(fileRoot, path);
    }

    // ========== 内部方法 ==========

    private String store(Path root, MultipartFile file) {
        try {
            Files.createDirectories(root);
            String filename = generateFilename(file.getOriginalFilename());
            Files.copy(file.getInputStream(), root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return filename;
        } catch (IOException e) {
            throw new StorageException("文件保存失败", e);
        }
    }

    private void delete(Path root, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(path));
        } catch (IOException e) {
            throw new StorageException("文件删除失败", e);
        }
    }

    private long size(Path root, String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        try {
            return Files.size(root.resolve(path));
        } catch (IOException e) {
            return 0;
        }
    }

    /** 生成不重复的文件名：UUID + 原文件扩展名（扩展名只保留字母和数字） */
    private String generateFilename(String originalFilename) {
        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
            }
        }
        return UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
    }

    /** 文件存储失败异常 */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package org.example.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源映射：把 /media/** 映射到本地上传目录，供前端访问上传的图片和文件
 * <p>
 * 数据库 images/files 表的 path 字段只存相对路径，
 * 实际文件保存在 application.properties 中配置的 app.upload.image-dir / app.upload.file-dir 目录下
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Path imageRoot;
    private final Path fileRoot;

    public WebMvcConfig(@Value("${app.upload.image-dir}") String imageDir,
                        @Value("${app.upload.file-dir}") String fileDir) {
        this.imageRoot = Paths.get(imageDir).toAbsolutePath().normalize();
        this.fileRoot = Paths.get(fileDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /media/images/** → 图片保存目录
        registry.addResourceHandler("/media/images/**")
                .addResourceLocations(imageRoot.toUri().toString());
        // /media/files/** → 文件保存目录
        registry.addResourceHandler("/media/files/**")
                .addResourceLocations(fileRoot.toUri().toString());
    }
}

package org.example.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
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

    /**
     * SPA 路由回退：前端使用 history 模式路由（/article/3/edit 等路径刷新时
     * 服务器上并不存在对应资源），把这些路径全部转发给 index.html 交给前端路由处理。
     * <ul>
     *   <li>排除 /api/**（后端接口）、/media/**（上传文件）与 /assets/**（前端构建产物）</li>
     *   <li>首段必须不含点号：PathPattern 的 /** 可匹配零段，若允许带点首段，
     *       favicon.svg 会被吞掉、forward:/index.html 还会再次命中本规则造成
     *       无限递归（StackOverflow）</li>
     * </ul>
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 单段路径：/about、/admin、/login 等
        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
        // 多级路径：/article/3/edit、/user/xxx 等。首段必须无点号（favicon.svg 等根级
        // 静态文件直接落到静态资源处理器），并排除 api、media、assets 前缀
        registry.addViewController("/{segment:^(?!api$|media$|assets$)[^\\.]*}/**")
                .setViewName("forward:/index.html");
    }
}

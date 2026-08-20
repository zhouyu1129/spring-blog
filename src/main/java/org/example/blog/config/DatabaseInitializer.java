package org.example.blog.config;

import org.example.blog.cache.RolePermissionCache;
import org.example.blog.dao.User;
import org.example.blog.service.UserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
public class DatabaseInitializer {

    @Bean
    public ApplicationRunner initDatabase(JdbcTemplate jdbcTemplate,
                                          DataSource dataSource,
                                          UserService userService,
                                          PasswordEncoder passwordEncoder,
                                          RolePermissionCache cache) {
        return args -> {
            // 建表
            executeSchemaSql(dataSource);
            System.out.println("[DatabaseInitializer] 表结构已创建");

            // 检查是否已有 admin 用户
            Integer adminCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = 'admin'",
                    Integer.class);

            if (adminCount == null || adminCount == 0) {
                // 创建默认 admin 用户
                createDefaultAdmin(userService, passwordEncoder);
                System.out.println("[DatabaseInitializer] 默认管理员用户已创建 (admin/admin123)");
            }

            // 表就绪后加载角色权限缓存
            cache.refresh();
            System.out.println("[DatabaseInitializer] 角色权限缓存已加载");
        };
    }

    /** 读取并执行 schema.sql */
    private void executeSchemaSql(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        populator.execute(dataSource);
    }

    /** 创建默认管理员用户 */
    private void createDefaultAdmin(UserService userService,
                                    PasswordEncoder passwordEncoder) {
        // 创建 admin 用户，isAdmin=true 即为管理员
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setRealName("Administrator");
        admin.setEmail("admin@example.com");
        admin.setEmailVerified(true);
        admin.setIsEnabled(true);
        admin.setStudentNumber("0000000000");
        admin.setIsStaff(true);
        admin.setIsAdmin(true);
        admin.setCreatedAt(LocalDateTime.now());
        userService.create(admin);
    }
}

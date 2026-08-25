package org.example.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 继承 SpringBootServletInitializer：WAR 既可 java -jar 直接运行（内嵌 Tomcat），
 * 也可部署到外部 Tomcat（tomcat 依赖为 provided，见 pom.xml）
 */
@SpringBootApplication
public class BlogApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }

}

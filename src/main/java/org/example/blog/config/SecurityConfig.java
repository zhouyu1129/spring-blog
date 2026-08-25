package org.example.blog.config;

import org.example.blog.service.CustomUserDetailsService;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // 公开 API
                .requestMatchers("/api/csrf", "/api/user/login", "/api/user/register",
                        "/api/user/forgot_password", "/api/user/reset_password",
                        "/api/user/verify_email").permitAll()
                // 静态资源
                .requestMatchers("/css/**", "/js/**", "/images/**", "/media/**", "/static/**").permitAll()
                // 其余 /api/user/** 需要认证
                .requestMatchers("/api/user/**").authenticated()
                // 管理员后端 API：查询（GET）需员工或管理员，修改（写操作）仅管理员
                .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 其余请求（含 SPA 页面路由与静态资源）
                .anyRequest().permitAll()
            )
            // 不使用 formLogin，登录由 ApiUserController 处理（支持 JSON 请求体）
            .formLogin(AbstractHttpConfigurer::disable)
            // 不使用 Spring Security 内置 logout，由 ApiUserController 处理
            .logout(AbstractHttpConfigurer::disable)
            // 启用 CSRF，使用 Cookie 存储，适配前端 X-XSRF-TOKEN 头
            // 使用 SpaCsrfTokenRequestHandler：前端直接从 XSRF-TOKEN Cookie 读取明文 token
            // 并通过 X-XSRF-TOKEN 头回传，需按明文校验（默认的 Xor 校验器会拒绝明文 token）
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .ignoringRequestMatchers("/api/user/login", "/api/user/register",
                        "/api/user/forgot_password", "/api/user/logout",
                        "/api/user/reset_password", "/api/user/verify_email",
                        "/api/user/resend_verification")
            );

        return http.build();
    }

    /**
     * SPA 专用 CSRF 请求处理器（Spring Security 官方推荐）。
     * <p>
     * 默认的 {@link XorCsrfTokenRequestAttributeHandler} 会对每次请求生成的「带随机掩码」的 token
     * 做 XOR 解码校验，前端若直接把 Cookie 中的明文 token 放进 X-XSRF-TOKEN 头回传，
     * 解码长度不匹配会被判定为非法（返回 null）从而导致 403。
     * <p>
     * 该处理器对头部的 token 使用父类 {@link CsrfTokenRequestAttributeHandler} 的明文校验，
     * 同时在渲染（例如 /api/csrf 响应体）时仍委托给 Xor 处理器以保留 BREACH 保护。
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        @NullMarked
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           Supplier<CsrfToken> csrfToken) {
            this.delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            // 前端通过请求头回传 token 时，按明文校验（与 Cookie 中的明文一致）；
            // 其它场景（如表单参数）沿用 Xor 处理器的解码逻辑。
            if (request.getHeader(csrfToken.getHeaderName()) != null) {
                return super.resolveCsrfTokenValue(request, csrfToken);
            }
            return this.delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieName("XSRF-TOKEN");
        repo.setHeaderName("X-XSRF-TOKEN");
        return repo;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:3000", "http://127.0.0.1:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

package org.example.blog.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.blog.dao.User;
import org.example.blog.service.CustomUserDetails;
import org.example.blog.service.EmailService;
import org.example.blog.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端 API 用户控制器
 * 所有路径以 /api/user 开头，与前端 api/index.ts 中 authApi 定义对应
 */
@RestController
@RequestMapping("/api/user")
public class ApiUserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public ApiUserController(UserService userService, PasswordEncoder passwordEncoder,
                             AuthenticationManager authenticationManager, EmailService emailService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    // ========== 登录 ==========

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletRequest request) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(401)
                    .body(Map.of("status", "error", "message", "请输入邮箱/用户名和密码"));
        }

        try {
            // 使用 AuthenticationManager 认证（支持邮箱/用户名/学号登录）
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, password);
            Authentication authentication = authenticationManager.authenticate(authToken);

            // 将认证信息存入 SecurityContext（Spring Session 会自动持久化到 Redis）
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);

            // 更新最后登录时间并返回用户信息
            Map<String, Object> userData = null;
            if (authentication.getName() != null) {
                User user = userService.getByUsername(authentication.getName());
                if (user != null) {
                    userService.updateLastLoggedAt(user.getId());
                    userData = toProfileMap(user);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("message", "登录成功");
            if (userData != null) {
                response.put("user", userData);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("status", "error", "message", "用户名或密码错误"));
        }
    }

    // ========== 登出 ==========

    @GetMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return ResponseEntity.ok(Map.of("status", "success", "message", "登出成功"));
    }

    // ========== 注册 ==========

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String studentNumber = body.get("student_number");
        String password = body.get("password");
        String confirmPassword = body.get("confirm_password");

        // 基本校验
        if (username == null || username.isBlank())
            return badRequest("用户名不能为空");
        if (email == null || email.isBlank())
            return badRequest("邮箱不能为空");
        if (studentNumber == null || studentNumber.isBlank())
            return badRequest("学号不能为空");
        if (password == null || password.isBlank())
            return badRequest("密码不能为空");
        if (!password.equals(confirmPassword))
            return badRequest("两次输入的密码不一致");

        // 格式校验
        if (!UserService.isUserNameValid(username))
            return badRequest("用户名不合法");
        if (!UserService.isEmailValid(email))
            return badRequest("邮箱不合法");
        if (!UserService.isPasswordValid(password))
            return badRequest("密码不合法（6-128位）");
        if (!UserService.isStudentNumberValid(studentNumber))
            return badRequest("学号不合法（必须10位数字）");

        // 唯一性校验
        if (userService.getByUsername(username) != null)
            return badRequest("用户名已被注册");
        if (userService.getByEmail(email) != null)
            return badRequest("邮箱已被使用");
        if (userService.getByStudentNumber(studentNumber) != null)
            return badRequest("学号已被注册");

        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(body.getOrDefault("nickname", username));
        user.setEmail(email);
        user.setStudentNumber(studentNumber);
        user.setIsEnabled(true);
        user.setIsStaff(false);
        user.setIsAdmin(false);

        userService.create(user);

        // 发送邮箱验证邮件
        emailService.sendEmailVerificationToken(email, user.getId().toString());

        return ResponseEntity.ok(Map.of("status", "success", "message", "注册成功，请查收邮箱验证邮件"));
    }

    // ========== 邮箱验证 ==========

    @GetMapping("/verify_email")
    public RedirectView verifyEmail(@RequestParam String token) {
        String userId = emailService.verifyEmailToken(token);
        if (userId != null) {
            User user = userService.getById(java.util.UUID.fromString(userId));
            if (user != null) {
                userService.verifyEmail(user.getId());
                return new RedirectView(frontendUrl + "/user/verify-email/result?status=success");
            }
        }
        return new RedirectView(frontendUrl + "/user/verify-email/result?status=error");
    }

    @PostMapping("/resend_verification")
    public ResponseEntity<?> resendVerification(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }

        User user = userService.getById(currentUser.getId());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "用户不存在"));
        }

        if (user.getEmailVerified() != null && user.getEmailVerified()) {
            return badRequest("邮箱已验证，无需重复验证");
        }

        emailService.sendEmailVerificationToken(user.getEmail(), user.getId().toString());
        return ResponseEntity.ok(Map.of("status", "success", "message", "验证邮件已重新发送"));
    }

    // ========== 获取当前用户信息 ==========

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }
        User user = userService.getById(currentUser.getId());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "用户不存在"));
        }
        return ResponseEntity.ok(Map.of("user", toProfileMap(user)));
    }

    // ========== 编辑个人资料 ==========

    @PostMapping("/profile/edit")
    public ResponseEntity<?> editProfile(@AuthenticationPrincipal CustomUserDetails currentUser,
                                         @RequestBody Map<String, String> body) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }

        String nickname = body.get("nickname");
        String realName = body.get("real_name");
        String mobile = body.get("mobile");
        String gender = body.get("gender");

        userService.editProfile(currentUser.getId(), nickname, realName, mobile, gender);
        return ResponseEntity.ok(Map.of("status", "success", "message", "个人资料更新成功"));
    }

    // ========== 修改邮箱 ==========

    @PostMapping("/profile/change_email")
    public ResponseEntity<?> changeEmail(@AuthenticationPrincipal CustomUserDetails authUser,
                                         @RequestBody Map<String, String> body) {
        if (authUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }

        String newEmail = body.get("new_email");
        String verificationCode = body.get("verification_code");

        if (newEmail == null || newEmail.isBlank())
            return badRequest("新邮箱不能为空");
        if (verificationCode == null || verificationCode.isBlank())
            return badRequest("验证码不能为空");

        if (!UserService.isEmailValid(newEmail))
            return badRequest("邮箱格式不合法");

        // 验证验证码（验证码发送到了当前用户的邮箱）
        User user = userService.getById(authUser.getId());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "用户不存在"));
        }
        if (!emailService.verifyCode(user.getEmail(), verificationCode)) {
            return badRequest("验证码错误或已过期");
        }

        // 检查新邮箱是否已被占用
        User existingUser = userService.getByEmail(newEmail);
        if (existingUser != null && !existingUser.getId().equals(authUser.getId())) {
            return badRequest("该邮箱已被其他用户使用");
        }

        userService.changeEmail(authUser.getId(), newEmail);
        return ResponseEntity.ok(Map.of("status", "success", "message", "邮箱修改成功，请查收验证邮件"));
    }

    // ========== 修改密码 ==========

    @PostMapping("/profile/change_password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal CustomUserDetails currentUser,
                                            @RequestBody Map<String, String> body) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }

        String oldPassword = body.get("old_password");
        String newPassword = body.get("new_password");
        String confirmPassword = body.get("confirm_password");

        if (oldPassword == null || oldPassword.isBlank())
            return badRequest("旧密码不能为空");
        if (newPassword == null || newPassword.isBlank())
            return badRequest("新密码不能为空");
        if (!newPassword.equals(confirmPassword))
            return badRequest("两次输入的新密码不一致");
        if (!UserService.isPasswordValid(newPassword))
            return badRequest("新密码不合法（6-128位）");

        boolean success = userService.changePassword(currentUser.getId(), oldPassword, newPassword);
        if (!success) {
            return badRequest("当前密码不正确");
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "密码修改成功"));
    }

    // ========== 发送邮箱验证码 ==========

    @PostMapping("/profile/send_email_code")
    public ResponseEntity<?> sendEmailCode(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "未登录"));
        }

        User user = userService.getById(currentUser.getId());
        if (user == null || user.getEmail() == null) {
            return badRequest("用户邮箱信息不完整");
        }

        boolean sent = emailService.sendVerificationCode(user.getEmail(), "修改邮箱");
        if (!sent) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "发送过于频繁，请稍后再试"));
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "验证码已发送，请查收邮件"));
    }

    // ========== 忘记密码 ==========

    @PostMapping("/forgot_password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String studentNumber = body.get("student_number");

        if (email == null || email.isBlank())
            return badRequest("邮箱不能为空");
        if (studentNumber == null || studentNumber.isBlank())
            return badRequest("学号不能为空");

        User user = userService.getByEmail(email);
        if (user == null || !studentNumber.equals(user.getStudentNumber())) {
            return badRequest("邮箱和学号不匹配");
        }

        // 生成密码重置令牌并发送邮件
        emailService.sendPasswordResetToken(email, user.getId().toString());

        return ResponseEntity.ok(Map.of("status", "success", "message", "密码重置链接已发送到您的邮箱"));
    }

    // ========== 重置密码（通过令牌）==========

    @PostMapping("/reset_password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("new_password");

        if (token == null || token.isBlank())
            return badRequest("重置令牌不能为空");
        if (newPassword == null || newPassword.isBlank())
            return badRequest("新密码不能为空");
        if (!UserService.isPasswordValid(newPassword))
            return badRequest("新密码不合法（6-128位）");

        String userId = emailService.verifyResetToken(token);
        if (userId == null) {
            return badRequest("重置令牌无效或已过期");
        }

        User user = userService.getById(java.util.UUID.fromString(userId));
        if (user == null) {
            return badRequest("用户不存在");
        }

        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        userService.update(update);

        return ResponseEntity.ok(Map.of("status", "success", "message", "密码重置成功"));
    }

    // ========== 查看他人主页 ==========

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId) {
        User targetUser;
        try {
            targetUser = userService.getById(java.util.UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return badRequest("无效的用户ID");
        }

        if (targetUser == null) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "用户不存在"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("target_user", toPublicProfileMap(targetUser));
        // TODO: 添加 article_page_obj 和 comment_page_obj（需要文章和评论模块）
        result.put("articles", java.util.List.of());
        result.put("comments", java.util.List.of());

        return ResponseEntity.ok(result);
    }

    // ========== 辅助方法 ==========

    /**
     * 将 User 转换为前端 profile 页面所需的完整信息 Map
     */
    private Map<String, Object> toProfileMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("nickname", user.getNickname());
        map.put("student_number", user.getStudentNumber());
        map.put("email_verified", user.getEmailVerified() != null && user.getEmailVerified());
        map.put("real_name", user.getRealName());
        map.put("mobile", user.getMobile());
        map.put("gender", user.getGender());
        map.put("date_joined", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put("last_login", user.getLastLoggedAt() != null ? user.getLastLoggedAt().toString() : null);
        return map;
    }

    /**
     * 将 User 转换为他人可见的公开信息 Map
     */
    private Map<String, Object> toPublicProfileMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("real_name", user.getRealName());
        map.put("gender", user.getGender());
        map.put("email_verified", user.getEmailVerified() != null && user.getEmailVerified());
        map.put("date_joined", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        return map;
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message));
    }
}

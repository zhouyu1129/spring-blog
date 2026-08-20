package org.example.blog.controller;

import org.example.blog.dao.User;
import org.example.blog.service.CustomUserDetails;
import org.example.blog.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册
     * POST /auth/register
     * Body: { "username": "...", "password": "...", "email": "...", "nickname": "..." }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");
        String StudentNumber = body.get("student_number");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不能为空"));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱不能为空"));
        }
        if (StudentNumber == null || StudentNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "学号不能为空"));
        }

        //检查字段是否合法
        if (!UserService.isUserNameValid(username)) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不合法"));
        }
        if (!UserService.isEmailValid(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱不合法"));
        }
        if (!UserService.isPasswordValid(password)) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不合法"));
        }
        if (!UserService.isStudentNumberValid(StudentNumber)) {
            return ResponseEntity.badRequest().body(Map.of("message", "学号不合法"));
        }

        // 检查unique字段是否已存在
        if (userService.getByUsername(username) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名已被注册"));
        }
        if (userService.getByEmail(email) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱已被使用"));
        }
        if (userService.getByStudentNumber(StudentNumber) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "学号已被注册"));
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(body.getOrDefault("nickname", username));
        user.setRealName(body.getOrDefault("rename", null));
        user.setStudentNumber(StudentNumber);
        user.setEmail(email);
        user.setIsEnabled(true);
        user.setIsStaff(false);
        user.setIsAdmin(false);

        userService.create(user);

        return ResponseEntity.ok(Map.of("message", "注册成功", "username", username));
    }

    /**
     * 获取当前登录用户信息
     * GET /auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@org.springframework.security.core.annotation.CurrentSecurityContext(expression = "authentication.principal") Object principal) {
        if (principal instanceof CustomUserDetails customUser) {
            return ResponseEntity.ok(Map.of(
                    "username", customUser.getUsername(),
                    "authorities", customUser.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
            ));
        }
        return ResponseEntity.ok(Map.of("message", "未登录"));
    }
}

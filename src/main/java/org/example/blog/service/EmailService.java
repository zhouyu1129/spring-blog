package org.example.blog.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱验证服务
 * 使用 Redis 存储验证码，JavaMailSender 发送邮件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username:noreply@blog.com}")
    private String fromEmail;

    /** 前端地址：邮件中的验证/重置链接以前端域名发出，由前端转发给后端处理 */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;
    /** 验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;
    /** 发送冷却时间（秒），防止频繁发送 */
    private static final int SEND_COOLDOWN_SECONDS = 60;

    // Redis Key 前缀
    private static final String CODE_KEY_PREFIX = "email:code:";
    private static final String COOLDOWN_KEY_PREFIX = "email:cooldown:";
    private static final String RESET_TOKEN_KEY_PREFIX = "email:reset:";

    // ========== 验证码相关 ==========

    /**
     * 发送验证码到指定邮箱
     * @param email 目标邮箱
     * @param purpose 用途描述（如"修改邮箱"、"注册验证"），用于邮件内容
     * @return true 发送成功，false 冷却中
     */
    public boolean sendVerificationCode(String email, String purpose) {
        // 检查冷却
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            return false;
        }

        // 生成验证码
        String code = generateCode();

        // 存储到 Redis
        String codeKey = CODE_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(CODE_EXPIRE_MINUTES));

        // 设置冷却
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(SEND_COOLDOWN_SECONDS));

        // 异步发送邮件
        // 测试用户的邮件直接发给自身
        if (email.contains("@example.com")) {
            sendCodeEmail(fromEmail, code, purpose);
            log.info("测试用户 " + email + " 的邮件直接发给自身: " + fromEmail);
        }
        else {
            sendCodeEmail(email, code, purpose);
        }

        return true;
    }

    /**
     * 验证验证码是否正确
     * @param email 邮箱
     * @param code 用户输入的验证码
     * @return true 验证通过
     */
    public boolean verifyCode(String email, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String codeKey = CODE_KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored != null && stored.equals(code)) {
            // 验证成功后删除验证码，防止重复使用
            redisTemplate.delete(codeKey);
            return true;
        }
        return false;
    }

    // ========== 邮箱验证令牌相关 ==========

    private static final String VERIFY_EMAIL_KEY_PREFIX = "email:verify:";
    /** 记录每个用户最新的验证令牌，用于重发时作废旧令牌 */
    private static final String VERIFY_EMAIL_USER_KEY_PREFIX = "email:verify:user:";

    /**
     * 发送邮箱验证令牌（作废该用户之前的令牌）
     * @param email 用户邮箱
     * @param userId 用户ID
     * @return 生成的令牌
     */
    public String sendEmailVerificationToken(String email, String userId) {
        // 作废该用户之前的验证令牌
        String userTokenKey = VERIFY_EMAIL_USER_KEY_PREFIX + userId;
        String oldToken = redisTemplate.opsForValue().get(userTokenKey);
        if (oldToken != null) {
            redisTemplate.delete(VERIFY_EMAIL_KEY_PREFIX + oldToken);
        }

        String token = generateToken();
        String tokenKey = VERIFY_EMAIL_KEY_PREFIX + token;
        redisTemplate.opsForValue().set(tokenKey, userId, Duration.ofHours(24));
        // 记录该用户最新的令牌
        redisTemplate.opsForValue().set(userTokenKey, token, Duration.ofHours(24));
        // 测试用户的邮件直接发给自身
        if (email.contains("@example.com")) {
            sendVerificationEmail(fromEmail, token);
            log.info("测试用户 " + email + " 的邮件直接发给自身: " + fromEmail);
        }
        else {
            sendVerificationEmail(email, token);
        }
        return token;
    }

    /**
     * 验证邮箱验证令牌，返回用户ID
     * @param token 验证令牌
     * @return 用户ID，令牌无效返回 null
     */
    public String verifyEmailToken(String token) {
        String tokenKey = VERIFY_EMAIL_KEY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(tokenKey);
        if (userId != null) {
            redisTemplate.delete(tokenKey);
            // 同时清除用户令牌映射
            redisTemplate.delete(VERIFY_EMAIL_USER_KEY_PREFIX + userId);
        }
        return userId;
    }

    // ========== 密码重置令牌相关 ==========

    /**
     * 生成密码重置令牌并发送重置邮件
     * @param email 用户邮箱
     * @param userId 用户ID
     * @return 生成的令牌
     */
    public String sendPasswordResetToken(String email, String userId) {
        // 生成令牌
        String token = generateToken();

        // 存储到 Redis，30分钟过期
        String tokenKey = RESET_TOKEN_KEY_PREFIX + token;
        redisTemplate.opsForValue().set(tokenKey, userId, Duration.ofMinutes(30));

        // 发送重置邮件
        // 测试用户的邮件直接发给自身
        if (email.contains("@example.com")) {
            sendResetEmail(fromEmail, token);
            log.info("测试用户 " + email + " 的邮件直接发给自身: " + fromEmail);
        }
        else {
            sendResetEmail(email, token);
        }

        return token;
    }

    /**
     * 验证密码重置令牌，返回用户ID
     * @param token 重置令牌
     * @return 用户ID，令牌无效返回 null
     */
    public String verifyResetToken(String token) {
        String tokenKey = RESET_TOKEN_KEY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(tokenKey);
        if (userId != null) {
            // 使用后删除令牌
            redisTemplate.delete(tokenKey);
        }
        return userId;
    }

    // ========== 私有方法 ==========

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        int code = ThreadLocalRandom.current().nextInt((int) Math.pow(10, CODE_LENGTH), (int) Math.pow(10, CODE_LENGTH + 1));
        return String.valueOf(code);
    }

    /**
     * 生成密码重置令牌
     */
    private String generateToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 发送验证码邮件
     */
    private void sendCodeEmail(String to, String code, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("【校园博客】" + purpose + "验证码");

            String content = """
                <div style="max-width:600px;margin:0 auto;padding:20px;font-family:sans-serif;">
                    <h2 style="color:#059669;">校园博客</h2>
                    <p>您好，</p>
                    <p>您正在进行<strong>%s</strong>操作，验证码为：</p>
                    <div style="background:#f0fdf4;border:2px dashed #059669;border-radius:8px;padding:16px;text-align:center;margin:20px 0;">
                        <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#059669;">%s</span>
                    </div>
                    <p style="color:#666;">验证码有效期为%d分钟，请尽快使用。如非本人操作，请忽略此邮件。</p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:20px 0;"/>
                    <p style="color:#999;font-size:12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
                """.formatted(purpose, code, CODE_EXPIRE_MINUTES);

            helper.setText(content, true);
            mailSender.send(message);
            log.info("验证码邮件已发送至: {}", to);
        } catch (MessagingException e) {
            log.error("发送验证码邮件失败: to={}, error={}", to, e.getMessage());
        }
    }

    /**
     * 发送密码重置邮件
     */
    private void sendResetEmail(String to, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("【校园博客】密码重置");

            String resetUrl = frontendUrl + "/api/user/reset_password?token=" + token;

            String content = """
                <div style="max-width:600px;margin:0 auto;padding:20px;font-family:sans-serif;">
                    <h2 style="color:#059669;">校园博客</h2>
                    <p>您好，</p>
                    <p>我们收到了您的密码重置请求。请点击下方按钮重置密码：</p>
                    <div style="text-align:center;margin:20px 0;">
                        <a href="%s" style="background:#059669;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;">重置密码</a>
                    </div>
                    <p style="color:#666;">此链接30分钟内有效。如非本人操作，请忽略此邮件。</p>
                    <p style="color:#999;font-size:12px;">如果按钮无法点击，请复制以下链接到浏览器打开：<br/>%s</p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:20px 0;"/>
                    <p style="color:#999;font-size:12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
                """.formatted(resetUrl, resetUrl);

            helper.setText(content, true);
            mailSender.send(message);
            log.info("密码重置邮件已发送至: {}", to);
        } catch (MessagingException e) {
            log.error("发送密码重置邮件失败: to={}, error={}", to, e.getMessage());
        }
    }

    /**
     * 发送邮箱验证邮件
     */
    private void sendVerificationEmail(String to, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("【校园博客】邮箱验证");

            String verifyUrl = frontendUrl + "/api/user/verify_email?token=" + token;

            String content = """
                <div style="max-width:600px;margin:0 auto;padding:20px;font-family:sans-serif;">
                    <h2 style="color:#059669;">校园博客</h2>
                    <p>您好，感谢注册校园博客！请点击下方按钮完成邮箱验证：</p>
                    <div style="text-align:center;margin:20px 0;">
                        <a href="%s" style="background:#059669;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;">验证邮箱</a>
                    </div>
                    <p style="color:#666;">此链接24小时内有效。如非本人操作，请忽略此邮件。</p>
                    <p style="color:#999;font-size:12px;">如果按钮无法点击，请复制以下链接到浏览器打开：<br/>%s</p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:20px 0;"/>
                    <p style="color:#999;font-size:12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
                """.formatted(verifyUrl, verifyUrl);

            helper.setText(content, true);
            mailSender.send(message);
            log.info("邮箱验证邮件已发送至: {}", to);
        } catch (MessagingException e) {
            log.error("发送邮箱验证邮件失败: to={}, error={}", to, e.getMessage());
        }
    }
}

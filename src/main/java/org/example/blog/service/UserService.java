package org.example.blog.service;

import lombok.RequiredArgsConstructor;
import org.example.blog.dao.Role;
import org.example.blog.dao.User;
import org.example.blog.mapper.UserMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern ONLY_VALID_CHAR_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern ONLY_NUMBERS_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern IS_EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    public static boolean isUserNameValid(@NonNull String username) {
        return username.length() >= 3 && username.length() <= 40 && ONLY_VALID_CHAR_PATTERN.matcher(username).matches() && !ONLY_NUMBERS_PATTERN.matcher(username).matches();
    }

    public static boolean isEmailValid(@NonNull String email) {
        return IS_EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isPasswordValid(@NonNull String password) {
        return password.length() >= 6 && password.length() <= 128;
    }

    public static boolean isStudentNumberValid(@NonNull String studentNumber) {
        return studentNumber.length() == 10 && STUDENT_ID_PATTERN.matcher(studentNumber).matches();
    }

    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public User getByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    public User getByStudentNumber(String studentNumber) {
        return userMapper.selectByStudentNumber(studentNumber);
    }

    public User getByUniqueField(@NonNull String field) {
        if (isEmailValid(field)) {
            return getByEmail(field);
        } else if (isStudentNumberValid(field)) {
            return getByStudentNumber(field);
        } else {
            return getByUsername(field);
        }
    }

    public User getById(UUID id) {
        return userMapper.selectById(id);
    }

    public List<User> getAll() {
        return userMapper.selectAll();
    }

    @Transactional
    public User create(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        if (user.getEmailVerified() == null) {
            user.setEmailVerified(false);
        }
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public User update(User user) {
        userMapper.update(user);
        return user;
    }

    @Transactional
    public void updateLastLoggedAt(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setLastLoggedAt(LocalDateTime.now());
        userMapper.update(user);
    }

    @Transactional
    public void delete(UUID id) {
        userMapper.deleteById(id);
    }

    @Transactional
    public void assignRole(UUID userId, Integer roleId) {
        userMapper.insertUserRole(userId, roleId);
    }

    @Transactional
    public void removeRole(UUID userId, Integer roleId) {
        userMapper.deleteUserRole(userId, roleId);
    }

    public List<Role> getUserRoles(UUID userId) {
        return userMapper.selectRolesByUserId(userId);
    }

    // ========== 前端 API 所需的扩展方法 ==========

    /**
     * 修改密码
     * @return true 表示修改成功，false 表示旧密码不匹配
     */
    @Transactional
    public boolean changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.update(update);
        return true;
    }

    /**
     * 修改邮箱
     */
    @Transactional
    public void changeEmail(UUID userId, String newEmail) {
        User update = new User();
        update.setId(userId);
        update.setEmail(newEmail);
        update.setEmailVerified(false);
        userMapper.update(update);
    }

    /**
     * 设置邮箱已验证
     */
    @Transactional
    public void verifyEmail(UUID userId) {
        User update = new User();
        update.setId(userId);
        update.setEmailVerified(true);
        userMapper.update(update);
    }

    /**
     * 编辑个人资料（仅安全字段：nickname, realName, mobile, gender）
     */
    @Transactional
    public void editProfile(UUID userId, String nickname, String realName,
                            String mobile, String gender) {
        User update = new User();
        update.setId(userId);
        update.setNickname(nickname);
        update.setRealName(realName);
        update.setMobile(mobile);
        update.setGender(gender);
        userMapper.update(update);
    }

    /**
     * 重置密码（用于忘记密码场景，通过邮箱和学号验证后调用）
     */
    @Transactional
    public boolean resetPassword(String email, String studentNumber, String newPassword) {
        User user = userMapper.selectByEmail(email);
        if (user == null || !studentNumber.equals(user.getStudentNumber())) {
            return false;
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.update(update);
        return true;
    }
}

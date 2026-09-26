package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service xử lý logic quên mật khẩu và đặt lại mật khẩu.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.reset-password.token-expiration-ms}")
    private long tokenExpirationMs;

    @Value("${app.reset-password.frontend-url}")
    private String frontendResetUrl;

    /**
     * Gửi email chứa link reset mật khẩu.
     */
    @Transactional
    public void sendResetPasswordEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email: " + email));

        // Tạo token reset
        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        user.setResetPasswordExpiry(
                LocalDateTime.now().plusSeconds(tokenExpirationMs / 1000));
        userRepository.save(user);

        // Gửi email
        String resetLink = frontendResetUrl + "?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[Quản Lý Bán Hàng] Đặt lại mật khẩu");
        message.setText(
                "Xin chào " + user.getFullName() + ",\n\n"
                + "Bạn đã yêu cầu đặt lại mật khẩu.\n"
                + "Nhấn vào link sau để đặt lại mật khẩu:\n\n"
                + resetLink + "\n\n"
                + "Link này sẽ hết hạn sau 1 giờ.\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.\n\n"
                + "Trân trọng,\n"
                + "Hệ thống Quản Lý Bán Hàng"
        );

        mailSender.send(message);
    }

    /**
     * Đặt lại mật khẩu bằng token.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new RuntimeException("Token không hợp lệ"));

        // Kiểm tra token hết hạn
        if (user.getResetPasswordExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token đã hết hạn. Vui lòng yêu cầu lại.");
        }

        // Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordExpiry(null);
        userRepository.save(user);
    }
}

package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service xử lý logic quên mật khẩu và đặt lại mật khẩu (S1-03).
 *
 * Yêu cầu:
 * - Link reset có hiệu lực 30 phút
 * - Link chỉ dùng được một lần
 * - Email không tồn tại vẫn hiển thị cùng một thông báo (không tiết lộ)
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    /** Token reset hết hạn sau 30 phút. */
    private static final int TOKEN_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.reset-password.frontend-url}")
    private String frontendResetUrl;

    /**
     * Gửi email chứa link reset mật khẩu.
     *
     * Nếu email không tồn tại → KHÔNG throw exception, chỉ log.
     * Frontend luôn nhận cùng response → không tiết lộ email có tồn tại.
     */
    @Transactional
    public void sendResetPasswordEmail(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            // Log nhưng không tiết lộ — frontend nhận cùng message
            logger.warn("Reset password requested for non-existent email: {}", email);
            return;
        }

        User user = optionalUser.get();

        // Tạo token reset — mỗi lần request tạo token mới (token cũ tự mất hiệu lực)
        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        user.setResetPasswordExpiry(
                LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES));
        userRepository.save(user);

        // Gửi email
        String resetLink = frontendResetUrl + "?token=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("[SaleStock] Đặt lại mật khẩu");
            message.setText(
                    "Xin chào " + user.getFullName() + ",\n\n"
                    + "Bạn đã yêu cầu đặt lại mật khẩu.\n"
                    + "Nhấn vào link sau để đặt lại mật khẩu:\n\n"
                    + resetLink + "\n\n"
                    + "Link này sẽ hết hạn sau " + TOKEN_EXPIRATION_MINUTES + " phút "
                    + "và chỉ có thể sử dụng một lần.\n"
                    + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.\n\n"
                    + "Trân trọng,\n"
                    + "Hệ thống SaleStock"
            );

            mailSender.send(message);
            logger.info("Reset password email sent to: {}", email);
        } catch (Exception e) {
            logger.error("Failed to send reset password email to {}: {}", email, e.getMessage());
            // Không throw — frontend vẫn nhận success response
        }
    }

    /**
     * Đặt lại mật khẩu bằng token.
     *
     * Token chỉ dùng được MỘT LẦN — sau khi sử dụng sẽ bị xoá.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new RuntimeException(
                        "Link đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng."));

        // Kiểm tra token hết hạn
        if (user.getResetPasswordExpiry().isBefore(LocalDateTime.now())) {
            // Xoá token hết hạn
            user.setResetPasswordToken(null);
            user.setResetPasswordExpiry(null);
            userRepository.save(user);
            throw new RuntimeException("Link đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại.");
        }

        // Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));

        // Xoá token — link chỉ dùng được MỘT LẦN
        user.setResetPasswordToken(null);
        user.setResetPasswordExpiry(null);

        userRepository.save(user);
        logger.info("Password reset successful for user: {}", user.getUsername());
    }
}

package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountMailService {
    private static final Logger logger = LoggerFactory.getLogger(AccountMailService.class);
    private final JavaMailSender mailSender;

    @Value("${app.login.frontend-url:http://localhost:5500/frontend/index.html}")
    private String loginUrl;

    public void sendActivation(User user, String temporaryPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("[SaleStock] Tài khoản của bạn đã được tạo");
            message.setText("Xin chào " + user.getFullName() + ",\n\n"
                    + "Tài khoản SaleStock của bạn đã sẵn sàng.\n"
                    + "Tên đăng nhập: " + user.getUsername() + "\n"
                    + "Mật khẩu tạm: " + temporaryPassword + "\n\n"
                    + "Đăng nhập tại: " + loginUrl + "\n"
                    + "Hệ thống sẽ yêu cầu bạn đổi mật khẩu sau khi đăng nhập.");
            mailSender.send(message);
        } catch (Exception ex) {
            logger.error("Không gửi được email kích hoạt cho {}: {}", user.getEmail(), ex.getMessage());
        }
    }
}

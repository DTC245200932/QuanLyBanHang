package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JavaMailSender mailSender;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, passwordEncoder, mailSender);
        ReflectionTestUtils.setField(passwordResetService, "frontendResetUrl",
                "http://localhost:5500/frontend/reset-password.html");
        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMs", 30 * 60 * 1000L);
    }

    @Test
    void forgotPasswordCreatesSingleUseTokenAndSendsConfiguredLink() {
        User user = User.builder()
                .username("sales01")
                .email("sales01@example.com")
                .fullName("Nhân viên thị trường")
                .password("encoded")
                .build();
        when(userRepository.findByEmail("sales01@example.com")).thenReturn(Optional.of(user));

        LocalDateTime beforeRequest = LocalDateTime.now();
        passwordResetService.sendResetPasswordEmail("sales01@example.com");

        assertThat(user.getResetPasswordToken()).isNotBlank();
        assertThat(user.getResetPasswordExpiry())
                .isAfterOrEqualTo(beforeRequest.plusMinutes(30))
                .isBefore(LocalDateTime.now().plusMinutes(31));
        verify(userRepository).save(user);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetPasswordConsumesTokenAndInvalidatesExistingSessions() {
        User user = User.builder()
                .username("sales01")
                .email("sales01@example.com")
                .password("old-encoded")
                .tokenVersion(4L)
                .resetPasswordToken("reset-token")
                .resetPasswordExpiry(LocalDateTime.now().plusMinutes(10))
                .build();
        when(userRepository.findByResetPasswordToken("reset-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-encoded");

        passwordResetService.resetPassword("reset-token", "NewPassword1");

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getTokenVersion()).isEqualTo(5L);
        assertThat(user.getResetPasswordToken()).isNull();
        assertThat(user.getResetPasswordExpiry()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void expiredResetTokenIsRejected() {
        User user = User.builder()
                .username("sales01")
                .email("sales01@example.com")
                .password("old-encoded")
                .resetPasswordToken("expired-token")
                .resetPasswordExpiry(LocalDateTime.now().minusSeconds(1))
                .build();
        when(userRepository.findByResetPasswordToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.resetPassword("expired-token", "NewPassword1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Link đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại.");
    }
}

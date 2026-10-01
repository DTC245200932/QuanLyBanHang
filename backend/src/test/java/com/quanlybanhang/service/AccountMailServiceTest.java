package com.quanlybanhang.service;

import com.quanlybanhang.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private AccountMailService accountMailService;
    private User user;

    @BeforeEach
    void setUp() {
        accountMailService = new AccountMailService(mailSender);
        ReflectionTestUtils.setField(accountMailService, "loginUrl",
                "http://localhost:5500/frontend/index.html");
        ReflectionTestUtils.setField(accountMailService, "senderAddress",
                "sender@example.com");
        ReflectionTestUtils.setField(accountMailService, "senderPassword",
                "configured-app-password");
        user = User.builder()
                .username("sales01")
                .email("recipient@example.com")
                .fullName("Nhân viên Kinh doanh")
                .build();
    }

    @Test
    void sendsTemporaryCredentialsFromConfiguredAddress() {
        accountMailService.sendActivation(user, "TempPass123");

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("recipient@example.com");
        assertThat(message.getText())
                .contains("sales01")
                .contains("TempPass123")
                .contains("http://localhost:5500/frontend/index.html");
    }

    @Test
    void rejectsSendingWhenSmtpCredentialsAreMissing() {
        ReflectionTestUtils.setField(accountMailService, "senderPassword", "");

        assertThatThrownBy(() -> accountMailService.sendActivation(user, "TempPass123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_USERNAME và MAIL_APP_PASSWORD");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void reportsDeliveryFailureInsteadOfClaimingSuccess() {
        doThrow(new MailAuthenticationException("Authentication failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> accountMailService.sendActivation(user, "TempPass123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Không thể gửi email mật khẩu tạm. Vui lòng kiểm tra cấu hình SMTP và thử lại.");
    }
}

package com.quanlybanhang.service;

import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.dto.request.LoginRequest;
import com.quanlybanhang.exception.AccountTemporarilyLockedException;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock RolePermissionConfig rolePermissionConfig;
    @InjectMocks AuthService authService;

    private LoginRequest request;

    @BeforeEach
    void setUp() {
        request = new LoginRequest();
        request.setUsername("sales01");
        request.setPassword("wrong123");
    }

    @Test
    void fifthConsecutiveFailureLocksAccountForFifteenMinutes() {
        User user = User.builder().username("sales01").password("encoded").active(true).build();
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountTemporarilyLockedException.class);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockTime()).isAfter(java.time.LocalDateTime.now().plusMinutes(14));
    }

    @Test
    void unknownAndKnownAccountsUseTheSameBadCredentialsMessage() {
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không đúng");
    }

    @Test
    void accountLockedByAdminCannotLogin() {
        User user = User.builder().username("sales01").password("encoded").active(false).build();
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(LockedException.class)
                .hasMessage("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void accountLockedByAdminCannotRefreshAnOpenSession() {
        User user = User.builder()
                .username("sales01")
                .password("encoded")
                .active(false)
                .tokenVersion(8L)
                .build();
        when(jwtUtils.validateToken("refresh-token")).thenReturn(true);
        when(jwtUtils.getTokenType("refresh-token")).thenReturn("refresh");
        when(tokenBlacklistService.isBlacklisted("refresh-token")).thenReturn(false);
        when(jwtUtils.getUsernameFromToken("refresh-token")).thenReturn("sales01");
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken("refresh-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        verify(jwtUtils, never()).generateAccessToken(any(String.class), anyLong());
    }
}

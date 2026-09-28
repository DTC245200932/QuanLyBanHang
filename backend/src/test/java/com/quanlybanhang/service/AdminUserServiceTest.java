package com.quanlybanhang.service;

import com.quanlybanhang.dto.response.UserResponse;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.RoleRepository;
import com.quanlybanhang.repository.TerritoryRepository;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock TerritoryRepository territoryRepository;
    @Mock DealerRepository dealerRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AccountMailService accountMailService;
    @InjectMocks AdminUserService adminUserService;

    @Test
    void lockRevokesSessionsAndMarksAssignedDealersForHandover() {
        User user = user(15L, "sales01", true, 4L);
        Dealer firstDealer = Dealer.builder().id(1L).name("Đại lý A").assignedUser(user).build();
        Dealer secondDealer = Dealer.builder().id(2L).name("Đại lý B").assignedUser(user).build();
        List<Dealer> dealers = List.of(firstDealer, secondDealer);
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(dealerRepository.findByAssignedUserId(15L)).thenReturn(dealers);
        when(userRepository.save(user)).thenReturn(user);
        when(dealerRepository.countByAssignedUserId(15L)).thenReturn(2L);

        UserResponse response = adminUserService.lock(
                15L, "  Nhân viên đã nghỉ việc  ", "admin");

        assertThat(user.getActive()).isFalse();
        assertThat(user.getLockReason()).isEqualTo("Nhân viên đã nghỉ việc");
        assertThat(user.getTokenVersion()).isEqualTo(5L);
        assertThat(dealers).allMatch(dealer -> dealer.getHandoverRequired());
        assertThat(response.getAssignedDealerCount()).isEqualTo(2L);
        verify(dealerRepository).saveAll(dealers);
        verify(userRepository).save(user);
    }

    @Test
    void lockRequiresAReasonEvenWhenServiceIsCalledDirectly() {
        assertThatThrownBy(() -> adminUserService.lock(15L, "   ", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bắt buộc nhập lý do khoá");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void administratorCannotLockTheCurrentAccount() {
        User user = user(1L, "admin", true, 2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.lock(1L, "Thử tự khoá", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bạn không thể tự khoá tài khoản đang đăng nhập");
        verify(userRepository, never()).save(any());
        verify(dealerRepository, never()).saveAll(any());
    }

    @Test
    void unlockRestoresLoginStateWithoutRevalidatingOldTokens() {
        User user = user(15L, "sales01", false, 5L);
        user.setLockReason("Nhân viên đã nghỉ việc");
        user.setFailedLoginAttempts(3);
        user.setLockTime(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(dealerRepository.countByAssignedUserId(15L)).thenReturn(2L);

        adminUserService.unlock(15L);

        assertThat(user.getActive()).isTrue();
        assertThat(user.getLockReason()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockTime()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(5L);
    }

    private User user(Long id, String username, boolean active, long tokenVersion) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .fullName(username)
                .password("encoded")
                .active(active)
                .tokenVersion(tokenVersion)
                .build();
    }
}

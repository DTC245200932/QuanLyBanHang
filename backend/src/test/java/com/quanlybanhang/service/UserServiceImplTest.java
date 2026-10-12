package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.UserProfileRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.UserProfileResponse;
import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Role;
import com.quanlybanhang.model.Territory;
import com.quanlybanhang.model.User;
import com.quanlybanhang.model.Warehouse;
import com.quanlybanhang.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("Lấy hồ sơ người dùng thành công kèm vai trò, kho, địa bàn và avatar URL")
    void profileIncludesStoredAvatarUrls() {
        Role role = Role.builder().id(1L).name(ERole.ROLE_SALES).build();
        Warehouse warehouse = Warehouse.builder().id(2L).code("KHO_HN").name("Kho Hà Nội").build();
        Territory territory = Territory.builder().id(3L).code("DB_MB").name("Miền Bắc").build();

        User user = User.builder()
                .id(7L)
                .username("admin")
                .email("admin@quanlybanhang.com")
                .password("encoded")
                .fullName("Quản trị viên")
                .phone("0901234567")
                .avatarFileName("7-avatar.png")
                .avatarThumbnailFileName("7-avatar-thumb.png")
                .avatarUpdatedAt(LocalDateTime.of(2026, 10, 3, 12, 0))
                .roles(Set.of(role))
                .warehouses(Set.of(warehouse))
                .territories(Set.of(territory))
                .active(true)
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        UserProfileResponse profile = userService.getUserProfile("admin");

        assertThat(profile.getId()).isEqualTo(7L);
        assertThat(profile.getUsername()).isEqualTo("admin");
        assertThat(profile.getEmail()).isEqualTo("admin@quanlybanhang.com");
        assertThat(profile.getFullName()).isEqualTo("Quản trị viên");
        assertThat(profile.getPhone()).isEqualTo("0901234567");
        assertThat(profile.getRoles()).containsExactly("ROLE_SALES");
        assertThat(profile.getWarehouses()).hasSize(1);
        assertThat(profile.getWarehouses().get(0).name()).isEqualTo("Kho Hà Nội");
        assertThat(profile.getTerritories()).hasSize(1);
        assertThat(profile.getTerritories().get(0).name()).isEqualTo("Miền Bắc");
        assertThat(profile.getAvatarUrl())
                .startsWith("/auth/users/7/avatar?thumbnail=false&v=");
        assertThat(profile.getAvatarThumbnailUrl())
                .startsWith("/auth/users/7/avatar?thumbnail=true&v=");
    }

    @Test
    @DisplayName("Lấy hồ sơ thất bại khi không tìm thấy người dùng")
    void getUserProfileThrowsWhenNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy người dùng");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0901234567",
            "0321234567",
            "0561234567",
            "0771234567",
            "0881234567",
            "+84901234567",
            "84901234567"
    })
    @DisplayName("Cập nhật họ tên và số điện thoại Việt Nam hợp lệ thành công")
    void updateUserProfileSuccessWithValidPhone(String validPhone) {
        Role role = Role.builder().id(1L).name(ERole.ROLE_SALES).build();
        Warehouse warehouse = Warehouse.builder().id(2L).code("KHO_HN").name("Kho Hà Nội").build();
        Territory territory = Territory.builder().id(3L).code("DB_MB").name("Miền Bắc").build();

        User user = User.builder()
                .id(10L)
                .username("sales1")
                .email("sales1@company.com")
                .fullName("Tên cũ")
                .phone("0900000000")
                .roles(Set.of(role))
                .warehouses(Set.of(warehouse))
                .territories(Set.of(territory))
                .active(true)
                .build();

        when(userRepository.findByUsername("sales1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileRequest request = UserProfileRequest.builder()
                .fullName("  Nguyễn Văn Mới  ")
                .phone("  " + validPhone + "  ")
                .build();

        ApiResponse response = userService.updateUserProfile("sales1", request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Cập nhật hồ sơ thành công");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        // Họ tên và sđt được cập nhật và cắt tỉa khoảng trắng
        assertThat(savedUser.getFullName()).isEqualTo("Nguyễn Văn Mới");
        assertThat(savedUser.getPhone()).isEqualTo(validPhone);

        // Không tự đổi tài khoản, email, vai trò, kho, địa bàn, active
        assertThat(savedUser.getUsername()).isEqualTo("sales1");
        assertThat(savedUser.getEmail()).isEqualTo("sales1@company.com");
        assertThat(savedUser.getRoles()).containsExactly(role);
        assertThat(savedUser.getWarehouses()).containsExactly(warehouse);
        assertThat(savedUser.getTerritories()).containsExactly(territory);
        assertThat(savedUser.getActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0123456789",   // Đầu 01 cũ 11 số hoặc không hợp lệ
            "0901234",      // Quá ngắn
            "090123456789", // Quá dài
            "090123456a",   // Chứa ký tự chữ
            "+1901234567",  // Mã vùng nước ngoài
            "0243123456",   // Điện thoại bàn
            "abc"
    })
    @DisplayName("Từ chối cập nhật khi số điện thoại không đúng định dạng Việt Nam")
    void updateUserProfileRejectsInvalidPhone(String invalidPhone) {
        User user = User.builder()
                .id(10L)
                .username("sales1")
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .build();
        when(userRepository.findByUsername("sales1")).thenReturn(Optional.of(user));

        UserProfileRequest request = UserProfileRequest.builder()
                .fullName("Nguyễn Văn A")
                .phone(invalidPhone)
                .build();

        assertThatThrownBy(() -> userService.updateUserProfile("sales1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Số điện thoại không đúng định dạng Việt Nam");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối cập nhật khi họ tên để trống")
    void updateUserProfileRejectsBlankFullName() {
        User user = User.builder().id(10L).username("sales1").build();
        when(userRepository.findByUsername("sales1")).thenReturn(Optional.of(user));

        UserProfileRequest request = UserProfileRequest.builder()
                .fullName("   ")
                .phone("0901234567")
                .build();

        assertThatThrownBy(() -> userService.updateUserProfile("sales1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Họ tên không được để trống");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối cập nhật khi họ tên vượt quá 100 ký tự")
    void updateUserProfileRejectsTooLongFullName() {
        User user = User.builder().id(10L).username("sales1").build();
        when(userRepository.findByUsername("sales1")).thenReturn(Optional.of(user));

        String longName = "A".repeat(101);
        UserProfileRequest request = UserProfileRequest.builder()
                .fullName(longName)
                .phone("0901234567")
                .build();

        assertThatThrownBy(() -> userService.updateUserProfile("sales1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Họ tên không được vượt quá 100 ký tự");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối cập nhật khi không tìm thấy người dùng")
    void updateUserProfileThrowsWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        UserProfileRequest request = UserProfileRequest.builder()
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .build();

        assertThatThrownBy(() -> userService.updateUserProfile("unknown", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy người dùng");
    }
}

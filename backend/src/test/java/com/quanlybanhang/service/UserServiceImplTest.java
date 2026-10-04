package com.quanlybanhang.service;

import com.quanlybanhang.dto.response.UserProfileResponse;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void profileIncludesStoredAvatarUrls() {
        User user = User.builder()
                .id(7L)
                .username("admin")
                .email("admin@quanlybanhang.com")
                .password("encoded")
                .fullName("Quản trị viên")
                .avatarFileName("7-avatar.png")
                .avatarThumbnailFileName("7-avatar-thumb.png")
                .avatarUpdatedAt(LocalDateTime.of(2026, 10, 3, 12, 0))
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        UserProfileResponse profile = userService.getUserProfile("admin");

        assertThat(profile.getAvatarUrl())
                .startsWith("/auth/users/7/avatar?thumbnail=false&v=");
        assertThat(profile.getAvatarThumbnailUrl())
                .startsWith("/auth/users/7/avatar?thumbnail=true&v=");
    }
}

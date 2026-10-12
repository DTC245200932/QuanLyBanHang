package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.UserProfileRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.NamedResourceResponse;
import com.quanlybanhang.dto.response.UserProfileResponse;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Pattern VIETNAMESE_PHONE_PATTERN = Pattern.compile("^(0|\\+84|84)[35789]\\d{8}$");

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public ApiResponse updateUserProfile(String username, UserProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Họ tên không được để trống");
        }
        if (request.getFullName().trim().length() > 100) {
            throw new IllegalArgumentException("Họ tên không được vượt quá 100 ký tự");
        }

        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        String trimmedPhone = request.getPhone().trim();
        if (!VIETNAMESE_PHONE_PATTERN.matcher(trimmedPhone).matches()) {
            throw new IllegalArgumentException("Số điện thoại không đúng định dạng Việt Nam");
        }

        user.setFullName(request.getFullName().trim());
        user.setPhone(trimmedPhone);

        User savedUser = userRepository.save(user);

        return ApiResponse.success("Cập nhật hồ sơ thành công", toProfileResponse(savedUser));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(AvatarService.avatarUrl(user, false))
                .avatarThumbnailUrl(AvatarService.avatarUrl(user, true))
                .roles(user.getRoles() != null ? user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .sorted()
                        .toList() : List.of())
                .warehouses(user.getWarehouses() != null ? user.getWarehouses().stream()
                        .map(warehouse -> new NamedResourceResponse(
                                warehouse.getId(), warehouse.getCode(), warehouse.getName()))
                        .toList() : List.of())
                .territories(user.getTerritories() != null ? user.getTerritories().stream()
                        .map(territory -> new NamedResourceResponse(
                                territory.getId(), territory.getCode(), territory.getName()))
                        .toList() : List.of())
                .active(user.getActive())
                .build();
    }
}

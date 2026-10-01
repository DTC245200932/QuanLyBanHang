package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.UserProfileRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.UserProfileResponse;

public interface UserService {
    UserProfileResponse getUserProfile(String username);
    ApiResponse updateUserProfile(String username, UserProfileRequest request);
}

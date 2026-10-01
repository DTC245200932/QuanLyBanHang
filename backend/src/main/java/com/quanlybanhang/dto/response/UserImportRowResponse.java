package com.quanlybanhang.dto.response;

import com.quanlybanhang.dto.request.AdminUserRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportRowResponse {
    private int rowNumber;
    private AdminUserRequest row;
    private boolean valid;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private UserResponse createdUser;
}

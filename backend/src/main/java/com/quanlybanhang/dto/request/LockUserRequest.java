package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LockUserRequest {
    @NotBlank(message = "Bắt buộc nhập lý do khoá")
    @Size(max = 500, message = "Lý do khoá tối đa 500 ký tự")
    private String reason;
}

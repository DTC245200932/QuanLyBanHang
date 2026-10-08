package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO yêu cầu khoá giao dịch đại lý (S3-07).
 * Bắt buộc nhập lý do khoá (tối đa 500 ký tự).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LockDealerRequest {

    @NotBlank(message = "Bắt buộc nhập lý do khoá")
    @Size(max = 500, message = "Lý do khoá tối đa 500 ký tự")
    private String reason;
}


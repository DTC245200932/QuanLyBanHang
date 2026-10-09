package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request phân công một nhân viên phụ trách chính cho một đại lý (S3-06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDealerRequest {

    @NotNull(message = "ID nhân viên phụ trách không được để trống")
    private Long assignedUserId;

    @Size(max = 500, message = "Lý do phân công tối đa 500 ký tự")
    private String reason;
}


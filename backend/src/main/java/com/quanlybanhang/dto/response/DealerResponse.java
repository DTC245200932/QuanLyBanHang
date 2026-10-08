package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.CustomerGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response chi tiết hồ sơ đại lý (S3-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerResponse {
    private Long id;
    private String code;
    private String name;
    private String taxCode;
    private CustomerGroup customerGroup;
    private String customerGroupDisplayName;

    // Territory info
    private Long territoryId;
    private String territoryCode;
    private String territoryName;

    // Assigned User info
    private Long assignedUserId;
    private String assignedUsername;
    private String assignedUserFullName;

    private Boolean active;
    private Boolean handoverRequired;
    private BigDecimal debtLimit;
    private Integer maxDebtDays;

    private String phone;
    private String email;
    private String address;

    /** Đại lý đã phát sinh giao dịch thì true; không thể xóa mà chỉ ngừng giao dịch. */
    private boolean hasTransactions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


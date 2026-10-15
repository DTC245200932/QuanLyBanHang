package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.CustomerGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request tạo mới hoặc cập nhật hồ sơ đại lý (S3-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerRequest {

    @NotBlank(message = "Mã đại lý không được để trống")
    @Size(max = 50, message = "Mã đại lý không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên đại lý không được để trống")
    @Size(max = 150, message = "Tên đại lý không được vượt quá 150 ký tự")
    private String name;

    @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
    private String taxCode;

    @NotNull(message = "Nhóm khách hàng không được để trống")
    private CustomerGroup customerGroup;

    private Long territoryId;

    private Long warehouseId;

    private Long assignedUserId;

    private Boolean active;

    private BigDecimal debtLimit;

    @jakarta.validation.constraints.Min(value = 0, message = "Số ngày nợ tối đa không được âm")
    private Integer maxDebtDays;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    @Pattern(regexp = "^(?:$|(?:0|\\+84|84)[35789]\\d{8})$", message = "Số điện thoại không đúng định dạng Việt Nam")
    private String phone;

    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;
}


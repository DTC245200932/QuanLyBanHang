package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank(message = "Mã nhà cung cấp không được để trống")
    @Size(max = 30, message = "Mã nhà cung cấp không được vượt quá 30 ký tự")
    private String code;

    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    @Size(max = 120, message = "Tên nhà cung cấp không được vượt quá 120 ký tự")
    private String name;

    @Size(max = 30, message = "Mã số thuế không được vượt quá 30 ký tự")
    private String taxCode;

    @Size(max = 120, message = "Người liên hệ không được vượt quá 120 ký tự")
    private String contactPerson;

    @Size(max = 200, message = "Điều khoản thanh toán không được vượt quá 200 ký tự")
    private String paymentTerms;
    
    private Boolean active;
}

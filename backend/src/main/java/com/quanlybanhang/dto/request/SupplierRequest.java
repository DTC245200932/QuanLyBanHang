package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierRequest {
    @Size(max = 30, message = "Mã nhà cung cấp không được vượt quá 30 ký tự")
    private String code;

    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    @Size(max = 120, message = "Tên nhà cung cấp không được vượt quá 120 ký tự")
    private String name;

    @Size(max = 30, message = "Mã số thuế không được vượt quá 30 ký tự")
    private String taxCode;

    @Size(max = 120, message = "Người liên hệ không được vượt quá 120 ký tự")
    private String contactPerson;

    @Size(max = 20, message = "Điện thoại liên hệ tối đa 20 ký tự")
    @Pattern(regexp = "^(?:$|(?:0|\\+84|84)[35789]\\d{8})$", message = "Số điện thoại không đúng định dạng Việt Nam")
    private String contactPhone;

    @Email(message = "Email không hợp lệ")
    @Size(max = 150, message = "Email tối đa 150 ký tự")
    private String email;

    @Size(max = 300, message = "Địa chỉ tối đa 300 ký tự")
    private String address;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String notes;

    @Size(max = 200, message = "Điều khoản thanh toán không được vượt quá 200 ký tự")
    private String paymentTerms;
    
    private Boolean active;
}

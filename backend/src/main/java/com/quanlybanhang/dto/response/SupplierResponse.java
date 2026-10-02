package com.quanlybanhang.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupplierResponse {
    private Long id;
    private String code;
    private String name;
    private String taxCode;
    private String contactPerson;
    private String paymentTerms;
    private Boolean active;
}

package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.SupplierRequest;
import com.quanlybanhang.dto.response.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupplierService {
    Page<SupplierResponse> getAllSuppliers(String keyword, Pageable pageable);

    SupplierResponse getSupplierById(Long id);

    SupplierResponse createSupplier(SupplierRequest request);

    SupplierResponse updateSupplier(Long id, SupplierRequest request);

    void deleteSupplier(Long id);
}

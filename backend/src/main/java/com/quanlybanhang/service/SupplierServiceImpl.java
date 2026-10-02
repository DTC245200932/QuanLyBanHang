package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.SupplierRequest;
import com.quanlybanhang.dto.response.SupplierResponse;
import com.quanlybanhang.model.Supplier;
import com.quanlybanhang.repository.SupplierRepository;
import com.quanlybanhang.repository.WarehouseVoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final WarehouseVoucherRepository warehouseVoucherRepository;

    @Override
    public Page<SupplierResponse> getAllSuppliers(String keyword, Pageable pageable) {
        Page<Supplier> suppliers;
        if (keyword != null && !keyword.trim().isEmpty()) {
            suppliers = supplierRepository.searchSuppliers(keyword.trim(), pageable);
        } else {
            suppliers = supplierRepository.findAll(pageable);
        }
        return suppliers.map(this::mapToResponse);
    }

    @Override
    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhà cung cấp"));
        return mapToResponse(supplier);
    }

    @Override
    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        if (supplierRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Mã nhà cung cấp đã tồn tại");
        }

        Supplier supplier = Supplier.builder()
                .code(request.getCode())
                .name(request.getName())
                .taxCode(request.getTaxCode())
                .contactPerson(request.getContactPerson())
                .paymentTerms(request.getPaymentTerms())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        supplier = supplierRepository.save(supplier);
        return mapToResponse(supplier);
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhà cung cấp"));

        if (!supplier.getCode().equals(request.getCode()) && supplierRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Mã nhà cung cấp đã tồn tại");
        }

        supplier.setCode(request.getCode());
        supplier.setName(request.getName());
        supplier.setTaxCode(request.getTaxCode());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPaymentTerms(request.getPaymentTerms());
        
        if (request.getActive() != null) {
            supplier.setActive(request.getActive());
        }

        supplier = supplierRepository.save(supplier);
        return mapToResponse(supplier);
    }

    @Override
    @Transactional
    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhà cung cấp"));

        if (warehouseVoucherRepository.existsBySupplierId(id)) {
            throw new RuntimeException("Không thể xóa nhà cung cấp đã có phiếu kho (chỉ có thể ngừng giao dịch)");
        }

        supplierRepository.delete(supplier);
    }

    private SupplierResponse mapToResponse(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .code(supplier.getCode())
                .name(supplier.getName())
                .taxCode(supplier.getTaxCode())
                .contactPerson(supplier.getContactPerson())
                .paymentTerms(supplier.getPaymentTerms())
                .active(supplier.getActive())
                .build();
    }
}

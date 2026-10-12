package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.SupplierRequest;
import com.quanlybanhang.dto.response.SupplierResponse;
import com.quanlybanhang.model.Supplier;
import com.quanlybanhang.repository.SupplierRepository;
import com.quanlybanhang.repository.WarehouseVoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private static final String CODE_PREFIX = "NCC";
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

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
        String code = normalizeCode(request.getCode());
        if (code == null) {
            code = generateUniqueCode();
        } else if (supplierRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã nhà cung cấp đã tồn tại");
        }

        Supplier supplier = Supplier.builder()
                .code(code)
                .name(request.getName().trim())
                .taxCode(request.getTaxCode())
                .contactPerson(request.getContactPerson())
                .contactPhone(request.getContactPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .notes(request.getNotes())
                .paymentTerms(request.getPaymentTerms())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        supplier = saveWithUniqueCode(supplier);
        return mapToResponse(supplier);
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhà cung cấp"));

        String code = normalizeCode(request.getCode());
        // Bỏ trống khi sửa hồ sơ thì giữ mã đã cấp, không sinh lại mã.
        if (code == null) code = supplier.getCode();
        if (supplierRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new IllegalArgumentException("Mã nhà cung cấp đã tồn tại");
        }

        supplier.setCode(code);
        supplier.setName(request.getName().trim());
        supplier.setTaxCode(request.getTaxCode());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setContactPhone(request.getContactPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setNotes(request.getNotes());
        supplier.setPaymentTerms(request.getPaymentTerms());
        
        if (request.getActive() != null) {
            supplier.setActive(request.getActive());
        }

        supplier = saveWithUniqueCode(supplier);
        return mapToResponse(supplier);
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(Long id, boolean active) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp"));
        supplier.setActive(active);
        return mapToResponse(supplierRepository.save(supplier));
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
                .contactPhone(supplier.getContactPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .notes(supplier.getNotes())
                .paymentTerms(supplier.getPaymentTerms())
                .active(supplier.getActive())
                .build();
    }

    private String normalizeCode(String input) {
        if (input == null || input.isBlank()) return null;
        String code = Normalizer.normalize(input.trim(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
        if (code.equals(CODE_PREFIX)) return null;
        if (!code.startsWith(CODE_PREFIX)) code = CODE_PREFIX + code;
        if (code.length() > 30) {
            throw new IllegalArgumentException("Mã nhà cung cấp gồm tiền tố NCC tối đa 30 ký tự");
        }
        if (code.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Mã nhà cung cấp không được chứa ký tự điều khiển");
        }
        return code;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = CODE_PREFIX + String.format(Locale.ROOT, "%09d", CODE_RANDOM.nextInt(1_000_000_000));
            if (!supplierRepository.existsByCodeIgnoreCase(code)) return code;
        }
        throw new IllegalStateException("Chưa tạo được mã nhà cung cấp duy nhất. Vui lòng thử lại.");
    }

    private Supplier saveWithUniqueCode(Supplier supplier) {
        try {
            // Flush để ràng buộc UNIQUE của DB chặn cả hai yêu cầu đồng thời.
            return supplierRepository.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Mã nhà cung cấp đã tồn tại. Vui lòng chọn mã khác hoặc để trống để tạo tự động.");
        }
    }
}

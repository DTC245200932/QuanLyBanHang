package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.UnitConversionRequest;
import com.quanlybanhang.dto.response.UnitConversionResponse;
import com.quanlybanhang.model.Permission;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.model.UnitConversion;
import com.quanlybanhang.repository.ProductRepository;
import com.quanlybanhang.repository.UnitConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnitConversionServiceImpl implements UnitConversionService {

    private final UnitConversionRepository unitConversionRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UnitConversionResponse> getConversionsByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + productId));

        List<UnitConversionResponse> result = new ArrayList<>();

        // Luôn đưa đơn vị cơ sở vào danh sách với hệ số quy đổi = 1.0
        result.add(UnitConversionResponse.builder()
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .unitName(product.getUnit())
                .conversionFactor(BigDecimal.ONE)
                .baseUnit(product.getUnit())
                .isBaseUnit(true)
                .price(product.getPrice())
                .costPrice(isSalesManager() ? product.getCostPrice() : null)
                .description("Đơn vị cơ sở của sản phẩm")
                .build());

        // Thêm các đơn vị quy đổi phụ (vd: lốc, thùng)
        List<UnitConversion> conversions = unitConversionRepository.findByProductIdOrderByConversionFactorAsc(productId);
        for (UnitConversion c : conversions) {
            result.add(mapToResponse(c));
        }

        return result;
    }

    @Override
    @Transactional
    public UnitConversionResponse addConversion(Long productId, UnitConversionRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + productId));

        String unitName = request.getUnitName().trim();

        if (product.getUnit() != null && product.getUnit().equalsIgnoreCase(unitName)) {
            throw new IllegalArgumentException("Đơn vị '" + unitName + "' trùng với đơn vị cơ sở của sản phẩm. Không cần khai báo thêm.");
        }

        if (unitConversionRepository.existsByProductIdAndUnitNameIgnoreCase(productId, unitName)) {
            throw new IllegalArgumentException("Đơn vị quy đổi '" + unitName + "' đã được khai báo cho sản phẩm này.");
        }

        if (request.getConversionFactor() == null || request.getConversionFactor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Hệ số quy đổi phải lớn hơn 0");
        }

        UnitConversion conversion = UnitConversion.builder()
                .product(product)
                .unitName(unitName)
                .conversionFactor(request.getConversionFactor())
                .isBaseUnit(false)
                .price(request.getPrice())
                .costPrice(isSalesManager() ? request.getCostPrice() : null)
                .barcode(request.getBarcode())
                .description(request.getDescription())
                .build();

        UnitConversion saved = unitConversionRepository.save(conversion);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public UnitConversionResponse updateConversion(Long productId, Long conversionId, UnitConversionRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + productId));

        UnitConversion conversion = unitConversionRepository.findById(conversionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị quy đổi với ID: " + conversionId));

        if (!conversion.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Đơn vị quy đổi không thuộc về sản phẩm này");
        }

        String unitName = request.getUnitName().trim();

        if (product.getUnit() != null && product.getUnit().equalsIgnoreCase(unitName)) {
            throw new IllegalArgumentException("Đơn vị '" + unitName + "' trùng với đơn vị cơ sở của sản phẩm");
        }

        if (unitConversionRepository.existsByProductIdAndUnitNameIgnoreCaseAndIdNot(productId, unitName, conversionId)) {
            throw new IllegalArgumentException("Đơn vị quy đổi '" + unitName + "' đã được sử dụng cho sản phẩm này");
        }

        if (request.getConversionFactor() == null || request.getConversionFactor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Hệ số quy đổi phải lớn hơn 0");
        }

        conversion.setUnitName(unitName);
        conversion.setConversionFactor(request.getConversionFactor());
        conversion.setPrice(request.getPrice());
        if (isSalesManager()) {
            conversion.setCostPrice(request.getCostPrice());
        }
        conversion.setBarcode(request.getBarcode());
        conversion.setDescription(request.getDescription());

        UnitConversion saved = unitConversionRepository.save(conversion);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteConversion(Long productId, Long conversionId) {
        UnitConversion conversion = unitConversionRepository.findById(conversionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị quy đổi với ID: " + conversionId));

        if (!conversion.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Đơn vị quy đổi không thuộc về sản phẩm này");
        }

        unitConversionRepository.delete(conversion);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getConversionFactor(Product product, String unitName) {
        if (unitName == null || unitName.isBlank()) {
            return BigDecimal.ONE;
        }

        String trimmed = unitName.trim();

        // 1. Kiểm tra nếu trùng đơn vị cơ sở của sản phẩm (vd: "lon")
        if (product.getUnit() != null && product.getUnit().equalsIgnoreCase(trimmed)) {
            return BigDecimal.ONE;
        }

        // 2. Tìm trong danh sách unitConversions đã nạp hoặc truy vấn repository
        if (product.getUnitConversions() != null && !product.getUnitConversions().isEmpty()) {
            for (UnitConversion uc : product.getUnitConversions()) {
                if (uc.getUnitName() != null && uc.getUnitName().equalsIgnoreCase(trimmed)) {
                    return uc.getConversionFactor();
                }
            }
        }

        // 3. Nếu chưa có trong cache collection, truy vấn qua repository
        Optional<UnitConversion> found = unitConversionRepository.findByProductIdAndUnitNameIgnoreCase(product.getId(), trimmed);
        if (found.isPresent()) {
            return found.get().getConversionFactor();
        }

        throw new IllegalArgumentException("Đơn vị tính '" + unitName + "' không hợp lệ cho SKU '" + product.getCode() +
                "' (" + product.getName() + "). Đơn vị cơ sở: '" + product.getUnit() + "'");
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal convertToBaseQuantity(Product product, String unitName, BigDecimal quantity) {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal factor = getConversionFactor(product, unitName);
        return quantity.multiply(factor);
    }

    @Override
    public UnitConversionResponse mapToResponse(UnitConversion conversion) {
        return UnitConversionResponse.builder()
                .id(conversion.getId())
                .productId(conversion.getProduct() != null ? conversion.getProduct().getId() : null)
                .productCode(conversion.getProduct() != null ? conversion.getProduct().getCode() : null)
                .productName(conversion.getProduct() != null ? conversion.getProduct().getName() : null)
                .unitName(conversion.getUnitName())
                .conversionFactor(conversion.getConversionFactor())
                .baseUnit(conversion.getProduct() != null ? conversion.getProduct().getUnit() : null)
                .isBaseUnit(conversion.getIsBaseUnit() != null ? conversion.getIsBaseUnit() : false)
                .price(conversion.getPrice())
                .costPrice(isSalesManager() ? conversion.getCostPrice() : null)
                .barcode(conversion.getBarcode())
                .description(conversion.getDescription())
                .createdAt(conversion.getCreatedAt())
                .updatedAt(conversion.getUpdatedAt())
                .build();
    }

    private boolean isSalesManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_SALES_MANAGER".equals(authority.getAuthority()) ||
                        Permission.FINANCE_VIEW_COST_PRICE.name().equals(authority.getAuthority()));
    }
}

package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.UnitConversionRequest;
import com.quanlybanhang.dto.response.UnitConversionResponse;
import com.quanlybanhang.model.Product;

import java.math.BigDecimal;
import java.util.List;

public interface UnitConversionService {

    List<UnitConversionResponse> getConversionsByProductId(Long productId);

    UnitConversionResponse addConversion(Long productId, UnitConversionRequest request);

    UnitConversionResponse updateConversion(Long productId, Long conversionId, UnitConversionRequest request);

    void deleteConversion(Long productId, Long conversionId);

    /**
     * Lấy hệ số quy đổi của đơn vị tính đối với sản phẩm.
     * Nếu unitName trùng đơn vị cơ sở (vd: "lon") -> trả về 1.0.
     * Nếu unitName là đơn vị quy đổi (vd: "thùng") -> trả về conversionFactor đã cấu hình (vd: 24.0).
     * Nếu không tìm thấy -> ném ngoại lệ IllegalArgumentException.
     */
    BigDecimal getConversionFactor(Product product, String unitName);

    /**
     * Quy đổi số lượng bất kỳ về đơn vị cơ sở:
     * baseQuantity = quantity * conversionFactor.
     */
    BigDecimal convertToBaseQuantity(Product product, String unitName, BigDecimal quantity);

    UnitConversionResponse mapToResponse(com.quanlybanhang.model.UnitConversion conversion);
}

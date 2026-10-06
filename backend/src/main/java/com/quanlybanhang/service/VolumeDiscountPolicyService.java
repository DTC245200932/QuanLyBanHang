package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.VolumeDiscountCalculateRequest;
import com.quanlybanhang.dto.request.VolumeDiscountPolicyRequest;
import com.quanlybanhang.dto.response.VolumeDiscountCalculationResponse;
import com.quanlybanhang.dto.response.VolumeDiscountPolicyResponse;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.DiscountTargetType;
import com.quanlybanhang.model.Product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý chính sách chiết khấu theo sản lượng (S3-01).
 */
public interface VolumeDiscountPolicyService {

    VolumeDiscountPolicyResponse createPolicy(VolumeDiscountPolicyRequest request, String username);

    VolumeDiscountPolicyResponse updatePolicy(Long id, VolumeDiscountPolicyRequest request, String username);

    void deletePolicy(Long id, String username);

    VolumeDiscountPolicyResponse toggleActive(Long id, Boolean active, String username);

    VolumeDiscountPolicyResponse getPolicyById(Long id);

    List<VolumeDiscountPolicyResponse> getAllPolicies(
            DiscountTargetType targetType,
            Long productId,
            Long categoryId,
            CustomerGroup customerGroup,
            Boolean active,
            String keyword);

    /**
     * Tính toán thử chiết khấu sản lượng cho danh sách mặt hàng (phục vụ frontend preview / kiểm tra).
     */
    VolumeDiscountCalculationResponse calculateDiscounts(VolumeDiscountCalculateRequest request);

    /**
     * DTO nội bộ đại diện dữ liệu đầu vào của một dòng đơn hàng khi tính chiết khấu.
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class OrderItemDiscountInput {
        private int index;
        private Product product;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal conversionFactor;
        private BigDecimal baseQuantity;
        private String baseUnit;
        private BigDecimal price;
        private BigDecimal subtotalAmount;
    }

    /**
     * DTO nội bộ kết quả chiết khấu cho một dòng sản phẩm.
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class ItemDiscountResult {
        private BigDecimal discountAmount;
        private BigDecimal discountPercent;
        private Long appliedPolicyId;
        private String appliedPolicyCode;
        private String appliedPolicyName;
        private String bestPolicyReason;
        private List<com.quanlybanhang.dto.response.CandidateDiscountPolicyInfo> candidatePolicies;
    }

    /**
     * DTO nội bộ tổng hợp chiết khấu của đơn hàng.
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class OrderDiscountSummary {
        private BigDecimal subtotalAmount;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
        private Map<Integer, ItemDiscountResult> itemResults;
    }

    /**
     * Tính toán và áp dụng chiết khấu theo sản lượng cho các dòng đơn hàng thực tế.
     * Quy tắc cốt lõi: Nhiều chính sách cùng áp dụng thì tự động lấy chính sách có lợi nhất cho khách (chiết khấu cao nhất).
     */
    OrderDiscountSummary calculateOrderDiscount(
            List<OrderItemDiscountInput> items,
            CustomerGroup customerGroup,
            LocalDate orderDate);
}


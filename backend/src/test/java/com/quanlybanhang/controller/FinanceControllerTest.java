package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.DebtLimitRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.ProductRepository;
import com.quanlybanhang.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceControllerTest {

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Authentication authentication;

    private FinanceController financeController;

    @BeforeEach
    void setUp() {
        financeController = new FinanceController(dealerRepository, auditLogService, productRepository);
    }

    @Test
    @DisplayName("Cập nhật hạn mức công nợ và số ngày nợ tối đa phải lưu DB và ghi nhật ký kèm lý do (S3-05)")
    void updateDebtLimitWritesAuditLog() {
        Dealer dealer = Dealer.builder()
                .id(7L)
                .name("Đại lý Minh Anh")
                .debtLimit(new BigDecimal("20000000"))
                .maxDebtDays(30)
                .build();
        DebtLimitRequest request = new DebtLimitRequest();
        request.setDebtLimit(new BigDecimal("50000000"));
        request.setMaxDebtDays(45);
        request.setReason("Tăng hạn mức quý 4");

        when(dealerRepository.findById(7L)).thenReturn(Optional.of(dealer));
        when(authentication.getName()).thenReturn("accountant01");

        ApiResponse response = financeController.updateDebtLimit(7L, request, authentication);

        assertEquals(new BigDecimal("50000000"), dealer.getDebtLimit());
        assertEquals(45, dealer.getMaxDebtDays());
        assertEquals("Đã cập nhật hạn mức công nợ", response.getMessage());
        assertEquals(new BigDecimal("50000000"), ((Map<?, ?>) response.getData()).get("debtLimit"));
        assertEquals(45, ((Map<?, ?>) response.getData()).get("maxDebtDays"));
        assertEquals("Tăng hạn mức quý 4", ((Map<?, ?>) response.getData()).get("reason"));
        verify(dealerRepository).save(dealer);
        verify(auditLogService).logAction(
                "DEBT_LIMIT", "7", "UPDATE", "20000000 (30 ngày)", "50000000 (45 ngày)", "Tăng hạn mức quý 4", "accountant01");
    }

    @Test
    @DisplayName("Bắt buộc nhập lý do khi thay đổi hạn mức công nợ (S3-05)")
    void updateDebtLimitRequiresReason() {
        Dealer dealer = Dealer.builder()
                .id(7L)
                .name("Đại lý Minh Anh")
                .debtLimit(new BigDecimal("20000000"))
                .maxDebtDays(30)
                .build();
        DebtLimitRequest request = new DebtLimitRequest();
        request.setDebtLimit(new BigDecimal("50000000"));
        request.setMaxDebtDays(45);
        request.setReason("   "); // empty / blank

        when(dealerRepository.findById(7L)).thenReturn(Optional.of(dealer));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                financeController.updateDebtLimit(7L, request, authentication));
        verify(dealerRepository, never()).save(dealer);
    }

    @Test
    @DisplayName("Không tạo nhật ký giả khi hạn mức và số ngày nợ không thay đổi (S3-05)")
    void unchangedDebtLimitDoesNotWriteAuditLog() {
        Dealer dealer = Dealer.builder()
                .id(7L)
                .name("Đại lý Minh Anh")
                .debtLimit(new BigDecimal("50000000.00"))
                .maxDebtDays(30)
                .build();
        DebtLimitRequest request = new DebtLimitRequest();
        request.setDebtLimit(new BigDecimal("50000000"));
        request.setMaxDebtDays(30);
        request.setReason("Kiểm tra không đổi");

        when(dealerRepository.findById(7L)).thenReturn(Optional.of(dealer));

        ApiResponse response = financeController.updateDebtLimit(7L, request, authentication);

        assertEquals("Hạn mức công nợ không thay đổi", response.getMessage());
        verify(dealerRepository, never()).save(dealer);
        verify(auditLogService, never()).logAction(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("API xem công nợ trả về cả hạn mức tiền và số ngày nợ tối đa (S3-05)")
    void getDebtReturnsMaxDebtDays() {
        Dealer dealer = Dealer.builder()
                .id(1L)
                .name("Đại lý Cấp 1")
                .debtLimit(new BigDecimal("100000000"))
                .maxDebtDays(60)
                .build();
        when(dealerRepository.findAll()).thenReturn(java.util.List.of(dealer));

        ApiResponse response = financeController.getDebt();
        assertEquals(true, response.isSuccess());

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) response.getData();
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).get("dealerId"));
        assertEquals("Đại lý Cấp 1", list.get(0).get("customerName"));
        assertEquals(new BigDecimal("100000000"), list.get(0).get("debtLimit"));
        assertEquals(60, list.get(0).get("maxDebtDays"));
        assertNull(list.get(0).get("debtAmount"));
        assertEquals(false, list.get(0).get("debtAmountAvailable"));
    }

    @Test
    void financeReturnsEmptyListsWhenCatalogIsEmpty() {
        when(productRepository.findAll()).thenReturn(List.of());
        assertEquals(List.of(), financeController.getCostPrice().getData());
        assertEquals(List.of(), financeController.getProfitMargin().getData());
    }

    @Test
    @SuppressWarnings("unchecked")
    void financeUsesStoredPricesAndKeepsNegativeMargin() {
        Product product = Product.builder().id(9L).code("CATALOG-9").name("Hàng trong danh mục")
                .price(new BigDecimal("300")).costPrice(new BigDecimal("400")).unit("hộp").build();
        when(productRepository.findAll()).thenReturn(List.of(product));
        Map<String, Object> cost = ((List<Map<String, Object>>) financeController.getCostPrice().getData()).get(0);
        assertEquals("CATALOG-9", cost.get("productCode"));
        assertEquals(product.getCostPrice(), cost.get("costPrice"));
        Map<String, Object> margin = ((List<Map<String, Object>>) financeController.getProfitMargin().getData()).get(0);
        assertEquals(new BigDecimal("-33.33"), margin.get("marginPercent"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void marginIsUnavailableForZeroSellingPrice() {
        Product product = Product.builder().id(9L).code("CATALOG-9")
                .price(BigDecimal.ZERO).costPrice(new BigDecimal("400")).build();
        when(productRepository.findAll()).thenReturn(List.of(product));
        Map<String, Object> margin = ((List<Map<String, Object>>) financeController.getProfitMargin().getData()).get(0);
        assertNull(margin.get("marginPercent"));
    }
}

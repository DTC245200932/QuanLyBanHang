package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.DebtLimitRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.repository.DealerRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private Authentication authentication;

    private FinanceController financeController;

    @BeforeEach
    void setUp() {
        financeController = new FinanceController(dealerRepository, auditLogService);
    }

    @Test
    @DisplayName("Cập nhật hạn mức công nợ phải lưu DB và ghi nhật ký giá trị trước/sau")
    void updateDebtLimitWritesAuditLog() {
        Dealer dealer = Dealer.builder()
                .id(7L)
                .name("Đại lý Minh Anh")
                .debtLimit(new BigDecimal("20000000"))
                .build();
        DebtLimitRequest request = new DebtLimitRequest();
        request.setDebtLimit(new BigDecimal("50000000"));

        when(dealerRepository.findById(7L)).thenReturn(Optional.of(dealer));
        when(authentication.getName()).thenReturn("accountant01");

        ApiResponse response = financeController.updateDebtLimit(7L, request, authentication);

        assertEquals(new BigDecimal("50000000"), dealer.getDebtLimit());
        assertEquals("Đã cập nhật hạn mức công nợ", response.getMessage());
        assertEquals(new BigDecimal("50000000"), ((Map<?, ?>) response.getData()).get("debtLimit"));
        verify(dealerRepository).save(dealer);
        verify(auditLogService).logAction(
                "DEBT_LIMIT", "7", "UPDATE", "20000000", "50000000", "accountant01");
    }

    @Test
    @DisplayName("Không tạo nhật ký giả khi hạn mức không thay đổi")
    void unchangedDebtLimitDoesNotWriteAuditLog() {
        Dealer dealer = Dealer.builder()
                .id(7L)
                .name("Đại lý Minh Anh")
                .debtLimit(new BigDecimal("50000000.00"))
                .build();
        DebtLimitRequest request = new DebtLimitRequest();
        request.setDebtLimit(new BigDecimal("50000000"));

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
                org.mockito.ArgumentMatchers.anyString());
    }
}

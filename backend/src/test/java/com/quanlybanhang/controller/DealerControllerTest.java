package com.quanlybanhang.controller;

import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.exception.DealerHasTransactionsException;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.DealerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DealerController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class})
class DealerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DealerService dealerService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNTANT", "SALES_MANAGER", "ADMIN", "SALES", "VIEWER"})
    @DisplayName("Các vai trò hợp lệ được phép xem danh sách đại lý (200 OK)")
    void allowedRolesCanReadDealers(String role) throws Exception {
        when(dealerService.getAllDealers(any(), any(), any(), any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(DealerResponse.builder()
                        .id(1L)
                        .code("DL001")
                        .name("Đại lý Cấp 1 Minh Phát")
                        .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                        .customerGroupDisplayName("Đại lý cấp 1")
                        .active(true)
                        .build()))
        );

        mvc.perform(get("/api/dealers").with(user("reader").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value("DL001"))
                .andExpect(jsonPath("$.data.content[0].name").value("Đại lý Cấp 1 Minh Phát"))
                .andExpect(jsonPath("$.data.content[0].customerGroupDisplayName").value("Đại lý cấp 1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNTANT", "SALES_MANAGER", "ADMIN"})
    @DisplayName("Kế toán, Quản lý kinh doanh và Admin được phép tạo đại lý mới (200 OK)")
    void managementRolesCanCreateDealer(String role) throws Exception {
        DealerResponse created = DealerResponse.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Cấp 1 Minh Phát")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .debtLimit(new BigDecimal("100000000"))
                .maxDebtDays(30)
                .active(true)
                .build();

        when(dealerService.createDealer(any(), any())).thenReturn(created);

        mvc.perform(post("/api/dealers").with(user("staff").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "code": "DL001",
                          "name": "Đại lý Cấp 1 Minh Phát",
                          "customerGroup": "DAI_LY_CAP_1",
                          "taxCode": "0101234567",
                          "phone": "0981234567",
                          "email": "minhphat@dealer.vn",
                          "address": "123 Lê Lợi, TP.HCM",
                          "debtLimit": 100000000,
                          "maxDebtDays": 30,
                          "active": true
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DL001"))
                .andExpect(jsonPath("$.data.name").value("Đại lý Cấp 1 Minh Phát"))
                .andExpect(jsonPath("$.data.maxDebtDays").value(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SALES", "VIEWER", "WAREHOUSE_STAFF", "WAREHOUSE_MANAGER"})
    @DisplayName("Các vai trò không đủ quyền bị chặn khi cố gắng tạo, sửa hoặc xóa đại lý (403 Forbidden)")
    void nonManagementRolesCannotModifyDealers(String role) throws Exception {
        mvc.perform(post("/api/dealers").with(user("user").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "code": "DL001",
                          "name": "Đại lý Test",
                          "customerGroup": "DAI_LY_CAP_1"
                        }
                        """))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/dealers/1").with(user("user").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "code": "DL001",
                          "name": "Đại lý Test",
                          "customerGroup": "DAI_LY_CAP_1"
                        }
                        """))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/dealers/1").with(user("user").roles(role)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/dealers/1/status").param("active", "false").with(user("user").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dealerService);
    }

    @Test
    @DisplayName("Chưa đăng nhập thì không thể truy cập API đại lý (401 Unauthorized)")
    void unauthenticatedAccessRejected() throws Exception {
        mvc.perform(get("/api/dealers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Xóa đại lý đã phát sinh giao dịch phải trả về 400 Bad Request kèm thông báo chi tiết (S3-03)")
    void deleteDealerWithTransactionsReturnsBadRequest() throws Exception {
        doThrow(new DealerHasTransactionsException("DL001", "Đại lý Minh Phát"))
                .when(dealerService).deleteDealer(eq(1L), any());

        mvc.perform(delete("/api/dealers/1").with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Đại lý 'Đại lý Minh Phát' (DL001) đã phát sinh giao dịch không thể xoá, chỉ có thể chuyển sang trạng thái ngừng giao dịch."));
    }

    @Test
    @DisplayName("Dữ liệu khai báo thiếu mã hoặc tên đại lý bị từ chối (400 Bad Request)")
    void invalidDataRejected() throws Exception {
        mvc.perform(post("/api/dealers").with(user("accountant").roles("ACCOUNTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "code": "",
                          "name": "",
                          "customerGroup": null
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(dealerService);
    }

    @Test
    @DisplayName("Khai báo số dòng mỗi trang vượt quá 200 bị từ chối")
    void excessivePageSizeRejected() throws Exception {
        mvc.perform(get("/api/dealers?size=250").with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dealerService);
    }

    @Test
    @DisplayName("Lấy danh mục tham chiếu options trả về 200 OK")
    void getOptionsReturnsOk() throws Exception {
        when(dealerService.getDealerOptions()).thenReturn(
                DealerOptionsResponse.builder()
                        .customerGroups(List.of(new DealerOptionsResponse.CustomerGroupOption("DAI_LY_CAP_1", "Đại lý cấp 1")))
                        .territories(List.of())
                        .assignedUsers(List.of())
                        .build());

        mvc.perform(get("/api/dealers/options").with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerGroups[0].value").value("DAI_LY_CAP_1"));
    }

    @Test
    @DisplayName("Khai báo số ngày nợ tối đa âm bị từ chối (400 Bad Request)")
    void negativeMaxDebtDaysRejected() throws Exception {
        mvc.perform(post("/api/dealers").with(user("accountant").roles("ACCOUNTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "code": "DL002",
                          "name": "Đại lý Test",
                          "customerGroup": "DAI_LY_CAP_1",
                          "maxDebtDays": -5
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(dealerService);
    }
}


package com.quanlybanhang.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.controller.StockCountController;
import com.quanlybanhang.dto.response.StockCountResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.model.StockCountStatus;
import com.quanlybanhang.service.StockCountService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockCountController.class)
@Import({SecurityConfig.class, RolePermissionConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class, ObjectMapper.class})
@DisplayName("StockCountSecurityTest - Kiểm thử phân quyền ma trận cho kiểm kê kho (S5-08)")
class StockCountSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockCountService stockCountService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThrough() throws Exception {
        doAnswer(i -> {
            ((FilterChain) i.getArgument(2)).doFilter(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("Chưa đăng nhập -> 401 Unauthorized")
    void unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/warehouse/stock-counts/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tài khoản không có quyền WAREHOUSE_VIEW hay WAREHOUSE_ADJUST -> 403 Forbidden")
    void withoutPermissions_Returns403() throws Exception {
        mockMvc.perform(get("/api/warehouse/stock-counts/options")
                        .with(user("sales").authorities(new SimpleGrantedAuthority("ROLE_SALES"), new SimpleGrantedAuthority("ORDER_CREATE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Thủ kho (ROLE_WAREHOUSE_STAFF) chỉ có WAREHOUSE_VIEW: Được xem/tạo/đếm nhưng KHÔNG ĐƯỢC chốt kiểm kê -> 403 Forbidden")
    void warehouseStaff_CannotCompleteStockCount_Returns403() throws Exception {
        var staffUser = user("staff").authorities(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"),
                new SimpleGrantedAuthority("WAREHOUSE_VIEW"),
                new SimpleGrantedAuthority("WAREHOUSE_IMPORT"),
                new SimpleGrantedAuthority("WAREHOUSE_EXPORT")
        );

        // Chốt kiểm kê bị 403 vì thiếu WAREHOUSE_ADJUST
        mockMvc.perform(post("/api/warehouse/stock-counts/1/complete")
                        .with(staffUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Thủ kho thử chốt\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Quản lý kho có WAREHOUSE_ADJUST: Được phép chốt kiểm kê -> 200 OK")
    void warehouseManager_CanCompleteStockCount_Returns200() throws Exception {
        var managerUser = user("manager").authorities(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"),
                new SimpleGrantedAuthority("WAREHOUSE_VIEW"),
                new SimpleGrantedAuthority("WAREHOUSE_ADJUST")
        );

        when(stockCountService.complete(eq(1L), any(), any()))
                .thenReturn(StockCountResponse.builder().id(1L).status(StockCountStatus.COMPLETED).build());

        mockMvc.perform(post("/api/warehouse/stock-counts/1/complete")
                        .with(managerUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Cân đối sổ sách\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_ADMIN luôn có toàn quyền chốt kiểm kê -> 200 OK")
    void admin_CanCompleteStockCount_Returns200() throws Exception {
        var adminUser = user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        when(stockCountService.complete(eq(1L), any(), any()))
                .thenReturn(StockCountResponse.builder().id(1L).status(StockCountStatus.COMPLETED).build());

        mockMvc.perform(post("/api/warehouse/stock-counts/1/complete")
                        .with(adminUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Admin duyệt chốt\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ma trận phân quyền: Thu hồi WAREHOUSE_ADJUST từ Quản lý kho -> bị chặn 403 ngay lập tức (không role fallback)")
    void revokingWarehouseAdjust_BlocksComplete_Returns403() throws Exception {
        // Chỉ có ROLE_WAREHOUSE_MANAGER nhưng quyền WAREHOUSE_ADJUST bị thu hồi khỏi token/ma trận
        var revokedUser = user("manager-revoked").authorities(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"),
                new SimpleGrantedAuthority("WAREHOUSE_VIEW")
        );

        mockMvc.perform(post("/api/warehouse/stock-counts/1/complete")
                        .with(revokedUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Thử chốt sau khi bị thu hồi quyền\"}"))
                .andExpect(status().isForbidden());
    }
}

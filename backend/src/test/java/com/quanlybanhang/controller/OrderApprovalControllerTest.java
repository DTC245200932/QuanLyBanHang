package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.request.OrderStatusUpdateRequest;
import com.quanlybanhang.dto.response.OrderApprovalHistoryResponse;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.dto.response.OrderViolationResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.repository.OrderDraftRepository;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.DealerDebtService;
import com.quanlybanhang.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class, ObjectMapper.class})
@DisplayName("OrderApprovalControllerTest - S4-05 API Duyệt đơn hàng")
class OrderApprovalControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private DealerDebtService dealerDebtService;

    @MockBean
    private OrderDraftRepository orderDraftRepository;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "sales_manager", authorities = {"ROLE_SALES_MANAGER", "ORDER_APPROVE", "ORDER_VIEW"})
    @DisplayName("GET /api/orders có phân trang và bộ lọc trả về dữ liệu chuẩn S4-05")
    void getOrders_PaginationAndFilter_ReturnsPage() throws Exception {
        OrderResponse resp = OrderResponse.builder()
                .id(1L)
                .orderCode("DH-001")
                .dealerName("Đại lý Minh Phát")
                .dealerCode("DL-01")
                .status("PENDING_APPROVAL")
                .totalAmount(new BigDecimal("150000000"))
                .approvalReasons(List.of("OVER_DEBT_LIMIT"))
                .debtExceeded(new BigDecimal("50000000"))
                .violations(List.of(OrderViolationResponse.builder()
                        .type("OVER_DEBT_LIMIT")
                        .value(new BigDecimal("50000000"))
                        .displayValue("50,000,000 đ")
                        .description("Vượt hạn mức nợ")
                        .build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getOrders(any(com.quanlybanhang.dto.request.OrderFilterRequest.class)))
                .thenReturn(com.quanlybanhang.dto.response.OrderFilterResult.builder()
                        .orders(List.of(resp)).totalOrders(1L).totalPages(1).build());

        mockMvc.perform(get("/api/orders?status=PENDING_APPROVAL&approvalRequired=true&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content[0].orderCode").value("DH-001"))
                .andExpect(jsonPath("$.content[0].dealerName").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.content[0].debtExceeded").value(50000000))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "sales_manager", authorities = {"ROLE_SALES_MANAGER", "ORDER_APPROVE"})
    @DisplayName("PATCH /api/orders/{id}/status - Duyệt đơn (APPROVED) thành công")
    void updateOrderStatus_Approve_Returns200() throws Exception {
        OrderResponse resp = OrderResponse.builder()
                .id(1L)
                .orderCode("DH-001")
                .status("RECORDED")
                .build();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
        req.setStatus("APPROVED");
        req.setComment("Duyệt đơn");

        when(orderService.updateOrderStatus(eq(1L), any(OrderStatusUpdateRequest.class), anyString()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECORDED"));
    }

    @Test
    @WithMockUser(username = "sales_manager", authorities = {"ROLE_SALES_MANAGER", "ORDER_APPROVE"})
    @DisplayName("PATCH /api/orders/{id}/status - Từ chối không nhập lý do -> trả về 400 Bad Request")
    void updateOrderStatus_RejectWithoutReason_Returns400() throws Exception {
        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
        req.setStatus("REJECTED");
        req.setComment("");

        when(orderService.updateOrderStatus(eq(1L), any(OrderStatusUpdateRequest.class), anyString()))
                .thenThrow(new IllegalArgumentException("Vui lòng nhập lý do/ý kiến từ chối đơn hàng"));

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Vui lòng nhập lý do/ý kiến từ chối đơn hàng"));
    }

    @Test
    @WithMockUser(username = "sales_manager", authorities = {"ROLE_SALES_MANAGER", "ORDER_APPROVE"})
    @DisplayName("PUT /api/orders/{id}/reject - Từ chối không có lý do -> trả về 400 Bad Request")
    void rejectOrder_WithoutReason_Returns400() throws Exception {
        when(orderService.rejectOrder(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Vui lòng nhập lý do/ý kiến từ chối đơn hàng"));

        mockMvc.perform(put("/api/orders/1/reject"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Vui lòng nhập lý do/ý kiến từ chối đơn hàng"));
    }

    @Test
    @WithMockUser(username = "sales_manager", authorities = {"ROLE_SALES_MANAGER", "ORDER_APPROVE"})
    @DisplayName("PUT /api/orders/{id}/return - Trả lại sửa không có lý do -> trả về 400 Bad Request")
    void returnOrder_WithoutReason_Returns400() throws Exception {
        when(orderService.returnOrder(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Vui lòng nhập lý do/ý kiến trả lại sửa đơn hàng"));

        mockMvc.perform(put("/api/orders/1/return"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Vui lòng nhập lý do/ý kiến trả lại sửa đơn hàng"));
    }

    @Test
    @WithMockUser(username = "sales_manager", authorities = {"ROLE_SALES_MANAGER", "ORDER_APPROVE"})
    @DisplayName("PUT /api/orders/{id}/return - Trả lại sửa có lý do -> thành công 200")
    void returnOrder_WithReason_Returns200() throws Exception {
        OrderResponse resp = OrderResponse.builder()
                .id(1L)
                .orderCode("DH-001")
                .status("RETURNED")
                .build();

        when(orderService.returnOrder(eq(1L), eq("Cần giảm số lượng"), any()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/orders/1/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Cần giảm số lượng"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RETURNED"));
    }

    @Test
    @WithMockUser(username = "sales_user", roles = "SALES")
    @DisplayName("Vai trò SALES không có quyền ORDER_APPROVE -> gọi duyệt đơn bị 403 Forbidden")
    void approveOrder_SalesUser_Returns403() throws Exception {
        when(orderService.approveOrder(1L, "sales_user"))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Đơn ngoài phạm vi phụ trách"));
        mockMvc.perform(put("/api/orders/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sales_user", roles = "SALES")
    @DisplayName("Vai trò SALES gọi PATCH status đơn hàng bị 403 Forbidden")
    void updateOrderStatus_SalesUser_Returns403() throws Exception {
        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest();
        req.setStatus("APPROVED");

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", authorities = {"ROLE_ADMIN", "ORDER_APPROVE"})
    @DisplayName("GET /api/orders/{id}/approval-history - Trả về danh sách lịch sử duyệt")
    void getOrderApprovalHistory_Returns200() throws Exception {
        OrderApprovalHistoryResponse item = OrderApprovalHistoryResponse.builder()
                .id(10L)
                .orderId(1L)
                .orderCode("DH-001")
                .action("APPROVED")
                .comment("Đã duyệt")
                .performedBy("admin_user")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getOrderApprovalHistory(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/orders/1/approval-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.data[0].performedBy").value("admin_user"));
    }
}

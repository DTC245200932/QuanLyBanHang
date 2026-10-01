package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class, ObjectMapper.class})
@DisplayName("ProductController - API Quản lý sản phẩm")
class ProductControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        this.mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "sales.emp", roles = "SALES")
    @DisplayName("GET /api/products danh sách sản phẩm thành công")
    void getAllProducts_Authenticated_Returns200() throws Exception {
        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .code("SP01")
                .name("iPhone 15")
                .price(new BigDecimal("25000000"))
                .build();

        when(productService.getAll(null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SP01"));
    }

    @Test
    @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
    @DisplayName("POST /api/products tạo sản phẩm thành công với SALES_MANAGER -> 201 Created")
    void createProduct_SalesManager_Returns201() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .code("SP01")
                .name("iPhone 15")
                .categoryId(5L)
                .price(new BigDecimal("25000000"))
                .build();

        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .code("SP01")
                .name("iPhone 15")
                .categoryId(5L)
                .build();

        when(productService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SP01"));
    }

    @Test
    @WithMockUser(username = "sales.emp", roles = "SALES")
    @DisplayName("POST /api/products bị từ chối với vai trò SALES -> 403 Forbidden")
    void createProduct_SalesRole_Returns403() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .code("SP01")
                .name("iPhone 15")
                .categoryId(5L)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
    @DisplayName("DELETE /api/products/{id} xóa sản phẩm thành công -> 200 OK")
    void deleteProduct_Success() throws Exception {
        doNothing().when(productService).delete(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
    @DisplayName("DELETE /api/products/{id} thất bại khi sản phẩm đã phát sinh giao dịch -> 400 Bad Request")
    void deleteProduct_HasTransactions_Returns400() throws Exception {
        org.mockito.Mockito.doThrow(new com.quanlybanhang.exception.ProductHasTransactionsException("SP01", "iPhone 15"))
                .when(productService).delete(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đã phát sinh giao dịch không thể xoá, chỉ có thể chuyển sang trạng thái ngừng kinh doanh")));
    }

    @Test
    @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
    @DisplayName("PATCH /api/products/{id}/status chuyển trạng thái ngừng kinh doanh -> 200 OK")
    void updateProductStatus_Success() throws Exception {
        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .code("SP01")
                .active(false)
                .status("Ngừng kinh doanh")
                .build();

        when(productService.updateStatus(1L, false)).thenReturn(response);

        mockMvc.perform(patch("/api/products/1/status")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.status").value("Ngừng kinh doanh"));
    }
}

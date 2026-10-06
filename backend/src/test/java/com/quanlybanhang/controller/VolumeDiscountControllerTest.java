package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.request.VolumeDiscountPolicyRequest;
import com.quanlybanhang.dto.request.VolumeDiscountTierRequest;
import com.quanlybanhang.dto.response.VolumeDiscountPolicyResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.model.DiscountTargetType;
import com.quanlybanhang.model.DiscountType;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.VolumeDiscountPolicyService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VolumeDiscountController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class, ObjectMapper.class})
@DisplayName("VolumeDiscountController - API Quản lý Chính sách chiết khấu theo sản lượng (S3-01)")
class VolumeDiscountControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockBean
    private VolumeDiscountPolicyService volumeDiscountPolicyService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtUtils jwtUtils;

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

        this.mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "sales_user", roles = "SALES")
    @DisplayName("GET /api/volume-discounts cho phép nhân viên kinh doanh xem -> 200 OK")
    void getAllPolicies_SalesRole_Returns200() throws Exception {
        VolumeDiscountPolicyResponse resp = VolumeDiscountPolicyResponse.builder()
                .id(1L)
                .code("CK-KEN-2026")
                .name("Chiết khấu Heineken 2026")
                .targetType(DiscountTargetType.PRODUCT)
                .build();

        when(volumeDiscountPolicyService.getAllPolicies(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/api/volume-discounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("CK-KEN-2026"));
    }

    @Test
    @WithMockUser(username = "sales_mgr", roles = "SALES_MANAGER")
    @DisplayName("POST /api/volume-discounts cho phép Quản lý kinh doanh tạo chính sách -> 201 Created")
    void createPolicy_SalesManager_Returns201() throws Exception {
        VolumeDiscountPolicyRequest req = VolumeDiscountPolicyRequest.builder()
                .code("CK-KEN-2026")
                .name("Chiết khấu Heineken 2026")
                .targetType(DiscountTargetType.PRODUCT)
                .productId(10L)
                .tiers(Collections.singletonList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("100"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("5.0"))
                                .build()
                ))
                .build();

        VolumeDiscountPolicyResponse resp = VolumeDiscountPolicyResponse.builder()
                .id(1L)
                .code("CK-KEN-2026")
                .name("Chiết khấu Heineken 2026")
                .targetType(DiscountTargetType.PRODUCT)
                .build();

        when(volumeDiscountPolicyService.createPolicy(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/volume-discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("CK-KEN-2026"));
    }

    @Test
    @WithMockUser(username = "sales_user", roles = "SALES")
    @DisplayName("POST /api/volume-discounts chặn nhân viên kinh doanh tự ý tạo chính sách -> 403 Forbidden")
    void createPolicy_SalesRole_Returns403() throws Exception {
        VolumeDiscountPolicyRequest req = VolumeDiscountPolicyRequest.builder()
                .code("CK-KEN-2026")
                .name("Chiết khấu Heineken 2026")
                .targetType(DiscountTargetType.PRODUCT)
                .productId(10L)
                .tiers(Collections.singletonList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("100"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("5.0"))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/volume-discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/volume-discounts chưa đăng nhập -> 401 Unauthorized")
    void getAllPolicies_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/volume-discounts"))
                .andExpect(status().isUnauthorized());
    }
}


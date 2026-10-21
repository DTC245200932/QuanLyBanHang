package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.request.UnitConversionRequest;
import com.quanlybanhang.dto.response.UnitConversionResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.UnitConversionService;
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

@WebMvcTest(controllers = UnitConversionController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class, ObjectMapper.class})
@DisplayName("UnitConversionController - API Quản lý đơn vị quy đổi (S2-07)")
class UnitConversionControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockBean
    private UnitConversionService unitConversionService;

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
    @WithMockUser(username = "warehouse_staff", authorities = {"ROLE_WAREHOUSE_STAFF", "WAREHOUSE_MANAGE", "PRODUCT_VIEW", "ORDER_VIEW", "WAREHOUSE_VIEW", "WAREHOUSE_IMPORT", "WAREHOUSE_EXPORT", "WAREHOUSE_TRANSFER"})
    @DisplayName("GET /api/products/{productId}/unit-conversions trả về danh sách đơn vị quy đổi")
    void getUnitConversions_Authenticated_Returns200() throws Exception {
        UnitConversionResponse resp = UnitConversionResponse.builder()
                .id(1L)
                .productId(10L)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .baseUnit("lon")
                .build();

        when(unitConversionService.getConversionsByProductId(10L)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/products/10/unit-conversions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].unitName").value("thùng"))
                .andExpect(jsonPath("$.data[0].conversionFactor").value(24.0));
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"ROLE_WAREHOUSE_STAFF", "WAREHOUSE_MANAGE", "PRODUCT_VIEW", "ORDER_VIEW", "WAREHOUSE_VIEW", "WAREHOUSE_IMPORT", "WAREHOUSE_EXPORT", "WAREHOUSE_TRANSFER"})
    @DisplayName("POST /api/products/{productId}/unit-conversions: Nhân viên kho tạo đơn vị quy đổi thành công -> 201 Created")
    void addUnitConversion_WarehouseStaff_Returns201() throws Exception {
        UnitConversionRequest req = UnitConversionRequest.builder()
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .build();

        UnitConversionResponse resp = UnitConversionResponse.builder()
                .id(1L)
                .productId(10L)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .baseUnit("lon")
                .build();

        when(unitConversionService.addConversion(eq(10L), any())).thenReturn(resp);

        mockMvc.perform(post("/api/products/10/unit-conversions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unitName").value("thùng"));
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"ROLE_WAREHOUSE_STAFF", "WAREHOUSE_MANAGE", "PRODUCT_VIEW", "ORDER_VIEW", "WAREHOUSE_VIEW", "WAREHOUSE_IMPORT", "WAREHOUSE_EXPORT", "WAREHOUSE_TRANSFER"})
    @DisplayName("DELETE /api/products/{productId}/unit-conversions/{id} xóa đơn vị quy đổi thành công")
    void deleteUnitConversion_Success() throws Exception {
        doNothing().when(unitConversionService).deleteConversion(10L, 1L);

        mockMvc.perform(delete("/api/products/10/unit-conversions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

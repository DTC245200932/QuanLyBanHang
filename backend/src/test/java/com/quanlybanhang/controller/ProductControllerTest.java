package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.request.ProductImportRowRequest;
import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductImportResponse;
import com.quanlybanhang.dto.response.ProductImportRowResponse;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import org.springframework.mock.web.MockMultipartFile;
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

    @Test
    @WithMockUser(authorities = {"ROLE_VIEWER", "WAREHOUSE_IMPORT"})
    void warehouseReferenceDoesNotRequireProductViewOrExposeSensitiveValues() throws Exception {
        when(productService.getAll((Long) null)).thenReturn(List.of(ProductResponse.builder()
                .id(1L).code("SKU-1").name("Sản phẩm").costPrice(new BigDecimal("150000"))
                .revenue(new BigDecimal("900000")).build()));
        mockMvc.perform(get("/api/products/lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("SKU-1"))
                .andExpect(jsonPath("$.data[0].costPrice").isEmpty())
                .andExpect(jsonPath("$.data[0].revenue").isEmpty());
        mockMvc.perform(get("/api/products")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_SALES_MANAGER"})
    void productRoleCannotBypassRevokedDeletePermission() throws Exception {
        mockMvc.perform(delete("/api/products/1")).andExpect(status().isForbidden());
        org.mockito.Mockito.verify(productService, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_VIEWER", "PRODUCT_DELETE"})
    void explicitDeletePermissionWorksForAnotherRole() throws Exception {
        mockMvc.perform(delete("/api/products/1")).andExpect(status().isOk());
        org.mockito.Mockito.verify(productService).delete(1L);
    }

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
    @WithMockUser(username = "sales.emp", authorities = {"ROLE_SALES", "PRODUCT_VIEW", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "REPORT_SALES"})
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
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
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
    @WithMockUser(username = "sales.emp", authorities = {"ROLE_SALES", "PRODUCT_VIEW", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "REPORT_SALES"})
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
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
    @DisplayName("DELETE /api/products/{id} xóa sản phẩm thành công -> 200 OK")
    void deleteProduct_Success() throws Exception {
        doNothing().when(productService).delete(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
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
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
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

    @Test
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
    @DisplayName("S2-08: GET /api/products/import/template tải tệp Excel mẫu thành công -> 200 OK")
    void downloadTemplate_SalesManager_Success() throws Exception {
        byte[] mockBytes = new byte[]{10, 20, 30};
        when(productService.generateImportTemplate()).thenReturn(mockBytes);

        mockMvc.perform(get("/api/products/import/template"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String disposition = result.getResponse().getHeader("Content-Disposition");
                    org.assertj.core.api.Assertions.assertThat(disposition).contains("attachment; filename=\"mau_nhap_san_pham.xlsx\"");
                });
    }

    @Test
    @WithMockUser(username = "sales.emp", authorities = {"ROLE_SALES", "PRODUCT_VIEW", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "REPORT_SALES"})
    @DisplayName("S2-08: GET /api/products/import/template bị chặn với vai trò SALES -> 403 Forbidden")
    void downloadTemplate_SalesRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/products/import/template"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
    @DisplayName("S2-08: POST /api/products/import/preview xem trước từ file tải lên thành công -> 200 OK")
    void previewImportFile_SalesManager_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        ProductImportResponse mockResponse = ProductImportResponse.builder()
                .totalRows(2)
                .validRows(2)
                .invalidRows(0)
                .createCount(1)
                .updateCount(1)
                .rows(List.of(
                        ProductImportRowResponse.builder()
                                .rowNumber(2)
                                .sku("HEIN-330")
                                .action("UPDATE")
                                .isUpdate(true)
                                .valid(true)
                                .build(),
                        ProductImportRowResponse.builder()
                                .rowNumber(3)
                                .sku("NEW-SKU")
                                .action("CREATE")
                                .isUpdate(false)
                                .valid(true)
                                .build()
                ))
                .build();

        when(productService.previewImport(any(org.springframework.web.multipart.MultipartFile.class))).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/products/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.updateCount").value(1))
                .andExpect(jsonPath("$.data.createCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].sku").value("HEIN-330"))
                .andExpect(jsonPath("$.data.rows[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows[0].update").value(true))
                .andExpect(jsonPath("$.data.rows[1].action").value("CREATE"))
                .andExpect(jsonPath("$.data.rows[1].update").value(false));
    }

    @Test
    @WithMockUser(username = "sales.mgr", authorities = {"ROLE_SALES_MANAGER", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_DELETE", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "ORDER_APPROVE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "WAREHOUSE_VIEW", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_PROFIT_MARGIN", "FINANCE_VIEW_DEBT", "FINANCE_MANAGE", "REPORT_SALES", "REPORT_INVENTORY", "REPORT_FINANCE"})
    @DisplayName("S2-08: POST /api/products/import nhập danh mục sản phẩm hàng loạt từ JSON -> 200 OK")
    void importProductsJson_SalesManager_Success() throws Exception {
        List<ProductImportRowRequest> rows = List.of(
                ProductImportRowRequest.builder().sku("HEIN-330").name("Bia Heineken").category("BEV").build()
        );

        ProductImportResponse mockResponse = ProductImportResponse.builder()
                .totalRows(1)
                .validRows(1)
                .importedRows(1)
                .updateCount(1)
                .createCount(0)
                .build();

        when(productService.importProducts(org.mockito.ArgumentMatchers.<List<ProductImportRowRequest>>any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/products/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rows)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importedRows").value(1));
    }

    @Test
    @WithMockUser(username = "sales.emp", authorities = {"ROLE_SALES", "PRODUCT_VIEW", "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT", "ORDER_CANCEL", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "REPORT_SALES"})
    @DisplayName("S2-08: POST /api/products/import bị chặn đối với vai trò SALES -> 403 Forbidden")
    void importProducts_SalesRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/products/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }
}

package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.request.CategoryRequest;
import com.quanlybanhang.dto.request.MoveProductsRequest;
import com.quanlybanhang.dto.response.CategoryResponse;
import com.quanlybanhang.exception.CategoryNotEmptyException;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CategoryController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class, ObjectMapper.class})
@DisplayName("CategoryController - API Quản lý nhóm hàng nhiều cấp (S2-06)")
class CategoryControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
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

    @Nested
    @DisplayName("Xem cây nhóm hàng và doanh số ngành hàng")
    class ReadEndpointsTests {

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("GET /api/categories/tree thành công với Quản lý kinh doanh")
        void getCategoryTree_Authenticated_Returns200() throws Exception {
            CategoryResponse response = CategoryResponse.builder()
                    .id(1L)
                    .code("NG-DIENTU")
                    .name("Điện tử")
                    .level(1)
                    .totalRevenue(new BigDecimal("150000000"))
                    .children(List.of())
                    .build();

            when(categoryService.getCategoryTree()).thenReturn(List.of(response));

            mockMvc.perform(get("/api/categories/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].code").value("NG-DIENTU"))
                    .andExpect(jsonPath("$.data[0].totalRevenue").value(150000000));
        }

        @Test
        @WithMockUser(username = "sales.emp", roles = "SALES")
        @DisplayName("GET /api/categories/tree thành công với Nhân viên kinh doanh")
        void getCategoryTree_SalesRole_Returns200() throws Exception {
            when(categoryService.getCategoryTree()).thenReturn(List.of());

            mockMvc.perform(get("/api/categories/tree"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("GET /api/categories/{id}/revenue xem doanh số ngành hàng")
        void getCategoryRevenue_Returns200() throws Exception {
            CategoryResponse response = CategoryResponse.builder()
                    .id(1L)
                    .code("NG-DIENTU")
                    .name("Điện tử")
                    .totalRevenue(new BigDecimal("350000000"))
                    .build();

            when(categoryService.getCategoryRevenue(1L)).thenReturn(response);

            mockMvc.perform(get("/api/categories/1/revenue"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalRevenue").value(350000000));
        }

        @Test
        @DisplayName("Chưa đăng nhập truy cập cây nhóm hàng -> 401 Unauthorized")
        void getCategoryTree_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/categories/tree"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Tạo, cập nhật và phân quyền quản lý nhóm hàng")
    class ManageEndpointsTests {

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("Quản lý kinh doanh tạo nhóm hàng mới -> 201 Created")
        void createCategory_SalesManager_Returns201() throws Exception {
            CategoryRequest request = CategoryRequest.builder()
                    .code("NG-DIENTU")
                    .name("Điện tử")
                    .build();

            CategoryResponse response = CategoryResponse.builder()
                    .id(1L)
                    .code("NG-DIENTU")
                    .name("Điện tử")
                    .level(1)
                    .build();

            when(categoryService.create(any())).thenReturn(response);

            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value("NG-DIENTU"));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("Quản trị viên tạo nhóm hàng mới -> 201 Created")
        void createCategory_Admin_Returns201() throws Exception {
            CategoryRequest request = CategoryRequest.builder().code("NG-TEST").name("Test").build();
            when(categoryService.create(any())).thenReturn(CategoryResponse.builder().id(2L).code("NG-TEST").build());

            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(username = "sales.emp", roles = "SALES")
        @DisplayName("Nhân viên kinh doanh (ROLE_SALES) không được tạo nhóm hàng -> 403 Forbidden")
        void createCategory_SalesRole_Returns403() throws Exception {
            CategoryRequest request = CategoryRequest.builder().code("NG-TEST").name("Test").build();

            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "warehouse.staff", roles = "WAREHOUSE_STAFF")
        @DisplayName("Thủ kho (ROLE_WAREHOUSE_STAFF) không được tạo nhóm hàng -> 403 Forbidden")
        void createCategory_WarehouseStaff_Returns403() throws Exception {
            CategoryRequest request = CategoryRequest.builder().code("NG-TEST").name("Test").build();

            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("Cập nhật nhóm hàng thành công với ROLE_SALES_MANAGER -> 200 OK")
        void updateCategory_SalesManager_Returns200() throws Exception {
            CategoryRequest request = CategoryRequest.builder().code("NG-UPDATE").name("Đã sửa").build();
            when(categoryService.update(eq(1L), any())).thenReturn(CategoryResponse.builder().id(1L).code("NG-UPDATE").name("Đã sửa").build());

            mockMvc.perform(put("/api/categories/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Đã sửa"));
        }
    }

    @Nested
    @DisplayName("Quy tắc xoá nhóm hàng: Nhóm còn sản phẩm thì không xoá được")
    class DeleteEndpointsTests {

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("Xoá nhóm hàng khi còn sản phẩm -> Trả về 400 Bad Request và thông báo lỗi")
        void deleteCategory_WhenNotEmpty_Returns400() throws Exception {
            doThrow(new CategoryNotEmptyException("Nhóm còn 3 sản phẩm thì không xoá được. Vui lòng chuyển hoặc xoá sản phẩm trước."))
                    .when(categoryService).delete(1L);

            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Nhóm còn 3 sản phẩm thì không xoá được. Vui lòng chuyển hoặc xoá sản phẩm trước."));
        }

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("Xoá nhóm hàng khi trống -> Trả về 200 OK")
        void deleteCategory_WhenEmpty_Returns200() throws Exception {
            doNothing().when(categoryService).delete(1L);

            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "sales.emp", roles = "SALES")
        @DisplayName("Nhân viên bán hàng không có quyền xoá nhóm hàng -> 403 Forbidden")
        void deleteCategory_SalesRole_Returns403() throws Exception {
            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Chuyển sản phẩm giữa các nhóm")
    class MoveProductsEndpointsTests {

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("Chuyển danh sách sản phẩm sang nhóm mới -> 200 OK")
        void moveProducts_SalesManager_Returns200() throws Exception {
            MoveProductsRequest request = MoveProductsRequest.builder()
                    .productIds(List.of(1L, 2L))
                    .targetCategoryId(5L)
                    .build();

            doNothing().when(categoryService).moveProducts(any());

            mockMvc.perform(post("/api/categories/move-products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = "sales.emp", roles = "SALES")
        @DisplayName("Nhân viên bán hàng không được chuyển sản phẩm -> 403 Forbidden")
        void moveProducts_SalesRole_Returns403() throws Exception {
            MoveProductsRequest request = MoveProductsRequest.builder()
                    .productIds(List.of(1L))
                    .targetCategoryId(5L)
                    .build();

            mockMvc.perform(post("/api/categories/move-products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "sales.mgr", roles = "SALES_MANAGER")
        @DisplayName("Chuyển 1 sản phẩm sang nhóm mới qua PUT -> 200 OK")
        void moveSingleProduct_Returns200() throws Exception {
            doNothing().when(categoryService).moveSingleProduct(1L, 5L);

            mockMvc.perform(put("/api/categories/products/1/move")
                            .param("targetCategoryId", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}

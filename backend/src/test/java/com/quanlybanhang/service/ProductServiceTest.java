package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.exception.ProductHasTransactionsException;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Quản lý danh mục sản phẩm (S2-05)")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSalesManagerAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sales.mgr", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_SALES_MANAGER")))
        );
    }

    private void mockSalesStaffAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sales.staff", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_SALES")))
        );
    }

    @Test
    @DisplayName("Tạo sản phẩm mới thành công với đầy đủ mã SKU, quy cách đóng gói, giá vốn (với Quản lý kinh doanh)")
    void createProduct_SalesManager_Success() {
        mockSalesManagerAuth();

        Category category = Category.builder().id(10L).code("NC-PHONE").name("Smartphone").build();
        ProductRequest request = ProductRequest.builder()
                .sku("SP-IP15")
                .name("iPhone 15")
                .categoryId(10L)
                .price(new BigDecimal("25000000"))
                .costPrice(new BigDecimal("20000000"))
                .revenue(BigDecimal.ZERO)
                .unit("chiếc")
                .packingSpecification("Hộp 1 máy kèm cáp")
                .imageUrl("https://example.com/ip15.jpg")
                .active(true)
                .build();

        when(productRepository.existsByCodeIgnoreCase("SP-IP15")).thenReturn(false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProductResponse response = productService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("SP-IP15");
        assertThat(response.getSku()).isEqualTo("SP-IP15");
        assertThat(response.getCategoryId()).isEqualTo(10L);
        assertThat(response.getCategoryName()).isEqualTo("Smartphone");
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(response.getCostPrice()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(response.getUnit()).isEqualTo("chiếc");
        assertThat(response.getPackingSpecification()).isEqualTo("Hộp 1 máy kèm cáp");
        assertThat(response.getImageUrl()).isEqualTo("https://example.com/ip15.jpg");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getStatus()).isEqualTo("Đang kinh doanh");
    }

    @Test
    @DisplayName("Giá vốn bị ẩn (null) khi người xem không phải Quản lý kinh doanh")
    void getById_NonSalesManager_HidesCostPrice() {
        mockSalesStaffAuth();

        Category category = Category.builder().id(10L).code("CAT10").name("Nhóm 10").build();
        Product product = Product.builder()
                .id(1L)
                .code("SP01")
                .name("iPhone 15")
                .category(category)
                .price(new BigDecimal("25000000"))
                .costPrice(new BigDecimal("20000000"))
                .revenue(BigDecimal.ZERO)
                .unit("chiếc")
                .active(true)
                .hasTransactions(false)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(response.getCostPrice()).isNull(); // Bảo mật: giá vốn chỉ Quản lý kinh doanh xem được
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi trùng mã SKU")
    void createProduct_DuplicateSku_ThrowsException() {
        ProductRequest request = ProductRequest.builder().sku("SP01").name("iPhone 15").categoryId(1L).build();
        when(productRepository.existsByCodeIgnoreCase("SP01")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Cập nhật sản phẩm chuyển sang nhóm hàng mới thành công")
    void updateProduct_ChangeCategory_Success() {
        mockSalesManagerAuth();

        Category oldCat = Category.builder().id(10L).code("CAT10").name("Nhóm Cũ").build();
        Category newCat = Category.builder().id(20L).code("CAT20").name("Nhóm Mới").build();
        Product product = Product.builder().id(1L).code("SP01").name("Tên Cũ").category(oldCat).build();

        ProductRequest request = ProductRequest.builder()
                .sku("SP01")
                .name("Tên Mới")
                .categoryId(20L)
                .price(new BigDecimal("100000"))
                .costPrice(new BigDecimal("70000"))
                .packingSpecification("Thùng 12 cái")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("SP01", 1L)).thenReturn(false);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(newCat));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Tên Mới");
        assertThat(product.getCategory()).isEqualTo(newCat);
        assertThat(product.getCostPrice()).isEqualByComparingTo(new BigDecimal("70000"));
        assertThat(product.getPackingSpecification()).isEqualTo("Thùng 12 cái");
    }

    @Test
    @DisplayName("Người không phải Quản lý kinh doanh không sửa được giá vốn khi cập nhật sản phẩm")
    void updateProduct_NonSalesManager_PreservesExistingCostPrice() {
        mockSalesStaffAuth();

        Category cat = Category.builder().id(10L).code("CAT10").name("Nhóm 10").build();
        Product product = Product.builder()
                .id(1L)
                .code("SP01")
                .name("Tên Cũ")
                .category(cat)
                .costPrice(new BigDecimal("15000000"))
                .build();

        // Cố tình truyền costPrice mới
        ProductRequest request = ProductRequest.builder()
                .sku("SP01")
                .name("Tên Mới")
                .categoryId(10L)
                .costPrice(new BigDecimal("99999999"))
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("SP01", 1L)).thenReturn(false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.update(1L, request);

        // Giá vốn gốc 15000000 vẫn được bảo toàn
        assertThat(product.getCostPrice()).isEqualByComparingTo(new BigDecimal("15000000"));
    }

    @Test
    @DisplayName("Sản phẩm đã phát sinh giao dịch thì KHÔNG xoá được, ném ProductHasTransactionsException")
    void deleteProduct_WithTransactions_ThrowsException() {
        Product product = Product.builder()
                .id(1L)
                .code("SP-BAN-CHAY")
                .name("Sản phẩm đã bán")
                .revenue(new BigDecimal("1000000"))
                .hasTransactions(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(ProductHasTransactionsException.class)
                .hasMessageContaining("đã phát sinh giao dịch không thể xoá, chỉ có thể chuyển sang trạng thái ngừng kinh doanh");

        verify(productRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Sản phẩm chưa phát sinh giao dịch xoá thành công")
    void deleteProduct_NoTransactions_Success() {
        Product product = Product.builder()
                .id(2L)
                .code("SP-MOI")
                .name("Sản phẩm chưa bán")
                .revenue(BigDecimal.ZERO)
                .hasTransactions(false)
                .build();

        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        productService.delete(2L);

        verify(productRepository).delete(product);
    }

    @Test
    @DisplayName("Cập nhật trạng thái ngừng kinh doanh thành công")
    void updateStatus_DiscontinueProduct_Success() {
        Product product = Product.builder()
                .id(1L)
                .code("SP01")
                .name("Sản phẩm 1")
                .active(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.updateStatus(1L, false);

        assertThat(product.getActive()).isFalse();
        assertThat(response.getActive()).isFalse();
        assertThat(response.getStatus()).isEqualTo("Ngừng kinh doanh");
    }

    @Test
    @DisplayName("Lấy danh sách sản phẩm có bộ lọc tìm kiếm và trạng thái")
    void getAll_WithFilters_ReturnsFilteredList() {
        Category cat = Category.builder().id(10L).code("CAT10").name("Nhóm 10").build();
        Product p1 = Product.builder().id(1L).code("SP-IP15").name("iPhone 15").category(cat).active(true).build();
        Product p2 = Product.builder().id(2L).code("SP-S24").name("Samsung S24").category(cat).active(false).build();

        when(productRepository.findByCategoryId(10L)).thenReturn(List.of(p1, p2));

        // Lọc active = true
        List<ProductResponse> activeList = productService.getAll(10L, null, true);
        assertThat(activeList).hasSize(1);
        assertThat(activeList.get(0).getCode()).isEqualTo("SP-IP15");

        // Tìm kiếm theo từ khoá "Samsung"
        List<ProductResponse> searchList = productService.getAll(10L, "samsung", null);
        assertThat(searchList).hasSize(1);
        assertThat(searchList.get(0).getCode()).isEqualTo("SP-S24");
    }
}


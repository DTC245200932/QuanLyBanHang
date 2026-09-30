package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Quản lý sản phẩm")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    @DisplayName("Tạo sản phẩm mới thành công gắn vào nhóm hàng")
    void createProduct_Success() {
        Category category = Category.builder().id(10L).code("NC-PHONE").name("Smartphone").build();
        ProductRequest request = ProductRequest.builder()
                .code("SP01")
                .name("iPhone 15")
                .categoryId(10L)
                .price(new BigDecimal("25000000"))
                .costPrice(new BigDecimal("20000000"))
                .revenue(new BigDecimal("50000000"))
                .unit("chiếc")
                .build();

        when(productRepository.existsByCode("SP01")).thenReturn(false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProductResponse response = productService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("SP01");
        assertThat(response.getCategoryId()).isEqualTo(10L);
        assertThat(response.getCategoryName()).isEqualTo("Smartphone");
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(response.getRevenue()).isEqualByComparingTo(new BigDecimal("50000000"));
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi trùng mã")
    void createProduct_DuplicateCode_ThrowsException() {
        ProductRequest request = ProductRequest.builder().code("SP01").name("iPhone 15").categoryId(1L).build();
        when(productRepository.existsByCode("SP01")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Cập nhật sản phẩm chuyển sang nhóm hàng mới thành công")
    void updateProduct_ChangeCategory_Success() {
        Category oldCat = Category.builder().id(10L).code("CAT10").name("Nhóm Cũ").build();
        Category newCat = Category.builder().id(20L).code("CAT20").name("Nhóm Mới").build();
        Product product = Product.builder().id(1L).code("SP01").name("Tên Cũ").category(oldCat).build();

        ProductRequest request = ProductRequest.builder()
                .code("SP01")
                .name("Tên Mới")
                .categoryId(20L)
                .price(new BigDecimal("100000"))
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByCodeAndIdNot("SP01", 1L)).thenReturn(false);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(newCat));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Tên Mới");
        assertThat(product.getCategory()).isEqualTo(newCat);
    }

    @Test
    @DisplayName("Lấy danh sách sản phẩm theo categoryId")
    void getAll_WithCategoryId_ReturnsFilteredList() {
        Category cat = Category.builder().id(10L).code("CAT10").name("Nhóm 10").build();
        Product p = Product.builder().id(1L).code("P1").name("SP 1").category(cat).build();

        when(productRepository.findByCategoryId(10L)).thenReturn(List.of(p));

        List<ProductResponse> list = productService.getAll(10L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCode()).isEqualTo("P1");
    }

    @Test
    @DisplayName("Xóa sản phẩm thành công")
    void deleteProduct_Success() {
        Product product = Product.builder().id(1L).code("P1").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.delete(1L);

        verify(productRepository).delete(product);
    }
}

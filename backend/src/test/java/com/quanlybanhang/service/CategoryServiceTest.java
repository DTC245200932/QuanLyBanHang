package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.CategoryRequest;
import com.quanlybanhang.dto.request.MoveProductsRequest;
import com.quanlybanhang.dto.response.CategoryResponse;
import com.quanlybanhang.exception.CategoryNotEmptyException;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService - Quản lý nhóm hàng nhiều cấp (S2-06)")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Nested
    @DisplayName("Cấu trúc cây nhóm hàng tối thiểu 3 cấp")
    class TreeHierarchyTests {

        @Test
        @DisplayName("Tạo nhóm hàng Cấp 1 (Ngành hàng) khi không có parentId -> level = 1")
        void createRootCategory_LevelIsOne() {
            CategoryRequest request = CategoryRequest.builder()
                    .code("NG-DIENTU")
                    .name("Điện tử & Thiết bị số")
                    .parentId(null)
                    .build();

            when(categoryRepository.existsByCode("NG-DIENTU")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
                Category cat = invocation.getArgument(0);
                cat.setId(1L);
                return cat;
            });

            CategoryResponse response = categoryService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getCode()).isEqualTo("NG-DIENTU");
            assertThat(response.getLevel()).isEqualTo(1);
            assertThat(response.getParentId()).isNull();
        }

        @Test
        @DisplayName("Tạo nhóm hàng Cấp 2 (Phân nhóm) với parent là Cấp 1 -> level = 2")
        void createLevel2Category_LevelIsTwo() {
            Category root = Category.builder().id(1L).code("NG-DIENTU").name("Điện tử").level(1).build();

            CategoryRequest request = CategoryRequest.builder()
                    .code("PN-DIDONG")
                    .name("Thiết bị di động")
                    .parentId(1L)
                    .build();

            when(categoryRepository.existsByCode("PN-DIDONG")).thenReturn(false);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
                Category cat = invocation.getArgument(0);
                cat.setId(2L);
                return cat;
            });

            CategoryResponse response = categoryService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getLevel()).isEqualTo(2);
            assertThat(response.getParentId()).isEqualTo(1L);
            assertThat(response.getParentName()).isEqualTo("Điện tử");
        }

        @Test
        @DisplayName("Tạo nhóm hàng Cấp 3 (Nhóm con) với parent là Cấp 2 -> level = 3")
        void createLevel3Category_LevelIsThree() {
            Category level2 = Category.builder().id(2L).code("PN-DIDONG").name("Thiết bị di động").level(2).build();

            CategoryRequest request = CategoryRequest.builder()
                    .code("NC-SMARTPHONE")
                    .name("Điện thoại thông minh")
                    .parentId(2L)
                    .build();

            when(categoryRepository.existsByCode("NC-SMARTPHONE")).thenReturn(false);
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(level2));
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
                Category cat = invocation.getArgument(0);
                cat.setId(3L);
                return cat;
            });

            CategoryResponse response = categoryService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.getLevel()).isEqualTo(3);
            assertThat(response.getParentId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Không được tạo trùng mã nhóm hàng")
        void createCategory_DuplicateCode_ThrowsException() {
            CategoryRequest request = CategoryRequest.builder().code("NG-DIENTU").name("Điện tử").build();
            when(categoryRepository.existsByCode("NG-DIENTU")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.create(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã tồn tại");
        }

        @Test
        @DisplayName("Không thể chọn chính nhóm hàng làm nhóm cha khi cập nhật")
        void updateCategory_ParentIsSelf_ThrowsException() {
            Category cat = Category.builder().id(5L).code("CAT5").name("Nhóm 5").level(2).build();
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));
            when(categoryRepository.existsByCodeAndIdNot("CAT5", 5L)).thenReturn(false);

            CategoryRequest request = CategoryRequest.builder().code("CAT5").name("Nhóm 5").parentId(5L).build();

            assertThatThrownBy(() -> categoryService.update(5L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Không thể chọn chính nhóm hàng này làm nhóm cha");
        }

        @Test
        @DisplayName("Không thể chọn nhóm hàng con cháu làm nhóm cha (ngăn ngừa chu trình)")
        void updateCategory_ParentIsDescendant_ThrowsException() {
            Category root = Category.builder().id(1L).code("ROOT").name("Root").level(1).build();
            Category child = Category.builder().id(2L).code("CHILD").name("Child").level(2).parent(root).build();
            Category grandChild = Category.builder().id(3L).code("GRAND").name("GrandChild").level(3).parent(child).build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
            when(categoryRepository.existsByCodeAndIdNot("ROOT", 1L)).thenReturn(false);
            when(categoryRepository.findById(3L)).thenReturn(Optional.of(grandChild));
            // collectDescendantIds for root -> findByParentIdOrderByNameAsc(1) -> [child], for child -> [grandChild]
            when(categoryRepository.findByParentIdOrderByNameAsc(1L)).thenReturn(List.of(child));
            when(categoryRepository.findByParentIdOrderByNameAsc(2L)).thenReturn(List.of(grandChild));
            when(categoryRepository.findByParentIdOrderByNameAsc(3L)).thenReturn(List.of());

            CategoryRequest request = CategoryRequest.builder().code("ROOT").name("Root").parentId(3L).build();

            assertThatThrownBy(() -> categoryService.update(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gây vòng lặp chu trình cây");
        }
    }

    @Nested
    @DisplayName("Quy tắc xoá nhóm hàng: Nhóm còn sản phẩm thì không xoá được")
    class DeleteCategoryRulesTests {

        @Test
        @DisplayName("Xoá nhóm hàng thất bại khi nhóm trực tiếp còn chứa sản phẩm -> ném CategoryNotEmptyException")
        void deleteCategory_WithDirectProducts_ThrowsCategoryNotEmptyException() {
            Category cat = Category.builder().id(10L).code("CAT10").name("Nhóm 10").build();
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(cat));
            when(productRepository.countByCategoryId(10L)).thenReturn(3L);

            assertThatThrownBy(() -> categoryService.delete(10L))
                    .isInstanceOf(CategoryNotEmptyException.class)
                    .hasMessageContaining("Nhóm còn 3 sản phẩm thì không xoá được");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Xoá nhóm hàng cha thất bại khi các nhóm con còn chứa sản phẩm -> ném CategoryNotEmptyException")
        void deleteCategory_WithDescendantProducts_ThrowsCategoryNotEmptyException() {
            Category root = Category.builder().id(1L).code("ROOT").name("Root").build();
            Category child = Category.builder().id(2L).code("CHILD").name("Child").parent(root).build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
            when(productRepository.countByCategoryId(1L)).thenReturn(0L);
            when(categoryRepository.countByParentId(1L)).thenReturn(1L);
            when(categoryRepository.findByParentIdOrderByNameAsc(1L)).thenReturn(List.of(child));
            when(categoryRepository.findByParentIdOrderByNameAsc(2L)).thenReturn(List.of());
            when(productRepository.countByCategoryIdIn(Set.of(2L))).thenReturn(5L);

            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(CategoryNotEmptyException.class)
                    .hasMessageContaining("các nhóm con vẫn còn 5 sản phẩm");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Xoá nhóm hàng cha thất bại khi vẫn còn nhóm con (dù nhóm con không có sp)")
        void deleteCategory_WithChildren_ThrowsCategoryNotEmptyException() {
            Category root = Category.builder().id(1L).code("ROOT").name("Root").build();
            Category child = Category.builder().id(2L).code("CHILD").name("Child").parent(root).build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
            when(productRepository.countByCategoryId(1L)).thenReturn(0L);
            when(categoryRepository.countByParentId(1L)).thenReturn(1L);
            when(categoryRepository.findByParentIdOrderByNameAsc(1L)).thenReturn(List.of(child));
            when(categoryRepository.findByParentIdOrderByNameAsc(2L)).thenReturn(List.of());
            when(productRepository.countByCategoryIdIn(Set.of(2L))).thenReturn(0L);

            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(CategoryNotEmptyException.class)
                    .hasMessageContaining("vẫn còn 1 nhóm con");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Xoá nhóm hàng thành công khi không còn sản phẩm và không có nhóm con")
        void deleteCategory_Empty_Success() {
            Category leaf = Category.builder().id(3L).code("LEAF").name("Nhóm lá trống").build();

            when(categoryRepository.findById(3L)).thenReturn(Optional.of(leaf));
            when(productRepository.countByCategoryId(3L)).thenReturn(0L);
            when(categoryRepository.countByParentId(3L)).thenReturn(0L);

            categoryService.delete(3L);

            verify(categoryRepository).delete(leaf);
        }
    }

    @Nested
    @DisplayName("Chuyển sản phẩm giữa các nhóm hàng")
    class MoveProductsTests {

        @Test
        @DisplayName("Chuyển danh sách sản phẩm sang nhóm hàng đích thành công")
        void moveProducts_Batch_Success() {
            Category target = Category.builder().id(20L).code("TARGET").name("Nhóm Đích").build();
            Category source = Category.builder().id(10L).code("SOURCE").name("Nhóm Nguồn").build();

            Product p1 = Product.builder().id(1L).code("P1").category(source).build();
            Product p2 = Product.builder().id(2L).code("P2").category(source).build();

            MoveProductsRequest request = MoveProductsRequest.builder()
                    .productIds(List.of(1L, 2L))
                    .targetCategoryId(20L)
                    .build();

            when(categoryRepository.findById(20L)).thenReturn(Optional.of(target));
            when(productRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

            categoryService.moveProducts(request);

            assertThat(p1.getCategory()).isEqualTo(target);
            assertThat(p2.getCategory()).isEqualTo(target);
            verify(productRepository).saveAll(List.of(p1, p2));
        }

        @Test
        @DisplayName("Chuyển một sản phẩm đơn lẻ sang nhóm hàng đích thành công")
        void moveSingleProduct_Success() {
            Category target = Category.builder().id(20L).code("TARGET").name("Nhóm Đích").build();
            Category source = Category.builder().id(10L).code("SOURCE").name("Nhóm Nguồn").build();
            Product p1 = Product.builder().id(1L).code("P1").category(source).build();

            when(categoryRepository.findById(20L)).thenReturn(Optional.of(target));
            when(productRepository.findById(1L)).thenReturn(Optional.of(p1));

            categoryService.moveSingleProduct(1L, 20L);

            assertThat(p1.getCategory()).isEqualTo(target);
            verify(productRepository).save(p1);
        }

        @Test
        @DisplayName("Chuyển sản phẩm thất bại khi nhóm đích không tồn tại")
        void moveProducts_TargetCategoryNotFound_ThrowsException() {
            MoveProductsRequest request = MoveProductsRequest.builder()
                    .productIds(List.of(1L))
                    .targetCategoryId(999L)
                    .build();

            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.moveProducts(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Không tìm thấy nhóm hàng đích");
        }
    }

    @Nested
    @DisplayName("Xem doanh số theo ngành hàng (tổng hợp cây nhánh)")
    class CategoryRevenueAggregationTests {

        @Test
        @DisplayName("Tổng hợp doanh số của Ngành hàng cấp 1 gồm tất cả nhóm con cấp 2 và cấp 3")
        void getCategoryTree_AggregatesRevenueAcrossThreeLevels() {
            // Cây 3 cấp:
            // Cấp 1: Ngành hàng Điện tử (ID 1)
            //   Cấp 2: Phân nhóm Thiết bị di động (ID 2)
            //     Cấp 3: Nhóm con Smartphone (ID 3) - Doanh số: 100M
            //     Cấp 3: Nhóm con Phụ kiện (ID 4) - Doanh số: 30M
            //   Cấp 2: Phân nhóm Laptop (ID 5) - Doanh số trực tiếp: 70M
            Category cat1 = Category.builder().id(1L).code("L1").name("Điện tử").level(1).build();
            Category cat2 = Category.builder().id(2L).code("L2").name("Thiết bị di động").level(2).parent(cat1).build();
            Category cat3 = Category.builder().id(3L).code("L3").name("Smartphone").level(3).parent(cat2).build();
            Category cat4 = Category.builder().id(4L).code("L4").name("Phụ kiện").level(3).parent(cat2).build();
            Category cat5 = Category.builder().id(5L).code("L5").name("Laptop").level(2).parent(cat1).build();

            when(categoryRepository.findByParentIsNullOrderByNameAsc()).thenReturn(List.of(cat1));

            // Hierarchy relationships
            when(categoryRepository.findByParentIdOrderByNameAsc(1L)).thenReturn(List.of(cat2, cat5));
            when(categoryRepository.findByParentIdOrderByNameAsc(2L)).thenReturn(List.of(cat3, cat4));
            when(categoryRepository.findByParentIdOrderByNameAsc(3L)).thenReturn(List.of());
            when(categoryRepository.findByParentIdOrderByNameAsc(4L)).thenReturn(List.of());
            when(categoryRepository.findByParentIdOrderByNameAsc(5L)).thenReturn(List.of());

            // Product counts
            when(productRepository.countByCategoryId(1L)).thenReturn(0L);
            when(productRepository.countByCategoryId(2L)).thenReturn(0L);
            when(productRepository.countByCategoryId(3L)).thenReturn(2L);
            when(productRepository.countByCategoryId(4L)).thenReturn(1L);
            when(productRepository.countByCategoryId(5L)).thenReturn(1L);

            // Subtree product counts
            when(productRepository.countByCategoryIdIn(Set.of(3L))).thenReturn(2L);
            when(productRepository.countByCategoryIdIn(Set.of(4L))).thenReturn(1L);
            when(productRepository.countByCategoryIdIn(Set.of(2L, 3L, 4L))).thenReturn(3L);
            when(productRepository.countByCategoryIdIn(Set.of(5L))).thenReturn(1L);
            when(productRepository.countByCategoryIdIn(Set.of(1L, 2L, 3L, 4L, 5L))).thenReturn(4L);

            // Revenue sums
            when(productRepository.sumRevenueByCategoryIds(Collections.singleton(3L))).thenReturn(new BigDecimal("100000000"));
            when(productRepository.sumRevenueByCategoryIds(Set.of(3L))).thenReturn(new BigDecimal("100000000"));

            when(productRepository.sumRevenueByCategoryIds(Collections.singleton(4L))).thenReturn(new BigDecimal("30000000"));
            when(productRepository.sumRevenueByCategoryIds(Set.of(4L))).thenReturn(new BigDecimal("30000000"));

            when(productRepository.sumRevenueByCategoryIds(Collections.singleton(2L))).thenReturn(BigDecimal.ZERO);
            when(productRepository.sumRevenueByCategoryIds(Set.of(2L, 3L, 4L))).thenReturn(new BigDecimal("130000000"));

            when(productRepository.sumRevenueByCategoryIds(Collections.singleton(5L))).thenReturn(new BigDecimal("70000000"));
            when(productRepository.sumRevenueByCategoryIds(Set.of(5L))).thenReturn(new BigDecimal("70000000"));

            when(productRepository.sumRevenueByCategoryIds(Collections.singleton(1L))).thenReturn(BigDecimal.ZERO);
            when(productRepository.sumRevenueByCategoryIds(Set.of(1L, 2L, 3L, 4L, 5L))).thenReturn(new BigDecimal("200000000"));

            List<CategoryResponse> tree = categoryService.getCategoryTree();

            assertThat(tree).hasSize(1);
            CategoryResponse rootResp = tree.get(0);
            assertThat(rootResp.getCode()).isEqualTo("L1");
            assertThat(rootResp.getTotalProductCount()).isEqualTo(4L);
            // Cấp 1 tổng hợp toàn bộ cây nhánh: 100M + 30M + 70M = 200M
            assertThat(rootResp.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("200000000"));
            assertThat(rootResp.getChildren()).hasSize(2);

            // Cấp 2 - Thiết bị di động: tổng hợp Smartphone (100M) + Phụ kiện (30M) = 130M
            CategoryResponse l2Resp = rootResp.getChildren().get(0);
            assertThat(l2Resp.getCode()).isEqualTo("L2");
            assertThat(l2Resp.getTotalProductCount()).isEqualTo(3L);
            assertThat(l2Resp.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("130000000"));
            assertThat(l2Resp.getChildren()).hasSize(2);

            // Cấp 3 - Smartphone
            CategoryResponse l3Resp = l2Resp.getChildren().get(0);
            assertThat(l3Resp.getCode()).isEqualTo("L3");
            assertThat(l3Resp.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("100000000"));
        }
    }
}

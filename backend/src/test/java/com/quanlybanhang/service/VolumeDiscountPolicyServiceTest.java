package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.VolumeDiscountCalculateItemRequest;
import com.quanlybanhang.dto.request.VolumeDiscountCalculateRequest;
import com.quanlybanhang.dto.request.VolumeDiscountPolicyRequest;
import com.quanlybanhang.dto.request.VolumeDiscountTierRequest;
import com.quanlybanhang.dto.response.VolumeDiscountCalculationResponse;
import com.quanlybanhang.dto.response.VolumeDiscountItemCalculationResponse;
import com.quanlybanhang.dto.response.VolumeDiscountPolicyResponse;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.PriceListRepository;
import com.quanlybanhang.repository.ProductRepository;
import com.quanlybanhang.repository.VolumeDiscountPolicyRepository;
import com.quanlybanhang.repository.VolumeDiscountTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VolumeDiscountPolicyService - Khai báo chính sách chiết khấu theo sản lượng (S3-01)")
class VolumeDiscountPolicyServiceTest {

    @Mock
    private VolumeDiscountPolicyRepository policyRepository;

    @Mock
    private VolumeDiscountTierRepository tierRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private PriceListRepository priceListRepository;

    @Mock
    private UnitConversionService unitConversionService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private VolumeDiscountPolicyServiceImpl discountService;

    private Product productKen;
    private Product productTiger;
    private Category categoryBia;
    private Category categoryNuocGiaiKhat;

    @BeforeEach
    void setUp() {
        categoryNuocGiaiKhat = Category.builder()
                .id(1L)
                .code("CAT-BEV")
                .name("Đồ uống")
                .level(1)
                .build();

        categoryBia = Category.builder()
                .id(2L)
                .code("CAT-BEER")
                .name("Bia các loại")
                .level(2)
                .parent(categoryNuocGiaiKhat)
                .build();

        productKen = Product.builder()
                .id(10L)
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .unit("lon")
                .price(new BigDecimal("16500"))
                .category(categoryBia)
                .build();

        productTiger = Product.builder()
                .id(20L)
                .code("SP-TIGER330")
                .name("Bia Tiger lon 330ml")
                .unit("lon")
                .price(new BigDecimal("15000"))
                .category(categoryBia)
                .build();
    }

    @Test
    @DisplayName("Tạo chính sách chiết khấu theo SKU thành công")
    void testCreateSkuPolicy_Success() {
        VolumeDiscountPolicyRequest request = VolumeDiscountPolicyRequest.builder()
                .code("CK-KEN-2026")
                .name("Chiết khấu sản lượng bia Heineken 2026")
                .targetType(DiscountTargetType.PRODUCT)
                .productId(10L)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .tiers(Arrays.asList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("50"))
                                .maxQuantity(new BigDecimal("99"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("5.0"))
                                .build(),
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("100"))
                                .maxQuantity(null)
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("10.0"))
                                .build()
                ))
                .build();

        when(policyRepository.existsByCode("CK-KEN-2026")).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(productKen));
        when(policyRepository.save(any(VolumeDiscountPolicy.class))).thenAnswer(i -> {
            VolumeDiscountPolicy p = i.getArgument(0);
            p.setId(100L);
            return p;
        });

        VolumeDiscountPolicyResponse response = discountService.createPolicy(request, "sales_mgr");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("CK-KEN-2026");
        assertThat(response.getTargetType()).isEqualTo(DiscountTargetType.PRODUCT);
        assertThat(response.getProductCode()).isEqualTo("SP-KEN330");
        assertThat(response.getTiers()).hasSize(2);
        verify(auditLogService).logAction(eq("DISCOUNT_POLICY"), eq("CK-KEN-2026"), eq("CREATE"), isNull(), anyString(), eq("sales_mgr"));
    }

    @Test
    @DisplayName("Tạo chính sách chiết khấu theo nhóm hàng thành công")
    void testCreateCategoryPolicy_Success() {
        VolumeDiscountPolicyRequest request = VolumeDiscountPolicyRequest.builder()
                .code("CK-BIA-2026")
                .name("Chiết khấu sản lượng nhóm Bia")
                .targetType(DiscountTargetType.CATEGORY)
                .categoryId(2L)
                .tiers(Collections.singletonList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("100"))
                                .maxQuantity(null)
                                .discountType(DiscountType.FIXED_AMOUNT)
                                .discountValue(new BigDecimal("500"))
                                .build()
                ))
                .build();

        when(policyRepository.existsByCode("CK-BIA-2026")).thenReturn(false);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(categoryBia));
        when(policyRepository.save(any(VolumeDiscountPolicy.class))).thenAnswer(i -> {
            VolumeDiscountPolicy p = i.getArgument(0);
            p.setId(101L);
            return p;
        });

        VolumeDiscountPolicyResponse response = discountService.createPolicy(request, "sales_mgr");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("CK-BIA-2026");
        assertThat(response.getTargetType()).isEqualTo(DiscountTargetType.CATEGORY);
        assertThat(response.getCategoryCode()).isEqualTo("CAT-BEER");
    }

    @Test
    @DisplayName("Chặn tạo chính sách trùng mã code")
    void testCreatePolicy_DuplicateCode_ThrowsException() {
        VolumeDiscountPolicyRequest request = VolumeDiscountPolicyRequest.builder()
                .code("CK-DUPLICATE")
                .name("Chính sách trùng mã")
                .targetType(DiscountTargetType.PRODUCT)
                .productId(10L)
                .tiers(Collections.singletonList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("10"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("5.0"))
                                .build()
                ))
                .build();

        when(policyRepository.existsByCode("CK-DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> discountService.createPolicy(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Chặn tạo chính sách có các bậc chồng lấn khoảng số lượng")
    void testCreatePolicy_OverlappingTiers_ThrowsException() {
        VolumeDiscountPolicyRequest request = VolumeDiscountPolicyRequest.builder()
                .code("CK-OVERLAP")
                .name("Chính sách chồng lấn")
                .targetType(DiscountTargetType.PRODUCT)
                .productId(10L)
                .tiers(Arrays.asList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("10"))
                                .maxQuantity(new BigDecimal("50"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("5.0"))
                                .build(),
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("40")) // Chồng lấn với 50
                                .maxQuantity(new BigDecimal("100"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("10.0"))
                                .build()
                ))
                .build();

        when(policyRepository.existsByCode("CK-OVERLAP")).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(productKen));

        assertThatThrownBy(() -> discountService.createPolicy(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chồng lấn");
    }

    @Test
    @DisplayName("Chặn chiết khấu phần trăm vượt quá 100%")
    void testCreatePolicy_PercentageOver100_ThrowsException() {
        VolumeDiscountPolicyRequest request = VolumeDiscountPolicyRequest.builder()
                .code("CK-OVER-100")
                .name("Chính sách % quá 100")
                .targetType(DiscountTargetType.PRODUCT)
                .productId(10L)
                .tiers(Collections.singletonList(
                        VolumeDiscountTierRequest.builder()
                                .minQuantity(new BigDecimal("10"))
                                .discountType(DiscountType.PERCENTAGE)
                                .discountValue(new BigDecimal("105.0"))
                                .build()
                ))
                .build();

        when(policyRepository.existsByCode("CK-OVER-100")).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(productKen));

        assertThatThrownBy(() -> discountService.createPolicy(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được vượt quá 100%");
    }

    @Test
    @DisplayName("Quy tắc cốt lõi: Nhiều chính sách cùng áp dụng thì tự động lấy chính sách có lợi nhất cho khách (chiết khấu cao nhất)")
    void testCoreRule_MultiplePolicies_ChoosesMostBeneficialForCustomer() {
        // Chính sách 1: SKU Heineken, mua từ 100 lon giảm 5%
        VolumeDiscountPolicy skuPolicy = VolumeDiscountPolicy.builder()
                .id(1L)
                .code("CK-SKU-KEN")
                .name("Chiết khấu Heineken 5%")
                .targetType(DiscountTargetType.PRODUCT)
                .product(productKen)
                .active(true)
                .tiers(new ArrayList<>())
                .build();
        skuPolicy.addTier(VolumeDiscountTier.builder()
                .id(101L)
                .policy(skuPolicy)
                .minQuantity(new BigDecimal("100"))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("5.0"))
                .build());

        // Chính sách 2: Nhóm Bia, mua từ 100 lon giảm 10%
        VolumeDiscountPolicy categoryPolicy = VolumeDiscountPolicy.builder()
                .id(2L)
                .code("CK-CAT-BEER")
                .name("Chiết khấu Nhóm Bia 10%")
                .targetType(DiscountTargetType.CATEGORY)
                .category(categoryBia)
                .active(true)
                .tiers(new ArrayList<>())
                .build();
        categoryPolicy.addTier(VolumeDiscountTier.builder()
                .id(102L)
                .policy(categoryPolicy)
                .minQuantity(new BigDecimal("100"))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.0"))
                .build());

        // Khách mua 100 lon Heineken với đơn giá 16,500đ:
        // - Theo chính sách SKU (5%): chiết khấu = 1,650,000 * 5% = 82,500đ
        // - Theo chính sách Nhóm Bia (10%): chiết khấu = 1,650,000 * 10% = 165,000đ
        // -> Hệ thống PHẢI chọn chính sách Nhóm Bia (10%) vì mang lại 165,000đ (có lợi nhất cho khách)!
        when(policyRepository.findActivePoliciesByDate(any(LocalDate.class)))
                .thenReturn(Arrays.asList(skuPolicy, categoryPolicy));

        VolumeDiscountPolicyService.OrderItemDiscountInput input = VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                .index(0)
                .product(productKen)
                .unit("lon")
                .quantity(new BigDecimal("100"))
                .conversionFactor(BigDecimal.ONE)
                .baseQuantity(new BigDecimal("100"))
                .baseUnit("lon")
                .price(new BigDecimal("16500"))
                .subtotalAmount(new BigDecimal("1650000.00"))
                .build();

        VolumeDiscountPolicyService.OrderDiscountSummary result = discountService.calculateOrderDiscount(
                Collections.singletonList(input),
                CustomerGroup.DAI_LY_CAP_1,
                LocalDate.now()
        );

        assertThat(result.getDiscountAmount()).isEqualByComparingTo("1650000.00".substring(0, 0) + "165000.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("1485000.00");

        VolumeDiscountPolicyService.ItemDiscountResult itemResult = result.getItemResults().get(0);
        assertThat(itemResult.getAppliedPolicyCode()).isEqualTo("CK-CAT-BEER");
        assertThat(itemResult.getDiscountPercent()).isEqualByComparingTo("10.0");
        assertThat(itemResult.getDiscountAmount()).isEqualByComparingTo("165000.00");
        assertThat(itemResult.getBestPolicyReason()).contains("có lợi nhất cho khách");
        assertThat(itemResult.getCandidatePolicies()).hasSize(2);
    }

    @Test
    @DisplayName("So sánh chiết khấu phần trăm vs số tiền cố định: chọn chính sách có lợi nhất")
    void testCoreRule_PercentageVsFixedAmount_ChoosesHigherDiscount() {
        // Chính sách 1: Giảm 5% cho Heineken (tại đơn giá 16,500đ là 825đ/lon)
        VolumeDiscountPolicy percentagePolicy = VolumeDiscountPolicy.builder()
                .id(1L)
                .code("CK-KEN-PERCENT")
                .name("Chiết khấu Heineken 5%")
                .targetType(DiscountTargetType.PRODUCT)
                .product(productKen)
                .active(true)
                .tiers(new ArrayList<>())
                .build();
        percentagePolicy.addTier(VolumeDiscountTier.builder()
                .id(101L)
                .policy(percentagePolicy)
                .minQuantity(new BigDecimal("50"))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("5.0"))
                .build());

        // Chính sách 2: Giảm 1,200đ / đơn vị cơ sở cho nhóm Bia
        VolumeDiscountPolicy fixedPolicy = VolumeDiscountPolicy.builder()
                .id(2L)
                .code("CK-BIA-FIXED")
                .name("Chiết khấu Nhóm Bia 1200đ/lon")
                .targetType(DiscountTargetType.CATEGORY)
                .category(categoryBia)
                .active(true)
                .tiers(new ArrayList<>())
                .build();
        fixedPolicy.addTier(VolumeDiscountTier.builder()
                .id(102L)
                .policy(fixedPolicy)
                .minQuantity(new BigDecimal("50"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("1200.00"))
                .build());

        when(policyRepository.findActivePoliciesByDate(any(LocalDate.class)))
                .thenReturn(Arrays.asList(percentagePolicy, fixedPolicy));

        // Khách mua 60 lon Heineken:
        // - Theo % (5%): 60 * 16,500 * 5% = 49,500đ
        // - Theo Fixed (1,200đ/lon): 60 * 1,200 = 72,000đ
        // -> Hệ thống chọn Fixed (72,000đ > 49,500đ)!
        VolumeDiscountPolicyService.OrderItemDiscountInput input = VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                .index(0)
                .product(productKen)
                .unit("lon")
                .quantity(new BigDecimal("60"))
                .conversionFactor(BigDecimal.ONE)
                .baseQuantity(new BigDecimal("60"))
                .baseUnit("lon")
                .price(new BigDecimal("16500"))
                .subtotalAmount(new BigDecimal("990000.00"))
                .build();

        VolumeDiscountPolicyService.OrderDiscountSummary result = discountService.calculateOrderDiscount(
                Collections.singletonList(input),
                CustomerGroup.DAI_LY_CAP_1,
                LocalDate.now()
        );

        VolumeDiscountPolicyService.ItemDiscountResult itemRes = result.getItemResults().get(0);
        assertThat(itemRes.getAppliedPolicyCode()).isEqualTo("CK-BIA-FIXED");
        assertThat(itemRes.getDiscountAmount()).isEqualByComparingTo("72000.00");
    }

    @Test
    @DisplayName("Sản phẩm thuộc nhóm con hưởng chính sách chiết khấu của nhóm cha cấp trên")
    void testHierarchy_ChildCategoryProductInheritsParentPolicy() {
        // Chính sách đặt ở ngành Đồ uống (Cấp 1)
        VolumeDiscountPolicy parentPolicy = VolumeDiscountPolicy.builder()
                .id(10L)
                .code("CK-BEV-ROOT")
                .name("Chiết khấu toàn ngành Đồ uống")
                .targetType(DiscountTargetType.CATEGORY)
                .category(categoryNuocGiaiKhat) // Cấp 1
                .active(true)
                .tiers(new ArrayList<>())
                .build();
        parentPolicy.addTier(VolumeDiscountTier.builder()
                .id(201L)
                .policy(parentPolicy)
                .minQuantity(new BigDecimal("100"))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("7.0"))
                .build());

        when(policyRepository.findActivePoliciesByDate(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(parentPolicy));

        // productKen thuộc categoryBia (Cấp 2, có parent là categoryNuocGiaiKhat)
        VolumeDiscountPolicyService.OrderItemDiscountInput input = VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                .index(0)
                .product(productKen)
                .unit("lon")
                .quantity(new BigDecimal("120"))
                .conversionFactor(BigDecimal.ONE)
                .baseQuantity(new BigDecimal("120"))
                .baseUnit("lon")
                .price(new BigDecimal("16500"))
                .subtotalAmount(new BigDecimal("1980000.00"))
                .build();

        VolumeDiscountPolicyService.OrderDiscountSummary result = discountService.calculateOrderDiscount(
                Collections.singletonList(input),
                null,
                LocalDate.now()
        );

        assertThat(result.getDiscountAmount()).isEqualByComparingTo("138600.00"); // 1,980,000 * 7%
        assertThat(result.getItemResults().get(0).getAppliedPolicyCode()).isEqualTo("CK-BEV-ROOT");
    }

    @Test
    @DisplayName("Nhiều sản phẩm cùng nhóm hàng cộng dồn sản lượng để đạt bậc chiết khấu nhóm")
    void testMultipleProductsInCategory_AggregateQuantityForTier() {
        // Chính sách nhóm Bia: mua từ 100 lon giảm 8%
        VolumeDiscountPolicy beerPolicy = VolumeDiscountPolicy.builder()
                .id(20L)
                .code("CK-BEER-GROUP")
                .name("Chiết khấu nhóm bia từ 100 lon")
                .targetType(DiscountTargetType.CATEGORY)
                .category(categoryBia)
                .active(true)
                .tiers(new ArrayList<>())
                .build();
        beerPolicy.addTier(VolumeDiscountTier.builder()
                .id(301L)
                .policy(beerPolicy)
                .minQuantity(new BigDecimal("100"))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("8.0"))
                .build());

        when(policyRepository.findActivePoliciesByDate(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(beerPolicy));

        // Khách mua 60 lon Heineken và 40 lon Tiger (tổng nhóm Bia = 100 lon)
        VolumeDiscountPolicyService.OrderItemDiscountInput kenInput = VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                .index(0)
                .product(productKen)
                .unit("lon")
                .quantity(new BigDecimal("60"))
                .conversionFactor(BigDecimal.ONE)
                .baseQuantity(new BigDecimal("60"))
                .baseUnit("lon")
                .price(new BigDecimal("16500"))
                .subtotalAmount(new BigDecimal("990000.00"))
                .build();

        VolumeDiscountPolicyService.OrderItemDiscountInput tigerInput = VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                .index(1)
                .product(productTiger)
                .unit("lon")
                .quantity(new BigDecimal("40"))
                .conversionFactor(BigDecimal.ONE)
                .baseQuantity(new BigDecimal("40"))
                .baseUnit("lon")
                .price(new BigDecimal("15000"))
                .subtotalAmount(new BigDecimal("600000.00"))
                .build();

        VolumeDiscountPolicyService.OrderDiscountSummary result = discountService.calculateOrderDiscount(
                Arrays.asList(kenInput, tigerInput),
                null,
                LocalDate.now()
        );

        // Cả 2 mặt hàng đều đạt bậc 8% nhờ tổng sản lượng nhóm đạt 100 lon
        assertThat(result.getItemResults().get(0).getDiscountAmount()).isEqualByComparingTo("79200.00"); // 990,000 * 8%
        assertThat(result.getItemResults().get(1).getDiscountAmount()).isEqualByComparingTo("48000.00"); // 600,000 * 8%
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("127200.00");
    }
}


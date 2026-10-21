package com.quanlybanhang.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * S5-02: Kiểm thử tích hợp đầy đủ HTTP/JWT/JPA/H2 cho Cổng đại lý đặt lại đơn hàng cũ.
 * Kiểm tra phân loại hàng còn kinh doanh vs ngừng kinh doanh, tính lại giá hiện hành,
 * và thực thi ma trận quyền động (live permission matrix).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dealer_reorder_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=sprint-audit-test-secret-only-at-least-32-characters",
        "app.bootstrap.admin-password=SprintTest2026",
        "app.bootstrap.admin-email=audit@example.invalid",
        "app.seed-demo-users=false",
        "app.seed-demo-data=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("S5-02 Integration: Đặt lại đơn hàng cũ trên Cổng đại lý (HTTP/JWT/H2/Ma trận quyền)")
class DealerPortalReorderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired WarehouseRepository warehouses;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired DealerRepository dealers;
    @Autowired PriceListRepository prices;
    @Autowired UnitConversionRepository conversions;
    @Autowired WarehouseInventoryRepository inventories;
    @Autowired PasswordEncoder passwords;
    @Autowired RolePermissionConfig matrix;
    @Autowired OrderRepository orders;

    private Warehouse warehouse;
    private Product activeProduct;
    private Product discontinuedProduct;
    private Dealer dealer1;
    private Dealer dealer2;
    private Order oldOrder;
    private String dealer1Token;
    private String dealer2Token;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String key = UUID.randomUUID().toString().substring(0, 8);
        warehouse = warehouses.save(Warehouse.builder().code("WH-" + key).name("Kho " + key).build());
        Category category = categories.save(Category.builder().code("CAT-" + key).name("Nhóm " + key).build());

        activeProduct = products.save(Product.builder()
                .code("SKU-ACT-" + key)
                .name("Bia Heineken Lon 330ml")
                .category(category)
                .unit("lon")
                .price(new BigDecimal("18000"))
                .costPrice(new BigDecimal("12000"))
                .active(true)
                .build());

        discontinuedProduct = products.save(Product.builder()
                .code("SKU-DISC-" + key)
                .name("Bia Cũ Ngừng Bán")
                .category(category)
                .unit("chai")
                .price(new BigDecimal("15000"))
                .costPrice(new BigDecimal("10000"))
                .active(false) // Ngừng kinh doanh
                .build());

        conversions.save(UnitConversion.builder()
                .product(activeProduct)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24"))
                .isBaseUnit(false)
                .price(new BigDecimal("432000"))
                .build());

        inventories.save(WarehouseInventory.builder()
                .warehouse(warehouse)
                .product(activeProduct)
                .baseQuantity(new BigDecimal("1000"))
                .baseUnit("lon")
                .build());

        User sales = createUser("sales-" + key, ERole.ROLE_SALES, null);

        dealer1 = dealers.save(Dealer.builder()
                .code("DL1-" + key)
                .name("Đại lý Minh Phát")
                .assignedUser(sales)
                .warehouse(warehouse)
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .debtLimit(new BigDecimal("50000000"))
                .active(true)
                .locked(false)
                .build());

        dealer2 = dealers.save(Dealer.builder()
                .code("DL2-" + key)
                .name("Đại lý Thành Công")
                .assignedUser(sales)
                .warehouse(warehouse)
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .debtLimit(new BigDecimal("50000000"))
                .active(true)
                .locked(false)
                .build());

        User rep1 = createUser("rep1-" + key, ERole.ROLE_DEALER, dealer1);
        User rep2 = createUser("rep2-" + key, ERole.ROLE_DEALER, dealer2);

        // Bảng giá hiệu lực hiện hành: giá lon tăng lên 20.000 (thùng 480.000)
        PriceList priceList = PriceList.builder()
                .code("BG-" + key)
                .name("Bảng giá hiện hành " + key)
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.now().minusDays(2))
                .endDate(LocalDate.now().plusDays(30))
                .active(true)
                .build();
        priceList.addItem(PriceListItem.builder()
                .product(activeProduct)
                .sellingPrice(new BigDecimal("20000"))
                .floorPrice(new BigDecimal("18000"))
                .build());
        prices.save(priceList);

        // Đơn hàng cũ của dealer1 gồm 2 dòng: activeProduct và discontinuedProduct
        OrderItem oldItem1 = OrderItem.builder()
                .product(activeProduct)
                .unit("thùng")
                .quantity(new BigDecimal("10"))
                .conversionFactor(new BigDecimal("24"))
                .baseUnit("lon")
                .baseQuantity(new BigDecimal("240"))
                .price(new BigDecimal("432000")) // Giá cũ
                .floorPrice(new BigDecimal("380000"))
                .subtotalAmount(new BigDecimal("4320000"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("4320000"))
                .build();

        OrderItem oldItem2 = OrderItem.builder()
                .product(discontinuedProduct)
                .unit("chai")
                .quantity(new BigDecimal("50"))
                .conversionFactor(BigDecimal.ONE)
                .baseUnit("chai")
                .baseQuantity(new BigDecimal("50"))
                .price(new BigDecimal("15000"))
                .floorPrice(new BigDecimal("12000"))
                .subtotalAmount(new BigDecimal("750000"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("750000"))
                .build();

        oldOrder = Order.builder()
                .orderCode("DH-OLD-" + key)
                .dealer(dealer1)
                .customerName(dealer1.getName())
                .customerGroup(dealer1.getCustomerGroup())
                .warehouse(warehouse)
                .status("DELIVERED")
                .subtotalAmount(new BigDecimal("5070000"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("5070000"))
                .createdBy(rep1.getUsername())
                .build();
        oldOrder.addItem(oldItem1);
        oldOrder.addItem(oldItem2);
        orders.save(oldOrder);

        dealer1Token = login(rep1.getUsername());
        dealer2Token = login(rep2.getUsername());
        adminToken = login("admin");
    }

    @Test
    @DisplayName("S5-02 Flow: Xem trước -> Đặt lại đơn -> Loại bỏ hàng ngừng bán, áp giá mới, đơn ở PENDING_APPROVAL")
    void fullReorderFlow_Success() throws Exception {
        // 1. Preview
        JsonNode preview = call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview"),
                dealer1Token, null, 200).get("data");

        assertThat(preview.get("canReorder").asBoolean()).isTrue();
        assertThat(preview.get("availableItems").size()).isEqualTo(1);
        JsonNode avail = preview.get("availableItems").get(0);
        assertThat(avail.get("productCode").asText()).isEqualTo(activeProduct.getCode());
        assertThat(avail.get("unit").asText()).isEqualTo("thùng");
        assertThat(avail.get("oldPrice").asDouble()).isEqualTo(432000.0);
        // Giá mới: 20.000 * 24 = 480.000
        assertThat(avail.get("currentPrice").asDouble()).isEqualTo(480000.0);

        // Hàng ngừng kinh doanh bị phân loại riêng
        assertThat(preview.get("discontinuedItems").size()).isEqualTo(1);
        assertThat(preview.get("discontinuedItems").get(0).get("productCode").asText())
                .isEqualTo(discontinuedProduct.getCode());
        assertThat(preview.get("warnings").size()).isEqualTo(1);

        // 2. Submit Reorder
        JsonNode reorderRes = call(post("/api/portal/orders/" + oldOrder.getId() + "/reorder"),
                dealer1Token, Map.of("note", "Giao hàng sớm"), 201).get("data");

        JsonNode createdOrder = reorderRes.get("order");
        assertThat(createdOrder.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(createdOrder.get("requiresApproval").asBoolean()).isTrue();
        assertThat(createdOrder.get("items").size()).isEqualTo(1);
        assertThat(createdOrder.get("items").get(0).get("productCode").asText()).isEqualTo(activeProduct.getCode());

        // Kiểm tra loại bỏ mặt hàng ngừng kinh doanh kèm thông báo
        assertThat(reorderRes.get("excludedItems").size()).isEqualTo(1);
        assertThat(reorderRes.get("warnings").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("S5-02 RLS: Đại lý khác truy cập đơn của đại lý 1 -> 403 Forbidden")
    void crossDealerAccess_Forbidden() throws Exception {
        call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview"),
                dealer2Token, null, 403);

        call(post("/api/portal/orders/" + oldOrder.getId() + "/reorder"),
                dealer2Token, Map.of(), 403);
    }

    @Test
    @DisplayName("S5-02 Live Permission Matrix: Thu hồi quyền ORDER_CREATE / ORDER_VIEW có hiệu lực ngay trên JWT đang mở")
    void livePermissionMatrixEnforcement() throws Exception {
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_DEALER);
        try {
            // Bước 1: Khi có đủ quyền -> thành công
            call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview"), dealer1Token, null, 200);

            // Bước 2: Thu hồi ORDER_CREATE khỏi ROLE_DEALER trong ma trận quyền DB
            matrix.updateRolePermissions(ERole.ROLE_DEALER, Set.of(Permission.ORDER_VIEW, Permission.PRODUCT_VIEW));

            // Ngay lập tức trên cùng token cũ:
            // POST /reorder phải trả 403
            call(post("/api/portal/orders/" + oldOrder.getId() + "/reorder"), dealer1Token, Map.of(), 403);
            // GET /reorder-preview cũng yêu cầu ORDER_CREATE -> trả 403
            call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview"), dealer1Token, null, 403);

            // Bước 3: Thu hồi ORDER_VIEW, cấp lại ORDER_CREATE
            matrix.updateRolePermissions(ERole.ROLE_DEALER, Set.of(Permission.ORDER_CREATE, Permission.PRODUCT_VIEW));
            call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview"), dealer1Token, null, 403);

            // Bước 4: Khôi phục quyền -> lại thành công
            matrix.updateRolePermissions(ERole.ROLE_DEALER, Set.of(Permission.ORDER_VIEW, Permission.ORDER_CREATE, Permission.PRODUCT_VIEW));
            call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview"), dealer1Token, null, 200);

            // Quản trị viên (ROLE_ADMIN) luôn luôn được phép bất kể ma trận
            call(get("/api/portal/orders/" + oldOrder.getId() + "/reorder-preview?dealerId=" + dealer1.getId()), adminToken, null, 200);
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, original);
        }
    }

    private User createUser(String username, ERole role, Dealer linkedDealer) {
        return users.save(User.builder()
                .username(username)
                .email(username + "@example.invalid")
                .fullName(username)
                .password(passwords.encode("SprintTest2026"))
                .roles(Set.of(roles.findByName(role).orElseThrow()))
                .dealer(linkedDealer)
                .warehouses(Set.of(warehouse))
                .active(true)
                .build());
    }

    private String login(String username) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("username", username, "password", "SprintTest2026"), 200)
                .get("accessToken").asText();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        String payload = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as("%s", payload).isEqualTo(expected);
        return json.readTree(payload);
    }
}

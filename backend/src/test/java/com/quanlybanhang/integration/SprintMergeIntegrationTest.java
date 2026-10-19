package com.quanlybanhang.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** HTTP, security, JPA and transactions use a disposable database, without mocks. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sprintmerge;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false", "app.jwt.secret=sprint-audit-test-secret-only-at-least-32-characters",
        "app.bootstrap.admin-password=SprintTest2026", "app.bootstrap.admin-email=audit@example.invalid",
        "app.seed-demo-users=false", "app.seed-demo-data=false",
        "app.order.reservation-check-interval-ms=3600000"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SprintMergeIntegrationTest {
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
    @Autowired SupplierRepository suppliers;
    @Autowired PasswordEncoder passwords;
    @Autowired RolePermissionConfig matrix;

    Warehouse warehouse, destination;
    Product product;
    Dealer dealer;
    String managerToken, dealerToken, salesToken, strangerToken;
    Long dealerUserId;

    @BeforeEach
    void fixtures() throws Exception {
        String key = UUID.randomUUID().toString().substring(0, 8);
        warehouse = warehouses.save(Warehouse.builder().code("WH-" + key).name("Kho kiểm thử " + key).build());
        destination = warehouses.save(Warehouse.builder().code("TO-" + key).name("Kho nhận " + key).build());
        var category = categories.save(Category.builder().code("CAT-" + key).name("Nhóm kiểm thử").build());
        product = products.save(Product.builder().code("SKU-" + key).name("Sản phẩm kiểm thử")
                .category(category).unit("lon").price(new BigDecimal("25000"))
                .costPrice(new BigDecimal("10000")).active(true).build());
        conversions.save(UnitConversion.builder().product(product).unitName("thùng")
                .conversionFactor(new BigDecimal("24")).isBaseUnit(false).price(BigDecimal.ONE).build());
        inventories.save(WarehouseInventory.builder().warehouse(warehouse).product(product)
                .baseQuantity(new BigDecimal("240")).baseUnit("lon").build());
        User sales = createUser("sale-" + key, ERole.ROLE_SALES, null);
        dealer = dealers.save(Dealer.builder().code("DL-" + key).name("Đại lý kiểm thử")
                .assignedUser(sales).warehouse(warehouse).customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .debtLimit(new BigDecimal("100000000")).build());
        User representative = createUser("dealer-" + key, ERole.ROLE_DEALER, dealer);
        dealerUserId = representative.getId();
        createUser("stranger-" + key, ERole.ROLE_SALES, null);
        User manager = createUser("manager-" + key, ERole.ROLE_SALES_MANAGER, null);
        var priceList = PriceList.builder().code("BG-" + key).name("Bảng giá kiểm thử")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1).startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(30)).build();
        priceList.addItem(PriceListItem.builder().product(product).sellingPrice(new BigDecimal("20000"))
                .floorPrice(new BigDecimal("18000")).build());
        prices.save(priceList);
        managerToken = login(manager.getUsername());
        dealerToken = login(representative.getUsername());
        salesToken = login(sales.getUsername());
        strangerToken = login("stranger-" + key);
    }

    @Test
    void portalOrderApprovalCancellationAndPdfUseTheSameDatabase() throws Exception {
        assertThat(call(get("/api/portal/profile"), dealerToken, null, 200).at("/data/id").asLong())
                .isEqualTo(dealer.getId());
        JsonNode catalog = call(get("/api/portal/catalog"), dealerToken, null, 200).get("data");
        JsonNode current = null;
        for (JsonNode item : catalog) if (item.get("id").asLong() == product.getId()) current = item;
        assertThat(current).isNotNull();
        assertThat(current.toString()).doesNotContain("costPrice");
        assertThat(current.at("/units/1/price").decimalValue()).isEqualByComparingTo("480000");
        call(get("/api/products"), dealerToken, null, 403);
        call(get("/api/orders"), dealerToken, null, 403);
        JsonNode created = call(post("/api/portal/orders"), dealerToken, orderBody(2, true), 201).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("960000");
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
        call(get("/api/portal/orders/" + id), dealerToken, null, 200);
        call(put("/api/orders/" + id + "/approve"), strangerToken, null, 403);
        call(put("/api/orders/" + id + "/approve"), salesToken, null, 200);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("48");
        JsonNode filtered = call(get("/api/orders").param("dealerId", dealer.getId().toString())
                .param("status", "RECORDED").param("page", "0").param("size", "1"), managerToken, null, 200);
        assertThat(filtered.get("totalAmount").decimalValue()).isEqualByComparingTo("960000");
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
        assertThat(filtered.get("data").size()).isEqualTo(1);
        MvcResult pdf = mvc.perform(get("/api/orders/" + id + "/pdf")
                .header("Authorization", "Bearer " + managerToken)).andReturn();
        assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        assertThat(call(get("/api/dealers/" + dealer.getId() + "/purchase-history"), salesToken, null, 200)
                .get("data").size()).isEqualTo(1);
        call(put("/api/orders/" + id + "/cancel"), salesToken, Map.of("reason", "Đại lý đổi lịch"), 200);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void adminWithoutDealerLinkCanSelectADealerThroughoutThePortal() throws Exception {
        String admin = login("admin");
        assertThat(users.findByUsername("admin").orElseThrow().getDealer()).isNull();
        JsonNode options = call(get("/api/portal/dealers"), admin, null, 200).get("data");
        assertThat(options.findValues("id").stream().map(JsonNode::asLong)).contains(dealer.getId());
        for (String path : List.of("profile", "catalog", "debt", "delivery-points", "orders")) {
            call(get("/api/portal/" + path).param("dealerId", dealer.getId().toString()), admin, null, 200);
            call(get("/api/portal/" + path), admin, null, 400);
        }
        call(get("/api/portal/profile").param("dealerId", "-1"), admin, null, 400);
        JsonNode created = call(post("/api/portal/orders").param("dealerId", dealer.getId().toString()),
                admin, orderBody(1, true), 201).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("dealerId").asLong()).isEqualTo(dealer.getId());
        assertThat(created.get("createdBy").asText()).isEqualTo("admin");
        assertThat(created.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("480000");
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
        JsonNode orders = call(get("/api/portal/orders").param("dealerId", dealer.getId().toString()),
                admin, null, 200).get("data");
        assertThat(orders.size()).isEqualTo(1);
        call(get("/api/portal/orders/" + id).param("dealerId", dealer.getId().toString()), admin, null, 200);
        Dealer other = dealers.save(Dealer.builder().code("OTHER-" + product.getCode()).name("Đại lý khác")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2).warehouse(warehouse).build());
        assertThat(call(get("/api/portal/orders").param("dealerId", other.getId().toString()), admin, null, 200)
                .get("data").isEmpty()).isTrue();
        call(get("/api/portal/orders/" + id).param("dealerId", other.getId().toString()), admin, null, 403);
    }

    @Test
    void dealerCannotSelectAnotherDealerOrReadAdminOptions() throws Exception {
        Dealer other = dealers.save(Dealer.builder().code("OTHER-" + product.getCode()).name("Đại lý khác")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2).warehouse(warehouse).build());
        call(get("/api/portal/dealers"), dealerToken, null, 403);
        for (String path : List.of("profile", "catalog", "products", "debt", "delivery-points", "orders", "orders/1")) {
            call(get("/api/portal/" + path).param("dealerId", other.getId().toString()), dealerToken, null, 403);
        }
        call(post("/api/portal/orders").param("dealerId", other.getId().toString()), dealerToken, orderBody(1, false), 403);
        call(get("/api/portal/profile").param("dealerId", dealer.getId().toString()), dealerToken, null, 200);
        Set<Permission> before = new HashSet<>(matrix.getPermissions(ERole.ROLE_DEALER));
        try {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, EnumSet.of(Permission.ORDER_CREATE, Permission.PRODUCT_VIEW));
            for (String path : List.of("profile", "debt", "delivery-points", "orders", "orders/1")) {
                call(get("/api/portal/" + path).param("dealerId", dealer.getId().toString()), dealerToken, null, 403);
            }
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, before);
        }
    }

    @Test
    void receiptsAndTransfersKeepPhysicalStockSeparateFromDraftsAndTransit() throws Exception {
        String admin = login("admin");
        Supplier supplier = suppliers.save(Supplier.builder().code("NCC-" + product.getCode()).name("NCC kiểm thử").build());
        Map<String, Object> receipt = Map.of("warehouseId", warehouse.getId(), "supplierId", supplier.getId(),
                "documentNumber", "PN-TEST", "status", "DRAFT", "items",
                List.of(Map.of("productId", product.getId(), "unit", "thùng", "quantity", 2)));
        JsonNode draft = call(post("/api/warehouse/import"), admin, receipt, 200).get("data");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
        long receiptId = draft.get("id").asLong();
        call(post("/api/warehouse/vouchers/" + receiptId + "/confirm"), admin, null, 200);
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("288");
        call(put("/api/warehouse/vouchers/" + receiptId), admin, receipt, 400);
        JsonNode transfer = call(post("/api/warehouse/transfers"), admin,
                Map.of("fromWarehouseId", warehouse.getId(), "toWarehouseId", destination.getId(),
                        "items", List.of(Map.of("productId", product.getId(), "unit", "thùng", "quantity", 1))), 200).get("data");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("264");
        var receiving = inventories.findByWarehouseIdAndProductId(destination.getId(), product.getId()).orElseThrow();
        assertThat(receiving.getBaseQuantity()).isEqualByComparingTo("0");
        assertThat(receiving.getInTransitQuantity()).isEqualByComparingTo("24");
        call(post("/api/warehouse/transfers/" + transfer.get("id").asLong() + "/receive"), admin, Map.of(), 200);
        receiving = inventories.findByWarehouseIdAndProductId(destination.getId(), product.getId()).orElseThrow();
        assertThat(receiving.getBaseQuantity()).isEqualByComparingTo("24");
        assertThat(receiving.getInTransitQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void concurrentSalesCannotReserveMoreThanPhysicalStock() {
        var first = CompletableFuture.supplyAsync(() -> createSale(8));
        var second = CompletableFuture.supplyAsync(() -> createSale(8));
        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 400);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("192");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
    }

    @Test
    void dealerPermissionRevocationIsCheckedByTheServer() throws Exception {
        Set<Permission> before = new HashSet<>(matrix.getPermissions(ERole.ROLE_DEALER));
        try {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, EnumSet.of(Permission.ORDER_VIEW));
            call(get("/api/portal/catalog"), dealerToken, null, 403);
            call(post("/api/portal/orders"), dealerToken, orderBody(1, false), 403);
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, before);
        }
    }

    @Test
    void adminCanSelectAndSaveTheDealerLinkedToAnAccount() throws Exception {
        String admin = login("admin");
        JsonNode options = call(get("/api/admin/assignment-options"), admin, null, 200);
        assertThat(options.at("/data/dealers").isArray()).isTrue();
        var account = users.findById(dealerUserId).orElseThrow();
        var body = new HashMap<String, Object>();
        body.put("username", account.getUsername()); body.put("email", account.getEmail());
        body.put("fullName", account.getFullName()); body.put("roles", List.of("ROLE_DEALER"));
        call(put("/api/admin/users/" + dealerUserId), admin, body, 400);
        body.put("dealerId", dealer.getId());
        assertThat(call(put("/api/admin/users/" + dealerUserId), admin, body, 200).at("/data/dealerId").asLong())
                .isEqualTo(dealer.getId());
    }

    private int createSale(int quantity) {
        try {
            return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + salesToken)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(orderBody(quantity, false))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Map<String, Object> orderBody(int quantity, boolean forgedPrice) {
        var line = new HashMap<String, Object>();
        line.put("productId", product.getId()); line.put("unit", "thùng"); line.put("quantity", quantity);
        if (forgedPrice) line.put("price", 1);
        return Map.of("dealerId", dealer.getId(), "warehouseId", warehouse.getId(),
                "expectedDeliveryDate", LocalDate.now().plusDays(1).toString(), "items", List.of(line));
    }

    private User createUser(String username, ERole role, Dealer linkedDealer) {
        return users.save(User.builder().username(username).email(username + "@example.invalid")
                .fullName(username).password(passwords.encode("SprintTest2026"))
                .roles(Set.of(roles.findByName(role).orElseThrow())).dealer(linkedDealer)
                .warehouses(Set.of(warehouse, destination)).active(true).build());
    }

    private String login(String username) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("username", username, "password", "SprintTest2026"), 200)
                .get("accessToken").asText();
    }

    private WarehouseInventory stock() {
        return inventories.findByWarehouseIdAndProductId(warehouse.getId(), product.getId()).orElseThrow();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s", response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }
}

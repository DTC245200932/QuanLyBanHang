package com.quanlybanhang.config;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Role;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.RoleRepository;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.repository.WarehouseRepository;
import com.quanlybanhang.repository.TerritoryRepository;
import com.quanlybanhang.model.Warehouse;
import com.quanlybanhang.model.Territory;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Tự động tạo dữ liệu ban đầu khi ứng dụng khởi chạy:
 * - 7 roles nghiệp vụ
 * - 1 tài khoản admin mặc định
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WarehouseRepository warehouseRepository;
    private final TerritoryRepository territoryRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final com.quanlybanhang.repository.UnitConversionRepository unitConversionRepository;
    private final com.quanlybanhang.repository.PriceListRepository priceListRepository;
    private final com.quanlybanhang.repository.DealerRepository dealerRepository;

    @Value("${app.seed-demo-users:false}")
    private boolean seedDemoUsers;

    @Value("${app.bootstrap.admin-password:}")
    private String bootstrapAdminPassword;

    @Value("${app.demo-user.password:}")
    private String demoUserPassword;

    @Override
    public void run(String... args) {
        // Tạo 7 roles nếu chưa có
        for (ERole eRole : ERole.values()) {
            if (!roleRepository.existsByName(eRole)) {
                roleRepository.save(new Role(eRole));
                logger.info("Đã tạo role: {}", eRole.name());
            }
        }

        if (warehouseRepository.count() == 0) {
            warehouseRepository.save(Warehouse.builder().code("KHO-HCM").name("Kho trung tâm TP.HCM").build());
            warehouseRepository.save(Warehouse.builder().code("KHO-HN").name("Kho Hà Nội").build());
        }
        if (territoryRepository.count() == 0) {
            territoryRepository.save(Territory.builder().code("HCM-DONG").name("TP.HCM - Khu Đông").build());
            territoryRepository.save(Territory.builder().code("HCM-TAY").name("TP.HCM - Khu Tây").build());
            territoryRepository.save(Territory.builder().code("HN").name("Hà Nội").build());
        }

        // Khởi tạo cây nhóm hàng nhiều cấp và sản phẩm mẫu (S2-06)
        if (categoryRepository.count() == 0) {
            seedCategoriesAndProducts();
        }

        // Khởi tạo bảng giá theo nhóm khách hàng (S2-10)
        if (priceListRepository.count() == 0) {
            seedPriceLists();
        }

        // Tạo tài khoản admin mặc định nếu chưa có
        if (!userRepository.existsByUsername("admin")) {
            if (!StringUtils.hasText(bootstrapAdminPassword)) {
                throw new IllegalStateException(
                        "Chưa cấu hình APP_BOOTSTRAP_ADMIN_PASSWORD để tạo tài khoản admin ban đầu");
            }

            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy role ADMIN"));

            User admin = User.builder()
                    .username("admin")
                    .email("admin@quanlybanhang.com")
                    .password(passwordEncoder.encode(bootstrapAdminPassword))
                    .fullName("Quản trị viên")
                    .phone("0900000000")
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
            logger.info("Đã tạo tài khoản admin ban đầu (username: admin)");
        }

        if (seedDemoUsers) {
            if (!StringUtils.hasText(demoUserPassword)) {
                throw new IllegalStateException(
                        "APP_DEMO_USER_PASSWORD là bắt buộc khi bật APP_SEED_DEMO_USERS");
            }
            createDemoUsers(demoUserPassword);
        }
    }

    /**
     * Tạo tài khoản minh hoạ khi chạy local với APP_SEED_DEMO_USERS=true.
     * Chế độ này mặc định tắt và không nên bật ở production.
     */
    private void createDemoUsers(String password) {
        Warehouse demoWarehouse = warehouseRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .findFirst()
                .orElseGet(() -> warehouseRepository.save(
                        Warehouse.builder().code("KHO-DEMO").name("Kho Demo").build()));
        Territory demoTerritory = territoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .findFirst()
                .orElseGet(() -> territoryRepository.save(
                        Territory.builder().code("DB-DEMO").name("Địa bàn Demo").build()));

        createDemoUser("sales.manager", password, "Quản lý kinh doanh Demo",
                "sales.manager@demo.local", ERole.ROLE_SALES_MANAGER, Set.of(), Set.of());
        createDemoUser("sales.demo", password, "Nhân viên kinh doanh Demo",
                "sales@demo.local", ERole.ROLE_SALES, Set.of(), Set.of(demoTerritory));
        createDemoUser("warehouse.demo", password, "Thủ kho Demo",
                "warehouse@demo.local", ERole.ROLE_WAREHOUSE_STAFF,
                Set.of(demoWarehouse), Set.of());
        createDemoUser("accountant.demo", password, "Kế toán Demo",
                "accountant@demo.local", ERole.ROLE_ACCOUNTANT, Set.of(), Set.of());
        createDemoUser("viewer.demo", password, "Người xem Demo",
                "viewer@demo.local", ERole.ROLE_VIEWER, Set.of(), Set.of());
    }

    private void createDemoUser(String username, String rawPassword, String fullName,
                                String email, ERole roleName, Set<Warehouse> warehouses,
                                Set<Territory> territories) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy role " + roleName));
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .active(true)
                .roles(Set.of(role))
                .warehouses(warehouses)
                .territories(territories)
                .build();
        userRepository.save(user);
        logger.info("Đã tạo tài khoản demo '{}' với vai trò {}", username, roleName);
    }

    /**
     * Khởi tạo dữ liệu mẫu cây nhóm hàng 3 cấp và các sản phẩm có doanh số (S2-06):
     * Cấp 1: Ngành hàng (Điện tử & Thiết bị số, Thiết bị gia dụng)
     * Cấp 2: Phân nhóm (Thiết bị di động, Máy tính & Laptop, Điện gia dụng nhà bếp)
     * Cấp 3: Nhóm con (Điện thoại thông minh, Phụ kiện cáp sạc & pin, Nồi chiên & Lò nướng)
     */
    private void seedCategoriesAndProducts() {
        // Cấp 1: Ngành hàng
        Category ngDienTu = categoryRepository.save(Category.builder()
                .code("NG-DIENTU")
                .name("Điện tử & Thiết bị số")
                .description("Ngành hàng điện tử và các thiết bị công nghệ số")
                .level(1)
                .parent(null)
                .active(true)
                .build());

        Category ngGiaDung = categoryRepository.save(Category.builder()
                .code("NG-GIADUNG")
                .name("Thiết bị gia dụng")
                .description("Ngành hàng đồ dùng và thiết bị nhà bếp gia đình")
                .level(1)
                .parent(null)
                .active(true)
                .build());

        // Cấp 2: Phân nhóm
        Category pnDiDong = categoryRepository.save(Category.builder()
                .code("PN-DIDONG")
                .name("Thiết bị di động")
                .description("Điện thoại, máy tính bảng và phụ kiện")
                .level(2)
                .parent(ngDienTu)
                .active(true)
                .build());

        Category pnMayTinh = categoryRepository.save(Category.builder()
                .code("PN-MAYTINH")
                .name("Máy tính & Laptop")
                .description("Laptop, PC và linh kiện văn phòng")
                .level(2)
                .parent(ngDienTu)
                .active(true)
                .build());

        Category pnNhaBep = categoryRepository.save(Category.builder()
                .code("PN-NHABEP")
                .name("Điện gia dụng nhà bếp")
                .description("Nồi chiên, lò vi sóng, bếp từ")
                .level(2)
                .parent(ngGiaDung)
                .active(true)
                .build());

        // Cấp 3: Nhóm con
        Category ncSmartPhone = categoryRepository.save(Category.builder()
                .code("NC-SMARTPHONE")
                .name("Điện thoại thông minh")
                .description("Các dòng smartphone cao cấp và tầm trung")
                .level(3)
                .parent(pnDiDong)
                .active(true)
                .build());

        Category ncPhuKien = categoryRepository.save(Category.builder()
                .code("NC-PHUKIEN")
                .name("Phụ kiện cáp sạc & pin")
                .description("Củ sạc, cáp sạc nhanh, pin dự phòng")
                .level(3)
                .parent(pnDiDong)
                .active(true)
                .build());

        Category ncNoiChien = categoryRepository.save(Category.builder()
                .code("NC-NOICHIEN")
                .name("Nồi chiên & Lò nướng")
                .description("Nồi chiên không dầu và lò nướng mini")
                .level(3)
                .parent(pnNhaBep)
                .active(true)
                .build());

        // Sản phẩm mẫu gắn với nhóm hàng và có doanh số (S2-05 & S2-06)
        productRepository.save(Product.builder()
                .code("SP-IP15PM")
                .name("iPhone 15 Pro Max 256GB")
                .category(ncSmartPhone)
                .price(new BigDecimal("29990000"))
                .costPrice(new BigDecimal("24000000"))
                .revenue(new BigDecimal("150000000"))
                .unit("chiếc")
                .packingSpecification("Hộp 1 máy kèm cáp sạc USB-C")
                .hasTransactions(true)
                .active(true)
                .build());

        productRepository.save(Product.builder()
                .code("SP-S24U")
                .name("Samsung Galaxy S24 Ultra 512GB")
                .category(ncSmartPhone)
                .price(new BigDecimal("26990000"))
                .costPrice(new BigDecimal("21500000"))
                .revenue(new BigDecimal("120000000"))
                .unit("chiếc")
                .packingSpecification("Hộp 1 máy kèm bút S-Pen")
                .hasTransactions(true)
                .active(true)
                .build());

        productRepository.save(Product.builder()
                .code("SP-ANKER65W")
                .name("Củ sạc nhanh Anker GaN 65W")
                .category(ncPhuKien)
                .price(new BigDecimal("650000"))
                .costPrice(new BigDecimal("400000"))
                .revenue(new BigDecimal("30000000"))
                .unit("cái")
                .packingSpecification("Hộp 1 củ sạc 3 cổng")
                .hasTransactions(true)
                .active(true)
                .build());

        productRepository.save(Product.builder()
                .code("SP-NC-PHILIPS")
                .name("Nồi chiên không dầu Philips XXL HD9650")
                .category(ncNoiChien)
                .price(new BigDecimal("4500000"))
                .costPrice(new BigDecimal("3200000"))
                .revenue(new BigDecimal("45000000"))
                .unit("chiếc")
                .packingSpecification("Thùng 1 nồi kèm vỉ nướng")
                .hasTransactions(true)
                .active(true)
                .build());

        // Khởi tạo sản phẩm đồ uống minh hoạ S2-07 (Đơn vị tính quy đổi: lon, lốc, thùng)
        Category ngDoUong = categoryRepository.save(Category.builder()
                .code("NG-DOUONG")
                .name("Đồ uống & Giải khát")
                .description("Bia, nước ngọt và đồ uống đóng lon/chai")
                .level(1)
                .parent(null)
                .active(true)
                .build());

        Category pnBia = categoryRepository.save(Category.builder()
                .code("PN-BIA")
                .name("Bia các loại")
                .description("Bia lon, bia chai các thương hiệu")
                .level(2)
                .parent(ngDoUong)
                .active(true)
                .build());

        Category ncBiaLon = categoryRepository.save(Category.builder()
                .code("NC-BIALON")
                .name("Bia lon đóng thùng")
                .description("Bia lon 330ml quy cách lốc và thùng")
                .level(3)
                .parent(pnBia)
                .active(true)
                .build());

        Product biaHeineken = productRepository.save(Product.builder()
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .category(ncBiaLon)
                .price(new BigDecimal("19000"))
                .costPrice(new BigDecimal("15000"))
                .revenue(new BigDecimal("80000000"))
                .unit("lon")
                .active(true)
                .build());

        unitConversionRepository.save(com.quanlybanhang.model.UnitConversion.builder()
                .product(biaHeineken)
                .unitName("lốc")
                .conversionFactor(new BigDecimal("6.0000"))
                .isBaseUnit(false)
                .price(new BigDecimal("112000"))
                .costPrice(new BigDecimal("90000"))
                .description("1 lốc = 6 lon")
                .build());

        unitConversionRepository.save(com.quanlybanhang.model.UnitConversion.builder()
                .product(biaHeineken)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .isBaseUnit(false)
                .price(new BigDecimal("435000"))
                .costPrice(new BigDecimal("355000"))
                .description("1 thùng = 24 lon")
                .build());

        // Sản phẩm mới chưa phát sinh giao dịch (để kiểm thử chức năng xoá)
        productRepository.save(Product.builder()
                .code("SP-TAI-NGHE-TEST")
                .name("Tai nghe Bluetooth Không dây TWS")
                .category(ncPhuKien)
                .price(new BigDecimal("850000"))
                .costPrice(new BigDecimal("500000"))
                .revenue(BigDecimal.ZERO)
                .unit("bộ")
                .packingSpecification("Hộp 1 đôi kèm kén sạc và cáp")
                .hasTransactions(false)
                .active(true)
                .build());

        logger.info("Đã khởi tạo dữ liệu mẫu cho S2-05, S2-06 và S2-07");
    }

    /**
     * Khởi tạo bảng giá theo nhóm khách hàng (S2-10):
     * - Bảng giá Đại lý cấp 1 (chiết khấu cao nhất)
     * - Bảng giá Đại lý cấp 2
     * - Bảng giá Khách lẻ niêm yết
     */
    private void seedPriceLists() {
        if (dealerRepository.count() == 0) {
            dealerRepository.save(com.quanlybanhang.model.Dealer.builder()
                    .name("Đại lý Cấp 1 Minh Phát")
                    .customerGroup(com.quanlybanhang.model.CustomerGroup.DAI_LY_CAP_1)
                    .handoverRequired(false)
                    .build());
            dealerRepository.save(com.quanlybanhang.model.Dealer.builder()
                    .name("Đại lý Cấp 2 Tân Bình")
                    .customerGroup(com.quanlybanhang.model.CustomerGroup.DAI_LY_CAP_2)
                    .handoverRequired(false)
                    .build());
        }

        Product heineken = productRepository.findByCode("SP-KEN330").orElse(null);
        Product ip15 = productRepository.findByCode("SP-IP15PM").orElse(null);
        Product taiNghe = productRepository.findByCode("SP-TAI-NGHE-TEST").orElse(null);

        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate startOfYear = now.withDayOfYear(1);
        java.time.LocalDate endOfYear = now.withDayOfYear(now.lengthOfYear());

        // 1. Bảng giá Đại lý cấp 1 (S2-10)
        com.quanlybanhang.model.PriceList bgDl1 = com.quanlybanhang.model.PriceList.builder()
                .code("BG-DLC1-" + now.getYear())
                .name("Bảng giá Đại lý cấp 1 năm " + now.getYear())
                .customerGroup(com.quanlybanhang.model.CustomerGroup.DAI_LY_CAP_1)
                .startDate(startOfYear)
                .endDate(endOfYear)
                .version(1)
                .active(true)
                .hasOrders(false)
                .description("Bảng giá ưu đãi dành cho hệ thống Đại lý cấp 1 toàn quốc")
                .createdBy("admin")
                .build();

        if (heineken != null) {
            bgDl1.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(heineken)
                    .sellingPrice(new BigDecimal("16500"))
                    .floorPrice(new BigDecimal("15500"))
                    .note("Giá đại lý cấp 1")
                    .build());
        }
        if (ip15 != null) {
            bgDl1.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(ip15)
                    .sellingPrice(new BigDecimal("26000000"))
                    .floorPrice(new BigDecimal("24500000"))
                    .note("Giá đại lý cấp 1")
                    .build());
        }
        if (taiNghe != null) {
            bgDl1.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(taiNghe)
                    .sellingPrice(new BigDecimal("700000"))
                    .floorPrice(new BigDecimal("550000"))
                    .note("Giá đại lý cấp 1")
                    .build());
        }
        priceListRepository.save(bgDl1);

        // 2. Bảng giá Đại lý cấp 2 (S2-10)
        com.quanlybanhang.model.PriceList bgDl2 = com.quanlybanhang.model.PriceList.builder()
                .code("BG-DLC2-" + now.getYear())
                .name("Bảng giá Đại lý cấp 2 năm " + now.getYear())
                .customerGroup(com.quanlybanhang.model.CustomerGroup.DAI_LY_CAP_2)
                .startDate(startOfYear)
                .endDate(endOfYear)
                .version(1)
                .active(true)
                .hasOrders(false)
                .description("Bảng giá phân phối cho Đại lý cấp 2 khu vực")
                .createdBy("admin")
                .build();

        if (heineken != null) {
            bgDl2.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(heineken)
                    .sellingPrice(new BigDecimal("17500"))
                    .floorPrice(new BigDecimal("16500"))
                    .note("Giá đại lý cấp 2")
                    .build());
        }
        if (ip15 != null) {
            bgDl2.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(ip15)
                    .sellingPrice(new BigDecimal("27500000"))
                    .floorPrice(new BigDecimal("25500000"))
                    .note("Giá đại lý cấp 2")
                    .build());
        }
        if (taiNghe != null) {
            bgDl2.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(taiNghe)
                    .sellingPrice(new BigDecimal("750000"))
                    .floorPrice(new BigDecimal("600000"))
                    .note("Giá đại lý cấp 2")
                    .build());
        }
        priceListRepository.save(bgDl2);

        // 3. Bảng giá Khách lẻ (S2-10)
        com.quanlybanhang.model.PriceList bgKhachLe = com.quanlybanhang.model.PriceList.builder()
                .code("BG-KHACHLE-" + now.getYear())
                .name("Bảng giá Bán lẻ niêm yết năm " + now.getYear())
                .customerGroup(com.quanlybanhang.model.CustomerGroup.KHACH_LE)
                .startDate(startOfYear)
                .endDate(endOfYear)
                .version(1)
                .active(true)
                .hasOrders(false)
                .description("Bảng giá bán lẻ niêm yết chính thức")
                .createdBy("admin")
                .build();

        if (heineken != null) {
            bgKhachLe.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(heineken)
                    .sellingPrice(new BigDecimal("19000"))
                    .floorPrice(new BigDecimal("18000"))
                    .note("Giá bán lẻ")
                    .build());
        }
        if (ip15 != null) {
            bgKhachLe.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(ip15)
                    .sellingPrice(new BigDecimal("29990000"))
                    .floorPrice(new BigDecimal("28000000"))
                    .note("Giá bán lẻ")
                    .build());
        }
        if (taiNghe != null) {
            bgKhachLe.addItem(com.quanlybanhang.model.PriceListItem.builder()
                    .product(taiNghe)
                    .sellingPrice(new BigDecimal("850000"))
                    .floorPrice(new BigDecimal("750000"))
                    .note("Giá bán lẻ")
                    .build());
        }
        priceListRepository.save(bgKhachLe);

        logger.info("Đã khởi tạo 3 bảng giá song song mẫu cho S2-10 (Đại lý cấp 1, cấp 2, Khách lẻ)");
    }
}

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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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

    @Value("${app.seed-demo-users:false}")
    private boolean seedDemoUsers;

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

        // Tạo tài khoản admin mặc định nếu chưa có
        if (!userRepository.existsByUsername("admin")) {
            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy role ADMIN"));

            User admin = User.builder()
                    .username("admin")
                    .email("admin@quanlybanhang.com")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Quản trị viên")
                    .phone("0900000000")
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
            logger.info("Đã tạo tài khoản admin mặc định (username: admin, password: admin123)");
        }

        if (seedDemoUsers) {
            createDemoUsers();
        }
    }

    /**
     * Tạo tài khoản minh hoạ khi chạy local với APP_SEED_DEMO_USERS=true.
     * Chế độ này mặc định tắt và không nên bật ở production.
     */
    private void createDemoUsers() {
        Warehouse demoWarehouse = warehouseRepository.findAllByActiveTrueOrderByNameAsc().get(0);
        Territory demoTerritory = territoryRepository.findAllByActiveTrueOrderByNameAsc().get(0);

        createDemoUser("sales.manager", "SalesManager123", "Quản lý kinh doanh Demo",
                "sales.manager@demo.local", ERole.ROLE_SALES_MANAGER, Set.of(), Set.of());
        createDemoUser("sales.demo", "SalesDemo123", "Nhân viên kinh doanh Demo",
                "sales@demo.local", ERole.ROLE_SALES, Set.of(), Set.of(demoTerritory));
        createDemoUser("warehouse.demo", "Warehouse123", "Thủ kho Demo",
                "warehouse@demo.local", ERole.ROLE_WAREHOUSE_STAFF,
                Set.of(demoWarehouse), Set.of());
        createDemoUser("accountant.demo", "Accountant123", "Kế toán Demo",
                "accountant@demo.local", ERole.ROLE_ACCOUNTANT, Set.of(), Set.of());
        createDemoUser("viewer.demo", "ViewerDemo123", "Người xem Demo",
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
}

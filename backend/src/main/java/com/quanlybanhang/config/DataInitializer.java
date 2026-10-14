package com.quanlybanhang.config;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Role;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.RoleRepository;
import com.quanlybanhang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/** Chỉ khởi tạo vai trò hệ thống và tài khoản quản trị ban đầu. */
@Component
@Order(0)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-password:}")
    private String bootstrapAdminPassword;

    @Value("${app.bootstrap.admin-email:}")
    private String bootstrapAdminEmail;

    @Override
    @Transactional
    public void run(String... args) {
        boolean createAdmin = !userRepository.existsByUsername("admin");
        if (createAdmin) {
            if (!StringUtils.hasText(bootstrapAdminPassword)) {
                throw new IllegalStateException(
                        "Chưa cấu hình APP_BOOTSTRAP_ADMIN_PASSWORD để tạo tài khoản admin ban đầu");
            }
            if (!StringUtils.hasText(bootstrapAdminEmail)) {
                throw new IllegalStateException(
                        "Chưa cấu hình APP_BOOTSTRAP_ADMIN_EMAIL bằng email quản trị thực tế");
            }
        }

        for (ERole roleName : ERole.values()) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(new Role(roleName));
                logger.info("Đã tạo role: {}", roleName.name());
            }
        }

        if (createAdmin) {
            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy role ADMIN"));
            User admin = User.builder()
                    .username("admin")
                    .email(bootstrapAdminEmail.trim())
                    .password(passwordEncoder.encode(bootstrapAdminPassword))
                    .fullName("Quản trị viên")
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build();
            userRepository.save(admin);
            logger.info("Đã tạo tài khoản quản trị ban đầu (username: admin)");
        }
    }
}

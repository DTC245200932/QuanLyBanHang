package com.quanlybanhang.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Chạy sau khi các vai trò và admin đã được khởi tạo. */
@Component
@Order(1)
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final DemoDataSeeder seeder;

    @Value("${app.seed-demo-users:false}")
    private boolean seedDemoUsers;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.demo-user.password:}")
    private String demoUserPassword;

    @Override
    public void run(String... args) {
        if (seedDemoUsers || seedDemoData) {
            seeder.seed(seedDemoUsers, seedDemoData, demoUserPassword);
        }
    }
}

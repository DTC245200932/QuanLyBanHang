package com.quanlybanhang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuanLyBanHangApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuanLyBanHangApplication.class, args);
    }
}

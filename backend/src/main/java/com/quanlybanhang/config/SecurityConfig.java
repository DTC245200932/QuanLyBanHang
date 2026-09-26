package com.quanlybanhang.config;

import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cấu hình Spring Security: JWT stateless, CORS, endpoint permissions.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final AuthEntryPointJwt unauthorizedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Tắt CSRF vì dùng JWT (stateless)
            .csrf(AbstractHttpConfigurer::disable)

            // Xử lý lỗi xác thực
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(unauthorizedHandler))

            // Không tạo session - JWT stateless
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Phân quyền các endpoint
            .authorizeHttpRequests(auth -> auth
                // API auth: ai cũng truy cập được
                .requestMatchers("/api/auth/**").permitAll()

                // API test public
                .requestMatchers("/api/test/public").permitAll()

                // API admin: chỉ ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // API manager: ADMIN hoặc MANAGER
                .requestMatchers("/api/manager/**").hasAnyRole("ADMIN", "MANAGER")

                // Tất cả request khác phải đăng nhập
                .anyRequest().authenticated()
            )

            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

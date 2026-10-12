package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.dto.request.UserProfileRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.NamedResourceResponse;
import com.quanlybanhang.dto.response.UserProfileResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/users/profile - Lấy hồ sơ cá nhân thành công")
    void getProfileSuccess() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("sales_user", "password");
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(1L)
                .username("sales_user")
                .email("sales@company.com")
                .fullName("Nguyễn Văn Bán Hàng")
                .phone("0901234567")
                .roles(List.of("ROLE_SALES"))
                .warehouses(List.of(new NamedResourceResponse(1L, "KHO_CHINH", "Kho Chính")))
                .territories(List.of(new NamedResourceResponse(1L, "MB", "Miền Bắc")))
                .active(true)
                .build();

        when(userService.getUserProfile("sales_user")).thenReturn(profile);

        mockMvc.perform(get("/api/users/profile").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy hồ sơ thành công"))
                .andExpect(jsonPath("$.data.username").value("sales_user"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Bán Hàng"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_SALES"))
                .andExpect(jsonPath("$.data.warehouses[0].name").value("Kho Chính"))
                .andExpect(jsonPath("$.data.territories[0].name").value("Miền Bắc"));

        verify(userService).getUserProfile("sales_user");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0901234567",
            "0321234567",
            "0561234567",
            "0771234567",
            "0881234567",
            "+84901234567",
            "84901234567"
    })
    @DisplayName("PUT /api/users/profile - Cập nhật họ tên và SĐT Việt Nam hợp lệ thành công")
    void updateProfileValidPhoneSuccess(String validPhone) throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("sales_user", "password");
        UserProfileRequest request = UserProfileRequest.builder()
                .fullName("Nguyễn Văn A")
                .phone(validPhone)
                .build();

        when(userService.updateUserProfile(eq("sales_user"), any(UserProfileRequest.class)))
                .thenReturn(ApiResponse.success("Cập nhật hồ sơ thành công"));

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật hồ sơ thành công"));

        verify(userService).updateUserProfile(eq("sales_user"), any(UserProfileRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0123456789",   // Đầu số không thuộc mạng di động Việt Nam hiện hành
            "090123",       // Quá ngắn
            "090123456789", // Quá dài
            "090123456a",   // Có chữ cái
            "+1901234567",  // Mã nước ngoài
            "0243123456",   // Điện thoại bàn
            "   "           // Trống
    })
    @DisplayName("PUT /api/users/profile - Trả 400 khi số điện thoại không đúng định dạng Việt Nam")
    void updateProfileInvalidPhoneReturnsBadRequest(String invalidPhone) throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("sales_user", "password");
        String payload = String.format("{\"fullName\":\"Nguyễn Văn A\",\"phone\":\"%s\"}", invalidPhone);

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.data.phone").exists());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PUT /api/users/profile - Trả 400 khi họ tên để trống")
    void updateProfileBlankFullNameReturnsBadRequest() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("sales_user", "password");
        String payload = "{\"fullName\":\"   \",\"phone\":\"0901234567\"}";

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.data.fullName").value("Họ tên không được để trống"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PUT /api/users/profile - Trả 400 khi họ tên dài hơn 100 ký tự")
    void updateProfileTooLongFullNameReturnsBadRequest() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("sales_user", "password");
        String longFullName = "A".repeat(101);
        String payload = String.format("{\"fullName\":\"%s\",\"phone\":\"0901234567\"}", longFullName);

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.data.fullName").value("Họ tên không được vượt quá 100 ký tự"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PUT /api/users/profile - Không tự đổi được tài khoản, vai trò, kho, địa bàn khi gửi trường thừa")
    void updateProfileIgnoresUnauthorizedFields() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("sales_user", "password");
        // Giả lập người dùng cố tình gửi payload chứa username, email, roles, warehouseIds, territoryIds
        String payloadWithExtraFields = """
                {
                    "fullName": "Nguyễn Văn Hợp Lệ",
                    "phone": "0987654321",
                    "username": "hacked_admin",
                    "email": "hacked@admin.com",
                    "roles": ["ROLE_ADMIN"],
                    "warehouseIds": [1, 2, 3],
                    "territoryIds": [1, 2],
                    "active": true
                }
                """;

        when(userService.updateUserProfile(eq("sales_user"), any(UserProfileRequest.class)))
                .thenReturn(ApiResponse.success("Cập nhật hồ sơ thành công"));

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithExtraFields))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Xác nhận service chỉ nhận UserProfileRequest chứa fullName và phone, dùng username từ Authentication
        verify(userService).updateUserProfile(eq("sales_user"), argThat(req ->
                req.getFullName().equals("Nguyễn Văn Hợp Lệ") &&
                req.getPhone().equals("0987654321")
        ));
    }

    @Test
    @DisplayName("GET /api/users/profile - Trả 401 khi chưa xác thực")
    void getProfileUnauthenticatedThrows() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(userService);
    }
}

package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.AdminUserRequest;
import com.quanlybanhang.dto.response.NamedResourceResponse;
import com.quanlybanhang.dto.response.UserResponse;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final Set<ERole> WAREHOUSE_ROLES = EnumSet.of(
            ERole.ROLE_WAREHOUSE_MANAGER, ERole.ROLE_WAREHOUSE_STAFF);
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WarehouseRepository warehouseRepository;
    private final TerritoryRepository territoryRepository;
    private final DealerRepository dealerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailService accountMailService;

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String keyword, ERole role, Boolean active, Pageable pageable) {
        Specification<User> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), term),
                    cb.like(cb.lower(root.get("username")), term),
                    cb.like(cb.lower(root.get("phone")), term)));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("roles", JoinType.INNER).get("name"), role);
            });
        }
        return userRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(findUser(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        return toResponse(userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng")));
    }

    @Transactional
    public UserResponse create(AdminUserRequest request) {
        validateDuplicates(request.getUsername(), request.getEmail(), null);
        Set<Role> roles = resolveRoles(request.getRoles());
        Set<Warehouse> warehouses = resolveWarehouses(request.getWarehouseIds());
        validateWarehouseAssignment(roles, warehouses);
        Set<Territory> territories = resolveTerritories(request.getTerritoryIds());

        String temporaryPassword = generateTemporaryPassword();
        User user = User.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .fullName(request.getFullName().trim())
                .phone(trimToNull(request.getPhone()))
                .password(passwordEncoder.encode(temporaryPassword))
                .roles(roles)
                .warehouses(warehouses)
                .territories(territories)
                .mustChangePassword(true)
                .active(true)
                .build();
        userRepository.save(user);
        accountMailService.sendActivation(user, temporaryPassword);
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, AdminUserRequest request, String actingUsername) {
        User user = findUser(id);
        validateDuplicates(request.getUsername(), request.getEmail(), id);
        Set<Role> roles = resolveRoles(request.getRoles());
        if (user.getUsername().equals(actingUsername)
                && user.getRoles().stream().anyMatch(r -> r.getName() == ERole.ROLE_ADMIN)
                && roles.stream().noneMatch(r -> r.getName() == ERole.ROLE_ADMIN)) {
            throw new IllegalArgumentException("Bạn không thể tự thu hồi vai trò quản trị của chính mình");
        }

        Set<Warehouse> warehouses = resolveWarehouses(request.getWarehouseIds());
        validateWarehouseAssignment(roles, warehouses);
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        user.setFullName(request.getFullName().trim());
        user.setPhone(trimToNull(request.getPhone()));
        user.setRoles(roles);
        user.setWarehouses(warehouses);
        user.setTerritories(resolveTerritories(request.getTerritoryIds()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse lock(Long id, String reason, String actingUsername) {
        User user = findUser(id);
        if (user.getUsername().equals(actingUsername)) {
            throw new IllegalArgumentException("Bạn không thể tự khoá tài khoản đang đăng nhập");
        }
        user.setActive(false);
        user.setLockReason(reason.trim());
        user.setTokenVersion(user.getTokenVersion() + 1);

        List<Dealer> dealers = dealerRepository.findByAssignedUserId(id);
        dealers.forEach(dealer -> dealer.setHandoverRequired(true));
        dealerRepository.saveAll(dealers);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse unlock(Long id) {
        User user = findUser(id);
        user.setActive(true);
        user.setLockReason(null);
        user.setFailedLoginAttempts(0);
        user.setLockTime(null);
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<NamedResourceResponse> warehouses() {
        return warehouseRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(w -> new NamedResourceResponse(w.getId(), w.getCode(), w.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<NamedResourceResponse> territories() {
        return territoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(t -> new NamedResourceResponse(t.getId(), t.getCode(), t.getName())).toList();
    }

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .active(user.getActive())
                .lockReason(user.getLockReason())
                .mustChangePassword(user.getMustChangePassword())
                .roles(user.getRoles().stream().map(r -> r.getName().name()).sorted().toList())
                .warehouses(user.getWarehouses().stream()
                        .map(w -> new NamedResourceResponse(w.getId(), w.getCode(), w.getName())).toList())
                .territories(user.getTerritories().stream()
                        .map(t -> new NamedResourceResponse(t.getId(), t.getCode(), t.getName())).toList())
                .assignedDealerCount(dealerRepository.countByAssignedUserId(user.getId()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String value : roleNames) {
            try {
                ERole role = ERole.valueOf(value.toUpperCase(Locale.ROOT));
                roles.add(roleRepository.findByName(role)
                        .orElseThrow(() -> new IllegalArgumentException("Vai trò chưa được cấu hình: " + value)));
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage() != null && ex.getMessage().startsWith("Vai trò")) throw ex;
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + value);
            }
        }
        return roles;
    }

    private Set<Warehouse> resolveWarehouses(Set<Long> ids) {
        Set<Long> safeIds = ids == null ? Set.of() : ids;
        Set<Warehouse> result = new HashSet<>(warehouseRepository.findAllById(safeIds));
        if (result.size() != safeIds.size()) throw new IllegalArgumentException("Có kho không tồn tại");
        return result;
    }

    private Set<Territory> resolveTerritories(Set<Long> ids) {
        Set<Long> safeIds = ids == null ? Set.of() : ids;
        Set<Territory> result = new HashSet<>(territoryRepository.findAllById(safeIds));
        if (result.size() != safeIds.size()) throw new IllegalArgumentException("Có địa bàn không tồn tại");
        return result;
    }

    private void validateWarehouseAssignment(Set<Role> roles, Set<Warehouse> warehouses) {
        boolean needsWarehouse = roles.stream().map(Role::getName).anyMatch(WAREHOUSE_ROLES::contains);
        if (needsWarehouse && warehouses.isEmpty()) {
            throw new IllegalArgumentException("Người dùng thuộc vai trò kho phải được gắn ít nhất một kho");
        }
    }

    private void validateDuplicates(String username, String email, Long currentId) {
        userRepository.findByUsername(username.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) throw new IllegalArgumentException("Tên đăng nhập đã được sử dụng");
        });
        userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) throw new IllegalArgumentException("Email đã được sử dụng");
        });
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder value = new StringBuilder("A7");
        for (int i = 0; i < 10; i++) value.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        return value.toString();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

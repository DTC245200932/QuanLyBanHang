package com.quanlybanhang.config;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.quanlybanhang.model.Permission.*;

/**
 * Cấu hình ma trận phân quyền: mỗi vai trò (ERole) được gán một tập Permission.
 *
 * Nguyên tắc thiết kế:
 * - Mặc định là TỪ CHỐI — chỉ role nào được gán permission mới truy cập được
 * - ADMIN quản trị hệ thống nhưng không mặc nhiên xem dữ liệu giá vốn
 * - Giá vốn + biên lợi nhuận chỉ cho SALES_MANAGER
 * - Thủ kho chỉ thao tác trên module kho, không thấy giá vốn
 * - Nhân viên kinh doanh không sửa tồn kho
 */
@Component
public class RolePermissionConfig {

    private final Map<ERole, Set<Permission>> rolePermissions;

    public RolePermissionConfig() {
        rolePermissions = new EnumMap<>(ERole.class);
        initDefaultPermissions();
    }

    private void initDefaultPermissions() {
        // ── ADMIN: toàn quyền quản trị ──
        EnumSet<Permission> adminPermissions = EnumSet.allOf(Permission.class);
        // Giá vốn và biên lợi nhuận là dữ liệu nghiệp vụ riêng của Quản lý kinh doanh.
        adminPermissions.remove(FINANCE_VIEW_COST_PRICE);
        adminPermissions.remove(FINANCE_VIEW_PROFIT_MARGIN);
        rolePermissions.put(ERole.ROLE_ADMIN, adminPermissions);

        // ── SALES_MANAGER: quản lý kinh doanh — xem giá vốn, biên lợi nhuận ──
        rolePermissions.put(ERole.ROLE_SALES_MANAGER, EnumSet.of(
                PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_EDIT, PRODUCT_DELETE,
                ORDER_VIEW, ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_APPROVE,
                CUSTOMER_VIEW, CUSTOMER_CREATE, CUSTOMER_EDIT,
                WAREHOUSE_VIEW,
                FINANCE_VIEW_COST_PRICE, FINANCE_VIEW_PROFIT_MARGIN,
                FINANCE_VIEW_DEBT, FINANCE_MANAGE,
                REPORT_SALES, REPORT_INVENTORY, REPORT_FINANCE
        ));

        // ── WAREHOUSE_MANAGER: quản lý kho — phê duyệt nhập/xuất, điều chỉnh tồn kho ──
        rolePermissions.put(ERole.ROLE_WAREHOUSE_MANAGER, EnumSet.of(
                PRODUCT_VIEW,
                ORDER_VIEW,
                WAREHOUSE_VIEW, WAREHOUSE_IMPORT, WAREHOUSE_EXPORT,
                WAREHOUSE_ADJUST, WAREHOUSE_APPROVE,
                REPORT_INVENTORY
        ));

        // ── SALES: nhân viên kinh doanh — tạo đơn, quản lý khách, không sửa tồn kho, không xem giá vốn ──
        rolePermissions.put(ERole.ROLE_SALES, EnumSet.of(
                PRODUCT_VIEW,
                ORDER_VIEW, ORDER_CREATE, ORDER_EDIT,
                CUSTOMER_VIEW, CUSTOMER_CREATE, CUSTOMER_EDIT,
                REPORT_SALES
        ));

        // ── WAREHOUSE_STAFF: thủ kho — nhập/xuất kho, không sửa tồn kho, không thấy giá vốn ──
        rolePermissions.put(ERole.ROLE_WAREHOUSE_STAFF, EnumSet.of(
                PRODUCT_VIEW,
                ORDER_VIEW,
                WAREHOUSE_VIEW, WAREHOUSE_IMPORT, WAREHOUSE_EXPORT
        ));

        // ── ACCOUNTANT: kế toán — báo cáo tài chính, công nợ, không thấy giá vốn ──
        rolePermissions.put(ERole.ROLE_ACCOUNTANT, EnumSet.of(
                PRODUCT_VIEW,
                ORDER_VIEW,
                WAREHOUSE_VIEW,
                FINANCE_VIEW_DEBT, FINANCE_MANAGE,
                REPORT_SALES, REPORT_INVENTORY, REPORT_FINANCE
        ));

        // ── VIEWER: chỉ xem — không thao tác dữ liệu ──
        rolePermissions.put(ERole.ROLE_VIEWER, EnumSet.of(
                PRODUCT_VIEW,
                ORDER_VIEW,
                WAREHOUSE_VIEW,
                CUSTOMER_VIEW,
                REPORT_SALES, REPORT_INVENTORY
        ));
    }

    /**
     * Cập nhật danh sách quyền cho một vai trò, đảm bảo các quy tắc nghiệp vụ cốt lõi:
     * 1. Giá vốn và biên lợi nhuận CHỈ lộ ra với vai trò Quản lý kinh doanh (ROLE_SALES_MANAGER).
     * 2. Nhân viên kinh doanh (ROLE_SALES) KHÔNG được sửa điều chỉnh tồn kho (WAREHOUSE_ADJUST).
     */
    public synchronized void updateRolePermissions(ERole role, Set<Permission> newPermissions) {
        if (role == null) {
            throw new IllegalArgumentException("Vai trò không được để trống");
        }
        if (newPermissions == null) {
            newPermissions = Collections.emptySet();
        }

        // Quy tắc: Giá vốn và biên lợi nhuận chỉ dành cho Quản lý kinh doanh
        if (role != ERole.ROLE_SALES_MANAGER) {
            if (newPermissions.contains(FINANCE_VIEW_COST_PRICE) || newPermissions.contains(FINANCE_VIEW_PROFIT_MARGIN)) {
                throw new IllegalArgumentException("Giá vốn và biên lợi nhuận chỉ được phép cấp cho vai trò Quản lý kinh doanh");
            }
        }

        // Quy tắc: Nhân viên kinh doanh không được sửa tồn kho
        if (role == ERole.ROLE_SALES && newPermissions.contains(WAREHOUSE_ADJUST)) {
            throw new IllegalArgumentException("Nhân viên kinh doanh không được phép sửa tồn kho");
        }

        EnumSet<Permission> perms = newPermissions.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(newPermissions);
        rolePermissions.put(role, perms);
    }

    /**
     * Khôi phục ma trận phân quyền về mặc định ban đầu.
     */
    public synchronized void resetToDefault() {
        rolePermissions.clear();
        initDefaultPermissions();
    }

    /**
     * Lấy tất cả permissions của một role.
     */
    public Set<Permission> getPermissions(ERole role) {
        return Collections.unmodifiableSet(
                rolePermissions.getOrDefault(role, EnumSet.noneOf(Permission.class)));
    }

    /**
     * Kiểm tra role có permission cụ thể không.
     */
    public boolean hasPermission(ERole role, Permission permission) {
        return rolePermissions.getOrDefault(role, EnumSet.noneOf(Permission.class))
                .contains(permission);
    }

    /**
     * Gom permissions từ nhiều roles (user có nhiều role).
     */
    public Set<Permission> getPermissions(Collection<ERole> roles) {
        Set<Permission> combined = EnumSet.noneOf(Permission.class);
        for (ERole role : roles) {
            combined.addAll(getPermissions(role));
        }
        return Collections.unmodifiableSet(combined);
    }

    /**
     * Lấy toàn bộ ma trận phân quyền (dùng cho API admin xem/quản lý).
     */
    public Map<ERole, Set<Permission>> getAllPermissions() {
        return Collections.unmodifiableMap(rolePermissions);
    }
}

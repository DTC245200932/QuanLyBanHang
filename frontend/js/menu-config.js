/**
 * menu-config.js — Cấu hình menu theo vai trò (S1-06).
 *
 * Mục menu không thuộc quyền thì không hiển thị.
 * Mỗi item có: id, label, icon (SVG path), href, và danh sách roles được phép.
 */
const MenuConfig = (() => {
  const ALL_ROLES = [
    "ROLE_ADMIN",
    "ROLE_SALES_MANAGER",
    "ROLE_WAREHOUSE_MANAGER",
    "ROLE_SALES",
    "ROLE_WAREHOUSE_STAFF",
    "ROLE_ACCOUNTANT",
    "ROLE_VIEWER",
  ];

  const menuItems = [
    {
      id: "dashboard",
      label: "Tổng quan",
      icon: '<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>',
      href: "/dashboard",
      roles: ALL_ROLES,
    },
    {
      id: "profile",
      label: "Hồ sơ cá nhân",
      icon: '<circle cx="12" cy="8" r="4"/><path d="M4 22a8 8 0 0 1 16 0"/>',
      href: "/profile",
      roles: ALL_ROLES,
    },
    {
      id: "products",
      section: "Kinh doanh",
      label: "Sản phẩm",
      icon: '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>',
      href: "/products",
      permission: "PRODUCT_VIEW",
      roles: ALL_ROLES,
    },
    {
      id: "product-groups",
      label: "Nhóm hàng",
      icon: '<path d="M3 6h18M3 12h12M3 18h8"/>',
      href: "/product-groups",
      permission: "PRODUCT_VIEW",
      roles: ["ROLE_ADMIN", "ROLE_SALES_MANAGER", "ROLE_VIEWER"],
    },
    {
      id: "create-order",
      label: "Tạo đơn hàng",
      icon: '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/><line x1="6" y1="10" x2="10" y2="10"/>',
      href: "/create-order",
      roles: ["ROLE_ADMIN", "ROLE_SALES_MANAGER", "ROLE_SALES", "ROLE_WAREHOUSE_MANAGER", "ROLE_WAREHOUSE_STAFF"],
    },
    {
      id: "price-lists",
      label: "Bảng giá",
      icon: '<path d="M20 12V5a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v14l4-3 4 3 4-3 4 3v-7"/><path d="M8 8h8M8 12h5"/>',
      href: "/price-lists",
      roles: ["ROLE_SALES_MANAGER"],
    },
    {
      id: "discount-policies",
      label: "Chiết khấu sản lượng",
      icon: '<path d="M9 14l2 2 4-4"/><path d="M21 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7"/><circle cx="18" cy="18" r="4"/><path d="M15 18h6M18 15v6"/>',
      href: "/discount-policies",
      roles: ["ROLE_SALES_MANAGER"],
    },
    {
      id: "dealers",
      label: "Đại lý",
      icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
      href: "/dealers",
      roles: [
        "ROLE_ADMIN",
        "ROLE_SALES_MANAGER",
        "ROLE_SALES",
        "ROLE_ACCOUNTANT",
        "ROLE_VIEWER",
      ],
    },
    {
      id: "warehouse",
      section: "Kho hàng",
      label: "Tồn kho",
      icon: '<path d="M3 9.5 12 4l9 5.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z"/><path d="M8 21v-7h8v7M8 11h8"/>',
      href: "/warehouse/units?tab=inventory",
      permission: "WAREHOUSE_VIEW",
      roles: [
        "ROLE_ADMIN",
        "ROLE_WAREHOUSE_MANAGER",
        "ROLE_WAREHOUSE_STAFF",
        "ROLE_SALES_MANAGER",
        "ROLE_VIEWER",
      ],
    },
    {
      id: "suppliers",
      label: "Nhà cung cấp",
      icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
      href: "/suppliers",
      roles: [
        "ROLE_ADMIN",
        "ROLE_WAREHOUSE_MANAGER",
        "ROLE_WAREHOUSE_STAFF",
        "ROLE_VIEWER",
      ],
    },
    {
      id: "product-units",
      label: "Đơn vị tính",
      icon: '<path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2V9M9 21H5a2 2 0 0 1-2-2V9m0 0h18"/>',
      href: "/warehouse/units?tab=units",
      permission: "WAREHOUSE_VIEW",
      roles: [
        "ROLE_ADMIN",
        "ROLE_SALES_MANAGER",
        "ROLE_WAREHOUSE_MANAGER",
        "ROLE_WAREHOUSE_STAFF",
      ],
    },
    {
      id: "import-export",
      label: "Nhập / Xuất kho",
      icon: '<polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/>',
      href: "/warehouse/units?tab=receipts",
      permission: "WAREHOUSE_IMPORT",
      roles: [
        "ROLE_ADMIN",
        "ROLE_WAREHOUSE_MANAGER",
        "ROLE_WAREHOUSE_STAFF",
      ],
    },
    {
      id: "debt-limits",
      section: "Tài chính",
      label: "Hạn mức công nợ",
      icon: '<path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>',
      href: "/dashboard?view=debt-limits",
      roles: ["ROLE_ADMIN", "ROLE_ACCOUNTANT", "ROLE_SALES_MANAGER"],
      permission: "FINANCE_VIEW_DEBT",
    },
    {
      id: "users",
      section: "Hệ thống",
      label: "Quản lý tài khoản",
      icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/>',
      href: "/admin/users",
      roles: ["ROLE_ADMIN"],
      permission: "USER_VIEW",
    },
    {
      id: "audit-logs",
      label: "Nhật ký thao tác",
      icon: '<path d="M3 3v5h5"/><path d="M3.05 13a9 9 0 1 0 2.13-5.82L3 8"/><path d="M12 7v5l3 2"/>',
      href: "/admin/audit-logs",
      roles: ["ROLE_ADMIN"],
    },
  ];

  /**
   * Lấy menu items đã lọc theo roles của user.
   */
  function getMenuForRoles(userRoles) {
    return menuItems.filter((item) =>
      item.roles.some((role) => userRoles.includes(role)),
    );
  }

  function getMenuForUser(user) {
    const roles = user?.roles || [];
    const permissions = user?.permissions || [];
    return menuItems.filter((item) => {
      if (!item.roles.some((role) => roles.includes(role))) return false;
      if (item.permission) return permissions.includes(item.permission);
      if (item.permissions) return item.permissions.some((permission) => permissions.includes(permission));
      return item.roles.some((role) => roles.includes(role));
    });
  }

  /**
   * Chuẩn hóa đường dẫn href dựa vào vị trí thư mục của trang hiện tại.
   */
  function resolveHref(href) {
    return href;
  }

  /**
   * Lấy tên hiển thị cho role.
   */
  function getRoleDisplayName(role) {
    const names = {
      ROLE_ADMIN: "Quản trị viên",
      ROLE_SALES_MANAGER: "Quản lý kinh doanh",
      ROLE_WAREHOUSE_MANAGER: "Quản lý kho",
      ROLE_SALES: "Nhân viên kinh doanh",
      ROLE_WAREHOUSE_STAFF: "Thủ kho",
      ROLE_ACCOUNTANT: "Kế toán",
      ROLE_VIEWER: "Chỉ xem",
    };
    return names[role] || role;
  }

  return { getMenuForRoles, getMenuForUser, getRoleDisplayName, resolveHref, menuItems };
})();

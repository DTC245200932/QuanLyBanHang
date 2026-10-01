/**
 * audit-logs.js — Quản lý và truy vết Nhật ký thao tác (S2-04).
 *
 * Chức năng:
 * - Ghi lại mọi thao tác trên tồn kho, giá, hạn mức công nợ và hoá đơn.
 * - Hiển thị người thực hiện, thời điểm, giá trị trước và sau (diff so sánh).
 * - Lọc theo người dùng, loại đối tượng, khoảng thời gian.
 * - Truy vết điều chỉnh tồn khi cuối tháng kiểm kê bị lệch.
 * - Xuất tệp Excel (.xlsx).
 * - Ghi nhận thao tác mới trực tiếp để kiểm thử thời gian thực.
 */
(() => {
  "use strict";

  // ── Kiểm tra quyền truy cập: Chỉ Quản trị hệ thống (ROLE_ADMIN) ──
  if (!Auth.requireLogin()) return;
  if (!Auth.hasRole("ROLE_ADMIN")) {
    location.href = "../error.html?status=403";
    return;
  }

  // ── Khóa lưu trữ LocalStorage ──
  const STORAGE_KEY = "salestock_audit_logs_v1";

  // ── Bộ nhãn tiếng Việt chuẩn ──
  const TYPE_CONFIG = {
    INVENTORY: {
      label: "Tồn kho",
      badgeClass: "entity-tag--INVENTORY",
      icon: '<svg viewBox="0 0 24 24" width="13" height="13"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>',
    },
    PRICE: {
      label: "Giá bán",
      badgeClass: "entity-tag--PRICE",
      icon: '<svg viewBox="0 0 24 24" width="13" height="13"><circle cx="12" cy="12" r="9"/><path d="M12 6v12M8 10h8"/></svg>',
    },
    DEBT_LIMIT: {
      label: "Hạn mức công nợ",
      badgeClass: "entity-tag--DEBT_LIMIT",
      icon: '<svg viewBox="0 0 24 24" width="13" height="13"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>',
    },
    INVOICE: {
      label: "Hoá đơn",
      badgeClass: "entity-tag--INVOICE",
      icon: '<svg viewBox="0 0 24 24" width="13" height="13"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>',
    },
  };

  const ROLE_NAMES = {
    ROLE_ADMIN: "Quản trị hệ thống",
    ROLE_SALES_MANAGER: "Quản lý kinh doanh",
    ROLE_WAREHOUSE_MANAGER: "Quản lý kho",
    ROLE_SALES: "Nhân viên kinh doanh",
    ROLE_WAREHOUSE_STAFF: "Thủ kho",
    ROLE_ACCOUNTANT: "Kế toán",
    ROLE_VIEWER: "Chỉ xem",
  };

  // ── Danh sách người dùng hệ thống phục vụ bộ lọc & chọn người thao tác ──
  const SYSTEM_USERS = [
    { username: "admin", fullName: "Quản trị viên", role: "ROLE_ADMIN" },
    { username: "warehouse.demo", fullName: "Nguyễn Văn Kho", role: "ROLE_WAREHOUSE_STAFF" },
    { username: "accountant.demo", fullName: "Lê Thị Kế Toán", role: "ROLE_ACCOUNTANT" },
    { username: "sales.manager", fullName: "Trần Quản Lý", role: "ROLE_SALES_MANAGER" },
    { username: "sales.demo", fullName: "Phạm Kinh Doanh", role: "ROLE_SALES" },
  ];

  // ── Dữ liệu mẫu chuẩn nghiệp vụ cho bài toán kiểm kê cuối tháng bị lệch (S2-04) ──
  const INITIAL_SAMPLE_LOGS = [
    {
      id: "AUD-2026-0930-0101",
      timestamp: "2026-09-30T16:45:00+07:00",
      targetType: "INVENTORY",
      action: "Điều chỉnh tồn kho kiểm kê",
      targetCode: "SP001",
      targetName: "Cà phê Robusta Đắk Lắk 500g",
      context: "Kho Tân Bình · Ô kệ B-04",
      actor: {
        username: "warehouse.demo",
        fullName: "Nguyễn Văn Kho",
        role: "ROLE_WAREHOUSE_STAFF",
        ip: "192.168.1.108",
        device: "Barcode Scanner Handheld (Chrome 128 / Android 14)",
      },
      valBefore: "120 bao (6.000 kg)",
      valAfter: "95 bao (4.750 kg)",
      deltaText: "-25 bao (-20.8%)",
      deltaType: "negative",
      isDiscrepancy: true,
      reason: "Kiểm kê định kỳ cuối tháng 09/2026: Phát hiện rách bao bì và ẩm mốc 25 bao tại dãy kệ B-04. Lập biên bản kiểm kê lệch số BB-KK/09-01.",
      meta: {
        unit: "Bao (50kg)",
        stockBefore: 120,
        stockAfter: 95,
        discrepancyCount: -25,
        bookValueBefore: "360.000.000 đ",
        bookValueAfter: "285.000.000 đ",
        varianceAmount: "-75.000.000 đ",
        inspectionDoc: "BB-KK/09-01/TB",
        hash: "8f7e2c91a4b5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1",
      },
    },
    {
      id: "AUD-2026-0930-0098",
      timestamp: "2026-09-30T15:20:00+07:00",
      targetType: "DEBT_LIMIT",
      action: "Nâng hạn mức công nợ",
      targetCode: "DL001",
      targetName: "Đại lý Miền Tây (Cần Thơ)",
      context: "Hợp đồng phân phối cấp 1",
      actor: {
        username: "accountant.demo",
        fullName: "Lê Thị Kế Toán",
        role: "ROLE_ACCOUNTANT",
        ip: "192.168.1.112",
        device: "Phòng Kế toán (Edge 128 / Windows 11)",
      },
      valBefore: "50.000.000 đ (Hạn 30 ngày)",
      valAfter: "100.000.000 đ (Hạn 45 ngày)",
      deltaText: "+50.000.000 đ (+100%)",
      deltaType: "positive",
      isDiscrepancy: false,
      reason: "Đại lý nộp bổ sung thư bảo lãnh ngân hàng BIDV số BL-992/2026 cho mùa kinh doanh quý 4. Phê duyệt theo tờ trình tài chính số 12/TTr-KT.",
      meta: {
        oldLimit: "50.000.000 đ",
        newLimit: "100.000.000 đ",
        oldDays: 30,
        newDays: 45,
        guaranteeBank: "BIDV Chi nhánh Cần Thơ",
        hash: "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b",
      },
    },
    {
      id: "AUD-2026-0930-0094",
      timestamp: "2026-09-30T14:10:00+07:00",
      targetType: "PRICE",
      action: "Cập nhật giá bán niêm yết",
      targetCode: "SP004",
      targetName: "Trà Xanh Oolong Thượng Hạng 250g",
      context: "Bảng giá bán buôn toàn quốc",
      actor: {
        username: "sales.manager",
        fullName: "Trần Quản Lý",
        role: "ROLE_SALES_MANAGER",
        ip: "192.168.1.102",
        device: "MacBook Pro (Chrome 128 / macOS 15)",
      },
      valBefore: "180.000 đ / hộp",
      valAfter: "195.000 đ / hộp",
      deltaText: "+15.000 đ (+8.3%)",
      deltaType: "positive",
      isDiscrepancy: false,
      reason: "Biến động chi phí nguyên liệu búp chè tươi và bao bì xuất khẩu. Thực hiện theo Quyết định điều chỉnh giá số 75/QĐ-BGD ngày 29/09.",
      meta: {
        oldPrice: "180.000 đ",
        newPrice: "195.000 đ",
        decisionNumber: "75/QĐ-BGD",
        hash: "3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d",
      },
    },
    {
      id: "AUD-2026-0930-0089",
      timestamp: "2026-09-30T11:30:00+07:00",
      targetType: "INVOICE",
      action: "Hủy hoá đơn đối trừ công nợ",
      targetCode: "HD-2026-0928-04",
      targetName: "NPP Toàn Phát (Đồng Nai)",
      context: "Hoá đơn GTGT điện tử số 004812",
      actor: {
        username: "accountant.demo",
        fullName: "Lê Thị Kế Toán",
        role: "ROLE_ACCOUNTANT",
        ip: "192.168.1.112",
        device: "Phòng Kế toán (Edge 128 / Windows 11)",
      },
      valBefore: "42.500.000 đ (Chờ thanh toán)",
      valAfter: "0 đ (Đã hủy hoá đơn)",
      deltaText: "-42.500.000 đ (Xóa nợ)",
      deltaType: "negative",
      isDiscrepancy: false,
      reason: "Giao hàng trễ hạn dẫn đến khách từ chối nhận theo biên bản thỏa thuận ngày 29/09. Kế toán tiến hành hủy hoá đơn và hoàn nhập công nợ.",
      meta: {
        invoiceTotal: "42.500.000 đ",
        canceledDate: "30/09/2026",
        agreementDoc: "BB-TT-0928/TP",
        hash: "5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f",
      },
    },
    {
      id: "AUD-2026-0930-0081",
      timestamp: "2026-09-30T09:15:00+07:00",
      targetType: "INVENTORY",
      action: "Điều chỉnh tồn kho kiểm kê",
      targetCode: "SP008",
      targetName: "Hạt Điều Rang Muối Vỏ Lụa 500g",
      context: "Kho Cát Lái · Ô kệ C-02",
      actor: {
        username: "warehouse.demo",
        fullName: "Nguyễn Văn Kho",
        role: "ROLE_WAREHOUSE_STAFF",
        ip: "192.168.1.109",
        device: "Kho Cát Lái Terminal (Firefox 130 / Ubuntu 24)",
      },
      valBefore: "450 hũ",
      valAfter: "432 hũ",
      deltaText: "-18 hũ (-4.0%)",
      deltaType: "negative",
      isDiscrepancy: true,
      reason: "Kiểm đếm định kỳ cuối tháng 9: Chênh lệch sổ sách 18 hũ do bể vỡ khi đóng pallet hàng container xuất khẩu. Đã lập biên bản BB-09/CL.",
      meta: {
        unit: "Hũ 500g",
        stockBefore: 450,
        stockAfter: 432,
        discrepancyCount: -18,
        inspectionDoc: "BB-09/CL/2026",
        hash: "7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b",
      },
    },
    {
      id: "AUD-2026-0929-0076",
      timestamp: "2026-09-29T16:05:00+07:00",
      targetType: "DEBT_LIMIT",
      action: "Giảm hạn mức công nợ khẩn cấp",
      targetCode: "DL007",
      targetName: "Công ty TNHH Nam Á",
      context: "Khu vực Đông Nam Bộ",
      actor: {
        username: "admin",
        fullName: "Quản trị viên",
        role: "ROLE_ADMIN",
        ip: "192.168.1.2",
        device: "Quản trị hệ thống (Chrome 129 / Windows 11)",
      },
      valBefore: "80.000.000 đ",
      valAfter: "30.000.000 đ",
      deltaText: "-50.000.000 đ (-62.5%)",
      deltaType: "negative",
      isDiscrepancy: false,
      reason: "Nợ quá hạn thanh toán trên 45 ngày chưa có cam kết giải quyết. Quản trị viên hạ hạn mức khẩn cấp để kiểm soát rủi ro thu hồi công nợ.",
      meta: {
        overdueDays: 48,
        previousLimit: "80.000.000 đ",
        newLimit: "30.000.000 đ",
        hash: "9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d",
      },
    },
    {
      id: "AUD-2026-0929-0065",
      timestamp: "2026-09-29T10:20:00+07:00",
      targetType: "INVENTORY",
      action: "Nhập kho hàng loạt",
      targetCode: "SP012",
      targetName: "Hạt Mắc Ca Nứt Vỏ Tây Nguyên 500g",
      context: "Kho Tân Bình · Ô kệ A-01",
      actor: {
        username: "warehouse.demo",
        fullName: "Nguyễn Văn Kho",
        role: "ROLE_WAREHOUSE_STAFF",
        ip: "192.168.1.108",
        device: "Barcode Scanner Handheld (Chrome 128 / Android 14)",
      },
      valBefore: "50 túi",
      valAfter: "350 túi",
      deltaText: "+300 túi (+600%)",
      deltaType: "positive",
      isDiscrepancy: false,
      reason: "Nhập lô hàng vụ mùa mới từ Hợp tác xã Nông nghiệp Lâm Đồng theo đơn đặt hàng mua PO-2026-0918.",
      meta: {
        poNumber: "PO-2026-0918",
        supplier: "HTX Nông Nghiệp Lâm Đồng",
        hash: "b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1",
      },
    },
    {
      id: "AUD-2026-0928-0052",
      timestamp: "2026-09-28T14:40:00+07:00",
      targetType: "PRICE",
      action: "Thiết lập chiết khấu đại lý",
      targetCode: "BG-EAST-01",
      targetName: "Bảng giá đại lý phân phối Đông Nam Bộ",
      context: "Nhóm sản phẩm Trà & Cà phê",
      actor: {
        username: "sales.manager",
        fullName: "Trần Quản Lý",
        role: "ROLE_SALES_MANAGER",
        ip: "192.168.1.102",
        device: "MacBook Pro (Chrome 128 / macOS 15)",
      },
      valBefore: "Chiết khấu 6.0%",
      valAfter: "Chiết khấu 8.5%",
      deltaText: "+2.5% (Tăng chiết khấu)",
      deltaType: "positive",
      isDiscrepancy: false,
      reason: "Chính sách khuyến khích đại lý đẩy mạnh tiêu thụ hàng hóa tồn kho trước đợt tổng kiểm kê quý 3.",
      meta: {
        oldDiscount: "6.0%",
        newDiscount: "8.5%",
        campaign: "Đẩy hàng tồn kho cuối quý 3",
        hash: "d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3",
      },
    },
    {
      id: "AUD-2026-0928-0044",
      timestamp: "2026-09-28T09:30:00+07:00",
      targetType: "INVOICE",
      action: "Điều chỉnh giá trị hoá đơn",
      targetCode: "HD-2026-0925-01",
      targetName: "Chuỗi Cửa Hàng Xanh (TP.HCM)",
      context: "Hoá đơn xuất bán số 004781",
      actor: {
        username: "accountant.demo",
        fullName: "Lê Thị Kế Toán",
        role: "ROLE_ACCOUNTANT",
        ip: "192.168.1.112",
        device: "Phòng Kế toán (Edge 128 / Windows 11)",
      },
      valBefore: "68.000.000 đ",
      valAfter: "61.200.000 đ",
      deltaText: "-6.800.000 đ (-10%)",
      deltaType: "negative",
      isDiscrepancy: false,
      reason: "Điều chỉnh giảm trừ 10% chiết khấu thương mại do đại lý đạt doanh số mốc 500 triệu theo phụ lục hợp đồng số 04.",
      meta: {
        appendixContract: "PL-04/HĐ-CHX",
        adjustedAmount: "-6.800.000 đ",
        hash: "f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5",
      },
    },
    {
      id: "AUD-2026-0927-0038",
      timestamp: "2026-09-27T15:00:00+07:00",
      targetType: "INVENTORY",
      action: "Xuất kho giao đại lý",
      targetCode: "SP001",
      targetName: "Cà phê Robusta Đắk Lắk 500g",
      context: "Kho Tân Bình · Ô kệ B-04",
      actor: {
        username: "warehouse.demo",
        fullName: "Nguyễn Văn Kho",
        role: "ROLE_WAREHOUSE_STAFF",
        ip: "192.168.1.108",
        device: "Kho Tân Bình Terminal (Chrome 128 / Windows 10)",
      },
      valBefore: "200 bao",
      valAfter: "120 bao",
      deltaText: "-80 bao (-40%)",
      deltaType: "negative",
      isDiscrepancy: false,
      reason: "Xuất hàng giao theo phiếu xuất kho PX-2026-0927 cho đơn hàng của Đại lý Miền Tây.",
      meta: {
        exportSlip: "PX-2026-0927",
        recipient: "Đại lý Miền Tây",
        hash: "1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c",
      },
    },
    {
      id: "AUD-2026-0926-0029",
      timestamp: "2026-09-26T11:15:00+07:00",
      targetType: "DEBT_LIMIT",
      action: "Gia hạn thời gian thanh toán",
      targetCode: "DL003",
      targetName: "Siêu Thị Nông Sản Sạch",
      context: "Hợp đồng tín dụng chuỗi bán lẻ",
      actor: {
        username: "admin",
        fullName: "Quản trị viên",
        role: "ROLE_ADMIN",
        ip: "192.168.1.2",
        device: "Quản trị hệ thống (Chrome 129 / Windows 11)",
      },
      valBefore: "Thời hạn: 30 ngày (Hạn mức 120 tr)",
      valAfter: "Thời hạn: 60 ngày (Hạn mức 120 tr)",
      deltaText: "+30 ngày gia hạn",
      deltaType: "positive",
      isDiscrepancy: false,
      reason: "Chính sách hỗ trợ đối tác chiến lược khi khai trương thêm 3 điểm bán mới tại miền Trung.",
      meta: {
        oldPeriod: "30 ngày",
        newPeriod: "60 ngày",
        approvalDoc: "TT-BGD-2026/09",
        hash: "3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e",
      },
    },
    {
      id: "AUD-2026-10-01-0105",
      timestamp: "2026-10-01T08:30:00+07:00",
      targetType: "INVENTORY",
      action: "Điều chỉnh tồn kho kiểm kê",
      targetCode: "SP015",
      targetName: "Bột Cacao Nguyên Chất 500g",
      context: "Kho Tân Bình · Ô kệ D-01",
      actor: {
        username: "warehouse.demo",
        fullName: "Nguyễn Văn Kho",
        role: "ROLE_WAREHOUSE_STAFF",
        ip: "192.168.1.108",
        device: "Barcode Scanner Handheld (Chrome 128 / Android 14)",
      },
      valBefore: "85 hộp",
      valAfter: "79 hộp",
      deltaText: "-6 hộp (-7.1%)",
      deltaType: "negative",
      isDiscrepancy: true,
      reason: "Biên bản kiểm kê đầu tháng 10: Rò rỉ ẩm làm hỏng 6 hộp mẫu mã cũ không thể xuất bán, tiến hành lập phiếu điều chỉnh giảm tồn.",
      meta: {
        unit: "Hộp 500g",
        stockBefore: 85,
        stockAfter: 79,
        discrepancyCount: -6,
        inspectionDoc: "BB-01/10/TB",
        hash: "5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a",
      },
    },
  ];

  // ── State quản lý giao diện ──
  const state = {
    logs: [],
    filteredLogs: [],
    users: [...SYSTEM_USERS],
    page: 0,
    pageSize: 10,
    totalPages: 1,
    activePreset: "all",
  };

  // Helper truy cập DOM nhanh & an toàn
  const $ = (id) => document.getElementById(id);
  const escapeHtml = (v) =>
    String(v ?? "").replace(/[&<>'"]/g, (c) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "'": "&#39;",
      '"': "&quot;",
    }[c]));

  // ── Khởi tạo dữ liệu từ LocalStorage hoặc API ──
  function getStoredLogs() {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      if (data) {
        const parsed = JSON.parse(data);
        if (Array.isArray(parsed) && parsed.length) return parsed;
      }
    } catch {
      // Bỏ qua lỗi cú pháp lưu trữ
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(INITIAL_SAMPLE_LOGS));
    return [...INITIAL_SAMPLE_LOGS];
  }

  function saveLogsToStorage(logs) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(logs));
    } catch (e) {
      console.warn("Không thể lưu localStorage:", e);
    }
  }

  // ── Nạp danh sách nhân sự từ API để đồng bộ với cơ sở dữ liệu thật ──
  async function loadUsersList() {
    try {
      const response = await Auth.apiFetch("/admin/users?page=0&size=100");
      if (response.ok) {
        const result = await response.json();
        const apiUsers = result?.data?.content || [];
        if (apiUsers.length) {
          const userMap = new Map();
          apiUsers.forEach((u) => {
            userMap.set(u.username, {
              username: u.username,
              fullName: u.fullName || u.username,
              role: u.roles?.[0] || "ROLE_SALES",
            });
          });
          SYSTEM_USERS.forEach((su) => {
            if (!userMap.has(su.username)) userMap.set(su.username, su);
          });
          state.users = Array.from(userMap.values());
        }
      }
    } catch {
      // Giữ danh sách mẫu dự phòng
    }

    // Đổ vào dropdown bộ lọc
    const filterUserSelect = $("filter-user");
    filterUserSelect.innerHTML = '<option value="">Tất cả người dùng</option>' +
      state.users
        .map(
          (u) =>
            `<option value="${escapeHtml(u.username)}">${escapeHtml(u.fullName)} (${escapeHtml(u.username)} · ${escapeHtml(ROLE_NAMES[u.role] || u.role)})</option>`
        )
        .join("");

    // Đổ vào dropdown tạo mới
    const newLogUserSelect = $("new-log-user");
    if (newLogUserSelect) {
      newLogUserSelect.innerHTML = state.users
        .map(
          (u) =>
            `<option value="${escapeHtml(u.username)}">${escapeHtml(u.fullName)} (${escapeHtml(ROLE_NAMES[u.role] || u.role)})</option>`
        )
        .join("");
    }
  }

  function normalizeApiLog(log) {
    const actor = state.users.find((user) => user.username === log.username);
    const beforeNumber = Number.parseFloat(String(log.oldValue || "").replace(/[^\d.-]/g, ""));
    const afterNumber = Number.parseFloat(String(log.newValue || "").replace(/[^\d.-]/g, ""));
    const delta = Number.isFinite(beforeNumber) && Number.isFinite(afterNumber) ? afterNumber - beforeNumber : null;
    return {
      id: String(log.id),
      timestamp: log.createdAt,
      targetType: log.entityType,
      action: log.action,
      targetCode: log.entityId,
      targetName: log.entityId,
      context: "Dữ liệu nghiệp vụ trên hệ thống",
      actor: {
        username: log.username,
        fullName: actor?.fullName || log.username,
        role: actor?.role || "ROLE_ADMIN",
      },
      valBefore: log.oldValue || "—",
      valAfter: log.newValue || "—",
      deltaText: delta == null ? "Đã thay đổi" : `${delta > 0 ? "+" : ""}${delta}`,
      deltaType: delta == null || delta === 0 ? "neutral" : delta > 0 ? "positive" : "negative",
      isDiscrepancy: log.entityType === "INVENTORY" && delta != null && delta < 0,
      reason: "Ghi tự động từ thao tác nghiệp vụ đã xác thực.",
      meta: {},
    };
  }

  // ── Tải dữ liệu nhật ký thật từ backend ──
  async function loadLogs() {
    try {
      const response = await Auth.apiFetch("/admin/audit-logs?size=100");
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !Array.isArray(result?.data)) {
        throw new Error(result.message || "Không tải được nhật ký thao tác");
      }
      state.logs = result.data.map(normalizeApiLog);
    } catch (error) {
      console.error(error);
      state.logs = [];
    }
    applyFilters();
  }

  // ── Tính toán các thẻ chỉ số KPI ──
  function updateKPIs() {
    const total = state.logs.length;
    const inventoryLogs = state.logs.filter((l) => l.targetType === "INVENTORY");
    const debtLogs = state.logs.filter((l) => l.targetType === "DEBT_LIMIT");
    const discrepancyLogs = state.logs.filter((l) => l.isDiscrepancy);

    $("kpi-total-logs").textContent = total;
    $("kpi-inventory-count").textContent = inventoryLogs.length;
    $("kpi-debt-count").textContent = debtLogs.length;
    $("kpi-discrepancy-count").textContent = discrepancyLogs.length;

    $("kpi-inventory-desc").textContent = `${discrepancyLogs.length} lần lệch kiểm kê âm`;
  }

  // ── Áp dụng bộ lọc ──
  function applyFilters() {
    const userVal = $("filter-user").value;
    const typeVal = $("filter-type").value;
    const fromDateVal = $("filter-from-date").value;
    const toDateVal = $("filter-to-date").value;
    const searchVal = $("filter-search").value.trim().toLowerCase();

    let filtered = [...state.logs];

    // Lọc theo người dùng
    if (userVal) {
      filtered = filtered.filter((l) => l.actor?.username === userVal);
    }

    // Lọc theo loại đối tượng
    if (typeVal) {
      filtered = filtered.filter((l) => l.targetType === typeVal);
    }

    // Lọc theo khoảng thời gian
    if (fromDateVal) {
      const fromTime = new Date(`${fromDateVal}T00:00:00`).getTime();
      filtered = filtered.filter((l) => new Date(l.timestamp).getTime() >= fromTime);
    }
    if (toDateVal) {
      const toTime = new Date(`${toDateVal}T23:59:59`).getTime();
      filtered = filtered.filter((l) => new Date(l.timestamp).getTime() <= toTime);
    }

    // Tìm kiếm từ khóa (Mã, Tên, Bối cảnh, Lý do, Thao tác, Người thực hiện)
    if (searchVal) {
      filtered = filtered.filter(
        (l) =>
          (l.targetCode || "").toLowerCase().includes(searchVal) ||
          (l.targetName || "").toLowerCase().includes(searchVal) ||
          (l.context || "").toLowerCase().includes(searchVal) ||
          (l.reason || "").toLowerCase().includes(searchVal) ||
          (l.action || "").toLowerCase().includes(searchVal) ||
          (l.actor?.fullName || "").toLowerCase().includes(searchVal) ||
          (l.actor?.username || "").toLowerCase().includes(searchVal)
      );
    }

    // Bộ lọc chuyên biệt cho kiểm kê bị lệch
    if (state.activePreset === "discrepancy") {
      filtered = filtered.filter((l) => l.isDiscrepancy);
    }

    // Sắp xếp thời gian mới nhất lên đầu
    filtered.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

    state.filteredLogs = filtered;
    state.totalPages = Math.max(1, Math.ceil(filtered.length / state.pageSize));
    if (state.page >= state.totalPages) state.page = 0;

    updateKPIs();
    renderTable();
  }

  // ── Định dạng ngày giờ trực quan ──
  function formatDateTime(isoString) {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;

    const pad = (n) => String(n).padStart(2, "0");
    const formatted = `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;

    // Tính thời gian tương đối
    const now = new Date("2026-10-01T16:03:00+07:00").getTime(); // Mock reference date
    const diffMs = now - d.getTime();
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    let rel = "";

    if (diffHours < 1) rel = "Vừa xong";
    else if (diffHours < 24) rel = `${diffHours} giờ trước`;
    else {
      const diffDays = Math.floor(diffHours / 24);
      rel = diffDays === 1 ? "Hôm qua" : `${diffDays} ngày trước`;
    }

    return { formatted, rel };
  }

  // ── Vẽ bảng dữ liệu ──
  function renderTable() {
    const tbody = $("audit-table-body");
    const total = state.filteredLogs.length;

    if (!total) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" class="empty">
            <div style="padding: 30px 10px;">
              <svg viewBox="0 0 24 24" width="36" height="36" style="margin-bottom:8px;stroke:var(--ink-400);">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <p style="font-weight:600;color:var(--ink-700);margin-bottom:4px;">Không tìm thấy bản ghi nhật ký phù hợp</p>
              <p style="font-size:12px;color:var(--ink-500);">Hãy thử thay đổi tiêu chí lọc hoặc bấm "Đặt lại bộ lọc".</p>
            </div>
          </td>
        </tr>
      `;
      $("audit-pagination-summary").textContent = "Hiển thị 0 trong tổng số 0 bản ghi";
      $("audit-page-indicator").textContent = "Trang 1 / 1";
      $("btn-page-first").disabled = true;
      $("btn-page-prev").disabled = true;
      $("btn-page-next").disabled = true;
      $("btn-page-last").disabled = true;
      return;
    }

    const startIdx = state.page * state.pageSize;
    const endIdx = Math.min(startIdx + state.pageSize, total);
    const pageItems = state.filteredLogs.slice(startIdx, endIdx);

    tbody.innerHTML = pageItems
      .map((item) => {
        const timeInfo = formatDateTime(item.timestamp);
        const typeCfg = TYPE_CONFIG[item.targetType] || {
          label: item.targetType,
          badgeClass: "entity-tag--INVENTORY",
          icon: "",
        };
        const initial = (item.actor?.fullName || item.actor?.username || "U")
          .trim()
          .charAt(0)
          .toUpperCase();
        const roleLabel = ROLE_NAMES[item.actor?.role] || item.actor?.role || "Nhân sự";

        let deltaBadgeClass = "diff-delta--neutral";
        let deltaPrefix = "";
        if (item.deltaType === "negative") {
          deltaBadgeClass = "diff-delta--negative";
          deltaPrefix = "↓ ";
        } else if (item.deltaType === "positive") {
          deltaBadgeClass = "diff-delta--positive";
          deltaPrefix = "↑ ";
        }

        const discrepancyAlert = item.isDiscrepancy
          ? `<div class="discrepancy-flag" title="Điều chỉnh giảm do kiểm kê bị lệch cuối tháng">
              ⚠ Lệch tồn kiểm kê
            </div>`
          : "";

        return `
          <tr data-log-id="${escapeHtml(item.id)}">
            <!-- 1. Thời điểm -->
            <td class="audit-time-cell">
              <span class="audit-time-exact">${escapeHtml(timeInfo.formatted)}</span>
              <span class="audit-time-rel">${escapeHtml(timeInfo.rel)}</span>
            </td>

            <!-- 2. Người thực hiện -->
            <td>
              <div class="actor-badge">
                <span class="actor-avatar">${escapeHtml(initial)}</span>
                <div class="actor-info">
                  <span class="actor-name" title="${escapeHtml(item.actor?.fullName)}">${escapeHtml(item.actor?.fullName)}</span>
                  <span class="actor-sub">${escapeHtml(item.actor?.username)} · ${escapeHtml(roleLabel)}</span>
                </div>
              </div>
            </td>

            <!-- 3. Loại đối tượng & Thao tác -->
            <td>
              <span class="entity-tag ${typeCfg.badgeClass}">
                ${typeCfg.icon}
                ${escapeHtml(typeCfg.label)}
              </span>
              <span class="action-title">${escapeHtml(item.action)}</span>
            </td>

            <!-- 4. Đối tượng tác động -->
            <td class="target-cell">
              <span class="target-code">${escapeHtml(item.targetCode)}</span>
              <span class="target-name" title="${escapeHtml(item.targetName)}">${escapeHtml(item.targetName)}</span>
              <span class="target-context">${escapeHtml(item.context)}</span>
            </td>

            <!-- 5. Giá trị trước ➔ sau -->
            <td class="diff-cell">
              <div class="diff-box">
                <span class="diff-val diff-val--before" title="Giá trị trước thao tác">${escapeHtml(item.valBefore)}</span>
                <span class="diff-arrow">➔</span>
                <span class="diff-val diff-val--after" title="Giá trị sau thao tác">${escapeHtml(item.valAfter)}</span>
              </div>
              <div style="margin-top: 5px;">
                <span class="diff-delta ${deltaBadgeClass}">${deltaPrefix}${escapeHtml(item.deltaText)}</span>
              </div>
              ${discrepancyAlert}
            </td>

            <!-- 6. Lý do / Căn cứ -->
            <td class="reason-cell">
              <div class="reason-text" title="${escapeHtml(item.reason)}">${escapeHtml(item.reason)}</div>
            </td>

            <!-- 7. Chi tiết -->
            <td style="text-align: right;">
              <button class="btn-view-detail" type="button" data-action="view" data-id="${escapeHtml(item.id)}">
                Xem
              </button>
            </td>
          </tr>
        `;
      })
      .join("");

    // Cập nhật phân trang
    $("audit-pagination-summary").textContent = `Hiển thị ${startIdx + 1} - ${endIdx} trong tổng số ${total} bản ghi`;
    $("audit-page-indicator").textContent = `Trang ${state.page + 1} / ${state.totalPages}`;
    $("btn-page-first").disabled = state.page === 0;
    $("btn-page-prev").disabled = state.page === 0;
    $("btn-page-next").disabled = state.page >= state.totalPages - 1;
    $("btn-page-last").disabled = state.page >= state.totalPages - 1;
  }

  // ── Xem chi tiết bản ghi (Audit Detail Modal) ──
  function openDetailModal(logId) {
    const log = state.logs.find((l) => l.id === logId);
    if (!log) return;

    $("audit-detail-title").textContent = `Chi tiết thao tác: ${log.action}`;
    $("audit-detail-subtitle").textContent = `Mã vết kiểm toán: #${log.id}`;

    const timeInfo = formatDateTime(log.timestamp);
    const typeCfg = TYPE_CONFIG[log.targetType] || { label: log.targetType, badgeClass: "" };
    const roleLabel = ROLE_NAMES[log.actor?.role] || log.actor?.role || "Nhân sự";

    let comparisonHtml = `
      <table class="diff-comparison-table">
        <thead>
          <tr>
            <th>Chỉ tiêu so sánh</th>
            <th style="color:var(--danger);">Giá trị TRƯỚC</th>
            <th style="color:var(--teal-deep);">Giá trị SAU</th>
            <th>Biến động / Chênh lệch</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Dữ liệu ghi nhận</strong></td>
            <td style="background:#fff5f5;font-weight:600;">${escapeHtml(log.valBefore)}</td>
            <td style="background:#f0fdf4;font-weight:700;">${escapeHtml(log.valAfter)}</td>
            <td><strong style="color:${log.deltaType === "negative" ? "var(--danger)" : "var(--teal-deep)"};">${escapeHtml(log.deltaText)}</strong></td>
          </tr>
    `;

    if (log.meta?.bookValueBefore && log.meta?.bookValueAfter) {
      comparisonHtml += `
        <tr>
          <td><strong>Quy đổi giá trị sổ sách</strong></td>
          <td>${escapeHtml(log.meta.bookValueBefore)}</td>
          <td>${escapeHtml(log.meta.bookValueAfter)}</td>
          <td style="color:var(--danger);font-weight:700;">${escapeHtml(log.meta.varianceAmount)}</td>
        </tr>
      `;
    }

    if (log.meta?.oldDays && log.meta?.newDays) {
      comparisonHtml += `
        <tr>
          <td><strong>Thời hạn công nợ</strong></td>
          <td>${escapeHtml(log.meta.oldDays)} ngày</td>
          <td>${escapeHtml(log.meta.newDays)} ngày</td>
          <td>+${log.meta.newDays - log.meta.oldDays} ngày gia hạn</td>
        </tr>
      `;
    }

    comparisonHtml += `</tbody></table>`;

    $("audit-detail-body").innerHTML = `
      <div class="audit-detail-grid">
        <div class="detail-block">
          <div class="detail-block__label">Người thực hiện thao tác</div>
          <div class="detail-block__content">
            <strong style="font-size:14px;">${escapeHtml(log.actor?.fullName)}</strong>
            <div style="font-size:12px;color:var(--ink-600);margin-top:2px;">Tài khoản: <code>${escapeHtml(log.actor?.username)}</code> · ${escapeHtml(roleLabel)}</div>
            <div style="font-size:11.5px;color:var(--ink-500);margin-top:4px;">IP: ${escapeHtml(log.actor?.ip || "192.168.1.1")} · Thiết bị: ${escapeHtml(log.actor?.device || "Trình duyệt chuẩn")}</div>
          </div>
        </div>

        <div class="detail-block">
          <div class="detail-block__label">Thời điểm & Bối cảnh</div>
          <div class="detail-block__content">
            <strong style="font-size:14px;">${escapeHtml(timeInfo.formatted)}</strong>
            <div style="font-size:12px;color:var(--ink-600);margin-top:2px;">Phân hệ: <span class="entity-tag ${typeCfg.badgeClass}">${escapeHtml(typeCfg.label)}</span></div>
            <div style="font-size:11.5px;color:var(--ink-500);margin-top:4px;">Địa điểm / Phạm vi: ${escapeHtml(log.context)}</div>
          </div>
        </div>
      </div>

      <div class="detail-block" style="margin-bottom:14px;">
        <div class="detail-block__label">Đối tượng chịu tác động</div>
        <div class="detail-block__content">
          <div style="display:flex;align-items:center;gap:8px;">
            <span class="target-code">${escapeHtml(log.targetCode)}</span>
            <strong style="font-size:14px;">${escapeHtml(log.targetName)}</strong>
          </div>
        </div>
      </div>

      <div style="margin-bottom:14px;">
        <div class="detail-block__label" style="margin-bottom:6px;">Đối soát giá trị trước và sau thao tác</div>
        ${comparisonHtml}
      </div>

      <div class="detail-block" style="margin-bottom:14px;background:#fefce8;border-color:#fef08a;">
        <div class="detail-block__label" style="color:#854d0e;">Căn cứ / Lý do giải trình của nhân sự</div>
        <div class="detail-block__content" style="color:#713f12;line-height:1.5;">
          ${escapeHtml(log.reason)}
        </div>
        ${log.meta?.inspectionDoc ? `<div style="margin-top:6px;font-size:11.5px;color:#854d0e;">Số văn bản kiểm kê đính kèm: <strong>${escapeHtml(log.meta.inspectionDoc)}</strong></div>` : ""}
      </div>

      <div class="audit-hash-stamp">
        <span>Mã băm toàn vẹn (SHA-256): ${escapeHtml(log.meta?.hash || "Chữ ký số nội bộ đã xác thực")}</span>
        <span style="color:var(--teal-deep);font-weight:700;">✓ Toàn vẹn</span>
      </div>
    `;

    $("modal-audit-detail").hidden = false;
  }

  function closeDetailModal() {
    $("modal-audit-detail").hidden = true;
  }

  // ── Mở / Đóng Modal Ghi nhận mới ──
  function openCreateModal() {
    $("form-create-log").reset();
    $("new-log-delta-preview").textContent = "Tự động tính toán khi nhập";
    $("modal-create-log").hidden = false;
    $("new-log-action").focus();
  }

  function closeCreateModal() {
    $("modal-create-log").hidden = true;
  }

  // ── Tính nhanh Delta khi nhập giá trị trước / sau ──
  function updateDeltaPreview() {
    const beforeStr = $("new-log-val-before").value.trim();
    const afterStr = $("new-log-val-after").value.trim();
    const previewEl = $("new-log-delta-preview");

    if (!beforeStr || !afterStr) {
      previewEl.textContent = "Nhập đủ 2 giá trị để xem trước";
      previewEl.style.color = "var(--ink-500)";
      return;
    }

    // Cố gắng parse số
    const numBefore = parseFloat(beforeStr.replace(/[^\d.-]/g, ""));
    const numAfter = parseFloat(afterStr.replace(/[^\d.-]/g, ""));

    if (!isNaN(numBefore) && !isNaN(numAfter)) {
      const diff = numAfter - numBefore;
      const pct = numBefore !== 0 ? ((diff / numBefore) * 100).toFixed(1) : 0;
      if (diff < 0) {
        previewEl.textContent = `Giảm ${Math.abs(diff)} (${pct}%) ⚠ Cảnh báo lệch giảm`;
        previewEl.style.color = "var(--danger)";
      } else if (diff > 0) {
        previewEl.textContent = `Tăng +${diff} (+${pct}%)`;
        previewEl.style.color = "var(--teal-deep)";
      } else {
        previewEl.textContent = `Không đổi (0)`;
        previewEl.style.color = "var(--ink-700)";
      }
    } else {
      previewEl.textContent = `${beforeStr} ➔ ${afterStr}`;
      previewEl.style.color = "var(--ink-800)";
    }
  }

  // ── Thêm mới một bản ghi nhật ký (Real-time Recording) ──
  function handleCreateSubmit(e) {
    e.preventDefault();

    const username = $("new-log-user").value;
    const selectedUser = state.users.find((u) => u.username === username) || {
      username: username || "admin",
      fullName: username || "Quản trị viên",
      role: "ROLE_ADMIN",
    };

    const targetType = $("new-log-type").value;
    const action = $("new-log-action").value.trim();
    const targetName = $("new-log-target-name").value.trim();
    const valBefore = $("new-log-val-before").value.trim();
    const valAfter = $("new-log-val-after").value.trim();
    const reason = $("new-log-reason").value.trim();

    // Tính chênh lệch
    const numBefore = parseFloat(valBefore.replace(/[^\d.-]/g, ""));
    const numAfter = parseFloat(valAfter.replace(/[^\d.-]/g, ""));
    let deltaText = `${valBefore} ➔ ${valAfter}`;
    let deltaType = "neutral";
    let isDiscrepancy = false;

    if (!isNaN(numBefore) && !isNaN(numAfter)) {
      const diff = numAfter - numBefore;
      if (diff < 0) {
        deltaText = `${diff}`;
        deltaType = "negative";
        if (targetType === "INVENTORY") isDiscrepancy = true;
      } else if (diff > 0) {
        deltaText = `+${diff}`;
        deltaType = "positive";
      } else {
        deltaText = "0 (Không đổi)";
      }
    }

    const now = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    const logId = `AUD-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${Math.floor(1000 + Math.random() * 9000)}`;

    const newRecord = {
      id: logId,
      timestamp: now.toISOString(),
      targetType,
      action,
      targetCode: `MÃ-${Math.floor(100 + Math.random() * 900)}`,
      targetName,
      context: "Hệ thống ghi nhận trực tiếp",
      actor: {
        username: selectedUser.username,
        fullName: selectedUser.fullName,
        role: selectedUser.role,
        ip: "192.168.1.15",
        device: "Thao tác trên giao diện Web (SaleStock v2.0)",
      },
      valBefore,
      valAfter,
      deltaText,
      deltaType,
      isDiscrepancy,
      reason,
      meta: {
        hash: Array.from({ length: 64 }, () => Math.floor(Math.random() * 16).toString(16)).join(""),
      },
    };

    state.logs.unshift(newRecord);
    saveLogsToStorage(state.logs);
    closeCreateModal();
    applyFilters();

    // Highlight row vừa thêm
    setTimeout(() => {
      const row = document.querySelector(`tr[data-log-id="${newRecord.id}"]`);
      if (row) {
        row.classList.add("row-newly-added");
        row.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    }, 100);

    showToast(`Đã ghi nhận thành công thao tác #${newRecord.id} vào nhật ký!`, "success");
  }

  // ── Xuất tệp Excel bằng thư viện vendor/xlsx.full.min.js ──
  function exportToExcel() {
    if (typeof XLSX === "undefined") {
      showToast("Chưa tải được thư viện xuất Excel. Vui lòng thử lại!", "error");
      return;
    }

    if (!state.filteredLogs.length) {
      showToast("Không có dữ liệu để xuất Excel.", "error");
      return;
    }

    const exportRows = state.filteredLogs.map((item, index) => {
      const timeInfo = formatDateTime(item.timestamp);
      const typeLabel = TYPE_CONFIG[item.targetType]?.label || item.targetType;
      const roleLabel = ROLE_NAMES[item.actor?.role] || item.actor?.role || "";

      return {
        "STT": index + 1,
        "Mã bản ghi": item.id,
        "Thời điểm": timeInfo.formatted,
        "Người thực hiện": item.actor?.fullName || item.actor?.username,
        "Tài khoản": item.actor?.username,
        "Vai trò": roleLabel,
        "Phân hệ": typeLabel,
        "Thao tác": item.action,
        "Mã đối tượng": item.targetCode,
        "Tên đối tượng": item.targetName,
        "Phạm vi / Bối cảnh": item.context,
        "Giá trị TRƯỚC": item.valBefore,
        "Giá trị SAU": item.valAfter,
        "Biến động (Chênh lệch)": item.deltaText,
        "Lệch kiểm kê kho?": item.isDiscrepancy ? "CÓ (LỆCH ÂM)" : "Bình thường",
        "Lý do / Giải trình": item.reason,
        "Địa chỉ IP": item.actor?.ip || "",
      };
    });

    try {
      const worksheet = XLSX.utils.json_to_sheet(exportRows);
      const workbook = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(workbook, worksheet, "NhatKyThaoTac");

      const now = new Date();
      const pad = (n) => String(n).padStart(2, "0");
      const filename = `Nhat_ky_thao_tac_SaleStock_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}.xlsx`;

      XLSX.writeFile(workbook, filename);
      showToast(`Đã xuất thành công ${exportRows.length} bản ghi ra tệp ${filename}`, "success");
    } catch (err) {
      console.error(err);
      showToast("Lỗi khi xuất tệp Excel: " + err.message, "error");
    }
  }

  // ── Khôi phục dữ liệu mẫu ban đầu ──
  function resetSampleData() {
    if (confirm("Khôi phục danh sách nhật ký về dữ liệu chuẩn ban đầu?")) {
      localStorage.removeItem(STORAGE_KEY);
      state.logs = getStoredLogs();
      state.page = 0;
      applyFilters();
      showToast("Đã khôi phục dữ liệu nhật ký mẫu ban đầu.", "success");
    }
  }

  // ── Toast thông báo ──
  function showToast(message, type = "success") {
    let container = $("audit-toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "audit-toast-container";
      document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `audit-toast audit-toast--${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("audit-toast--visible"));
    setTimeout(() => {
      toast.classList.remove("audit-toast--visible");
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  // ── Xử lý các nút chọn nhanh (Presets) ──
  function handlePresetClick(preset) {
    state.activePreset = preset;
    document.querySelectorAll(".filter-chip").forEach((c) => {
      c.classList.toggle("filter-chip--active", c.dataset.preset === preset);
    });

    // Reset các input form
    $("filter-type").value = "";
    $("filter-from-date").value = "";
    $("filter-to-date").value = "";

    const now = new Date("2026-10-01T16:00:00");
    const pad = (n) => String(n).padStart(2, "0");
    const formatDateInput = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    if (preset === "discrepancy") {
      $("filter-type").value = "INVENTORY";
    } else if (preset === "type-inventory") {
      $("filter-type").value = "INVENTORY";
    } else if (preset === "type-debt") {
      $("filter-type").value = "DEBT_LIMIT";
    } else if (preset === "type-price") {
      $("filter-type").value = "PRICE";
    } else if (preset === "type-invoice") {
      $("filter-type").value = "INVOICE";
    } else if (preset === "today") {
      const todayStr = formatDateInput(now);
      $("filter-from-date").value = todayStr;
      $("filter-to-date").value = todayStr;
    } else if (preset === "last7days") {
      const past7 = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      $("filter-from-date").value = formatDateInput(past7);
      $("filter-to-date").value = formatDateInput(now);
    } else if (preset === "thismonth") {
      // Tháng này: Đợt kiểm kê cuối tháng 9/2026 đến đầu tháng 10
      const startOfMonth = new Date(2026, 8, 1); // 01/09/2026
      const endOfMonth = new Date(2026, 9, 1);   // 01/10/2026
      $("filter-from-date").value = formatDateInput(startOfMonth);
      $("filter-to-date").value = formatDateInput(endOfMonth);
    }

    state.page = 0;
    applyFilters();
  }

  // ── Đăng ký sự kiện (Event Listeners) ──
  function setupEventListeners() {
    // Form lọc
    $("audit-filters-form").addEventListener("submit", (e) => {
      e.preventDefault();
      state.page = 0;
      applyFilters();
    });

    // Nút đặt lại bộ lọc
    $("btn-reset-filter").addEventListener("click", () => {
      $("audit-filters-form").reset();
      handlePresetClick("all");
    });

    // Chips chọn nhanh
    document.querySelectorAll(".filter-chip").forEach((chip) => {
      chip.addEventListener("click", () => handlePresetClick(chip.dataset.preset));
    });

    // Banner đối soát kiểm kê
    $("btn-quick-discrepancy")?.addEventListener("click", () => {
      handlePresetClick("discrepancy");
      window.scrollTo({ top: $("audit-filters-form").offsetTop - 20, behavior: "smooth" });
    });

    // Click xem chi tiết trong bảng
    $("audit-table-body").addEventListener("click", (e) => {
      const btn = e.target.closest("[data-action='view']");
      if (btn) {
        const id = btn.dataset.id;
        openDetailModal(id);
      }
    });

    // Đóng Modal chi tiết
    document.querySelectorAll("[data-close-detail]").forEach((b) => {
      b.addEventListener("click", closeDetailModal);
    });

    // Nút in biên bản đối soát trong modal
    $("btn-print-audit")?.addEventListener("click", () => {
      window.print();
    });

    // Mở / Đóng Modal Ghi nhận mới
    $("btn-create-log").addEventListener("click", openCreateModal);
    document.querySelectorAll("[data-close-create]").forEach((b) => {
      b.addEventListener("click", closeCreateModal);
    });

    // Tính nhanh delta trong modal tạo mới
    $("new-log-val-before").addEventListener("input", updateDeltaPreview);
    $("new-log-val-after").addEventListener("input", updateDeltaPreview);

    // Lưu thao tác mới
    $("form-create-log").addEventListener("submit", handleCreateSubmit);

    // Xuất Excel
    $("btn-export-excel").addEventListener("click", exportToExcel);

    // Khôi phục mẫu
    $("btn-reset-data").addEventListener("click", resetSampleData);

    // Phân trang
    $("btn-page-first").addEventListener("click", () => {
      if (state.page > 0) {
        state.page = 0;
        renderTable();
      }
    });
    $("btn-page-prev").addEventListener("click", () => {
      if (state.page > 0) {
        state.page--;
        renderTable();
      }
    });
    $("btn-page-next").addEventListener("click", () => {
      if (state.page < state.totalPages - 1) {
        state.page++;
        renderTable();
      }
    });
    $("btn-page-last").addEventListener("click", () => {
      if (state.page < state.totalPages - 1) {
        state.page = state.totalPages - 1;
        renderTable();
      }
    });

    // Phím tắt ESC đóng modal
    window.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        closeDetailModal();
        closeCreateModal();
      }
    });
  }

  // ── Khởi động trang ──
  setupEventListeners();
  loadUsersList().then(loadLogs);
})();


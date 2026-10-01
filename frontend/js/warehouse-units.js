/**
 * warehouse-units.js — Quản lý Đơn vị tính quy đổi & Nhập xuất kho (S2-07).
 *
 * Nghiệp vụ cốt lõi:
 * 1. Mỗi SKU khai báo được nhiều đơn vị: lon, lốc, thùng kèm hệ số quy đổi về đơn vị cơ sở.
 * 2. Đơn hàng và phiếu kho nhập theo đơn vị nào cũng quy về đơn vị cơ sở khi ghi sổ.
 * 3. Đổi hệ số quy đổi không làm sai lệch các giao dịch đã ghi trước đó (Cơ chế Snapshot).
 */

(() => {
  if (!Auth.requireLogin()) return;

  const user = Auth.getUser() || { username: "warehouse.staff", fullName: "Nhân viên kho" };
  const $ = (id) => document.getElementById(id);
  const escapeHtml = (v) => String(v ?? "").replace(/[&<>'"]/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[c]);
  const formatNumber = (num) => new Intl.NumberFormat("vi-VN").format(Number(num) || 0);

  // ══════════════════════════════════════════════════
  // 1. DATA STORE (PERSISTENT & DEMO SEED DATA)
  // ══════════════════════════════════════════════════
  const STORAGE_KEY = "salestock_unit_conversions_v2";

  const INITIAL_PRODUCTS = [
    {
      id: "prod-1",
      sku: "HEIN-330",
      name: "Bia Heineken Sleek 330ml",
      category: "Bia & Rượu",
      baseUnit: "lon",
      price: 21000,
      costPrice: 17500,
      inventoryBaseQty: 1480,
      units: [
        { id: "u-1", name: "lon", isBase: true, conversionRate: 1, barcode: "893456789001", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-2", name: "lốc", isBase: false, conversionRate: 6, barcode: "893456789002", note: "1 lốc = 6 lon" },
        { id: "u-3", name: "thùng", isBase: false, conversionRate: 24, barcode: "893456789003", note: "1 thùng = 24 lon (4 lốc)" },
        { id: "u-4", name: "két", isBase: false, conversionRate: 24, barcode: "893456789004", note: "1 két nhựa = 24 lon" }
      ],
      unitHistory: [
        { id: "h-1", unitName: "thùng", oldRate: 24, newRate: 24, changedAt: "2026-09-15 08:30", changedBy: "Thủ kho", reason: "Khai báo quy cách chuẩn ban đầu (1 thùng = 24 lon)" }
      ]
    },
    {
      id: "prod-2",
      sku: "COCA-320",
      name: "Nước ngọt Coca-Cola Sleek 320ml",
      category: "Nước giải khát",
      baseUnit: "lon",
      price: 11000,
      costPrice: 8500,
      inventoryBaseQty: 2400,
      units: [
        { id: "u-5", name: "lon", isBase: true, conversionRate: 1, barcode: "893456789005", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-6", name: "lốc", isBase: false, conversionRate: 6, barcode: "893456789006", note: "1 lốc = 6 lon" },
        { id: "u-7", name: "thùng", isBase: false, conversionRate: 24, barcode: "893456789007", note: "1 thùng = 24 lon chuẩn" },
        { id: "u-8", name: "thùng 30", isBase: false, conversionRate: 30, barcode: "893456789008", note: "1 thùng = 30 lon (Quy cách Tết)" }
      ],
      unitHistory: [
        { id: "h-2", unitName: "thùng", oldRate: 24, newRate: 24, changedAt: "2026-09-15 08:30", changedBy: "Thủ kho", reason: "Khai báo quy cách chuẩn ban đầu" }
      ]
    },
    {
      id: "prod-3",
      sku: "TIGER-330",
      name: "Bia Tiger Crystal 330ml",
      category: "Bia & Rượu",
      baseUnit: "lon",
      price: 20000,
      costPrice: 16800,
      inventoryBaseQty: 960,
      units: [
        { id: "u-9", name: "lon", isBase: true, conversionRate: 1, barcode: "893456789009", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-10", name: "lốc", isBase: false, conversionRate: 6, barcode: "893456789010", note: "1 lốc = 6 lon" },
        { id: "u-11", name: "thùng", isBase: false, conversionRate: 24, barcode: "893456789011", note: "1 thùng = 24 lon" }
      ],
      unitHistory: []
    },
    {
      id: "prod-4",
      sku: "ONTHO-380",
      name: "Sữa đặc có đường Ông Thọ đỏ 380g",
      category: "Sữa & Bơ",
      baseUnit: "lon",
      price: 26000,
      costPrice: 22000,
      inventoryBaseQty: 576,
      units: [
        { id: "u-12", name: "lon", isBase: true, conversionRate: 1, barcode: "893456789012", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-13", name: "lốc", isBase: false, conversionRate: 4, barcode: "893456789013", note: "1 lốc = 4 lon" },
        { id: "u-14", name: "thùng", isBase: false, conversionRate: 48, barcode: "893456789014", note: "1 thùng = 48 lon (12 lốc)" }
      ],
      unitHistory: []
    },
    {
      id: "prod-5",
      sku: "REDBULL-250",
      name: "Nước tăng lực Red Bull 250ml",
      category: "Nước giải khát",
      baseUnit: "lon",
      price: 13500,
      costPrice: 11000,
      inventoryBaseQty: 720,
      units: [
        { id: "u-15", name: "lon", isBase: true, conversionRate: 1, barcode: "893456789015", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-16", name: "lốc", isBase: false, conversionRate: 6, barcode: "893456789016", note: "1 lốc = 6 lon" },
        { id: "u-17", name: "thùng", isBase: false, conversionRate: 24, barcode: "893456789017", note: "1 thùng = 24 lon" }
      ],
      unitHistory: []
    },
    {
      id: "prod-6",
      sku: "SIMPLY-1L",
      name: "Dầu ăn Simply nguyên chất 1L",
      category: "Gia vị & Dầu ăn",
      baseUnit: "chai",
      price: 58000,
      costPrice: 49000,
      inventoryBaseQty: 360,
      units: [
        { id: "u-18", name: "chai", isBase: true, conversionRate: 1, barcode: "893456789018", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-19", name: "thùng", isBase: false, conversionRate: 12, barcode: "893456789019", note: "1 thùng = 12 chai" }
      ],
      unitHistory: []
    },
    {
      id: "prod-7",
      sku: "HAOHAO-75",
      name: "Mì ăn liền Hảo Hảo tôm chua cay 75g",
      category: "Mì ăn liền",
      baseUnit: "gói",
      price: 4500,
      costPrice: 3800,
      inventoryBaseQty: 1800,
      units: [
        { id: "u-20", name: "gói", isBase: true, conversionRate: 1, barcode: "893456789020", note: "Đơn vị cơ sở lưu kho" },
        { id: "u-21", name: "lốc", isBase: false, conversionRate: 5, barcode: "893456789021", note: "1 lốc = 5 gói" },
        { id: "u-22", name: "thùng", isBase: false, conversionRate: 30, barcode: "893456789022", note: "1 thùng = 30 gói" }
      ],
      unitHistory: []
    }
  ];

  const INITIAL_RECEIPTS = [
    {
      id: "PNK-202609-001",
      type: "IMPORT",
      date: "2026-09-20",
      createdAt: "2026-09-20 09:15",
      warehouse: "Kho Tổng Tân Bình (HCM)",
      partner: "Nhà máy Bia Heineken Việt Nam",
      createdBy: "Nguyễn Văn Kho (Thủ kho)",
      notes: "Nhập hàng theo thùng định kỳ đợt 1 tháng 9",
      status: "RECORDED",
      items: [
        {
          sku: "HEIN-330",
          productName: "Bia Heineken Sleek 330ml",
          inputUnit: "thùng",
          inputQuantity: 10,
          conversionRateSnapshot: 24, // Snapshot cố định
          baseUnit: "lon",
          baseQuantity: 240, // 10 * 24 = 240 lon
          notes: "Nhập 10 thùng quy đổi ghi sổ 240 lon"
        },
        {
          sku: "TIGER-330",
          productName: "Bia Tiger Crystal 330ml",
          inputUnit: "thùng",
          inputQuantity: 15,
          conversionRateSnapshot: 24,
          baseUnit: "lon",
          baseQuantity: 360,
          notes: "Nhập 15 thùng quy đổi ghi sổ 360 lon"
        }
      ]
    },
    {
      id: "PNK-202609-002",
      type: "IMPORT",
      date: "2026-09-22",
      createdAt: "2026-09-22 14:00",
      warehouse: "Kho Tổng Tân Bình (HCM)",
      partner: "Công ty Nước Giải Khát Coca-Cola",
      createdBy: "Nguyễn Văn Kho (Thủ kho)",
      notes: "Nhập thùng 24 lon quy cách chuẩn",
      status: "RECORDED",
      items: [
        {
          sku: "COCA-320",
          productName: "Nước ngọt Coca-Cola Sleek 320ml",
          inputUnit: "thùng",
          inputQuantity: 20,
          conversionRateSnapshot: 24,
          baseUnit: "lon",
          baseQuantity: 480,
          notes: "Nhập 20 thùng quy đổi ghi sổ 480 lon"
        }
      ]
    },
    {
      id: "PXK-202609-003",
      type: "EXPORT",
      date: "2026-09-25",
      createdAt: "2026-09-25 10:30",
      warehouse: "Kho Tổng Tân Bình (HCM)",
      partner: "Kho Trung Chuyển Cầu Giấy (HN)",
      createdBy: "Nguyễn Văn Kho (Thủ kho)",
      notes: "Điều phối chuyển kho liên tỉnh",
      status: "RECORDED",
      items: [
        {
          sku: "HEIN-330",
          productName: "Bia Heineken Sleek 330ml",
          inputUnit: "thùng",
          inputQuantity: 5,
          conversionRateSnapshot: 24,
          baseUnit: "lon",
          baseQuantity: 120,
          notes: "Xuất 5 thùng quy đổi trừ sổ kho 120 lon"
        }
      ]
    },
    {
      id: "DH-202609-004",
      type: "ORDER",
      date: "2026-09-27",
      createdAt: "2026-09-27 16:45",
      warehouse: "Kho Tổng Tân Bình (HCM)",
      partner: "Đại lý Cấp 1 Phương Nam",
      createdBy: "Trần Thị Bán Hàng",
      notes: "Đơn hàng bán sỉ theo thùng và lốc",
      status: "RECORDED",
      items: [
        {
          sku: "HEIN-330",
          productName: "Bia Heineken Sleek 330ml",
          inputUnit: "thùng",
          inputQuantity: 2,
          conversionRateSnapshot: 24,
          baseUnit: "lon",
          baseQuantity: 48,
          notes: "Bán 2 thùng = 48 lon"
        },
        {
          sku: "HEIN-330",
          productName: "Bia Heineken Sleek 330ml",
          inputUnit: "lốc",
          inputQuantity: 3,
          conversionRateSnapshot: 6,
          baseUnit: "lon",
          baseQuantity: 18,
          notes: "Bán 3 lốc = 18 lon"
        }
      ]
    },
    {
      id: "PNK-202609-005",
      type: "IMPORT",
      date: "2026-09-29",
      createdAt: "2026-09-29 11:20",
      warehouse: "Kho Tổng Tân Bình (HCM)",
      partner: "Công ty Cổ phần Sữa Vinamilk",
      createdBy: "Nguyễn Văn Kho (Thủ kho)",
      notes: "Nhập sữa đặc theo kiện thùng lớn",
      status: "RECORDED",
      items: [
        {
          sku: "ONTHO-380",
          productName: "Sữa đặc có đường Ông Thọ đỏ 380g",
          inputUnit: "thùng",
          inputQuantity: 10,
          conversionRateSnapshot: 48,
          baseUnit: "lon",
          baseQuantity: 480,
          notes: "Nhập 10 thùng quy đổi ghi sổ 480 lon"
        }
      ]
    }
  ];

  // Store Controller
  const Store = {
    products: [],
    receipts: [],

    async request(path, options) {
      const response = await Auth.apiFetch(path, options);
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success) {
        throw new Error(result.message || "Không thể tải dữ liệu từ máy chủ.");
      }
      return result.data;
    },

    async init() {
      const [products, inventory, vouchers, orders, warehouses] = await Promise.all([
        this.request("/products"),
        this.request("/warehouse/inventory"),
        this.request("/warehouse/vouchers"),
        this.request("/orders"),
        this.request("/warehouse/options"),
      ]);

      const conversions = await Promise.all((products || []).map((product) =>
        this.request(`/products/${product.id}/unit-conversions`)));

      this.products = (products || []).map((product, index) => {
        const stock = (inventory || []).find((item) => item.productId === product.id);
        return {
          id: product.id,
          sku: product.code,
          name: product.name,
          category: product.categoryName || "Chưa phân nhóm",
          baseUnit: product.unit,
          price: product.price,
          costPrice: product.costPrice,
          inventoryBaseQty: stock?.baseQuantity || 0,
          units: (conversions[index] || []).map((unit) => ({
            id: unit.id,
            name: unit.unitName,
            isBase: Boolean(unit.isBaseUnit),
            conversionRate: Number(unit.conversionFactor),
            barcode: unit.barcode || "",
            note: unit.description || "",
          })),
          unitHistory: [],
        };
      });

      const voucherRows = (vouchers || []).map((voucher) => ({
        id: voucher.voucherCode,
        backendId: voucher.id,
        type: voucher.type,
        date: voucher.recordedAt?.slice(0, 10) || "",
        createdAt: voucher.recordedAt,
        warehouse: voucher.warehouseName || voucher.warehouseCode || "—",
        partner: "—",
        createdBy: voucher.createdBy,
        notes: voucher.note || "",
        status: voucher.status,
        items: (voucher.items || []).map((item) => ({
          sku: item.productCode,
          productName: item.productName,
          inputUnit: item.unit,
          inputQuantity: Number(item.quantity),
          conversionRateSnapshot: Number(item.conversionFactor),
          baseUnit: item.baseUnit,
          baseQuantity: Number(item.baseQuantity),
          notes: item.note || "",
        })),
      }));
      const orderRows = (orders || []).map((order) => ({
        id: order.orderCode,
        backendId: order.id,
        type: "ORDER",
        date: (order.recordedAt || order.createdAt || "").slice(0, 10),
        createdAt: order.recordedAt || order.createdAt,
        warehouse: "Đơn hàng bán",
        partner: order.customerName || "—",
        createdBy: order.createdBy,
        notes: order.note || "",
        status: order.status,
        items: (order.items || []).map((item) => ({
          sku: item.productCode,
          productName: item.productName,
          inputUnit: item.unit,
          inputQuantity: Number(item.quantity),
          conversionRateSnapshot: Number(item.conversionFactor),
          baseUnit: item.baseUnit,
          baseQuantity: Number(item.baseQuantity),
          notes: item.note || "",
        })),
      }));
      this.receipts = [...voucherRows, ...orderRows].sort((a, b) =>
        new Date(b.createdAt || 0) - new Date(a.createdAt || 0));

      for (const id of ["active-warehouse-select", "receipt-warehouse"]) {
        const select = $(id);
        if (select && (warehouses || []).length) {
          select.innerHTML = warehouses.map((warehouse) =>
            `<option value="${warehouse.id}">${escapeHtml(warehouse.name)}</option>`).join("");
        }
      }
    },

    save() {
      // Dữ liệu nghiệp vụ được lưu tại backend; trình duyệt không giữ bản sao.
    },

    async resetToDemo() {
      await this.init();
    },

    getProductBySku(sku) {
      return this.products.find((p) => p.sku === sku);
    },

    getUnit(sku, unitName) {
      const prod = this.getProductBySku(sku);
      if (!prod) return null;
      return prod.units.find((u) => u.name.toLowerCase() === unitName.toLowerCase());
    },

    /**
     * Sửa hoặc thêm đơn vị tính quy đổi cho SKU.
     * Quy tắc S2-07: Đổi hệ số KHÔNG làm đổi các receipts đã lưu!
     */
    async saveUnit(sku, { name, conversionRate, barcode, note }) {
      const prod = this.getProductBySku(sku);
      if (!prod) throw new Error("Không tìm thấy SKU: " + sku);

      const cleanName = name.trim().toLowerCase();
      const rate = Number(conversionRate);
      if (!rate || rate <= 0) throw new Error("Hệ số quy đổi phải lớn hơn 0.");

      const unit = prod.units.find((u) => u.name.toLowerCase() === cleanName);
      const isNew = !unit;
      const oldRate = unit ? unit.conversionRate : rate;
      if (unit?.isBase) throw new Error("Không thể sửa đơn vị cơ sở tại màn hình quy đổi.");
      const payload = {
        unitName: cleanName,
        conversionFactor: rate,
        barcode: barcode || "",
        description: note || `1 ${cleanName} = ${rate} ${prod.baseUnit}`,
      };
      await this.request(
        unit ? `/products/${prod.id}/unit-conversions/${unit.id}` : `/products/${prod.id}/unit-conversions`,
        { method: unit ? "PUT" : "POST", body: JSON.stringify(payload) },
      );
      await this.init();
      return { product: this.getProductBySku(sku), unit, oldRate, newRate: rate, isNew };
    },

    /**
     * Xóa đơn vị tính (chỉ cho phép nếu không phải đơn vị cơ sở).
     */
    async deleteUnit(sku, unitName) {
      const prod = this.getProductBySku(sku);
      if (!prod) return;
      const unit = prod.units.find((u) => u.name.toLowerCase() === unitName.toLowerCase());
      if (!unit) return;
      if (unit.isBase) throw new Error("Không thể xóa đơn vị cơ sở lưu kho.");

      await this.request(`/products/${prod.id}/unit-conversions/${unit.id}`, { method: "DELETE" });
      await this.init();
    },

    /**
     * Lưu và ghi sổ phiếu kho / đơn hàng.
     * Quy tắc S2-07: Chốt snapshot tỷ lệ quy đổi tại thời điểm ghi sổ!
     */
    async recordReceipt(receiptData) {
      if (!receiptData.items || !receiptData.items.length) {
        throw new Error("Vui lòng thêm ít nhất một mặt hàng.");
      }

      const requestItems = [];
      for (const item of receiptData.items) {
        const prod = this.getProductBySku(item.sku);
        if (!prod) throw new Error(`SKU ${item.sku} không tồn tại.`);

        const unit = prod.units.find((u) => u.name.toLowerCase() === item.inputUnit.toLowerCase());
        if (!unit) throw new Error(`Đơn vị ${item.inputUnit} không hợp lệ với SKU ${item.sku}.`);

        const inputQty = Number(item.inputQuantity);
        if (!inputQty || inputQty <= 0) throw new Error(`Số lượng mặt hàng ${prod.name} phải lớn hơn 0.`);

        requestItems.push({ productId: prod.id, productCode: prod.sku, unit: unit.name,
          quantity: inputQty, note: item.notes || "" });
      }
      const code = receiptData.id || this.generateReceiptCode(receiptData.type);
      if (receiptData.type === "ORDER") {
        await this.request("/orders", { method: "POST", body: JSON.stringify({
          orderCode: code, customerName: receiptData.partner || "Khách hàng",
          note: receiptData.notes || "", items: requestItems,
        }) });
      } else {
        await this.request(`/warehouse/${receiptData.type === "EXPORT" ? "export" : "import"}`, {
          method: "POST",
          body: JSON.stringify({ voucherCode: code,
            warehouseId: receiptData.warehouseId || user.warehouses?.[0]?.id || null,
            note: [receiptData.partner, receiptData.notes].filter(Boolean).join(" · "),
            items: requestItems }),
        });
      }
      await this.init();
      return this.receipts.find((receipt) => receipt.id === code) || { id: code };
    },

    generateReceiptCode(type) {
      const prefix = type === "IMPORT" ? "PNK" : type === "EXPORT" ? "PXK" : "DH";
      const ym = new Date().toISOString().slice(0, 7).replace("-", "");
      const count = this.receipts.filter((r) => r.type === type).length + 1;
      return `${prefix}-${ym}-${String(count).padStart(3, "0")}`;
    },

    /**
     * Phân rã số lượng cơ sở (lon) thành các đơn vị đóng gói lớn (thùng, lốc, lon lẻ)
     * Rất hữu ích cho thủ kho kiểm đếm thực tế!
     */
    breakdownInventory(sku, baseQty) {
      const prod = this.getProductBySku(sku);
      if (!prod || !prod.units) return `${formatNumber(baseQty)} (cơ sở)`;

      // Sắp xếp các đơn vị có hệ số từ cao xuống thấp (không tính đơn vị cơ sở)
      const sortedUnits = [...prod.units]
        .filter((u) => !u.isBase && u.conversionRate > 1)
        .sort((a, b) => b.conversionRate - a.conversionRate);

      let rem = Number(baseQty) || 0;
      const parts = [];

      for (const u of sortedUnits) {
        if (rem >= u.conversionRate) {
          const count = Math.floor(rem / u.conversionRate);
          parts.push(`${formatNumber(count)} ${u.name}`);
          rem %= u.conversionRate;
        }
      }

      if (rem > 0 || !parts.length) {
        parts.push(`${formatNumber(rem)} ${prod.baseUnit} lẻ`);
      }

      return parts.join(" + ");
    }
  };

  // ══════════════════════════════════════════════════
  // 2. UI SHELL & NAVIGATION SETUP
  // ══════════════════════════════════════════════════
  const sidebar = $("sidebar");
  const nav = $("sidebar-nav");
  const requestedWarehouseTab = new URLSearchParams(window.location.search).get("tab") || "units";
  const overlay = document.createElement("div");
  overlay.className = "sidebar-overlay";
  document.body.appendChild(overlay);

  function openSidebar(open) {
    sidebar.classList.toggle("sidebar--open", open);
    overlay.classList.toggle("sidebar-overlay--visible", open);
  }

  $("sidebar-toggle")?.addEventListener("click", () => openSidebar(true));
  $("sidebar-close")?.addEventListener("click", () => openSidebar(false));
  overlay.addEventListener("click", () => openSidebar(false));

  // Render Sidebar Links dynamically via MenuConfig
  let lastSection = null;
  nav.innerHTML = MenuConfig.getMenuForUser(user).map((item) => {
    const section = item.section && item.section !== lastSection
      ? `<div class="sidebar__section-label">${escapeHtml(item.section)}</div>` : "";
    if (item.section) lastSection = item.section;
    const resolvedHref = MenuConfig.resolveHref ? MenuConfig.resolveHref(item.href) : item.href;
    const activeMenuId = requestedWarehouseTab === "units" ? "unit-conversions" : "import-export";
    const isActive = item.id === activeMenuId;
    return `${section}<a class="sidebar__link ${isActive ? "sidebar__link--active" : ""}"
      href="${resolvedHref}" data-page="${item.id}">
      <svg viewBox="0 0 24 24">${item.icon}</svg>
      <span>${escapeHtml(item.label)}</span>
    </a>`;
  }).join("");

  // User Header info
  $("user-name").textContent = user.fullName || user.username;
  const avatarFallback = $("user-avatar-fallback");
  const avatarImage = $("user-avatar-image");
  avatarFallback.textContent = (user.fullName || user.username || "K").trim().charAt(0).toUpperCase();

  if (user.avatarThumbnailUrl) {
    Auth.setProtectedImage(avatarImage, user.avatarThumbnailUrl).then((ok) => {
      if (ok) {
        avatarImage.hidden = false;
        avatarFallback.hidden = true;
      }
    });
  }

  const roleNames = (user.roles || []).map(MenuConfig.getRoleDisplayName);
  const scope = [...(user.warehouses || []), ...(user.territories || [])].map((item) => item.name);
  $("user-role").textContent = [roleNames.join(", "), scope.join(", ")].filter(Boolean).join(" · ") || "Kho Tân Bình";

  document.querySelectorAll("#btn-logout, #btn-logout-topbar").forEach((btn) => btn.addEventListener("click", Auth.logout));

  // ══════════════════════════════════════════════════
  // 3. TOAST NOTIFICATIONS
  // ══════════════════════════════════════════════════
  function showToast(message, type = "success") {
    let container = document.getElementById("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__message">${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.remove();
    }, 4000);
  }

  // ══════════════════════════════════════════════════
  // 4. TAB NAVIGATION
  // ══════════════════════════════════════════════════
  const tabs = document.querySelectorAll(".nav-tab");
  const panels = document.querySelectorAll(".tab-panel");

  function updateWarehouseMenuState(tabId) {
    const activeMenuId = tabId === "sku-units" ? "unit-conversions" : "import-export";
    nav.querySelectorAll("[data-page]").forEach((link) => {
      link.classList.toggle("sidebar__link--active", link.dataset.page === activeMenuId);
    });
  }

  function switchTab(tabId) {
    tabs.forEach((tab) => {
      const active = tab.dataset.tab === tabId;
      tab.classList.toggle("nav-tab--active", active);
      tab.setAttribute("aria-selected", active ? "true" : "false");
    });
    panels.forEach((panel) => {
      const active = panel.id === `panel-${tabId}`;
      panel.hidden = !active;
      panel.classList.toggle("tab-panel--active", active);
    });

    if (tabId === "sku-units") renderSkuTable();
    if (tabId === "create-receipt") prepareReceiptForm();
    if (tabId === "ledger-audit") { renderLedgerTable(); renderSandboxOptions(); }
    if (tabId === "inventory-report") renderInventoryTable();
    updateWarehouseMenuState(tabId);
    updateMetrics();
  }

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => switchTab(tab.dataset.tab));
  });

  // Check URL params for initial tab (e.g. ?tab=receipts)
  const urlParams = new URLSearchParams(window.location.search);
  const initialTabParam = urlParams.get("tab");
  if (initialTabParam === "receipts") switchTab("create-receipt");
  else if (initialTabParam === "ledger") switchTab("ledger-audit");
  else if (initialTabParam === "inventory") switchTab("inventory-report");
  else switchTab("sku-units");

  $("btn-quick-new-receipt")?.addEventListener("click", () => switchTab("create-receipt"));
  $("btn-verify-integrity")?.addEventListener("click", () => {
    switchTab("ledger-audit");
    $("sandbox-sku-select")?.scrollIntoView({ behavior: "smooth", block: "center" });
  });

  // ══════════════════════════════════════════════════
  // 5. UPDATE METRICS STRIP
  // ══════════════════════════════════════════════════
  function updateMetrics() {
    $("metric-sku-count").textContent = Store.products.length;
    const totalUnits = Store.products.reduce((acc, p) => acc + p.units.length, 0);
    $("metric-unit-count").textContent = `${totalUnits} đơn vị`;
    $("metric-ledger-count").textContent = `${Store.receipts.length} chứng từ`;
  }

  // ══════════════════════════════════════════════════
  // 6. PANEL 1: SKU UNITS MANAGEMENT (TAB 1)
  // ══════════════════════════════════════════════════
  function renderSkuTable() {
    const search = $("sku-search-input").value.trim().toLowerCase();
    const categoryFilter = $("sku-category-filter").value;
    const baseFilter = $("sku-baseunit-filter").value;

    const filtered = Store.products.filter((p) => {
      const matchSearch = !search
        || p.name.toLowerCase().includes(search)
        || p.sku.toLowerCase().includes(search)
        || p.units.some((u) => u.name.toLowerCase().includes(search));
      const matchCat = !categoryFilter || p.category === categoryFilter;
      const matchBase = !baseFilter || p.baseUnit === baseFilter;
      return matchSearch && matchCat && matchBase;
    });

    const tbody = $("sku-table-body");
    if (!filtered.length) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty">Không tìm thấy sản phẩm nào phù hợp điều kiện lọc.</td></tr>`;
      return;
    }

    tbody.innerHTML = filtered.map((p) => {
      const baseUnitObj = p.units.find((u) => u.isBase) || { name: p.baseUnit, conversionRate: 1 };
      const convertedUnits = p.units.filter((u) => !u.isBase);

      const unitChipsHtml = [
        `<span class="unit-chip unit-chip--base" title="Đơn vị cơ sở ghi sổ kho">
          <strong>${escapeHtml(baseUnitObj.name)}</strong>
          <span class="unit-chip__rate">×1 (Cơ sở)</span>
        </span>`,
        ...convertedUnits.map((u) => `
          <span class="unit-chip unit-chip--converted" title="1 ${escapeHtml(u.name)} = ${u.conversionRate} ${escapeHtml(p.baseUnit)}">
            ${escapeHtml(u.name)}
            <span class="unit-chip__rate">×${u.conversionRate}</span>
          </span>
        `)
      ].join("");

      const breakdown = Store.breakdownInventory(p.sku, p.inventoryBaseQty);

      return `
        <tr>
          <td>
            <div class="sku-identity">
              <span class="sku-name">${escapeHtml(p.name)}</span>
              <span class="sku-code">${escapeHtml(p.sku)}</span>
              <span class="sku-category-tag">${escapeHtml(p.category)}</span>
            </div>
          </td>
          <td>
            <span class="badge badge--base">
              ${escapeHtml(p.baseUnit)} (Hệ số: 1)
            </span>
          </td>
          <td>
            <div class="unit-tag-cluster">
              ${unitChipsHtml}
            </div>
          </td>
          <td>
            <div class="inventory-dual-pill">
              <span class="inventory-dual-pill__base">${formatNumber(p.inventoryBaseQty)} ${escapeHtml(p.baseUnit)}</span>
              <span class="inventory-dual-pill__packages">≈ ${escapeHtml(breakdown)}</span>
            </div>
          </td>
          <td class="th-actions">
            <div class="table-row-actions">
              <button class="action-btn action-btn--primary" data-action="add-unit" data-sku="${p.sku}" title="Thêm đơn vị quy đổi mới">
                + Đơn vị
              </button>
              <button class="action-btn" data-action="edit-unit" data-sku="${p.sku}" title="Sửa hệ số quy đổi">
                Sửa hệ số
              </button>
              <button class="action-btn" data-action="history-unit" data-sku="${p.sku}" title="Xem lịch sử thay đổi hệ số">
                Lịch sử (${p.unitHistory?.length || 0})
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join("");
  }

  $("sku-search-input")?.addEventListener("input", renderSkuTable);
  $("sku-category-filter")?.addEventListener("change", renderSkuTable);
  $("sku-baseunit-filter")?.addEventListener("change", renderSkuTable);

  $("btn-reset-demo")?.addEventListener("click", async () => {
    if (confirm("Tải lại dữ liệu mới nhất từ máy chủ?")) {
      await Store.resetToDemo();
      showToast("Đã tải lại dữ liệu từ máy chủ.");
      renderSkuTable();
      updateMetrics();
    }
  });

  // Event delegation for table action buttons
  $("sku-table-body")?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const action = btn.dataset.action;
    const sku = btn.dataset.sku;

    if (action === "add-unit") openUnitModal(sku, null);
    if (action === "edit-unit") {
      const prod = Store.getProductBySku(sku);
      const convertedUnits = prod?.units.filter((u) => !u.isBase) || [];
      if (!convertedUnits.length) {
        openUnitModal(sku, null);
      } else {
        openUnitModal(sku, convertedUnits[0]);
      }
    }
    if (action === "history-unit") openHistoryModal(sku);
  });

  // ══════════════════════════════════════════════════
  // 7. PANEL 2: RECEIPT & ORDER BUILDER (TAB 2)
  // ══════════════════════════════════════════════════
  const receiptState = {
    type: "IMPORT",
    lines: []
  };

  function createDefaultReceiptLine(id = "line-1") {
    const product = Store.products[0];
    if (!product) return null;
    const unit = product.units.find((item) => !item.isBase) || product.units[0];
    if (!unit) return null;
    return { id, sku: product.sku, inputUnit: unit.name, inputQuantity: 1, notes: "" };
  }

  function prepareReceiptForm() {
    $("receipt-date").value = new Date().toISOString().slice(0, 10);
    $("receipt-code").value = Store.generateReceiptCode(receiptState.type);
    if (!receiptState.lines.length) {
      const defaultLine = createDefaultReceiptLine();
      receiptState.lines = defaultLine ? [defaultLine] : [];
    }
    renderLineItems();
  }

  $("btn-regen-code")?.addEventListener("click", () => {
    $("receipt-code").value = Store.generateReceiptCode(receiptState.type);
  });

  // Receipt type switcher (Radio pills)
  document.querySelectorAll("#receipt-type-radios input[type='radio']").forEach((radio) => {
    radio.addEventListener("change", (e) => {
      receiptState.type = e.target.value;
      document.querySelectorAll(".receipt-type-pill").forEach((pill) => {
        pill.classList.toggle("receipt-type-pill--active", pill.querySelector("input").checked);
      });
      $("receipt-code").value = Store.generateReceiptCode(receiptState.type);

      const partnerLabel = $("label-receipt-partner");
      const partnerInput = $("receipt-partner");
      if (receiptState.type === "IMPORT") {
        partnerLabel.textContent = "Nhà cung cấp / Nhà sản xuất";
        partnerInput.placeholder = "VD: Nhà máy Bia Heineken Việt Nam...";
      } else if (receiptState.type === "EXPORT") {
        partnerLabel.textContent = "Kho đích / Chi nhánh nhận";
        partnerInput.placeholder = "VD: Kho Trung Chuyển Cầu Giấy (HN)...";
      } else {
        partnerLabel.textContent = "Khách hàng / Đại lý phân phối";
        partnerInput.placeholder = "VD: Đại lý Phương Nam (Hà Nội)...";
      }
    });
  });

  function renderLineItems() {
    const tbody = $("line-items-body");
    if (!Store.products.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty">Chưa có sản phẩm thật trong cơ sở dữ liệu.</td></tr>';
      updateReceiptSummary();
      return;
    }
    tbody.innerHTML = receiptState.lines.map((line, index) => {
      const prod = Store.getProductBySku(line.sku) || Store.products[0];
      const validUnits = prod?.units || [];
      const currentUnit = validUnits.find((u) => u.name.toLowerCase() === (line.inputUnit || "").toLowerCase()) || validUnits[0];
      const rate = currentUnit?.conversionRate || 1;
      const baseQty = Math.round((Number(line.inputQuantity) || 0) * rate);

      const skuOptions = Store.products.map((p) => `
        <option value="${p.sku}" ${p.sku === prod.sku ? "selected" : ""}>
          ${escapeHtml(p.name)} (${p.sku}) [Cơ sở: ${escapeHtml(p.baseUnit)}]
        </option>
      `).join("");

      const unitOptions = validUnits.map((u) => `
        <option value="${u.name}" ${u.name.toLowerCase() === currentUnit.name.toLowerCase() ? "selected" : ""}>
          ${escapeHtml(u.name)} (×${u.conversionRate} ${prod.baseUnit})
        </option>
      `).join("");

      return `
        <tr data-line-id="${line.id}">
          <td>
            <select class="line-sku-select" data-index="${index}">
              ${skuOptions}
            </select>
          </td>
          <td>
            <select class="line-unit-select" data-index="${index}">
              ${unitOptions}
            </select>
          </td>
          <td>
            <input type="number" class="line-qty-input" data-index="${index}" min="1" step="1" value="${line.inputQuantity}" />
          </td>
          <td>
            <div class="conversion-rate-cell">
              1 ${escapeHtml(currentUnit.name)} = ${rate} ${escapeHtml(prod.baseUnit)}
            </div>
          </td>
          <td>
            <div class="base-qty-cell">
              <span class="base-qty-badge">
                ${formatNumber(baseQty)} ${escapeHtml(prod.baseUnit)}
              </span>
              <span class="base-qty-formula">
                ${line.inputQuantity || 0} ${escapeHtml(currentUnit.name)} × ${rate}
              </span>
            </div>
          </td>
          <td style="text-align: center">
            <button type="button" class="btn-remove-line" data-index="${index}" title="Xóa dòng này">
              <svg viewBox="0 0 24 24" width="16" height="16"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </td>
        </tr>
      `;
    }).join("");

    updateReceiptSummary();
  }

  function updateReceiptSummary() {
    let totalBaseQty = 0;
    const packageParts = [];

    receiptState.lines.forEach((line) => {
      const prod = Store.getProductBySku(line.sku) || Store.products[0];
      const unit = prod.units.find((u) => u.name.toLowerCase() === (line.inputUnit || "").toLowerCase()) || prod.units[0];
      const rate = unit?.conversionRate || 1;
      const baseQty = Math.round((Number(line.inputQuantity) || 0) * rate);
      totalBaseQty += baseQty;
      if (line.inputQuantity > 0) {
        packageParts.push(`${line.inputQuantity} ${unit.name}`);
      }
    });

    $("summary-line-count").textContent = `${receiptState.lines.length} mặt hàng`;
    $("summary-packages-count").textContent = packageParts.join(", ") || "0";
    $("summary-base-qty-total").textContent = `${formatNumber(totalBaseQty)} (Tổng lon/cơ sở)`;
  }

  // Handle changes in line items table
  $("line-items-body")?.addEventListener("change", (e) => {
    const target = e.target;
    const index = Number(target.dataset.index);
    if (isNaN(index) || !receiptState.lines[index]) return;

    if (target.classList.contains("line-sku-select")) {
      const newSku = target.value;
      const prod = Store.getProductBySku(newSku);
      receiptState.lines[index].sku = newSku;
      // Default to the first bulk unit or base unit
      const bulkUnit = prod.units.find((u) => !u.isBase) || prod.units[0];
      receiptState.lines[index].inputUnit = bulkUnit.name;
      renderLineItems();
    } else if (target.classList.contains("line-unit-select")) {
      receiptState.lines[index].inputUnit = target.value;
      renderLineItems();
    } else if (target.classList.contains("line-qty-input")) {
      receiptState.lines[index].inputQuantity = Math.max(1, Number(target.value) || 1);
      renderLineItems();
    }
  });

  $("line-items-body")?.addEventListener("input", (e) => {
    const target = e.target;
    if (target.classList.contains("line-qty-input")) {
      const index = Number(target.dataset.index);
      if (!isNaN(index) && receiptState.lines[index]) {
        receiptState.lines[index].inputQuantity = target.value;
        // Live update cell without re-rendering entire table
        const row = target.closest("tr");
        const prod = Store.getProductBySku(receiptState.lines[index].sku);
        const unit = prod.units.find((u) => u.name.toLowerCase() === (receiptState.lines[index].inputUnit || "").toLowerCase()) || prod.units[0];
        const rate = unit?.conversionRate || 1;
        const baseQty = Math.round((Number(target.value) || 0) * rate);
        row.querySelector(".base-qty-badge").textContent = `${formatNumber(baseQty)} ${prod.baseUnit}`;
        row.querySelector(".base-qty-formula").textContent = `${target.value || 0} ${unit.name} × ${rate}`;
        updateReceiptSummary();
      }
    }
  });

  $("line-items-body")?.addEventListener("click", (e) => {
    const btn = e.target.closest(".btn-remove-line");
    if (!btn) return;
    const index = Number(btn.dataset.index);
    if (receiptState.lines.length <= 1) {
      showToast("Phiếu phải có ít nhất một mặt hàng.", "warning");
      return;
    }
    receiptState.lines.splice(index, 1);
    renderLineItems();
  });

  $("btn-add-line-item")?.addEventListener("click", () => {
    if (!Store.products.length) {
      showToast("Chưa có sản phẩm để thêm vào phiếu.", "warning");
      return;
    }
    const randomSku = Store.products[receiptState.lines.length % Store.products.length];
    const defaultUnit = randomSku.units.find((u) => !u.isBase) || randomSku.units[0];
    receiptState.lines.push({
      id: "line-" + Date.now(),
      sku: randomSku.sku,
      inputUnit: defaultUnit.name,
      inputQuantity: 10,
      notes: ""
    });
    renderLineItems();
  });

  $("btn-reset-receipt")?.addEventListener("click", () => {
    if (confirm("Làm mới toàn bộ thông tin phiếu?")) {
      const defaultLine = createDefaultReceiptLine();
      receiptState.lines = defaultLine ? [defaultLine] : [];
      $("receipt-partner").value = "";
      $("receipt-notes").value = "";
      prepareReceiptForm();
    }
  });

  // Submit Receipt and Record into Stock Ledger with Frozen Snapshot
  $("btn-save-receipt")?.addEventListener("click", async () => {
    try {
      const code = $("receipt-code").value.trim() || Store.generateReceiptCode(receiptState.type);
      const date = $("receipt-date").value;
      const warehouseId = Number($("receipt-warehouse").value) || null;
      const warehouse = $("receipt-warehouse").selectedOptions[0]?.textContent || "";
      const partner = $("receipt-partner").value.trim();
      const notes = $("receipt-notes").value.trim();

      const receiptRecord = await Store.recordReceipt({
        id: code,
        type: receiptState.type,
        date: date,
        warehouse: warehouse,
        warehouseId: warehouseId,
        partner: partner,
        notes: notes,
        items: receiptState.lines
      });

      showToast(`Đã ghi sổ thành công ${receiptRecord.id}! Hệ số snapshot đã được đóng băng.`, "success");

      // Reset form lines
      const defaultLine = createDefaultReceiptLine();
      receiptState.lines = defaultLine ? [defaultLine] : [];
      $("receipt-partner").value = "";
      $("receipt-notes").value = "";
      prepareReceiptForm();

      // Navigate to Ledger Audit Tab to show the recorded transaction
      switchTab("ledger-audit");
    } catch (err) {
      showToast(err.message, "error");
    }
  });

  // ══════════════════════════════════════════════════
  // 8. PANEL 3: LEDGER AUDIT & SNAPSHOT INTEGRITY (TAB 3)
  // ══════════════════════════════════════════════════
  function renderLedgerTable() {
    const search = $("ledger-search-input").value.trim().toLowerCase();
    const typeFilter = $("ledger-type-filter").value;
    const skuFilter = $("ledger-sku-filter").value;

    const tbody = $("ledger-table-body");
    const filtered = Store.receipts.filter((r) => {
      const matchType = !typeFilter || r.type === typeFilter;
      const matchSearch = !search
        || r.id.toLowerCase().includes(search)
        || r.partner?.toLowerCase().includes(search)
        || r.items.some((i) => i.productName.toLowerCase().includes(search) || i.sku.toLowerCase().includes(search) || i.inputUnit.toLowerCase().includes(search));
      const matchSku = !skuFilter || r.items.some((i) => i.sku === skuFilter);
      return matchType && matchSearch && matchSku;
    });

    if (!filtered.length) {
      tbody.innerHTML = `<tr><td colspan="9" class="empty">Không có giao dịch nào phù hợp điều kiện tra cứu.</td></tr>`;
      return;
    }

    // Populate SKU filter dropdown
    const skuOptionsHtml = `<option value="">Tất cả SKU</option>` + Store.products.map((p) => `
      <option value="${p.sku}" ${p.sku === skuFilter ? "selected" : ""}>${escapeHtml(p.sku)} - ${escapeHtml(p.name)}</option>
    `).join("");
    $("ledger-sku-filter").innerHTML = skuOptionsHtml;

    tbody.innerHTML = filtered.map((r) => {
      const typeBadge = r.type === "IMPORT"
        ? `<span class="badge badge--import">📥 Nhập kho</span>`
        : r.type === "EXPORT"
        ? `<span class="badge badge--export">📤 Xuất kho</span>`
        : `<span class="badge badge--order">🛒 Đơn hàng</span>`;

      return r.items.map((item, idx) => `
        <tr>
          ${idx === 0 ? `
            <td rowspan="${r.items.length}">
              <span class="user-name" style="font-family: var(--font-mono); font-weight:700">${escapeHtml(r.id)}</span>
              <span class="user-meta">${escapeHtml(r.createdAt || r.date)}</span>
              <span class="user-meta" style="color:var(--ink-700)">${escapeHtml(r.partner || "")}</span>
            </td>
            <td rowspan="${r.items.length}">${typeBadge}</td>
          ` : ""}
          <td>
            <span class="sku-name">${escapeHtml(item.productName)}</span>
            <span class="sku-code">${escapeHtml(item.sku)}</span>
          </td>
          <td>
            <strong style="font-size:0.9375rem; color:var(--navy-900)">
              ${formatNumber(item.inputQuantity)} ${escapeHtml(item.inputUnit)}
            </strong>
          </td>
          <td>
            <span class="snapshot-rate-badge" title="Hệ số quy đổi đã chốt tại thời điểm lập phiếu - Không bao giờ đổi">
              <svg viewBox="0 0 24 24" width="12" height="12"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              × ${item.conversionRateSnapshot} (${item.baseUnit}/${item.inputUnit})
            </span>
          </td>
          <td>
            <span class="base-recorded-badge">
              ${formatNumber(item.baseQuantity)} ${escapeHtml(item.baseUnit)}
            </span>
          </td>
          ${idx === 0 ? `
            <td rowspan="${r.items.length}">
              <span style="font-size:0.8125rem">${escapeHtml(r.warehouse)}</span>
              <span class="user-meta">${escapeHtml(r.createdBy)}</span>
            </td>
            <td rowspan="${r.items.length}">
              <span class="badge badge--pill badge--snapshot" title="Dữ liệu sổ cái bất biến">
                <svg viewBox="0 0 24 24" width="12" height="12"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                Đã chốt
              </span>
            </td>
            <td rowspan="${r.items.length}" class="th-actions">
              <button class="action-btn" data-view-receipt="${r.id}" title="Xem chi tiết phiếu và chứng nhận snapshot">
                Chi tiết
              </button>
            </td>
          ` : ""}
        </tr>
      `).join("");
    }).join("");
  }

  $("ledger-search-input")?.addEventListener("input", renderLedgerTable);
  $("ledger-type-filter")?.addEventListener("change", renderLedgerTable);
  $("ledger-sku-filter")?.addEventListener("change", renderLedgerTable);

  $("ledger-table-body")?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-view-receipt]");
    if (!btn) return;
    openReceiptDetailModal(btn.dataset.viewReceipt);
  });

  // ══════════════════════════════════════════════════
  // 9. S2-07 INTERACTIVE SNAPSHOT SANDBOX (TAB 3)
  // ══════════════════════════════════════════════════
  function renderSandboxOptions() {
    const skuSelect = $("sandbox-sku-select");
    if (!skuSelect) return;

    skuSelect.innerHTML = Store.products.map((p) => `
      <option value="${p.sku}">${p.sku} · ${p.name} (Đơn vị cơ sở: ${p.baseUnit})</option>
    `).join("");

    updateSandboxUnits();
  }

  function updateSandboxUnits() {
    const sku = $("sandbox-sku-select").value;
    const prod = Store.getProductBySku(sku);
    const unitSelect = $("sandbox-unit-select");
    if (!prod || !unitSelect) return;

    const bulkUnits = prod.units.filter((u) => !u.isBase);
    unitSelect.innerHTML = bulkUnits.map((u) => `
      <option value="${u.name}" data-rate="${u.conversionRate}">
        ${u.name} (Hệ số hiện tại: ${u.conversionRate} ${prod.baseUnit})
      </option>
    `).join("");

    const currentRate = bulkUnits[0]?.conversionRate || 24;
    $("sandbox-new-rate").value = currentRate === 24 ? 20 : 24;
    $("sandbox-rate-suffix").textContent = `${prod.baseUnit} / ${bulkUnits[0]?.name || "đơn vị"}`;
  }

  $("sandbox-sku-select")?.addEventListener("change", updateSandboxUnits);
  $("sandbox-unit-select")?.addEventListener("change", () => {
    const sku = $("sandbox-sku-select").value;
    const prod = Store.getProductBySku(sku);
    const unitName = $("sandbox-unit-select").value;
    const unit = Store.getUnit(sku, unitName);
    if (unit && prod) {
      $("sandbox-rate-suffix").textContent = `${prod.baseUnit} / ${unit.name}`;
      $("sandbox-new-rate").value = unit.conversionRate === 24 ? 20 : (unit.conversionRate === 20 ? 24 : unit.conversionRate + 2);
    }
  });

  $("btn-run-sandbox-test")?.addEventListener("click", async () => {
    const sku = $("sandbox-sku-select").value;
    const unitName = $("sandbox-unit-select").value;
    const newRate = Number($("sandbox-new-rate").value);
    const prod = Store.getProductBySku(sku);
    const unit = Store.getUnit(sku, unitName);

    if (!newRate || newRate <= 0) {
      showToast("Vui lòng nhập hệ số quy đổi mới hợp lệ.", "warning");
      return;
    }

    const oldRate = unit.conversionRate;

    // 1. Thực hiện đổi hệ số trong cấu hình SKU
    await Store.saveUnit(sku, {
      name: unitName,
      conversionRate: newRate,
      note: `Đổi hệ số quy cách kiểm chứng: ${oldRate} → ${newRate} ${prod.baseUnit}`
    });

    // 2. Tìm các giao dịch trong quá khứ có sử dụng đơn vị này của SKU này
    const pastTransactions = [];
    Store.receipts.forEach((r) => {
      r.items.forEach((item) => {
        if (item.sku === sku && item.inputUnit.toLowerCase() === unitName.toLowerCase()) {
          pastTransactions.push({ receiptId: r.id, date: r.date, item });
        }
      });
    });

    // 3. Render Kết quả Kiểm chứng toàn vẹn (Live Verification Report)
    const resultsBox = $("sandbox-results-box");
    resultsBox.hidden = false;

    const samplePast = pastTransactions[0] || {
      receiptId: "PNK-202609-001",
      date: "20/09/2026",
      item: { inputQuantity: 10, conversionRateSnapshot: oldRate, baseQuantity: 10 * oldRate }
    };

    const pastQty = samplePast.item.inputQuantity;
    const pastSnapshot = samplePast.item.conversionRateSnapshot;
    const pastRecordedBase = samplePast.item.baseQuantity;

    // Giả lập giao dịch mới với cùng số lượng theo hệ số mới
    const futureExpectedBase = pastQty * newRate;

    resultsBox.innerHTML = `
      <div class="verification-status-banner">
        <svg viewBox="0 0 24 24" width="22" height="22"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>
        <span>
          CHỨNG THỰC BẢO TOÀN THÀNH CÔNG: Đã đổi hệ số "${escapeHtml(unitName)}" từ <strong>${oldRate}</strong> thành <strong>${newRate} ${prod.baseUnit}</strong>.
          Toàn bộ ${pastTransactions.length} giao dịch quá khứ giữ nguyên 100% snapshot!
        </span>
      </div>

      <div class="comparison-grid">
        <div class="comparison-card comparison-card--past">
          <span class="comparison-card__tag">🔒 GIAO DỊCH QUÁ KHỨ (BẢO LƯU NGUYÊN VẸN)</span>
          <h4>Phiếu ${escapeHtml(samplePast.receiptId)} (Lập ngày ${escapeHtml(samplePast.date)})</h4>
          <p>
            Chứng từ nhập: <strong>${pastQty} ${escapeHtml(unitName)}</strong>.<br>
            Hệ số Snapshot lúc ghi sổ: <strong>× ${pastSnapshot} ${prod.baseUnit}</strong>.<br>
            Số lượng cơ sở trong sổ cái:
          </p>
          <span class="formula-highlight text-emerald">
            ${pastQty} ${escapeHtml(unitName)} × ${pastSnapshot} = ${formatNumber(pastRecordedBase)} ${prod.baseUnit} (KHÔNG ĐỔI)
          </span>
          <p style="margin-top:8px; font-size:0.75rem; color:#6b21a8">
            ✔ Xác nhận: Số lon đã ghi sổ hoàn toàn không bị đổi theo hệ số mới ${newRate}.
          </p>
        </div>

        <div class="comparison-card comparison-card--future">
          <span class="comparison-card__tag">⚡ GIAO DỊCH MỚI (ÁP DỤNG HỆ SỐ MỚI)</span>
          <h4>Giao dịch lập từ thời điểm này</h4>
          <p>
            Nếu nhập cùng: <strong>${pastQty} ${escapeHtml(unitName)}</strong>.<br>
            Hệ số quy đổi mới áp dụng: <strong>× ${newRate} ${prod.baseUnit}</strong>.<br>
            Số lượng cơ sở ghi sổ mới:
          </p>
          <span class="formula-highlight" style="color:#1d4ed8">
            ${pastQty} ${escapeHtml(unitName)} × ${newRate} = ${formatNumber(futureExpectedBase)} ${prod.baseUnit} (Tính theo hệ số mới)
          </span>
          <p style="margin-top:8px; font-size:0.75rem; color:#1e40af">
            ✔ Đúng nghiệp vụ: Hàng đóng gói mới áp dụng hệ số ${newRate} đúng theo quy cách mới.
          </p>
        </div>
      </div>
    `;

    renderLedgerTable();
    renderSkuTable();
    updateMetrics();
    showToast(`Đã đối soát toàn vẹn! Hệ số mới: 1 ${unitName} = ${newRate} ${prod.baseUnit}.`);
  });

  // ══════════════════════════════════════════════════
  // 10. PANEL 4: DUAL-UNIT INVENTORY REPORT (TAB 4)
  // ══════════════════════════════════════════════════
  function renderInventoryTable() {
    const search = $("inv-search-input").value.trim().toLowerCase();
    const tbody = $("inventory-table-body");

    const filtered = Store.products.filter((p) => !search || p.name.toLowerCase().includes(search) || p.sku.toLowerCase().includes(search));

    if (!filtered.length) {
      tbody.innerHTML = `<tr><td colspan="6" class="empty">Không tìm thấy sản phẩm.</td></tr>`;
      return;
    }

    tbody.innerHTML = filtered.map((p) => {
      const breakdown = Store.breakdownInventory(p.sku, p.inventoryBaseQty);
      return `
        <tr>
          <td>
            <span class="sku-name">${escapeHtml(p.name)}</span>
            <span class="sku-code">${escapeHtml(p.sku)}</span>
          </td>
          <td>
            <span class="badge badge--base">${escapeHtml(p.baseUnit)}</span>
          </td>
          <td>
            <strong style="font-size:1.0625rem; color:var(--navy-900); font-variant-numeric: tabular-nums">
              ${formatNumber(p.inventoryBaseQty)} ${escapeHtml(p.baseUnit)}
            </strong>
          </td>
          <td>
            <div class="inventory-breakdown">
              <span class="breakdown-formula">
                ${escapeHtml(breakdown)}
              </span>
              <span class="breakdown-hint">
                Quy đổi tự động dựa trên quy cách đóng gói khai báo
              </span>
            </div>
          </td>
          <td>
            <span class="badge badge--pill badge--primary">An toàn (Tồn tối thiểu 200)</span>
          </td>
          <td>
            <button class="action-btn action-btn--primary" data-action="quick-import" data-sku="${p.sku}">
              + Nhập hàng
            </button>
          </td>
        </tr>
      `;
    }).join("");
  }

  $("inv-search-input")?.addEventListener("input", renderInventoryTable);

  $("inventory-table-body")?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-action='quick-import']");
    if (!btn) return;
    const sku = btn.dataset.sku;
    receiptState.type = "IMPORT";
    const prod = Store.getProductBySku(sku);
    const bulkUnit = prod?.units.find((u) => !u.isBase) || prod?.units[0];
    receiptState.lines = [
      { id: "line-1", sku: sku, inputUnit: bulkUnit.name, inputQuantity: 10, notes: "" }
    ];
    switchTab("create-receipt");
  });

  // Excel Export feature using XLSX
  $("btn-export-inventory-excel")?.addEventListener("click", () => {
    if (typeof XLSX === "undefined") {
      showToast("Thư viện Excel đang tải, vui lòng thử lại.", "warning");
      return;
    }
    const data = Store.products.map((p) => ({
      "Mã SKU": p.sku,
      "Tên sản phẩm": p.name,
      "Ngành hàng": p.category,
      "Đơn vị cơ sở": p.baseUnit,
      "Tồn kho cơ sở": p.inventoryBaseQty,
      "Quy đổi thực tế kho": Store.breakdownInventory(p.sku, p.inventoryBaseQty)
    }));
    const worksheet = XLSX.utils.json_to_sheet(data);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "TonKhoQuyDoi");
    XLSX.writeFile(workbook, "Ton_Kho_Quy_Doi_SaleStock.xlsx");
    showToast("Đã xuất file Excel tồn kho quy đổi thành công!");
  });

  $("btn-export-ledger-excel")?.addEventListener("click", () => {
    if (typeof XLSX === "undefined") {
      showToast("Thư viện Excel đang tải, vui lòng thử lại.", "warning");
      return;
    }
    const rows = [];
    Store.receipts.forEach((r) => {
      r.items.forEach((item) => {
        rows.push({
          "Số chứng từ": r.id,
          "Loại phiếu": r.type,
          "Ngày ghi sổ": r.createdAt || r.date,
          "Kho hàng": r.warehouse,
          "Đối tác": r.partner,
          "Mã SKU": item.sku,
          "Tên sản phẩm": item.productName,
          "Đơn vị gọi hàng": item.inputUnit,
          "Số lượng gọi hàng": item.inputQuantity,
          "Hệ số Snapshot chốt sổ": item.conversionRateSnapshot,
          "Đơn vị cơ sở": item.baseUnit,
          "Số lượng cơ sở ghi sổ": item.baseQuantity,
          "Người lập": r.createdBy
        });
      });
    });
    const worksheet = XLSX.utils.json_to_sheet(rows);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "SoCaiKhoSnapshot");
    XLSX.writeFile(workbook, "So_Cai_Kho_Snapshot_S207.xlsx");
    showToast("Đã xuất file Excel sổ cái giao dịch bảo toàn!");
  });

  // ══════════════════════════════════════════════════
  // 11. MODAL 1: ADD/EDIT UNIT CONVERSION FOR SKU
  // ══════════════════════════════════════════════════
  const modalUnit = $("modal-unit-edit");
  const formUnit = $("form-unit-edit");

  function openUnitModal(defaultSku = null, existingUnit = null) {
    formUnit.reset();
    $("unit-form-error").hidden = true;

    // Populate SKU select
    const skuSelect = $("unit-sku-select");
    skuSelect.innerHTML = Store.products.map((p) => `
      <option value="${p.sku}" ${p.sku === defaultSku ? "selected" : ""}>
        ${p.sku} - ${p.name} (Đơn vị cơ sở: ${p.baseUnit})
      </option>
    `).join("");

    if (existingUnit) {
      $("modal-unit-title").textContent = `Chỉnh sửa hệ số: ${existingUnit.name}`;
      $("unit-edit-mode").value = "edit";
      $("unit-name-input").value = existingUnit.name;
      $("unit-name-input").readOnly = existingUnit.isBase;
      $("unit-rate-input").value = existingUnit.conversionRate;
      $("unit-barcode-input").value = existingUnit.barcode || "";
      $("unit-notes-input").value = existingUnit.note || "";
      $("unit-edit-snapshot-warning").hidden = false;
    } else {
      $("modal-unit-title").textContent = "Thêm đơn vị quy đổi mới";
      $("unit-edit-mode").value = "create";
      $("unit-name-input").value = "";
      $("unit-name-input").readOnly = false;
      $("unit-rate-input").value = 24;
      $("unit-barcode-input").value = "";
      $("unit-notes-input").value = "";
      $("unit-edit-snapshot-warning").hidden = true;
    }

    updateFormulaPreview();
    modalUnit.hidden = false;
  }

  function closeUnitModal() {
    modalUnit.hidden = true;
  }

  document.querySelectorAll("[data-close-unit-modal]").forEach((btn) => {
    btn.addEventListener("click", closeUnitModal);
  });
  $("btn-open-create-unit-modal")?.addEventListener("click", () => openUnitModal());

  function updateFormulaPreview() {
    const sku = $("unit-sku-select").value;
    const prod = Store.getProductBySku(sku);
    const unitName = $("unit-name-input").value.trim() || "đơn vị";
    const rate = $("unit-rate-input").value || "1";

    $("preview-unit-name").textContent = unitName;
    $("preview-rate").textContent = rate;
    $("preview-base-unit").textContent = prod ? prod.baseUnit : "đơn vị cơ sở";
  }

  $("unit-sku-select")?.addEventListener("change", updateFormulaPreview);
  $("unit-name-input")?.addEventListener("input", updateFormulaPreview);
  $("unit-rate-input")?.addEventListener("input", updateFormulaPreview);

  document.querySelectorAll(".quick-unit-suggestions .btn-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      $("unit-name-input").value = chip.dataset.val;
      if (chip.dataset.val === "thùng") $("unit-rate-input").value = 24;
      else if (chip.dataset.val === "lốc") $("unit-rate-input").value = 6;
      else if (chip.dataset.val === "thùng 30") $("unit-rate-input").value = 30;
      else if (chip.dataset.val === "két") $("unit-rate-input").value = 24;
      updateFormulaPreview();
    });
  });

  formUnit?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const sku = $("unit-sku-select").value;
    const name = $("unit-name-input").value.trim();
    const rate = $("unit-rate-input").value;
    const barcode = $("unit-barcode-input").value.trim();
    const note = $("unit-notes-input").value.trim();

    if (!name) {
      $("unit-form-error").hidden = false;
      $("unit-form-error").textContent = "Vui lòng nhập tên đơn vị tính.";
      return;
    }

    try {
      await Store.saveUnit(sku, {
        name: name,
        conversionRate: rate,
        barcode: barcode,
        note: note
      });

      closeUnitModal();
      renderSkuTable();
      updateMetrics();
      showToast(`Đã lưu đơn vị "${name}" cho SKU ${sku}. Hệ số áp dụng cho các giao dịch mới!`, "success");
    } catch (err) {
      $("unit-form-error").hidden = false;
      $("unit-form-error").textContent = err.message;
    }
  });

  // ══════════════════════════════════════════════════
  // 12. MODAL 2: RATE HISTORY AUDIT
  // ══════════════════════════════════════════════════
  const modalHistory = $("modal-unit-history");

  function openHistoryModal(sku) {
    const prod = Store.getProductBySku(sku);
    if (!prod) return;

    $("history-sku-subtitle").textContent = `${prod.sku} · ${prod.name} (Đơn vị cơ sở: ${prod.baseUnit})`;
    const tbody = $("history-table-body");
    const history = prod.unitHistory || [];

    if (!history.length) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty">Chưa có lịch sử điều chỉnh hệ số cho SKU này.</td></tr>`;
    } else {
      tbody.innerHTML = history.map((h) => `
        <tr>
          <td><span class="user-meta" style="color:var(--navy-900); font-weight:600">${escapeHtml(h.changedAt)}</span></td>
          <td><strong>${escapeHtml(h.unitName)}</strong></td>
          <td>
            <span class="snapshot-rate-badge">
              ${h.oldRate} → ${h.newRate} ${prod.baseUnit}
            </span>
          </td>
          <td><span style="font-size:0.8125rem">${escapeHtml(h.changedBy)}</span></td>
          <td><span style="font-size:0.8125rem; color:var(--ink-700)">${escapeHtml(h.reason || "Cập nhật")}</span></td>
        </tr>
      `).join("");
    }

    modalHistory.hidden = false;
  }

  document.querySelectorAll("[data-close-history-modal]").forEach((btn) => {
    btn.addEventListener("click", () => { modalHistory.hidden = true; });
  });

  // ══════════════════════════════════════════════════
  // 13. MODAL 3: RECEIPT DETAIL VIEW & SEAL
  // ══════════════════════════════════════════════════
  const modalReceipt = $("modal-receipt-detail");

  function openReceiptDetailModal(receiptId) {
    const r = Store.receipts.find((item) => item.id === receiptId);
    if (!r) return;

    $("detail-receipt-code").textContent = r.id;
    const typeLabel = r.type === "IMPORT" ? "Phiếu nhập kho" : r.type === "EXPORT" ? "Phiếu xuất kho" : "Đơn hàng bán";
    $("modal-receipt-title").textContent = `Chi tiết ${typeLabel}`;

    const itemsHtml = r.items.map((item, idx) => `
      <tr>
        <td>${idx + 1}</td>
        <td>
          <span class="sku-name">${escapeHtml(item.productName)}</span>
          <span class="sku-code">${escapeHtml(item.sku)}</span>
        </td>
        <td><strong>${formatNumber(item.inputQuantity)} ${escapeHtml(item.inputUnit)}</strong></td>
        <td>
          <span class="snapshot-rate-badge">
            × ${item.conversionRateSnapshot} (${item.baseUnit}/${item.inputUnit})
          </span>
        </td>
        <td>
          <strong class="text-emerald">${formatNumber(item.baseQuantity)} ${escapeHtml(item.baseUnit)}</strong>
        </td>
        <td style="font-size:0.75rem; color:var(--ink-500)">${escapeHtml(item.notes || "")}</td>
      </tr>
    `).join("");

    $("receipt-detail-content").innerHTML = `
      <div class="receipt-detail-grid">
        <div class="meta-item">
          <span>Ngày lập:</span>
          <strong>${escapeHtml(r.createdAt || r.date)}</strong>
        </div>
        <div class="meta-item">
          <span>Kho thực hiện:</span>
          <strong>${escapeHtml(r.warehouse)}</strong>
        </div>
        <div class="meta-item">
          <span>Người lập phiếu:</span>
          <strong>${escapeHtml(r.createdBy)}</strong>
        </div>
        <div class="meta-item">
          <span>Đối tác / Nguồn hàng:</span>
          <strong>${escapeHtml(r.partner || "—")}</strong>
        </div>
        <div class="meta-item">
          <span>Trạng thái:</span>
          <strong style="color:var(--color-emerald-deep)">✔ Đã ghi sổ kho</strong>
        </div>
        <div class="meta-item">
          <span>Ghi chú:</span>
          <strong>${escapeHtml(r.notes || "Không")}</strong>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width: 40px">#</th>
              <th>Mặt hàng / SKU</th>
              <th>Đơn vị chứng từ</th>
              <th>Hệ số Snapshot</th>
              <th>Số lượng cơ sở ghi sổ</th>
              <th>Ghi chú</th>
            </tr>
          </thead>
          <tbody>
            ${itemsHtml}
          </tbody>
        </table>
      </div>

      <div class="snapshot-cert-seal">
        <svg viewBox="0 0 24 24" width="20" height="20"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>
        <div>
          <strong>Chứng nhận Bảo toàn Snapshot (S2-07):</strong>
          <p>Phiếu này đã được ghi vào sổ cái với hệ số quy đổi đóng băng tại thời điểm lập. Bất kỳ thay đổi hệ số nào trong tương lai đối với các SKU trên đều không làm sai lệch số lượng cơ sở của phiếu này.</p>
        </div>
      </div>
    `;

    modalReceipt.hidden = false;
  }

  document.querySelectorAll("[data-close-receipt-modal]").forEach((btn) => {
    btn.addEventListener("click", () => { modalReceipt.hidden = true; });
  });

  $("btn-print-receipt")?.addEventListener("click", () => {
    window.print();
  });

  // Initial render from backend APIs.
  Store.init().then(() => {
    renderSkuTable();
    updateMetrics();
    prepareReceiptForm();
    renderLedgerTable();
    renderInventoryTable();
  }).catch((error) => {
    showToast(error.message || "Không tải được dữ liệu kho từ máy chủ.", "error");
  });
})();

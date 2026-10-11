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

  const user = Auth.getUser() || {};
  const canManageUnits = Auth.hasAnyRole("ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER", "ROLE_WAREHOUSE_STAFF", "ROLE_SALES_MANAGER");
  const canRecordVouchers = Auth.hasAnyRole("ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER", "ROLE_WAREHOUSE_STAFF");
  const $ = (id) => document.getElementById(id);
  const escapeHtml = (v) => String(v ?? "").replace(/[&<>'"]/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[c]);
  const formatNumber = (num) => new Intl.NumberFormat("vi-VN").format(Number(num) || 0);
  const statusLabel = (status) => ({
    RECORDED: "Đã ghi sổ", PENDING_APPROVAL: "Chờ duyệt",
    CANCELLED: "Đã hủy", REJECTED: "Đã từ chối",
  })[status] || status || "—";

  // ══════════════════════════════════════════════════
  // Data from the API
  // ══════════════════════════════════════════════════
  const Store = {
    products: [],
    receipts: [],
    loaded: false,

    async request(path, options) {
      const response = await Auth.apiFetch(path, options);
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success) {
        throw new Error(result.message || "Không thể tải dữ liệu từ máy chủ.");
      }
      return result.data;
    },

    async init() {
      this.loaded = false;
      const warehouses = await this.request("/warehouse/options");
      const currentWarehouse = $("active-warehouse-select")?.value;
      const preferredWarehouse = warehouses.find(warehouse => String(warehouse.id) === currentWarehouse)
        || warehouses.find(warehouse => user.warehouses?.some(scope => scope.id === warehouse.id))
        || warehouses[0];
      if (!preferredWarehouse) throw new Error("Chưa có kho đang hoạt động. Vui lòng liên hệ quản trị viên.");
      for (const id of ["active-warehouse-select", "receipt-warehouse"]) {
        const select = $(id);
        if (select) {
          select.innerHTML = warehouses.map(warehouse =>
            `<option value="${warehouse.id}">${escapeHtml(warehouse.name)}</option>`).join("");
          select.value = String(preferredWarehouse.id);
        }
      }
      const [products, inventory, vouchers, orders] = await Promise.all([
        this.request("/products"),
        this.request(`/warehouse/inventory?warehouseId=${preferredWarehouse.id}`),
        this.request(`/warehouse/vouchers?warehouseId=${preferredWarehouse.id}`),
        this.request("/orders"),
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

      for (const [id, field, label] of [
        ["sku-category-filter", "category", "Tất cả nhóm hàng"],
        ["sku-baseunit-filter", "baseUnit", "Tất cả đơn vị cơ sở"],
      ]) {
        const select = $(id);
        if (select) {
          const current = select.value;
          const values = [...new Set(this.products.map(product => product[field]).filter(Boolean))].sort();
          select.innerHTML = `<option value="">${label}</option>` + values.map(value =>
            `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`).join("");
          if (values.includes(current)) select.value = current;
        }
      }
      this.loaded = true;
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
  const initialTabParam = new URLSearchParams(window.location.search).get("tab");
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
  $("btn-avatar")?.addEventListener("click", () => {
    window.location.href = "/profile";
  });

  const roleNames = (user.roles || []).map(MenuConfig.getRoleDisplayName);
  const scope = [...(user.warehouses || []), ...(user.territories || [])].map((item) => item.name);
  $("user-role").textContent = [roleNames.join(", "), scope.join(", ")].filter(Boolean).join(" · ") || "—";

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
    if (tabId === "ledger-audit") renderLedgerTable();
    if (tabId === "inventory-report") renderInventoryTable();
    const tabParam = {
      "sku-units": "units",
      "create-receipt": "receipts",
      "ledger-audit": "ledger",
      "inventory-report": "inventory",
    }[tabId];
    if (tabParam) {
      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.set("tab", tabParam);
      window.history.replaceState(null, "", currentUrl);
    }
    AppNavigation.refresh();
    updateMetrics();
  }

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => switchTab(tab.dataset.tab));
  });

  $("btn-quick-new-receipt").hidden = !canRecordVouchers;
  $("tab-create-receipt").hidden = !canRecordVouchers;
  $("btn-open-create-unit-modal").hidden = !canManageUnits;

  $("btn-quick-new-receipt")?.addEventListener("click", () => switchTab("create-receipt"));

  // ══════════════════════════════════════════════════
  // 5. UPDATE METRICS STRIP
  // ══════════════════════════════════════════════════
  function updateMetrics() {
    if (!Store.loaded) return;
    $("metric-sku-count").textContent = Store.products.length;
    const totalUnits = Store.products.reduce((acc, p) => acc + p.units.filter((unit) => !unit.isBase).length, 0);
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
            </div>
          </td>
        </tr>
      `;
    }).join("");
    tbody.querySelectorAll("[data-action]").forEach(button => { button.hidden = !canManageUnits; });
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

      showToast(`Đã ghi sổ phiếu ${receiptRecord.id}.`, "success");

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
              <span class="badge badge--pill">${escapeHtml(statusLabel(r.status))}</span>
            </td>
            <td rowspan="${r.items.length}" class="th-actions">
              <button class="action-btn" data-view-receipt="${r.id}" title="Xem chi tiết phiếu">
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
  // Inventory report
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
    tbody.querySelectorAll("[data-action=quick-import]").forEach(button => { button.hidden = !canRecordVouchers; });
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
          "Hệ số ghi sổ": item.conversionRateSnapshot,
          "Đơn vị cơ sở": item.baseUnit,
          "Số lượng cơ sở ghi sổ": item.baseQuantity,
          "Người lập": r.createdBy
        });
      });
    });
    const worksheet = XLSX.utils.json_to_sheet(rows);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "SoCaiKho");
    XLSX.writeFile(workbook, "so-cai-kho.xlsx");
    showToast("Đã xuất sổ cái kho.");
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
          <strong>${escapeHtml(statusLabel(r.status))}</strong>
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
              <th>Hệ số ghi sổ</th>
              <th>Số lượng cơ sở ghi sổ</th>
              <th>Ghi chú</th>
            </tr>
          </thead>
          <tbody>
            ${itemsHtml}
          </tbody>
        </table>
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

  const requestedTab = {
    receipts: "create-receipt", ledger: "ledger-audit", inventory: "inventory-report",
  }[initialTabParam] || "sku-units";
  switchTab(requestedTab);

  $("active-warehouse-select").addEventListener("change", async () => {
    try {
      await Store.init();
      switchTab(document.querySelector(".nav-tab--active")?.dataset.tab || requestedTab);
    } catch (error) {
      showToast(error.message || "Không tải được dữ liệu kho.", "error");
    }
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

/**
 * product-units.js — S2-07: Quản lý đơn vị tính & hệ số quy đổi theo SKU.
 *
 * Luồng:
 *  - Mỗi sản phẩm có 1 đơn vị cơ sở (base unit) và 0..n đơn vị quy đổi.
 *  - Khi nhập/xuất kho, nhân viên chọn đơn vị kho → hệ thống tự tính số lượng
 *    theo đơn vị cơ sở trước khi ghi sổ.
 *  - Đổi hệ số chỉ áp dụng cho giao dịch tương lai; lịch sử không thay đổi.
 *
 * API (backend, chưa implement — dùng mock):
 *  GET    /products/units?page=&size=&search=&status=
 *  GET    /products/{id}/units
 *  POST   /products/units              — tạo sản phẩm + khai báo units
 *  PUT    /products/{id}/units         — cập nhật units
 *  DELETE /products/{id}               — xoá sản phẩm (chỉ khi chưa có giao dịch)
 */
(() => {
  /* ── Auth guard ── */
  if (!Auth.requireLogin()) return;
  if (
    !Auth.hasAnyRole(
      "ROLE_ADMIN",
      "ROLE_WAREHOUSE_MANAGER",
      "ROLE_WAREHOUSE_STAFF",
    )
  ) {
    location.href = "error.html?status=403";
    return;
  }

  const canEdit = Auth.hasAnyRole(
    "ROLE_ADMIN",
    "ROLE_WAREHOUSE_MANAGER",
    "ROLE_WAREHOUSE_STAFF",
  );

  /* ── DOM helpers ── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? "").replace(
      /[&<>'"]/g,
      (c) =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[c],
    );

  /* ── State ── */
  const state = {
    page: 0,
    totalPages: 1,
    totalElements: 0,
    products: [],
    editingId: null,
  };

  /* ─────────────────────────────────────────
     MOCK DATA (replace with Auth.apiFetch)
     ───────────────────────────────────────── */
  const _mockProducts = [
    {
      id: 1,
      name: "Bia Sài Gòn Special 330ml",
      sku: "BSG-330",
      status: "active",
      baseUnit: { id: 10, name: "lon" },
      derivedUnits: [
        { id: 11, name: "lốc", factor: 6 },
        { id: 12, name: "thùng", factor: 24 },
      ],
    },
    {
      id: 2,
      name: "Nước suối Lavie 500ml",
      sku: "LAV-500",
      status: "active",
      baseUnit: { id: 20, name: "chai" },
      derivedUnits: [
        { id: 21, name: "thùng", factor: 24 },
      ],
    },
    {
      id: 3,
      name: "Coca-Cola lon 330ml",
      sku: "CC-330",
      status: "active",
      baseUnit: { id: 30, name: "lon" },
      derivedUnits: [
        { id: 31, name: "lốc", factor: 6 },
        { id: 32, name: "thùng", factor: 24 },
        { id: 33, name: "két", factor: 48 },
      ],
    },
    {
      id: 4,
      name: "Mì Hảo Hảo tôm chua cay",
      sku: "HH-TOMCC",
      status: "active",
      baseUnit: { id: 40, name: "gói" },
      derivedUnits: [
        { id: 41, name: "thùng", factor: 30 },
      ],
    },
    {
      id: 5,
      name: "Dầu ăn Neptune 1L",
      sku: "NEP-1L",
      status: "inactive",
      baseUnit: { id: 50, name: "chai" },
      derivedUnits: [
        { id: 51, name: "thùng", factor: 12 },
      ],
    },
  ];

  async function apiFetchProducts({ page, search, status }) {
    // TODO: replace with real API call
    // const params = new URLSearchParams({ page, size: 10, search, status });
    // const res = await Auth.apiFetch(`/products/units?${params}`);
    // return res.json();
    await new Promise((r) => setTimeout(r, 420)); // simulate latency
    let data = _mockProducts.filter((p) => {
      const q = search.toLowerCase();
      const matchSearch =
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        p.baseUnit.name.toLowerCase().includes(q) ||
        p.derivedUnits.some((u) => u.name.toLowerCase().includes(q));
      const matchStatus = !status || p.status === status;
      return matchSearch && matchStatus;
    });
    const size = 10;
    const totalElements = data.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / size));
    const safePage = Math.min(page, totalPages - 1);
    const content = data.slice(safePage * size, safePage * size + size);
    return { success: true, data: { content, totalElements, totalPages, number: safePage } };
  }

  async function apiSaveProduct(id, payload) {
    await new Promise((r) => setTimeout(r, 520));
    if (id) {
      const idx = _mockProducts.findIndex((p) => p.id === id);
      if (idx !== -1) {
        _mockProducts[idx] = { ..._mockProducts[idx], ...payload, id };
      }
    } else {
      const newId = Date.now();
      _mockProducts.unshift({
        id: newId,
        status: "active",
        ...payload,
        derivedUnits: payload.derivedUnits.map((u, i) => ({
          id: newId * 100 + i,
          ...u,
        })),
        baseUnit: { id: newId * 100 + 99, name: payload.baseUnit.name },
      });
    }
    return { success: true };
  }

  async function apiDeleteProduct(id) {
    await new Promise((r) => setTimeout(r, 380));
    const idx = _mockProducts.findIndex((p) => p.id === id);
    if (idx !== -1) _mockProducts.splice(idx, 1);
    return { success: true };
  }

  /* ─────────────────────────────────────────
     LOAD + RENDER products
     ───────────────────────────────────────── */
  async function loadProducts() {
    $("pu-list").innerHTML = skeletonHTML();
    $("product-count").textContent = "Đang tải…";

    const search = $("search-input").value.trim();
    const status = $("filter-status").value;

    try {
      const result = await apiFetchProducts({
        page: state.page,
        search,
        status,
      });
      if (!result.success) throw new Error(result.message || "Lỗi tải dữ liệu");

      const page = result.data;
      state.products = page.content;
      state.totalPages = page.totalPages;
      state.totalElements = page.totalElements;
      state.page = page.number;

      renderProducts();
      renderPagination();
    } catch (err) {
      $("pu-list").innerHTML = `<div class="pu-empty">
        <svg viewBox="0 0 24 24" width="48" height="48"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <p>${escHtml(err.message || "Không tải được dữ liệu")}</p>
      </div>`;
      $("product-count").textContent = "";
    }
  }

  function skeletonHTML() {
    return `<div class="pu-skeleton" aria-hidden="true">
      <div class="pu-skeleton__card"></div>
      <div class="pu-skeleton__card"></div>
      <div class="pu-skeleton__card"></div>
    </div>`;
  }

  function renderProducts() {
    $("product-count").textContent =
      state.totalElements === 0
        ? "Không có sản phẩm"
        : `${state.totalElements} sản phẩm`;

    if (!state.products.length) {
      $("pu-list").innerHTML = `<div class="pu-empty">
        <svg viewBox="0 0 24 24" width="48" height="48">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
          <line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
        <p>Chưa có sản phẩm nào. Thêm sản phẩm để khai báo đơn vị tính.</p>
        ${canEdit ? `<button class="btn btn--primary" id="btn-empty-add" type="button">Thêm sản phẩm</button>` : ""}
      </div>`;
      document
        .getElementById("btn-empty-add")
        ?.addEventListener("click", () => openModal());
      return;
    }

    $("pu-list").innerHTML = state.products
      .map((p) => productCardHTML(p))
      .join("");
  }

  function productCardHTML(p) {
    const baseTag = `<span class="pu-unit-tag pu-unit-tag--base">
      ${escHtml(p.baseUnit.name)}
      <span class="pu-unit-tag__factor">cơ sở</span>
    </span>`;

    const derivedTags = p.derivedUnits
      .map(
        (u) => `<span class="pu-unit-divider">›</span>
      <span class="pu-unit-tag">
        ${escHtml(u.name)}
        <span class="pu-unit-tag__factor">= ${u.factor} ${escHtml(p.baseUnit.name)}</span>
      </span>`,
      )
      .join("");

    const statusBadge =
      p.status === "inactive"
        ? `<span class="pu-unit-tag" style="background:var(--amber-soft);color:#735019;border-color:rgba(230,162,60,.2)">Ngừng dùng</span>`
        : "";

    const actions = canEdit
      ? `<div class="pu-card__actions">
          <button class="pu-action-btn" data-edit="${p.id}" title="Chỉnh sửa đơn vị">Sửa</button>
          <button class="pu-action-btn pu-action-btn--danger" data-delete="${p.id}" title="Xoá sản phẩm">Xoá</button>
        </div>`
      : "";

    return `<div class="pu-card" role="listitem">
      <div class="pu-card__main">
        <div class="pu-card__name">${escHtml(p.name)} ${statusBadge}</div>
        <div class="pu-card__sku">${escHtml(p.sku)}</div>
        <div class="pu-units">
          ${baseTag}${derivedTags}
        </div>
      </div>
      ${actions}
    </div>`;
  }

  function renderPagination() {
    $("page-summary").textContent =
      state.totalPages > 1
        ? `Trang ${state.page + 1} / ${state.totalPages}`
        : "";
    $("prev-page").disabled = state.page === 0;
    $("next-page").disabled = state.page >= state.totalPages - 1;
  }

  /* ─────────────────────────────────────────
     MODAL: open / close / derived unit rows
     ───────────────────────────────────────── */

  function openModal(product = null) {
    state.editingId = product?.id ?? null;

    $("form-units").reset();
    $("form-message").hidden = true;
    $("form-message").className = "form-message";
    $("derived-units-list").innerHTML = "";

    if (product) {
      $("modal-product-title").textContent = "Chỉnh sửa đơn vị tính";
      $("modal-product-sku").textContent = product.sku;
      $("product-id").value = product.id;
      $("product-name").value = product.name;
      $("product-sku").value = product.sku;
      $("base-unit-name").value = product.baseUnit.name;
      // Render existing derived units
      product.derivedUnits.forEach((u) => addDerivedRow(u));
      // If product has transactions, lock base unit
      $("base-unit-name").readOnly = false; // TODO: lock if transactions exist
    } else {
      $("modal-product-title").textContent = "Khai báo đơn vị tính";
      $("modal-product-sku").textContent = "";
      $("product-id").value = "";
    }

    $("modal-product").hidden = false;
    $("product-name").focus();
  }

  function closeModal() {
    $("modal-product").hidden = true;
  }

  function addDerivedRow(unit = {}) {
    const rowId = `du-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    const row = document.createElement("div");
    row.className = "derived-unit-row";
    row.dataset.rowId = rowId;

    row.innerHTML = `
      <div class="form-field">
        <label for="${rowId}-name">Tên đơn vị</label>
        <input
          id="${rowId}-name"
          class="du-name"
          type="text"
          required
          maxlength="30"
          placeholder="VD: thùng"
          value="${escHtml(unit.name ?? "")}"
        />
      </div>
      <div class="form-field">
        <label for="${rowId}-factor">Hệ số quy đổi</label>
        <input
          id="${rowId}-factor"
          class="du-factor"
          type="number"
          required
          min="2"
          max="99999"
          step="1"
          placeholder="VD: 24"
          value="${unit.factor ?? ""}"
        />
      </div>
      <button type="button" class="derived-unit-remove" title="Xoá đơn vị này" aria-label="Xoá đơn vị này">
        <svg viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/></svg>
      </button>
      <div class="derived-unit-row__hint">
        1 <strong class="du-hint-name">${escHtml(unit.name ?? "đơn vị")}</strong>
        =
        <strong class="du-hint-factor">${unit.factor ?? "?"}</strong>
        <span class="du-hint-base">${escHtml($("base-unit-name").value || "đơn vị cơ sở")}</span>
      </div>
    `;

    // Live hint update
    const nameInput = row.querySelector(".du-name");
    const factorInput = row.querySelector(".du-factor");
    const hintName = row.querySelector(".du-hint-name");
    const hintFactor = row.querySelector(".du-hint-factor");
    const hintBase = row.querySelector(".du-hint-base");

    function updateHint() {
      hintName.textContent = nameInput.value || "đơn vị";
      hintFactor.textContent = factorInput.value || "?";
      hintBase.textContent =
        $("base-unit-name").value || "đơn vị cơ sở";
    }
    nameInput.addEventListener("input", updateHint);
    factorInput.addEventListener("input", updateHint);
    $("base-unit-name").addEventListener("input", updateHint);

    // Remove row
    row.querySelector(".derived-unit-remove").addEventListener("click", () => {
      if ($("derived-units-list").children.length === 1) {
        showFormMsg("Cần ít nhất một đơn vị quy đổi, hoặc xoá tất cả nếu chỉ dùng đơn vị cơ sở.", "error");
        return;
      }
      row.remove();
    });

    $("derived-units-list").appendChild(row);
    nameInput.focus();
  }

  /* ── Collect form data ── */
  function collectFormData() {
    const name = $("product-name").value.trim();
    const sku = $("product-sku").value.trim();
    const baseUnitName = $("base-unit-name").value.trim();

    const derivedRows = [
      ...$("derived-units-list").querySelectorAll(".derived-unit-row"),
    ];
    const derivedUnits = [];

    for (const row of derivedRows) {
      const duName = row.querySelector(".du-name").value.trim();
      const duFactor = parseInt(row.querySelector(".du-factor").value, 10);

      if (!duName) {
        throw new Error("Tên đơn vị quy đổi không được để trống.");
      }
      if (!duFactor || duFactor < 2) {
        throw new Error(
          `Hệ số quy đổi của "${duName}" phải là số nguyên ≥ 2.`,
        );
      }
      derivedUnits.push({ name: duName, factor: duFactor });
    }

    // Check duplicate unit names
    const allNames = [baseUnitName, ...derivedUnits.map((u) => u.name)];
    const dupes = allNames.filter((n, i) => allNames.indexOf(n) !== i);
    if (dupes.length) {
      throw new Error(
        `Tên đơn vị bị trùng: "${dupes[0]}". Mỗi đơn vị phải có tên riêng.`,
      );
    }

    return {
      name,
      sku,
      baseUnit: { name: baseUnitName },
      derivedUnits,
    };
  }

  /* ── Show form message ── */
  function showFormMsg(text, type = "error") {
    const el = $("form-message");
    el.hidden = false;
    el.textContent = text;
    el.className = `form-message form-message--${type}`;
  }

  /* ── Form submit ── */
  $("form-units").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("form-message").hidden = true;

    let payload;
    try {
      payload = collectFormData();
    } catch (err) {
      showFormMsg(err.message);
      return;
    }

    const btn = $("btn-save");
    btn.disabled = true;
    btn.textContent = "Đang lưu…";

    try {
      const result = await apiSaveProduct(state.editingId, payload);
      if (!result.success) throw new Error(result.message || "Không lưu được");

      closeModal();
      showToast(
        state.editingId
          ? "Đã cập nhật đơn vị tính thành công."
          : "Đã khai báo đơn vị tính thành công.",
        "success",
      );
      state.page = 0;
      await loadProducts();
    } catch (err) {
      showFormMsg(err.message || "Có lỗi xảy ra, vui lòng thử lại.");
    } finally {
      btn.disabled = false;
      btn.textContent = "Lưu khai báo";
    }
  });

  /* ─────────────────────────────────────────
     CONFIRM MODAL
     ───────────────────────────────────────── */
  let _confirmResolve = null;

  function openConfirm(message) {
    return new Promise((resolve) => {
      _confirmResolve = resolve;
      $("confirm-message").textContent = message;
      $("modal-confirm").hidden = false;
    });
  }

  function closeConfirm(result) {
    $("modal-confirm").hidden = true;
    _confirmResolve?.(result);
    _confirmResolve = null;
  }

  $("confirm-ok").addEventListener("click", () => closeConfirm(true));
  $("confirm-cancel").addEventListener("click", () => closeConfirm(false));
  $("confirm-close").addEventListener("click", () => closeConfirm(false));

  /* ─────────────────────────────────────────
     EVENT WIRING
     ───────────────────────────────────────── */

  // Add product button
  $("btn-add-product").addEventListener("click", () => openModal());

  // Modal close
  $("modal-close").addEventListener("click", closeModal);
  $("btn-cancel").addEventListener("click", closeModal);

  // Add derived unit row
  $("btn-add-unit").addEventListener("click", () => addDerivedRow());

  // Overlay click to close
  $("modal-product").addEventListener("click", (e) => {
    if (e.target === $("modal-product")) closeModal();
  });
  $("modal-confirm").addEventListener("click", (e) => {
    if (e.target === $("modal-confirm")) closeConfirm(false);
  });

  // Search & filter
  let searchDebounce = null;
  $("search-input").addEventListener("input", () => {
    clearTimeout(searchDebounce);
    searchDebounce = setTimeout(() => {
      state.page = 0;
      loadProducts();
    }, 300);
  });
  $("filter-status").addEventListener("change", () => {
    state.page = 0;
    loadProducts();
  });

  // Pagination
  $("prev-page").addEventListener("click", () => {
    if (state.page > 0) {
      state.page--;
      loadProducts();
    }
  });
  $("next-page").addEventListener("click", () => {
    if (state.page < state.totalPages - 1) {
      state.page++;
      loadProducts();
    }
  });

  // Delegated: edit / delete buttons inside list
  $("pu-list").addEventListener("click", async (e) => {
    const editBtn = e.target.closest("[data-edit]");
    const deleteBtn = e.target.closest("[data-delete]");

    if (editBtn) {
      const product = state.products.find(
        (p) => String(p.id) === editBtn.dataset.edit,
      );
      if (product) openModal(product);
      return;
    }

    if (deleteBtn && canEdit) {
      const product = state.products.find(
        (p) => String(p.id) === deleteBtn.dataset.delete,
      );
      if (!product) return;

      const confirmed = await openConfirm(
        `Xoá sản phẩm "${product.name}" và toàn bộ đơn vị tính? Chỉ được xoá nếu chưa có giao dịch liên quan.`,
      );
      if (!confirmed) return;

      try {
        const result = await apiDeleteProduct(product.id);
        if (!result.success) throw new Error(result.message);
        showToast("Đã xoá sản phẩm.", "success");
        if (state.products.length === 1 && state.page > 0) state.page--;
        await loadProducts();
      } catch (err) {
        showToast(err.message || "Không xoá được sản phẩm.", "error");
      }
    }
  });

  /* ─────────────────────────────────────────
     TOAST
     ───────────────────────────────────────── */
  function showToast(message, type = "success") {
    let container = document.getElementById("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }

    const iconSvg =
      type === "success"
        ? `<svg viewBox="0 0 24 24" width="18" height="18"><polyline points="20 6 9 17 4 12"/></svg>`
        : `<svg viewBox="0 0 24 24" width="18" height="18"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;

    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__icon">${iconSvg}</span><span>${escHtml(message)}</span>`;
    container.appendChild(toast);

    requestAnimationFrame(() => {
      requestAnimationFrame(() => toast.classList.add("toast--visible"));
    });

    setTimeout(() => {
      toast.classList.add("toast--exit");
      toast.addEventListener("animationend", () => toast.remove(), {
        once: true,
      });
    }, 3500);
  }

  /* ─────────────────────────────────────────
     INIT
     ───────────────────────────────────────── */
  loadProducts();
})();

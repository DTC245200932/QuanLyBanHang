/**
 * products-import.js — Nhập danh mục sản phẩm hàng loạt từ Excel (S2-08).
 *
 * Yêu cầu:
 *  - SheetJS (xlsx.full.min.js) được tải trước script này.
 *  - Auth helper (auth.js) đã sẵn sàng.
 *  - Chỉ hiển thị nút nhập Excel cho ROLE_SALES_MANAGER (xử lý bởi products.js).
 *
 * Luồng:
 *  1. Mở modal → dropzone / tải tệp mẫu
 *  2. Người dùng chọn .xlsx → đọc & kiểm tra → hiện bản xem trước có badge
 *     (Tạo mới / Cập nhật / Lỗi) theo từng dòng
 *  3. Bấm "Nhập dữ liệu" → gọi API theo batch → progress bar
 *  4. Hoàn tất → hiện tóm tắt → reload danh mục
 */
(() => {
  "use strict";

  /* ── Helpers ── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? "").replace(/[&<>'"]/g, (c) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "'": "&#39;",
      '"': "&quot;",
    }[c]));

  /* Template columns (order must match TEMPLATE_HEADERS) */
  const COL = {
    SKU: "Mã SKU",
    NAME: "Tên sản phẩm",
    CATEGORY: "Nhóm hàng",
    UNIT: "Đơn vị tính",
    PACKING: "Quy cách",
    PRICE: "Giá bán",
    COST: "Giá vốn",
    DESCRIPTION: "Mô tả",
    ACTIVE: "Trạng thái",
  };

  const TEMPLATE_HEADERS = [
    COL.SKU,
    COL.NAME,
    COL.CATEGORY,
    COL.UNIT,
    COL.PACKING,
    COL.PRICE,
    COL.COST,
    COL.DESCRIPTION,
    COL.ACTIVE,
  ];

  const MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB
  const BATCH_SIZE = 20; // rows sent per concurrent batch

  /* ── State ── */
  const state = {
    /** @type {import('./products').Product[]} */
    existingProducts: [],
    /** @type {import('./products').Category[]} */
    categories: [],
    /** @type {ParsedRow[]} */
    rows: [],
    importing: false,
  };

  /* ── Wait for products.js to expose its catalog ── */
  function getProductsCatalog() {
    // products.js exposes window.__productsCatalog after loadCatalog resolves
    return window.__productsCatalog || { products: [], categories: [] };
  }

  /* ── Lifecycle: steps ── */
  const STEPS = ["upload", "preview", "progress", "done"];

  function showStep(name) {
    STEPS.forEach((s) => {
      const el = $(`import-step-${s}`);
      if (el) el.hidden = s !== name;
    });
  }

  /* ── Open / close ── */
  function openModal() {
    const catalog = getProductsCatalog();
    state.existingProducts = catalog.products || [];
    state.categories = catalog.categories || [];
    state.rows = [];
    state.importing = false;
    showStep("upload");
    resetUploadStep();
    updateActionButtons("upload");
    $("import-excel-modal").hidden = false;
    $("import-file-input").focus();
  }

  function closeModal() {
    if (state.importing) return; // block close during import
    $("import-excel-modal").hidden = true;
    resetUploadStep();
  }

  function resetUploadStep() {
    const input = $("import-file-input");
    if (input) input.value = "";
    const dropzone = $("import-dropzone");
    if (dropzone) dropzone.classList.remove("import-dropzone--dragover");
  }

  /* ── Action button state ── */
  function updateActionButtons(step) {
    const startBtn = $("btn-start-import");
    const cancelBtn = $("btn-cancel-import");

    if (step === "upload") {
      startBtn.disabled = true;
      startBtn.textContent = "Nhập dữ liệu";
      startBtn.innerHTML = `<svg viewBox="0 0 24 24" width="15" height="15" style="vertical-align:-2px;margin-right:4px;" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>Nhập dữ liệu`;
      cancelBtn.textContent = "Huỷ";
      cancelBtn.disabled = false;
    } else if (step === "preview") {
      const validRows = state.rows.filter((r) => r.status !== "error");
      startBtn.disabled = validRows.length === 0;
      startBtn.innerHTML = `<svg viewBox="0 0 24 24" width="15" height="15" style="vertical-align:-2px;margin-right:4px;" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>Nhập dữ liệu (${validRows.length} dòng)`;
      cancelBtn.textContent = "Huỷ";
      cancelBtn.disabled = false;
    } else if (step === "progress") {
      startBtn.disabled = true;
      startBtn.textContent = "Đang nhập…";
      cancelBtn.disabled = true;
    } else if (step === "done") {
      startBtn.disabled = true;
      startBtn.textContent = "Hoàn tất";
      cancelBtn.textContent = "Đóng";
      cancelBtn.disabled = false;
    }
  }

  /* ══════════════════════════════════════════
     TEMPLATE DOWNLOAD
  ══════════════════════════════════════════ */
  function downloadTemplate() {
    if (typeof XLSX === "undefined") {
      showToast("Chưa tải được thư viện xuất Excel.", "error");
      return;
    }
    const sampleRows = [
      {
        [COL.SKU]: "SP001",
        [COL.NAME]: "Sản phẩm mẫu A",
        [COL.CATEGORY]: "Nhóm 1",
        [COL.UNIT]: "Cái",
        [COL.PACKING]: "Hộp 12 cái",
        [COL.PRICE]: 150000,
        [COL.COST]: 90000,
        [COL.DESCRIPTION]: "Mô tả mẫu",
        [COL.ACTIVE]: "Đang kinh doanh",
      },
      {
        [COL.SKU]: "SP002",
        [COL.NAME]: "Sản phẩm mẫu B",
        [COL.CATEGORY]: "Nhóm 2",
        [COL.UNIT]: "Kg",
        [COL.PACKING]: "",
        [COL.PRICE]: 80000,
        [COL.COST]: 55000,
        [COL.DESCRIPTION]: "",
        [COL.ACTIVE]: "Đang kinh doanh",
      },
    ];
    const ws = XLSX.utils.json_to_sheet(sampleRows, { header: TEMPLATE_HEADERS });
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "DanhMucSanPham");
    XLSX.writeFile(wb, "mau_nhap_san_pham_SaleStock.xlsx");
  }

  /* ══════════════════════════════════════════
     FILE PARSING
  ══════════════════════════════════════════ */

  /**
   * @typedef {Object} ParsedRow
   * @property {number} rowIndex - 1-based row number in Excel (after header)
   * @property {'new'|'update'|'error'|'skip'} status
   * @property {string} sku
   * @property {string} name
   * @property {string} categoryName
   * @property {number|null} categoryId
   * @property {string} unit
   * @property {string} packing
   * @property {number} price
   * @property {number} cost
   * @property {string} description
   * @property {boolean} active
   * @property {string[]} errors
   * @property {string|null} note
   * @property {number|null} existingId - set when status === 'update'
   */

  function normalizeStatus(value) {
    const v = String(value ?? "").trim().toLocaleLowerCase("vi");
    if (!v || v === "đang kinh doanh" || v === "active" || v === "true" || v === "1") return true;
    if (v === "ngừng kinh doanh" || v === "inactive" || v === "false" || v === "0") return false;
    return true; // default
  }

  function parseCurrency(value) {
    if (value === null || value === undefined || value === "") return null;
    if (typeof value === "number") return value;
    const n = Number(String(value).replace(/[^\d.-]/g, ""));
    return Number.isFinite(n) ? n : null;
  }

  function findCategory(name) {
    if (!name) return null;
    const lower = String(name).trim().toLocaleLowerCase("vi");
    return state.categories.find(
      (c) => String(c.name).toLocaleLowerCase("vi") === lower
    ) || null;
  }

  function parseFile(file) {
    return new Promise((resolve, reject) => {
      if (!file) return reject(new Error("Không có tệp được chọn."));
      if (!file.name.endsWith(".xlsx")) {
        return reject(new Error("Tệp phải có định dạng .xlsx."));
      }
      if (file.size > MAX_FILE_BYTES) {
        return reject(new Error("Tệp vượt quá giới hạn 10 MB."));
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const workbook = XLSX.read(e.target.result, { type: "array", cellDates: true });
          const sheetName = workbook.SheetNames[0];
          if (!sheetName) throw new Error("Tệp Excel không có sheet nào.");
          const sheet = workbook.Sheets[sheetName];
          const raw = XLSX.utils.sheet_to_json(sheet, { defval: "" });
          if (!raw.length) throw new Error("Sheet đầu tiên không có dữ liệu.");
          resolve(raw);
        } catch (err) {
          reject(new Error(`Đọc tệp thất bại: ${err.message}`));
        }
      };
      reader.onerror = () => reject(new Error("Không đọc được tệp."));
      reader.readAsArrayBuffer(file);
    });
  }

  /**
   * Build ParsedRow[] from raw XLSX rows.
   * Duplicate SKUs within the same file are treated as errors on the second occurrence.
   */
  function buildParsedRows(raw) {
    const skuMap = new Map(
      state.existingProducts.map((p) => [String(p.code ?? "").toUpperCase(), p])
    );
    const seenInFile = new Map(); // SKU → first rowIndex

    return raw.map((rawRow, idx) => {
      const rowIndex = idx + 2; // +2: header is row 1
      const errors = [];
      const notes = [];

      const sku = String(rawRow[COL.SKU] ?? "").trim().toUpperCase();
      const name = String(rawRow[COL.NAME] ?? "").trim();
      const categoryName = String(rawRow[COL.CATEGORY] ?? "").trim();
      const unit = String(rawRow[COL.UNIT] ?? "").trim();
      const packing = String(rawRow[COL.PACKING] ?? "").trim();
      const priceRaw = parseCurrency(rawRow[COL.PRICE]);
      const costRaw = parseCurrency(rawRow[COL.COST]);
      const description = String(rawRow[COL.DESCRIPTION] ?? "").trim();
      const active = normalizeStatus(rawRow[COL.ACTIVE]);

      /* Validation */
      if (!sku) errors.push("Thiếu Mã SKU");
      if (!name) errors.push("Thiếu Tên sản phẩm");
      if (!unit) errors.push("Thiếu Đơn vị tính");
      if (priceRaw === null || priceRaw < 0) errors.push("Giá bán không hợp lệ");
      if (costRaw === null || costRaw < 0) errors.push("Giá vốn không hợp lệ");
      if (sku && seenInFile.has(sku)) {
        errors.push(`Mã SKU trùng với dòng ${seenInFile.get(sku)} trong file`);
      }

      if (sku && !seenInFile.has(sku)) seenInFile.set(sku, rowIndex);

      /* Category */
      const matchedCategory = categoryName ? findCategory(categoryName) : null;
      if (categoryName && !matchedCategory) {
        errors.push(`Nhóm hàng "${categoryName}" không tìm thấy`);
      }

      /* Determine status */
      let status = "new";
      let existingId = null;
      let note = null;

      if (!errors.length) {
        const existing = skuMap.get(sku);
        if (existing) {
          status = "update";
          existingId = existing.id;
          note = `SKU đã tồn tại — sẽ cập nhật`;
        }
      } else {
        status = "error";
      }

      return {
        rowIndex,
        status,
        sku,
        name,
        categoryName,
        categoryId: matchedCategory?.id ?? null,
        unit,
        packing,
        price: priceRaw ?? 0,
        cost: costRaw ?? 0,
        description,
        active,
        errors,
        note,
        existingId,
      };
    });
  }

  /* ══════════════════════════════════════════
     RENDER PREVIEW
  ══════════════════════════════════════════ */

  function renderPreview(rows) {
    const total = rows.length;
    const newCount = rows.filter((r) => r.status === "new").length;
    const updateCount = rows.filter((r) => r.status === "update").length;
    const errorCount = rows.filter((r) => r.status === "error").length;

    /* Stats badges */
    $("import-preview-stats").innerHTML = `
      <span class="import-stat-badge import-stat-badge--total">
        ${total} dòng tổng
      </span>
      <span class="import-stat-badge import-stat-badge--new">
        ✦ ${newCount} tạo mới
      </span>
      <span class="import-stat-badge import-stat-badge--update">
        ↻ ${updateCount} cập nhật
      </span>
      ${errorCount ? `<span class="import-stat-badge import-stat-badge--error">⚠ ${errorCount} lỗi</span>` : ""}
    `;

    /* Preview rows */
    $("import-preview-body").innerHTML = rows
      .map((row) => {
        const rowClass = `import-row--${row.status}`;
        const badgeLabel =
          row.status === "new"
            ? "✦ Tạo mới"
            : row.status === "update"
            ? "↻ Cập nhật"
            : row.status === "error"
            ? "⚠ Lỗi"
            : "Bỏ qua";
        const badgeClass = `import-row-badge--${row.status}`;
        const skuCell =
          row.status === "update"
            ? `<span class="import-sku-updated">${escHtml(row.sku)}</span>`
            : escHtml(row.sku || "—");
        const errorOrNote =
          row.status === "error"
            ? `<span class="import-error-text">${row.errors.map(escHtml).join(" · ")}</span>`
            : row.note
            ? `<span class="import-note-text">${escHtml(row.note)}</span>`
            : "";

        return `
          <tr class="${rowClass}" data-row="${row.rowIndex}">
            <td class="import-col-row" style="text-align:center;color:var(--ink-400);font-size:0.75rem;">${row.rowIndex}</td>
            <td><span class="import-row-badge ${badgeClass}">${badgeLabel}</span></td>
            <td>${skuCell}</td>
            <td>${escHtml(row.name || "—")}</td>
            <td>${escHtml(row.categoryName || "—")}</td>
            <td>${escHtml(row.unit || "—")}</td>
            <td>${escHtml(row.packing || "—")}</td>
            <td>${row.price != null ? row.price.toLocaleString("vi-VN") : "—"}</td>
            <td>${row.cost != null ? row.cost.toLocaleString("vi-VN") : "—"}</td>
            <td class="import-col-error">${errorOrNote}</td>
          </tr>`;
      })
      .join("");

    /* Error summary */
    const errorRows = rows.filter((r) => r.status === "error");
    const errSummary = $("import-error-summary");
    if (errorRows.length) {
      errSummary.hidden = false;
      errSummary.innerHTML = `
        <strong>${errorRows.length} dòng có lỗi và sẽ bị bỏ qua khi nhập.</strong>
        Kiểm tra lại các dòng được đánh dấu đỏ, sửa và tải lại tệp nếu cần.
      `;
    } else {
      errSummary.hidden = true;
    }
  }

  /* ══════════════════════════════════════════
     HANDLE FILE
  ══════════════════════════════════════════ */

  async function handleFile(file) {
    if (!file) return;
    try {
      const raw = await parseFile(file);
      state.rows = buildParsedRows(raw);
      renderPreview(state.rows);
      showStep("preview");
      updateActionButtons("preview");
    } catch (err) {
      showToast(err.message, "error");
    }
  }

  /* ══════════════════════════════════════════
     IMPORT API CALLS
  ══════════════════════════════════════════ */

  /**
   * Send one row to the API.
   * @param {ParsedRow} row
   * @returns {Promise<{ok:boolean, message:string}>}
   */
  async function sendRow(row) {
    const payload = {
      code: row.sku,
      name: row.name,
      categoryId: row.categoryId,
      unit: row.unit,
      packingSpecification: row.packing,
      price: row.price,
      costPrice: row.cost,
      description: row.description,
      active: row.active,
      imageUrl: "",
    };

    try {
      const isUpdate = row.status === "update" && row.existingId != null;
      const response = await Auth.apiFetch(
        isUpdate ? `/products/${row.existingId}` : "/products",
        { method: isUpdate ? "PUT" : "POST", body: JSON.stringify(payload) }
      );
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success) {
        return { ok: false, message: result.message || "Lỗi không xác định" };
      }
      return { ok: true, message: result.message || "Thành công" };
    } catch (err) {
      return { ok: false, message: err.message || "Lỗi kết nối" };
    }
  }

  async function runImport() {
    const validRows = state.rows.filter((r) => r.status !== "error");
    if (!validRows.length) return;

    state.importing = true;
    showStep("progress");
    updateActionButtons("progress");

    const total = validRows.length;
    let done = 0;
    let successCount = 0;
    let failCount = 0;

    function updateProgress() {
      const pct = total ? Math.round((done / total) * 100) : 0;
      $("import-progress-fill").style.width = `${pct}%`;
      $("import-progress-label").textContent = `Đang nhập… ${done} / ${total}`;
    }

    updateProgress();

    /* Process in batches to avoid overwhelming the server */
    for (let i = 0; i < validRows.length; i += BATCH_SIZE) {
      const batch = validRows.slice(i, i + BATCH_SIZE);
      const results = await Promise.all(batch.map(sendRow));
      results.forEach((r) => {
        done += 1;
        if (r.ok) successCount += 1;
        else failCount += 1;
      });
      updateProgress();
    }

    /* Done */
    state.importing = false;
    showStep("done");
    updateActionButtons("done");
    renderDone(successCount, failCount, total);

    /* Reload catalog so the table reflects the changes */
    if (typeof window.__reloadProductsCatalog === "function") {
      window.__reloadProductsCatalog();
    }
  }

  function renderDone(successCount, failCount, total) {
    const hasPartial = failCount > 0;
    const iconClass = hasPartial ? "import-done-icon--partial" : "import-done-icon--success";
    const iconSvg = hasPartial
      ? `<svg viewBox="0 0 24 24" width="24" height="24"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`
      : `<svg viewBox="0 0 24 24" width="24" height="24"><polyline points="20 6 9 17 4 12"/></svg>`;
    const title = hasPartial ? "Nhập một phần hoàn tất" : "Nhập thành công";
    const newImported = state.rows.filter(
      (r) => r.status === "new"
    ).length;
    const updateImported = state.rows.filter(
      (r) => r.status === "update"
    ).length;

    $("import-done-summary").innerHTML = `
      <div class="import-done-icon ${iconClass}">${iconSvg}</div>
      <p class="import-done-title">${title}</p>
      <p class="import-done-detail">Đã xử lý <strong>${total}</strong> dòng hợp lệ từ tệp Excel.</p>
      <div class="import-done-stats">
        <span class="import-stat-badge import-stat-badge--new">✦ ${successCount > 0 ? Math.min(newImported, successCount) : 0} tạo mới</span>
        <span class="import-stat-badge import-stat-badge--update">↻ ${updateImported > 0 ? Math.min(updateImported, successCount) : 0} cập nhật</span>
        ${failCount ? `<span class="import-stat-badge import-stat-badge--error">⚠ ${failCount} thất bại</span>` : ""}
      </div>
    `;
  }

  /* ══════════════════════════════════════════
     TOAST (reuse pattern from products.js)
  ══════════════════════════════════════════ */

  function showToast(message, type = "success") {
    let container = $("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.setAttribute("role", "status");
    toast.innerHTML = `<span class="toast__message">${escHtml(message)}</span>`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.remove();
    }, 4200);
  }

  /* ══════════════════════════════════════════
     BRIDGE: expose reload hook from products.js
  ══════════════════════════════════════════ */

  // products.js must call window.__reloadProductsCatalog() after loadCatalog.
  // We also store catalog data for SKU lookup.
  // This is done by patching after products.js finishes setup.
  function patchProductsCatalog() {
    // Poll until products.js sets up loadCatalog
    const originalLoad = window.__reloadProductsCatalog;
    if (typeof originalLoad !== "function") {
      // Not yet exposed; we'll rely on the catalog being loaded when user opens modal.
    }
  }

  /* ══════════════════════════════════════════
     EVENT WIRING
  ══════════════════════════════════════════ */

  function init() {
    /* Open */
    $("btn-import-excel")?.addEventListener("click", openModal);

    /* Close */
    $("btn-close-import")?.addEventListener("click", closeModal);
    $("btn-cancel-import")?.addEventListener("click", () => {
      if ($("import-step-done") && !$("import-step-done").hidden) {
        closeModal();
      } else {
        closeModal();
      }
    });

    /* Click-outside */
    $("import-excel-modal")?.addEventListener("click", (e) => {
      if (e.target === $("import-excel-modal")) closeModal();
    });

    /* Escape key */
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && !$("import-excel-modal")?.hidden) closeModal();
    });

    /* Template download */
    $("btn-download-template")?.addEventListener("click", downloadTemplate);

    /* Dropzone: file input change */
    $("import-file-input")?.addEventListener("change", (e) => {
      handleFile(e.target.files?.[0]);
    });

    /* Dropzone: keyboard trigger */
    $("import-dropzone")?.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        $("import-file-input").click();
      }
    });

    /* Drag-and-drop */
    const dropzone = $("import-dropzone");
    if (dropzone) {
      dropzone.addEventListener("dragover", (e) => {
        e.preventDefault();
        dropzone.classList.add("import-dropzone--dragover");
      });
      dropzone.addEventListener("dragleave", () => {
        dropzone.classList.remove("import-dropzone--dragover");
      });
      dropzone.addEventListener("drop", (e) => {
        e.preventDefault();
        dropzone.classList.remove("import-dropzone--dragover");
        handleFile(e.dataTransfer?.files?.[0]);
      });
    }

    /* Change file */
    $("btn-import-change-file")?.addEventListener("click", () => {
      showStep("upload");
      resetUploadStep();
      updateActionButtons("upload");
    });

    /* Start import */
    $("btn-start-import")?.addEventListener("click", () => {
      const isDone = !$("import-step-done")?.hidden;
      if (isDone) {
        closeModal();
        return;
      }
      runImport();
    });

    patchProductsCatalog();
  }

  /* ══════════════════════════════════════════
     EXPOSE catalog update hook for products.js
  ══════════════════════════════════════════ */

  // products.js sets window.__productsCatalog = { products, categories } after each loadCatalog.
  // Import script reads it on openModal() so SKU lookup is always fresh.

  document.addEventListener("DOMContentLoaded", init);
  // If DOMContentLoaded already fired (script loaded after):
  if (document.readyState !== "loading") init();
})();


/**
 * Nhập danh mục sản phẩm từ Excel (S2-08).
 *
 * Luồng giao diện:
 * 1. Tải file mẫu hoặc chọn file Excel.
 * 2. Gửi file lên backend để kiểm tra và xem trước từng dòng.
 * 3. Xác nhận một lần để backend tạo mới/cập nhật các dòng hợp lệ.
 */
(async () => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const escHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "'": "&#39;",
    '"': "&quot;",
  }[char]));
  const formatAmount = (value) => value == null || value === ""
    ? "—"
    : Number(value).toLocaleString("vi-VN");
  const MAX_FILE_BYTES = 20 * 1024 * 1024;
  const STEP_ORDER = ["upload", "preview", "progress", "done"];
  let initialized = false;

  const state = {
    file: null,
    rows: [],
    preview: null,
    importing: false,
    previewRequestId: 0,
    previousFocus: null,
    previousOverflow: "",
  };

  const modal = () => $("import-excel-modal");

  function setMessage(id, message = "", tone = "error") {
    const element = $(id);
    if (!element) return;
    element.textContent = message;
    element.hidden = !message;
    element.classList.toggle("product-import__message--info", tone === "info");
  }

  async function readApiResult(response, fallbackMessage) {
    const body = await response.json().catch(() => null);
    if (!response.ok || body?.success === false) {
      throw new Error(body?.message || fallbackMessage);
    }
    return body?.data ?? body;
  }

  function setStep(step) {
    STEP_ORDER.forEach((name) => {
      const section = $(`import-step-${name}`);
      if (section) section.hidden = name !== step;
    });
    document.querySelectorAll("[data-import-step-label]").forEach((label) => {
      const index = STEP_ORDER.indexOf(label.dataset.importStepLabel);
      const current = STEP_ORDER.indexOf(step);
      label.classList.toggle("product-import__step--active", index === current);
      label.classList.toggle("product-import__step--complete", index < current);
    });
    updateActionButtons(step);
  }

  function updateActionButtons(step) {
    const startButton = $("btn-start-import");
    const cancelButton = $("btn-cancel-import");
    if (!startButton || !cancelButton) return;

    if (step === "upload") {
      startButton.disabled = true;
      startButton.textContent = "Nhập dữ liệu";
      cancelButton.disabled = false;
      cancelButton.textContent = "Huỷ";
    } else if (step === "preview") {
      const validRows = state.rows.filter((row) => row.status !== "error");
      startButton.disabled = state.importing || validRows.length === 0;
      startButton.textContent = validRows.length
        ? `Nhập dữ liệu (${validRows.length} dòng)`
        : "Không có dòng hợp lệ";
      cancelButton.disabled = false;
      cancelButton.textContent = "Huỷ";
    } else if (step === "progress") {
      startButton.disabled = true;
      startButton.textContent = "Đang nhập…";
      cancelButton.disabled = true;
      cancelButton.textContent = "Đang xử lý…";
    } else {
      startButton.disabled = false;
      startButton.textContent = "Đóng";
      cancelButton.disabled = false;
      cancelButton.textContent = "Đóng";
    }
  }

  function resetFlow() {
    state.previewRequestId++;
    state.file = null;
    state.rows = [];
    state.preview = null;
    state.importing = false;
    const input = $("import-file-input");
    if (input) input.value = "";
    if ($("import-dropzone")) $("import-dropzone").disabled = false;
    $("import-preview-body").innerHTML = "";
    $("import-preview-stats").innerHTML = "";
    $("import-preview-file").textContent = "";
    $("import-file-status").textContent = "Chưa chọn tệp.";
    $("import-progress-fill").style.width = "0%";
    $("import-progress-label").textContent = "Hệ thống đang xử lý file Excel…";
    setMessage("import-upload-message");
    setMessage("import-error-summary");
    setMessage("import-done-message");
    setStep("upload");
  }

  function openModal() {
    if (!Auth.hasPermission("PRODUCT_CREATE") || !Auth.hasPermission("PRODUCT_EDIT")) return;
    state.previousFocus = document.activeElement;
    state.previousOverflow = document.body.style.overflow;
    resetFlow();
    modal().hidden = false;
    document.body.style.overflow = "hidden";
    $("import-dropzone").focus();
  }

  function closeModal() {
    if (state.importing) return;
    modal().hidden = true;
    document.body.style.overflow = state.previousOverflow;
    state.previousFocus?.focus?.();
    resetFlow();
  }

  async function downloadTemplate() {
    const button = $("btn-download-template");
    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = "Đang tải…";
    try {
      const response = await Auth.apiFetch("/products/import/template");
      if (!response.ok) throw new Error("Không tải được file mẫu.");
      const blob = await response.blob();
      const disposition = response.headers.get("Content-Disposition") || "";
      const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
      const filename = encodedName ? decodeURIComponent(encodedName) : "mau_nhap_san_pham_SaleStock.xlsx";
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      setMessage("import-upload-message", error.message || "Không tải được file mẫu.");
    } finally {
      button.disabled = false;
      button.textContent = originalText;
    }
  }

  function validateFile(file) {
    if (!file) throw new Error("Vui lòng chọn một file Excel.");
    const name = String(file.name || "").toLowerCase();
    if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
      throw new Error("File phải có định dạng .xlsx hoặc .xls.");
    }
    if (file.size > MAX_FILE_BYTES) {
      throw new Error("File vượt quá giới hạn 20 MB.");
    }
  }

  function normalizeRows(rows) {
    return (Array.isArray(rows) ? rows : []).map((row) => {
      const errors = Array.isArray(row.errors) ? row.errors : [];
      const status = row.valid === false || errors.length
        ? "error"
        : row.isUpdate || row.action === "UPDATE"
          ? "update"
          : "new";
      return {
        ...row,
        status,
        rowNumber: row.rowNumber ?? row.rowIndex ?? "—",
        sku: row.sku || row.code || "",
        category: row.resolvedCategoryName || row.category || "",
        packing: row.packingSpecification || "",
        errors,
      };
    });
  }

  function renderPreview(result) {
    state.rows = normalizeRows(result?.rows);
    const total = Number(result?.totalRows) || state.rows.length;
    const valid = Number(result?.validRows) || state.rows.filter((row) => row.status !== "error").length;
    const invalid = Number(result?.invalidRows) || state.rows.filter((row) => row.status === "error").length;
    const createCount = Number(result?.createCount) || state.rows.filter((row) => row.status === "new").length;
    const updateCount = Number(result?.updateCount) || state.rows.filter((row) => row.status === "update").length;

    $("import-preview-stats").innerHTML = `
      <span class="import-stat-badge import-stat-badge--total">${total.toLocaleString("vi-VN")} dòng tổng</span>
      <span class="import-stat-badge import-stat-badge--new">✦ ${createCount.toLocaleString("vi-VN")} tạo mới</span>
      <span class="import-stat-badge import-stat-badge--update">↻ ${updateCount.toLocaleString("vi-VN")} cập nhật</span>
      ${invalid ? `<span class="import-stat-badge import-stat-badge--error">⚠ ${invalid.toLocaleString("vi-VN")} lỗi</span>` : ""}
    `;

    $("import-preview-body").innerHTML = state.rows.length
      ? state.rows.map((row) => {
        const isError = row.status === "error";
        const label = isError ? "⚠ Lỗi" : row.status === "update" ? "↻ Cập nhật" : "✦ Tạo mới";
        const note = isError
          ? row.errors.join(" · ")
          : row.status === "update" ? "SKU đã tồn tại — sẽ cập nhật" : "SKU chưa tồn tại — sẽ tạo mới";
        return `<tr class="import-row--${row.status}">
          <td class="import-col-row">${escHtml(row.rowNumber)}</td>
          <td><span class="import-row-badge import-row-badge--${row.status}">${label}</span></td>
          <td class="import-sku">${escHtml(row.sku || "—")}</td>
          <td>${escHtml(row.name || "—")}</td>
          <td>${escHtml(row.category || "—")}</td>
          <td>${escHtml(row.unit || "—")}</td>
          <td>${escHtml(row.packing || "—")}</td>
          <td>${formatAmount(row.price)}</td>
          <td>${formatAmount(row.costPrice)}</td>
          <td class="import-col-error">${escHtml(note)}</td>
        </tr>`;
      }).join("")
      : '<tr><td class="catalog-empty" colspan="10">File không có dòng dữ liệu để nhập.</td></tr>';

    setMessage(
      "import-error-summary",
      invalid
        ? `${invalid} dòng có lỗi sẽ được bỏ qua. Chỉ ${valid} dòng hợp lệ được nhập.`
        : "Tất cả dòng đều hợp lệ. Kiểm tra lại bản xem trước trước khi nhập.",
      invalid ? "error" : "info",
    );
    $("import-error-summary").classList.toggle("product-import__message--info", !invalid);
    updateActionButtons("preview");
  }

  async function previewFile(file, requestId) {
    validateFile(file);
    $("import-file-status").textContent = `${file.name} · ${(file.size / 1024 / 1024).toFixed(2)} MB`;
    setMessage("import-upload-message");
    const formData = new FormData();
    formData.append("file", file);
    const response = await Auth.apiFetch("/products/import/preview", {
      method: "POST",
      body: formData,
    });
    const result = await readApiResult(response, "Không thể xem trước file Excel.");
    if (requestId !== state.previewRequestId) return;
    state.file = file;
    state.preview = result;
    $("import-preview-file").textContent = file.name;
    renderPreview(result);
    setStep("preview");
  }

  async function handleFile(file) {
    const requestId = ++state.previewRequestId;
    state.file = null;
    state.rows = [];
    state.preview = null;
    setStep("upload");
    try {
      validateFile(file);
      $("import-file-status").textContent = "Đang kiểm tra file…";
      $("import-dropzone").disabled = true;
      await previewFile(file, requestId);
    } catch (error) {
      if (requestId !== state.previewRequestId) return;
      $("import-file-status").textContent = "Chưa chọn tệp.";
      setMessage("import-upload-message", error.message || "Không thể đọc file Excel.");
    } finally {
      if (requestId === state.previewRequestId) $("import-dropzone").disabled = false;
    }
  }

  function renderDone(result) {
    const imported = Number(result?.importedRows) || 0;
    const total = Number(result?.totalRows) || state.rows.length;
    const invalid = Number(result?.invalidRows) || 0;
    const creates = Number(result?.createCount) || 0;
    const updates = Number(result?.updateCount) || 0;
    const partial = invalid > 0;
    $("import-done-summary").innerHTML = `
      <div class="import-done-icon import-done-icon--${partial ? "partial" : "success"}" aria-hidden="true">
        ${partial
          ? '<svg viewBox="0 0 24 24"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4m0 3h.01"/></svg>'
          : '<svg viewBox="0 0 24 24"><path d="m5 12 4 4L19 6"/></svg>'}
      </div>
      <h3>${partial ? "Đã nhập các dòng hợp lệ" : "Nhập sản phẩm thành công"}</h3>
      <p>Đã xử lý ${imported} / ${total} dòng trong file.</p>
      <div class="import-done-stats">
        <span class="import-stat-badge import-stat-badge--new">✦ ${creates} tạo mới</span>
        <span class="import-stat-badge import-stat-badge--update">↻ ${updates} cập nhật</span>
        ${invalid ? `<span class="import-stat-badge import-stat-badge--error">⚠ ${invalid} bỏ qua</span>` : ""}
      </div>
    `;
    setMessage(
      "import-done-message",
      partial ? "Các dòng lỗi không được nhập. Bạn có thể sửa file và thực hiện lại." : "Danh mục sản phẩm đã được cập nhật.",
      partial ? "error" : "info",
    );
  }

  async function runImport() {
    if (!state.file || state.importing) return;
    state.importing = true;
    setStep("progress");
    $("import-progress-fill").style.width = "55%";
    try {
      const formData = new FormData();
      formData.append("file", state.file);
      const response = await Auth.apiFetch("/products/import", {
        method: "POST",
        body: formData,
      });
      const result = await readApiResult(response, "Không thể nhập danh mục sản phẩm.");
      $("import-progress-fill").style.width = "100%";
      renderDone(result);
      setStep("done");
      if (typeof window.__reloadProductsCatalog === "function") {
        await window.__reloadProductsCatalog();
      }
    } catch (error) {
      state.importing = false;
      setStep("preview");
      setMessage("import-error-summary", error.message || "Không thể nhập danh mục sản phẩm.");
    } finally {
      state.importing = false;
      updateActionButtons($("import-step-done").hidden ? "preview" : "done");
    }
  }

  function wireEvents() {
    const importButton = $("btn-import-excel");
    const dropzone = $("import-dropzone");
    const fileInput = $("import-file-input");
    importButton?.addEventListener("click", openModal);
    $("btn-close-import")?.addEventListener("click", closeModal);
    $("btn-cancel-import")?.addEventListener("click", closeModal);
    $("btn-download-template")?.addEventListener("click", downloadTemplate);
    $("btn-import-change-file")?.addEventListener("click", () => {
      setStep("upload");
      state.file = null;
      fileInput.value = "";
      $("import-file-status").textContent = "Chưa chọn tệp.";
      setMessage("import-upload-message");
      fileInput.focus();
    });
    $("btn-start-import")?.addEventListener("click", () => {
      if (!$("import-step-done").hidden) closeModal();
      else runImport();
    });
    dropzone?.addEventListener("click", (event) => {
      if (event.target !== fileInput) fileInput.click();
    });
    dropzone?.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        fileInput.click();
      }
    });
    fileInput?.addEventListener("change", (event) => handleFile(event.target.files?.[0]));
    dropzone?.addEventListener("dragover", (event) => {
      event.preventDefault();
      dropzone.classList.add("product-import__dropzone--dragover");
    });
    dropzone?.addEventListener("dragleave", () => dropzone.classList.remove("product-import__dropzone--dragover"));
    dropzone?.addEventListener("drop", (event) => {
      event.preventDefault();
      dropzone.classList.remove("product-import__dropzone--dragover");
      handleFile(event.dataTransfer?.files?.[0]);
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && modal() && !modal().hidden) closeModal();
    });
    modal()?.addEventListener("click", (event) => {
      if (event.target === modal()) closeModal();
    });
  }

  function init() {
    if (initialized || !$("import-excel-modal")) return;
    initialized = true;
    $("btn-import-excel").hidden = !Auth.hasPermission("PRODUCT_CREATE") || !Auth.hasPermission("PRODUCT_EDIT");
    wireEvents();
    setStep("upload");
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init, { once: true });
  else init();
})();

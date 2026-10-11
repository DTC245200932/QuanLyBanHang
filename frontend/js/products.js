(() => {
  if (!Auth.requireLogin()) return;

  const canManage = Auth.hasRole("ROLE_SALES_MANAGER");
  const canImport = Auth.hasAnyRole("ROLE_SALES_MANAGER", "ROLE_ADMIN");
  const canViewPriceHistory = canManage || Auth.hasRole("ROLE_ADMIN");
  const state = {
    products: [],
    categories: [],
    editingProduct: null,
  };
  const $ = (id) => document.getElementById(id);
  const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[char]);
  const formatCurrency = (value) => new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(Number(value) || 0);

  const modal = $("product-modal");
  const form = $("product-form");
  const formMessage = $("product-form-message");
  const costHelp = $("cost-help");
  const saveButton = $("save-product");

  $("create-product").hidden = !canManage;
  $("btn-import-excel").hidden = !canImport;
  $("catalog-access-note").hidden = canManage;
  document.querySelectorAll(".cost-column").forEach((column) => {
    column.hidden = !canManage;
  });

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
    toast.innerHTML = `<span class="toast__message">${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.remove();
    }, 4200);
  }

  function setFormMessage(message = "") {
    formMessage.textContent = message;
    formMessage.hidden = !message;
  }

  function categoryOptions(selectedId = "") {
    return state.categories.map((category) => {
      const depth = Math.max(Number(category.level || 1) - 1, 0);
      const prefix = depth ? `${"— ".repeat(depth)}` : "";
      return `<option value="${category.id}" ${String(category.id) === String(selectedId) ? "selected" : ""}>${escapeHtml(prefix + category.name)}</option>`;
    }).join("");
  }

  function populateCategorySelects() {
    $("category-filter").innerHTML = `<option value="">Tất cả nhóm hàng</option>${categoryOptions()}`;
    $("product-category").innerHTML = `<option value="">Chọn nhóm hàng</option>${categoryOptions()}`;
    $("category-summary").textContent = `${state.categories.length} nhóm hàng`;
  }

  function filteredProducts() {
    const keyword = $("product-search").value.trim().toLocaleLowerCase("vi");
    const categoryId = $("category-filter").value;
    const status = $("status-filter").value;
    return state.products.filter((product) => {
      const matchesKeyword = !keyword
        || String(product.code || "").toLocaleLowerCase("vi").includes(keyword)
        || String(product.name || "").toLocaleLowerCase("vi").includes(keyword);
      const matchesCategory = !categoryId || String(product.categoryId) === categoryId;
      const active = product.active !== false;
      const matchesStatus = !status
        || (status === "active" && active)
        || (status === "inactive" && !active);
      return matchesKeyword && matchesCategory && matchesStatus;
    });
  }

  function productThumbnail(product) {
    if (product.imageUrl) {
      return `<span class="product-thumbnail"><img data-protected-src="${escapeHtml(product.imageUrl)}" alt="Ảnh ${escapeHtml(product.name)}"></span>`;
    }
    const fallback = String(product.code || product.name || "SP").slice(0, 2).toUpperCase();
    return `<span class="product-thumbnail" aria-hidden="true">${escapeHtml(fallback)}</span>`;
  }

  function renderProducts() {
    const products = filteredProducts();
    $("product-summary").textContent = `${products.length} / ${state.products.length} sản phẩm`;
    if (!products.length) {
      $("products-body").innerHTML = `
        <tr><td class="catalog-empty" colspan="7">
          Không tìm thấy sản phẩm phù hợp. Hãy thay đổi bộ lọc hoặc từ khoá tìm kiếm.
        </td></tr>`;
      return;
    }

    $("products-body").innerHTML = products.map((product) => {
      const active = product.active !== false;
      const packaging = product.packingSpecification
        ? escapeHtml(product.packingSpecification)
        : '<span class="product-backend-note">Chưa có dữ liệu quy cách</span>';
      const costCell = canManage
        ? `<td class="cost-column"><span class="product-price">${product.costPrice == null ? "—" : formatCurrency(product.costPrice)}</span>${product.costPrice == null ? '<span class="product-detail">Chưa có giá vốn</span>' : ""}</td>`
        : "";
      const actions = canManage
        ? `<button class="product-edit" type="button" data-edit-product="${product.id}">Sửa</button>
           <button class="product-edit" type="button" data-toggle-product="${product.id}">${active ? "Ngừng bán" : "Kích hoạt"}</button>
           ${product.canDelete ? `<button class="product-edit" type="button" data-delete-product="${product.id}">Xoá</button>` : ""}`
        : "";
      const historyAction = canViewPriceHistory
        ? `<button class="product-edit product-history-button" type="button" data-price-history="${escapeHtml(product.id)}" aria-label="${escapeHtml(`Xem lịch sử giá của ${product.code}`)}">Lịch sử giá</button>`
        : "";
      return `
        <tr>
          <td>
            <div class="product-identity">
              ${productThumbnail(product)}
              <span>
                <span class="product-name">${escapeHtml(product.name)}</span>
                <span class="product-code">${escapeHtml(product.code)}</span>
              </span>
            </div>
          </td>
          <td>
            ${escapeHtml(product.categoryName || "Chưa phân nhóm")}
            <span class="product-detail">${escapeHtml(product.categoryCode || "")}</span>
          </td>
          <td>
            ${escapeHtml(product.unit || "—")}
            ${packaging}
          </td>
          <td><span class="product-price">${formatCurrency(product.price)}</span></td>
          ${costCell}
          <td><span class="product-status ${active ? "" : "product-status--inactive"}">${active ? "Đang kinh doanh" : "Ngừng kinh doanh"}</span></td>
          <td><div class="product-actions">${historyAction}${actions}</div></td>
        </tr>`;
    }).join("");
    document.querySelectorAll("img[data-protected-src]").forEach((image) => {
      Auth.setProtectedImage(image, image.dataset.protectedSrc);
    });
  }

  async function loadCatalog() {
    $("products-body").innerHTML = '<tr><td class="catalog-empty" colspan="7">Đang tải danh sách sản phẩm…</td></tr>';
    try {
      const [productsResponse, categoriesResponse] = await Promise.all([
        Auth.apiFetch("/products"),
        Auth.apiFetch("/categories"),
      ]);
      const [productsResult, categoriesResult] = await Promise.all([
        productsResponse.json(),
        categoriesResponse.json(),
      ]);
      if (!productsResponse.ok || !productsResult.success) {
        throw new Error(productsResult.message || "Không tải được danh sách sản phẩm.");
      }
      if (!categoriesResponse.ok || !categoriesResult.success) {
        throw new Error(categoriesResult.message || "Không tải được nhóm hàng.");
      }
      state.products = productsResult.data || [];
      state.categories = categoriesResult.data || [];
      window.__productsCatalog = {
        products: state.products,
        categories: state.categories,
      };
      populateCategorySelects();
      renderProducts();
    } catch (error) {
      $("product-summary").textContent = "Không tải được danh mục";
      $("products-body").innerHTML = `<tr><td class="catalog-empty" colspan="7">${escapeHtml(error.message || "Không thể kết nối tới máy chủ.")}</td></tr>`;
    }
  }

  function clearInvalidFields() {
    form.querySelectorAll('[aria-invalid="true"]').forEach((field) => {
      field.removeAttribute("aria-invalid");
    });
  }

  function openProductModal(product = null) {
    if (!canManage) return;
    state.editingProduct = product;
    form.reset();
    clearInvalidFields();
    setFormMessage();
    $("product-id").value = product?.id || "";
    $("product-modal-title").textContent = product ? "Cập nhật sản phẩm" : "Thêm sản phẩm";
    $("product-code").value = product?.code || "";
    $("product-name").value = product?.name || "";
    $("product-category").innerHTML = `<option value="">Chọn nhóm hàng</option>${categoryOptions(product?.categoryId)}`;
    $("product-unit").value = product?.unit || "cái";
    $("product-packing").value = product?.packingSpecification || "";
    $("product-price").value = Number(product?.price || 0);
    $("product-cost").value = product?.costPrice ?? "";
    $("product-description").value = product?.description || "";
    $("product-active").value = String(product?.active !== false);
    $("product-image-url").value = product?.imageUrl || "";
    costHelp.textContent = product && product.costPrice == null
      ? "Nhập giá vốn hiện tại để lưu sản phẩm."
      : "Chỉ Quản lý kinh doanh được xem và cập nhật.";
    modal.hidden = false;
    $("product-code").focus();
  }

  function closeProductModal() {
    modal.hidden = true;
    state.editingProduct = null;
  }

  function validateForm() {
    clearInvalidFields();
    const requiredIds = [
      "product-code", "product-name", "product-category",
      "product-unit", "product-price", "product-cost",
    ];
    const missing = requiredIds.map($).filter((field) => !String(field.value).trim());
    missing.forEach((field) => field.setAttribute("aria-invalid", "true"));
    if (missing.length) return "Vui lòng nhập đầy đủ các trường bắt buộc.";

    const code = $("product-code").value.trim().toUpperCase();
    const duplicate = state.products.some((product) => product.code?.toUpperCase() === code
      && String(product.id) !== String(state.editingProduct?.id || ""));
    if (duplicate) {
      $("product-code").setAttribute("aria-invalid", "true");
      return `Mã SKU ${code} đã tồn tại.`;
    }
    if (Number($("product-price").value) < 0 || Number($("product-cost").value) < 0) {
      return "Giá bán và giá vốn không được âm.";
    }
    return "";
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const validationMessage = validateForm();
    if (validationMessage) {
      setFormMessage(validationMessage);
      return;
    }

    const product = state.editingProduct;
    const payload = {
      code: $("product-code").value.trim().toUpperCase(),
      name: $("product-name").value.trim(),
      description: $("product-description").value.trim(),
      categoryId: Number($("product-category").value),
      price: Number($("product-price").value),
      costPrice: Number($("product-cost").value),
      revenue: Number(product?.revenue || 0),
      unit: $("product-unit").value.trim(),
      packingSpecification: $("product-packing").value.trim(),
      imageUrl: $("product-image-url").value,
      active: $("product-active").value === "true",
    };

    saveButton.disabled = true;
    saveButton.textContent = "Đang lưu…";
    setFormMessage();
    try {
      const imageFile = $("product-image").files?.[0];
      if (imageFile) {
        const formData = new FormData();
        formData.append("file", imageFile);
        const uploadResponse = await Auth.apiFetch("/products/upload-image", { method: "POST", body: formData });
        const uploadResult = await uploadResponse.json().catch(() => null);
        if (!uploadResponse.ok || !uploadResult?.success) {
          throw new Error(uploadResult?.message || "Không tải được ảnh sản phẩm.");
        }
        payload.imageUrl = uploadResult.data.imageUrl;
      }
      const response = await Auth.apiFetch(product ? `/products/${product.id}` : "/products", {
        method: product ? "PUT" : "POST",
        body: JSON.stringify(payload),
      });
      const result = await response.json().catch(() => null);
      if (!response.ok || !result?.success) {
        throw new Error(result?.message || "Không lưu được sản phẩm.");
      }
      closeProductModal();
      showToast(result.message || "Đã lưu sản phẩm.");
      await loadCatalog();
    } catch (error) {
      setFormMessage(error.message || "Không thể kết nối tới máy chủ.");
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = "Lưu sản phẩm";
    }
  });

  $("create-product").addEventListener("click", () => openProductModal());
  document.querySelectorAll("[data-close-product]").forEach((button) => {
    button.addEventListener("click", closeProductModal);
  });
  $("products-body").addEventListener("click", (event) => {
    const historyButton = event.target.closest("[data-price-history]");
    if (historyButton) {
      const product = state.products.find(item => String(item.id) === historyButton.dataset.priceHistory);
      if (product) openPriceHistory(product);
      return;
    }
    const editButton = event.target.closest("[data-edit-product]");
    const toggleButton = event.target.closest("[data-toggle-product]");
    const deleteButton = event.target.closest("[data-delete-product]");
    if (editButton) {
      const product = state.products.find((item) => String(item.id) === editButton.dataset.editProduct);
      if (product) openProductModal(product);
      return;
    }
    if (toggleButton) {
      const product = state.products.find((item) => String(item.id) === toggleButton.dataset.toggleProduct);
      if (!product) return;
      Auth.apiFetch(`/products/${product.id}/status?active=${product.active === false}`, { method: "PATCH" })
        .then(async response => { const result = await response.json(); if (!response.ok || !result.success) throw new Error(result.message); showToast(result.message); await loadCatalog(); })
        .catch(error => showToast(error.message || "Không cập nhật được trạng thái.", "error"));
      return;
    }
    if (deleteButton) {
      const product = state.products.find((item) => String(item.id) === deleteButton.dataset.deleteProduct);
      if (!product || !confirm(`Xoá sản phẩm ${product.code}?`)) return;
      Auth.apiFetch(`/products/${product.id}`, { method: "DELETE" })
        .then(async response => { const result = await response.json(); if (!response.ok || !result.success) throw new Error(result.message); showToast(result.message); await loadCatalog(); })
        .catch(error => showToast(error.message || "Không xoá được sản phẩm.", "error"));
    }
  });
  [$("product-search"), $("category-filter"), $("status-filter")].forEach((control) => {
    control.addEventListener(control.tagName === "INPUT" ? "input" : "change", renderProducts);
  });
  $("clear-filters").addEventListener("click", () => {
    $("product-search").value = "";
    $("category-filter").value = "";
    $("status-filter").value = "";
    renderProducts();
  });
  modal.addEventListener("click", (event) => {
    if (event.target === modal) closeProductModal();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) closeProductModal();
  });

  const historyModal = $("price-history-modal");
  const historyState = { product: null, rows: [], page: 0, loading: false, error: "", requestId: 0, controller: null };
  const historyPageSize = 10;
  let historyPreviousFocus;
  let historyPreviousOverflow;

  function historyPrice(value) {
    if (value == null || String(value).trim() === "") return "Chưa có dữ liệu";
    return Number.isFinite(Number(value)) ? formatCurrency(value) : String(value);
  }

  function historyTime(value) {
    if (!value) return "Chưa có dữ liệu";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Chưa có dữ liệu";
    return new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit", month: "2-digit", year: "numeric",
      hour: "2-digit", minute: "2-digit", second: "2-digit",
    }).format(date);
  }

  function renderPriceHistory() {
    const { rows, page, loading, error } = historyState;
    const totalPages = Math.max(1, Math.ceil(rows.length / historyPageSize));
    historyModal.setAttribute("aria-busy", String(loading));
    $("filter-price-history").disabled = loading;
    $("reset-price-history").disabled = loading;
    $("price-history-from").disabled = loading;
    $("price-history-to").disabled = loading;
    $("price-history-prev").disabled = loading || !!error || page === 0;
    $("price-history-next").disabled = loading || !!error || page >= totalPages - 1;
    $("price-history-page").textContent = loading || error ? "—" : `Trang ${page + 1} / ${totalPages}`;
    $("price-history-error").textContent = error;
    $("price-history-error").hidden = !error;
    $("price-history-summary").textContent = loading ? "Đang tải lịch sử thay đổi giá…"
      : error ? "Chưa tải được lịch sử giá" : `${rows.length.toLocaleString("vi-VN")} lần thay đổi giá · Mới nhất trước`;
    if (loading || error || !rows.length) {
      const title = loading ? "Đang tải dữ liệu…" : error ? "Không tải được lịch sử giá" : "Chưa có thay đổi giá trong khoảng thời gian này";
      $("price-history-rows").innerHTML = `<tr><td colspan="4" class="price-history__empty">
        <strong>${title}</strong>
        ${error ? '<span>Kiểm tra kết nối máy chủ và thử lại.</span><button class="btn btn--secondary" type="button" data-retry-price-history>Thử tải lại</button>' : ""}
      </td></tr>`;
      return;
    }
    $("price-history-rows").innerHTML = rows.slice(page * historyPageSize, (page + 1) * historyPageSize).map(row => `<tr>
      <td>${escapeHtml(historyTime(row.appliedAt))}</td>
      <td><span class="product-price">${escapeHtml(historyPrice(row.oldPrice))}</span></td>
      <td><span class="product-price">${escapeHtml(historyPrice(row.newPrice))}</span></td>
      <td>${escapeHtml(row.modifiedBy || "Chưa có dữ liệu")}</td>
    </tr>`).join("");
    historyModal.querySelector(".price-history__table-wrap").scrollTop = 0;
  }

  async function loadPriceHistory() {
    if (!historyState.product) return;
    const from = $("price-history-from").value;
    const to = $("price-history-to").value;
    if (from && to && from > to) {
      $("price-history-error").textContent = "Ngày bắt đầu không được sau ngày kết thúc.";
      $("price-history-error").hidden = false;
      $("price-history-from").focus();
      return;
    }
    historyState.controller?.abort();
    const controller = new AbortController();
    historyState.controller = controller;
    const requestId = ++historyState.requestId;
    const productId = String(historyState.product.id);
    historyState.rows = [];
    historyState.page = 0;
    historyState.error = "";
    historyState.loading = true;
    renderPriceHistory();
    try {
      const response = await Auth.apiFetch(`/products/${productId}/price-history`, { signal: controller.signal });
      const result = await response.json().catch(() => null);
      if (requestId !== historyState.requestId) return;
      if (!response.ok || result?.success === false || !Array.isArray(result?.data)) {
        throw new Error(result?.message || "Không tải được lịch sử giá. Vui lòng thử lại.");
      }

      const fromTime = from ? new Date(`${from}T00:00:00`).getTime() : null;
      const toTime = to ? new Date(`${to}T23:59:59.999`).getTime() : null;
      historyState.rows = result.data
        .filter((row) => {
          const appliedAt = new Date(row.appliedAt).getTime();
          if (fromTime !== null && (!Number.isFinite(appliedAt) || appliedAt < fromTime)) return false;
          if (toTime !== null && (!Number.isFinite(appliedAt) || appliedAt > toTime)) return false;
          return true;
        })
        .sort((a, b) => (new Date(b.appliedAt).getTime() || 0) - (new Date(a.appliedAt).getTime() || 0));
    } catch (error) {
      if (requestId !== historyState.requestId || error.name === "AbortError") return;
      historyState.error = error.message || "Không thể kết nối tới máy chủ. Vui lòng thử lại.";
    } finally {
      if (requestId === historyState.requestId) {
        historyState.loading = false;
        renderPriceHistory();
      }
    }
  }

  function openPriceHistory(product) {
    if (!canViewPriceHistory) return;
    historyPreviousFocus = document.activeElement;
    historyPreviousOverflow = document.body.style.overflow;
    historyState.product = product;
    historyState.rows = [];
    historyState.page = 0;
    historyState.error = "";
    $("price-history-product").textContent = `${product.code} · ${product.name}`;
    $("price-history-filters").reset();
    const canReadApi = canViewPriceHistory;
    $("price-history-unavailable").hidden = canReadApi;
    $("price-history-content").hidden = !canReadApi;
    historyModal.hidden = false;
    document.querySelector(".app-shell").inert = true;
    document.body.style.overflow = "hidden";
    historyModal.querySelector("[data-close-price-history]").focus();
    if (canReadApi) loadPriceHistory();
  }

  function closePriceHistory() {
    if (historyModal.hidden) return;
    historyState.requestId++;
    historyState.controller?.abort();
    historyState.loading = false;
    historyModal.hidden = true;
    document.querySelector(".app-shell").inert = false;
    document.body.style.overflow = historyPreviousOverflow;
    historyPreviousFocus?.focus();
  }

  $("price-history-filters").addEventListener("submit", event => { event.preventDefault(); loadPriceHistory(); });
  $("reset-price-history").addEventListener("click", () => { $("price-history-filters").reset(); loadPriceHistory(); });
  $("price-history-rows").addEventListener("click", event => { if (event.target.closest("[data-retry-price-history]")) loadPriceHistory(); });
  $("price-history-prev").addEventListener("click", () => { if (historyState.page > 0) { historyState.page--; renderPriceHistory(); } });
  $("price-history-next").addEventListener("click", () => {
    if ((historyState.page + 1) * historyPageSize < historyState.rows.length) { historyState.page++; renderPriceHistory(); }
  });
  historyModal.querySelectorAll("[data-close-price-history]").forEach(button => button.addEventListener("click", closePriceHistory));
  historyModal.addEventListener("click", event => { if (event.target === historyModal) closePriceHistory(); });
  document.addEventListener("keydown", event => {
    if (historyModal.hidden) return;
    if (event.key === "Escape") closePriceHistory();
    if (event.key !== "Tab") return;
    const controls = [...historyModal.querySelectorAll('button:not(:disabled), input:not(:disabled), [tabindex="0"]')]
      .filter(element => element.getClientRects().length);
    const first = controls[0];
    const last = controls[controls.length - 1];
    if ((event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
      event.preventDefault();
      (event.shiftKey ? last : first)?.focus();
    }
  });

  window.__reloadProductsCatalog = loadCatalog;
  loadCatalog();
})();

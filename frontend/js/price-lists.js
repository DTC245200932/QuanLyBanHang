(() => {
  if (!Auth.requireLogin()) return;
  if (!Auth.hasRole("ROLE_SALES_MANAGER")) {
    window.location.href = "error.html?status=403";
    return;
  }

  const $ = (id) => document.getElementById(id);
  const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[char]);
  const formatCurrency = (value) => new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(Number(value) || 0);
  const formatDate = (value) => value
    ? new Intl.DateTimeFormat("vi-VN").format(new Date(`${value}T00:00:00`))
    : "—";

  const customerGroupLabels = {
    DEALER_LEVEL_1: "Đại lý cấp 1",
    DEALER_LEVEL_2: "Đại lý cấp 2",
    RETAIL: "Khách lẻ",
  };
  const statusLabels = {
    active: "Đang hiệu lực",
    upcoming: "Sắp hiệu lực",
    expired: "Đã hết hiệu lực",
  };

  const state = {
    products: [],
    priceLists: [],
    editingId: null,
    sequence: 1,
  };

  const modal = $("price-list-modal");
  const form = $("price-list-form");
  const formMessage = $("price-form-message");

  function localDateString(date = new Date()) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function defaultEndDate() {
    const date = new Date();
    date.setMonth(date.getMonth() + 1);
    return localDateString(date);
  }

  function priceListStatus(priceList) {
    const today = localDateString();
    if (priceList.endDate < today) return "expired";
    if (priceList.startDate > today) return "upcoming";
    return "active";
  }

  function showToast(message, type = "success") {
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.setAttribute("role", "status");
    toast.textContent = message;
    $("toast-container").appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.remove();
    }, 4200);
  }

  function showFormMessage(message = "") {
    formMessage.textContent = message;
    formMessage.hidden = !message;
  }

  async function loadProducts() {
    try {
      const response = await Auth.apiFetch("/products?active=true");
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success) {
        throw new Error(result.message || "Không tải được danh mục sản phẩm.");
      }
      state.products = (result.data || []).filter((product) => product.active !== false);
      $("catalog-product-count").textContent = state.products.length;
      $("create-price-list").disabled = state.products.length === 0;
      if (!state.products.length) {
        showToast("Chưa có sản phẩm đang kinh doanh để lập bảng giá.", "warning");
      }
    } catch (error) {
      $("catalog-product-count").textContent = "0";
      $("create-price-list").disabled = true;
      showToast(error.message || "Không thể kết nối tới máy chủ.", "error");
    }
  }

  function filteredPriceLists() {
    const keyword = $("price-search").value.trim().toLocaleLowerCase("vi");
    const group = $("customer-group-filter").value;
    const status = $("price-status-filter").value;
    return state.priceLists.filter((priceList) => {
      const matchesKeyword = !keyword
        || priceList.name.toLocaleLowerCase("vi").includes(keyword)
        || priceList.code.toLocaleLowerCase("vi").includes(keyword);
      const matchesGroup = !group || priceList.customerGroup === group;
      const matchesStatus = !status || priceListStatus(priceList) === status;
      return matchesKeyword && matchesGroup && matchesStatus;
    });
  }

  function updateOverview() {
    const activeCount = state.priceLists.filter((item) => priceListStatus(item) === "active").length;
    const approvalCount = state.priceLists.filter((item) => item.lines.some((line) => line.requiresApproval)).length;
    $("price-list-count").textContent = state.priceLists.length;
    $("active-price-list-count").textContent = activeCount;
    $("approval-price-list-count").textContent = approvalCount;
  }

  function renderPriceLists() {
    const priceLists = filteredPriceLists();
    updateOverview();
    $("price-result-summary").textContent = state.priceLists.length
      ? `Hiển thị ${priceLists.length} / ${state.priceLists.length} bảng giá trong phiên.`
      : "Chưa có bảng giá trong phiên làm việc này.";

    if (!priceLists.length) {
      const message = state.priceLists.length
        ? "Không tìm thấy bảng giá phù hợp với bộ lọc."
        : "Chọn “Tạo bảng giá” để soạn bản nháp đầu tiên.";
      $("price-lists-body").innerHTML = `
        <tr><td class="price-empty" colspan="6">
          <strong>${state.priceLists.length ? "Không có kết quả" : "Chưa có bảng giá"}</strong>
          <span>${message}</span>
        </td></tr>`;
      return;
    }

    $("price-lists-body").innerHTML = priceLists.map((priceList) => {
      const status = priceListStatus(priceList);
      const approvalLines = priceList.lines.filter((line) => line.requiresApproval).length;
      const actionLabel = priceList.usedInOrders ? "Tạo phiên bản mới" : "Sửa bản nháp";
      return `
        <tr>
          <td>
            <span class="price-list-name">
              <strong>${escapeHtml(priceList.name)}</strong>
              <span>${escapeHtml(priceList.code)} · Phiên bản ${priceList.version}</span>
            </span>
          </td>
          <td><span class="customer-badge">${escapeHtml(customerGroupLabels[priceList.customerGroup])}</span></td>
          <td>
            <span class="effective-period">
              <strong>${formatDate(priceList.startDate)} – ${formatDate(priceList.endDate)}</strong>
              <span>${priceList.usedInOrders ? "Đã phát sinh đơn" : "Bản nháp chưa lưu server"}</span>
            </span>
          </td>
          <td>
            <strong>${priceList.lines.length} SKU</strong>
            ${approvalLines ? `<span class="approval-badge">${approvalLines} dòng cần duyệt</span>` : ""}
          </td>
          <td><span class="price-status price-status--${status}">${statusLabels[status]}</span></td>
          <td><span class="price-actions"><button class="price-action" data-edit-price-list="${priceList.id}" type="button">${actionLabel}</button></span></td>
        </tr>`;
    }).join("");
  }

  function renderPriceLines(lines = []) {
    if (!state.products.length) {
      $("price-lines-body").innerHTML = '<tr><td class="price-empty" colspan="6">Không có sản phẩm đang kinh doanh.</td></tr>';
      return;
    }

    const lineMap = new Map(lines.map((line) => [String(line.productId), line]));
    $("price-lines-body").innerHTML = state.products.map((product) => {
      const existing = lineMap.get(String(product.id));
      const enabled = Boolean(existing);
      const salePrice = existing?.salePrice ?? product.price ?? "";
      const floorPrice = existing?.floorPrice ?? product.price ?? "";
      const searchText = `${product.code || ""} ${product.name || ""}`.toLocaleLowerCase("vi");
      return `
        <tr class="price-line ${enabled ? "price-line--enabled" : ""}" data-product-id="${product.id}" data-search="${escapeHtml(searchText)}" data-current-price="${Number(product.price) || 0}">
          <td class="line-check"><input class="line-enabled" type="checkbox" ${enabled ? "checked" : ""} aria-label="Áp dụng giá cho ${escapeHtml(product.name)}" /></td>
          <td><span class="product-cell"><strong>${escapeHtml(product.name)}</strong><span>${escapeHtml(product.code)}</span></span></td>
          <td><span class="current-price">${formatCurrency(product.price)}</span></td>
          <td><input class="price-line-input line-sale-price" type="number" min="0" step="1000" value="${escapeHtml(salePrice)}" ${enabled ? "" : "disabled"} aria-label="Giá bán ${escapeHtml(product.name)}" /></td>
          <td><input class="price-line-input line-floor-price" type="number" min="0" step="1000" value="${escapeHtml(floorPrice)}" ${enabled ? "" : "disabled"} aria-label="Giá sàn ${escapeHtml(product.name)}" /></td>
          <td><span class="line-rule">${enabled ? "Đang kiểm tra" : "Chưa áp dụng"}</span></td>
        </tr>`;
    }).join("");

    $("price-lines-body").querySelectorAll(".price-line").forEach(syncPriceLine);
    updateSelectedLineCount();
  }

  function syncPriceLine(row) {
    const checkbox = row.querySelector(".line-enabled");
    const saleInput = row.querySelector(".line-sale-price");
    const floorInput = row.querySelector(".line-floor-price");
    const rule = row.querySelector(".line-rule");
    const enabled = checkbox.checked;

    row.classList.toggle("price-line--enabled", enabled);
    saleInput.disabled = !enabled;
    floorInput.disabled = !enabled;

    if (!enabled) {
      rule.className = "line-rule";
      rule.textContent = "Chưa áp dụng";
      saleInput.removeAttribute("aria-invalid");
      floorInput.removeAttribute("aria-invalid");
      return;
    }

    if (saleInput.value === "") saleInput.value = row.dataset.currentPrice;
    if (floorInput.value === "") floorInput.value = row.dataset.currentPrice;
    const salePrice = Number(saleInput.value);
    const floorPrice = Number(floorInput.value);
    if (!Number.isFinite(salePrice) || !Number.isFinite(floorPrice)) {
      rule.className = "line-rule";
      rule.textContent = "Chưa đủ giá";
    } else if (salePrice < floorPrice) {
      rule.className = "line-rule line-rule--approval";
      rule.textContent = "Cần duyệt";
    } else {
      rule.className = "line-rule line-rule--ok";
      rule.textContent = "Đạt giá sàn";
    }
  }

  function updateSelectedLineCount() {
    const selected = $("price-lines-body").querySelectorAll(".line-enabled:checked").length;
    $("selected-line-count").textContent = selected;
    const checkboxes = [...$("price-lines-body").querySelectorAll(".line-enabled")];
    $("select-all-lines").checked = checkboxes.length > 0 && checkboxes.every((item) => item.checked);
    $("select-all-lines").indeterminate = selected > 0 && selected < checkboxes.length;
  }

  function openPriceModal(priceList = null, createVersion = false) {
    form.reset();
    form.querySelectorAll('[aria-invalid="true"]').forEach((field) => field.removeAttribute("aria-invalid"));
    showFormMessage();
    $("line-product-search").value = "";
    $("select-all-lines").checked = false;
    $("select-all-lines").indeterminate = false;

    const editing = priceList && !createVersion;
    state.editingId = editing ? priceList.id : null;
    $("price-list-id").value = editing ? priceList.id : "";
    $("price-list-version").value = priceList ? priceList.version + (createVersion ? 1 : 0) : 1;
    $("price-modal-title").textContent = createVersion
      ? "Tạo phiên bản bảng giá mới"
      : priceList ? "Cập nhật bản nháp bảng giá" : "Tạo bảng giá";
    $("price-modal-subtitle").textContent = createVersion
      ? "Bản đã phát sinh đơn được giữ nguyên; thay đổi sẽ nằm trong phiên bản kế tiếp."
      : "Khai báo phạm vi áp dụng và giá cho từng SKU.";
    $("price-list-name").value = priceList
      ? `${priceList.name}${createVersion ? ` - Phiên bản ${priceList.version + 1}` : ""}`
      : "";
    $("price-customer-group").value = priceList?.customerGroup || "";
    $("price-start-date").value = createVersion ? localDateString() : priceList?.startDate || localDateString();
    $("price-end-date").value = createVersion ? defaultEndDate() : priceList?.endDate || defaultEndDate();
    $("price-list-description").value = priceList?.description || "";
    renderPriceLines(priceList?.lines || []);
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    $("price-list-name").focus();
  }

  function closePriceModal() {
    modal.hidden = true;
    state.editingId = null;
    document.body.style.overflow = "";
  }

  function collectPriceLines() {
    const rows = [...$("price-lines-body").querySelectorAll(".price-line")]
      .filter((row) => row.querySelector(".line-enabled").checked);
    if (!rows.length) throw new Error("Chọn ít nhất một sản phẩm để áp dụng bảng giá.");

    return rows.map((row) => {
      const saleInput = row.querySelector(".line-sale-price");
      const floorInput = row.querySelector(".line-floor-price");
      const salePrice = Number(saleInput.value);
      const floorPrice = Number(floorInput.value);
      if (saleInput.value === "" || !Number.isFinite(salePrice) || salePrice < 0) {
        saleInput.setAttribute("aria-invalid", "true");
        throw new Error("Giá bán áp dụng phải là số không âm.");
      }
      if (floorInput.value === "" || !Number.isFinite(floorPrice) || floorPrice < 0) {
        floorInput.setAttribute("aria-invalid", "true");
        throw new Error("Giá sàn phải là số không âm.");
      }
      const product = state.products.find((item) => String(item.id) === row.dataset.productId);
      return {
        productId: Number(row.dataset.productId),
        productCode: product?.code || "",
        productName: product?.name || "",
        salePrice,
        floorPrice,
        requiresApproval: salePrice < floorPrice,
      };
    });
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    form.querySelectorAll('[aria-invalid="true"]').forEach((field) => field.removeAttribute("aria-invalid"));
    showFormMessage();

    const requiredIds = ["price-list-name", "price-customer-group", "price-start-date", "price-end-date"];
    const missing = requiredIds.map($).filter((field) => !field.value.trim());
    if (missing.length) {
      missing.forEach((field) => field.setAttribute("aria-invalid", "true"));
      showFormMessage("Nhập đầy đủ tên, nhóm khách hàng và thời gian hiệu lực.");
      return;
    }
    if ($("price-end-date").value < $("price-start-date").value) {
      $("price-end-date").setAttribute("aria-invalid", "true");
      showFormMessage("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
      return;
    }

    let lines;
    try {
      lines = collectPriceLines();
    } catch (error) {
      showFormMessage(error.message);
      return;
    }

    const existing = state.priceLists.find((item) => item.id === state.editingId);
    const priceList = {
      id: existing?.id || `draft-${Date.now()}-${state.sequence}`,
      code: existing?.code || `BG-NHAP-${String(state.sequence++).padStart(3, "0")}`,
      version: Number($("price-list-version").value) || 1,
      name: $("price-list-name").value.trim(),
      customerGroup: $("price-customer-group").value,
      startDate: $("price-start-date").value,
      endDate: $("price-end-date").value,
      description: $("price-list-description").value.trim(),
      lines,
      usedInOrders: existing?.usedInOrders || false,
    };

    if (existing) {
      state.priceLists = state.priceLists.map((item) => item.id === existing.id ? priceList : item);
    } else {
      state.priceLists.unshift(priceList);
    }
    closePriceModal();
    renderPriceLists();
    const approvalCount = lines.filter((line) => line.requiresApproval).length;
    showToast(approvalCount
      ? `Đã lưu bản nháp trong phiên; ${approvalCount} dòng giá cần duyệt.`
      : "Đã lưu bản nháp trong phiên làm việc.", approvalCount ? "warning" : "success");
  });

  $("create-price-list").addEventListener("click", () => openPriceModal());
  document.querySelectorAll("[data-close-price-modal]").forEach((button) => {
    button.addEventListener("click", closePriceModal);
  });
  modal.addEventListener("click", (event) => {
    if (event.target === modal) closePriceModal();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) closePriceModal();
  });

  $("price-lines-body").addEventListener("change", (event) => {
    const row = event.target.closest(".price-line");
    if (!row) return;
    syncPriceLine(row);
    updateSelectedLineCount();
  });
  $("price-lines-body").addEventListener("input", (event) => {
    const row = event.target.closest(".price-line");
    if (row) syncPriceLine(row);
  });
  $("select-all-lines").addEventListener("change", (event) => {
    $("price-lines-body").querySelectorAll(".price-line:not([hidden])").forEach((row) => {
      row.querySelector(".line-enabled").checked = event.target.checked;
      syncPriceLine(row);
    });
    updateSelectedLineCount();
  });
  $("line-product-search").addEventListener("input", (event) => {
    const keyword = event.target.value.trim().toLocaleLowerCase("vi");
    $("price-lines-body").querySelectorAll(".price-line").forEach((row) => {
      row.hidden = Boolean(keyword) && !row.dataset.search.includes(keyword);
    });
  });

  $("price-lists-body").addEventListener("click", (event) => {
    const button = event.target.closest("[data-edit-price-list]");
    if (!button) return;
    const priceList = state.priceLists.find((item) => item.id === button.dataset.editPriceList);
    if (priceList) openPriceModal(priceList, priceList.usedInOrders);
  });

  [$("price-search"), $("customer-group-filter"), $("price-status-filter")].forEach((control) => {
    control.addEventListener(control.tagName === "INPUT" ? "input" : "change", renderPriceLists);
  });
  $("clear-price-filters").addEventListener("click", () => {
    $("price-search").value = "";
    $("customer-group-filter").value = "";
    $("price-status-filter").value = "";
    renderPriceLists();
  });

  renderPriceLists();
  loadProducts();
})();

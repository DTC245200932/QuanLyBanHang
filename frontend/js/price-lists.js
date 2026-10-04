(() => {
  if (!Auth.requireLogin()) return;
  if (!Auth.hasAnyRole("ROLE_SALES_MANAGER", "ROLE_ADMIN")) {
    window.location.href = "/error?status=403";
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
    DAI_LY_CAP_1: "Đại lý cấp 1",
    DAI_LY_CAP_2: "Đại lý cấp 2",
    KHACH_LE: "Khách lẻ",
  };
  const statusLabels = {
    active: "Đang hiệu lực",
    upcoming: "Sắp hiệu lực",
    expired: "Đã hết hiệu lực",
    inactive: "Ngừng áp dụng",
  };

  const state = {
    products: [],
    priceLists: [],
    customerGroups: [],
    editingId: null,
    sourceId: null,
    modalMode: "create",
    loadingPriceLists: true,
    loadError: "",
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
    if (priceList.active === false) return "inactive";
    const today = localDateString();
    if (priceList.endDate < today) return "expired";
    if (priceList.startDate > today) return "upcoming";
    return "active";
  }

  function normalizePriceList(priceList) {
    return {
      ...priceList,
      id: Number(priceList.id),
      version: Number(priceList.version) || 1,
      active: priceList.active !== false,
      hasOrders: Boolean(priceList.hasOrders),
      items: Array.isArray(priceList.items) ? priceList.items.map((item) => ({
        ...item,
        productId: Number(item.productId),
        sellingPrice: Number(item.sellingPrice),
        floorPrice: Number(item.floorPrice),
      })) : [],
    };
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

  async function parseApiResponse(response, fallbackMessage) {
    const result = await response.json().catch(() => ({}));
    if (!response.ok || result.success === false) {
      throw new Error(result.message || fallbackMessage);
    }
    return result;
  }

  function updateCustomerGroupOptions() {
    if (!state.customerGroups.length) return;
    state.customerGroups.forEach((group) => {
      customerGroupLabels[group.code] = group.name;
    });
    const options = state.customerGroups
      .map((group) => `<option value="${escapeHtml(group.code)}">${escapeHtml(group.name)}</option>`)
      .join("");
    $("customer-group-filter").innerHTML = `<option value="">Tất cả nhóm</option>${options}`;
    $("price-customer-group").innerHTML = `<option value="">Chọn nhóm áp dụng</option>${options}`;
  }

  async function loadCustomerGroups() {
    try {
      const response = await Auth.apiFetch("/price-lists/customer-groups");
      const result = await parseApiResponse(response, "Không tải được nhóm khách hàng.");
      state.customerGroups = Array.isArray(result.data) ? result.data : [];
      updateCustomerGroupOptions();
    } catch (error) {
      console.warn(error.message || "Không tải được nhóm khách hàng.");
    }
  }

  async function loadProducts() {
    try {
      const response = await Auth.apiFetch("/products");
      const result = await parseApiResponse(response, "Không tải được danh mục sản phẩm.");
      state.products = Array.isArray(result.data) ? result.data : [];
      const activeProductCount = state.products.filter((product) => product.active !== false).length;
      $("catalog-product-count").textContent = activeProductCount;
      $("create-price-list").disabled = activeProductCount === 0;
      if (!activeProductCount) {
        showToast("Chưa có sản phẩm đang kinh doanh để lập bảng giá.", "warning");
      }
    } catch (error) {
      $("catalog-product-count").textContent = "0";
      $("create-price-list").disabled = true;
      showToast(error.message || "Không thể kết nối tới máy chủ.", "error");
    }
  }

  async function loadPriceLists() {
    state.loadingPriceLists = true;
    state.loadError = "";
    renderPriceLists();
    try {
      const response = await Auth.apiFetch("/price-lists");
      const result = await parseApiResponse(response, "Không tải được danh sách bảng giá.");
      state.priceLists = Array.isArray(result.data)
        ? result.data.map(normalizePriceList)
        : [];
    } catch (error) {
      state.priceLists = [];
      state.loadError = error.message || "Không tải được danh sách bảng giá.";
      showToast(state.loadError, "error");
    } finally {
      state.loadingPriceLists = false;
      renderPriceLists();
    }
  }

  function filteredPriceLists() {
    const keyword = $("price-search").value.trim().toLocaleLowerCase("vi");
    const group = $("customer-group-filter").value;
    const status = $("price-status-filter").value;
    return state.priceLists.filter((priceList) => {
      const name = String(priceList.name || "").toLocaleLowerCase("vi");
      const code = String(priceList.code || "").toLocaleLowerCase("vi");
      const matchesKeyword = !keyword || name.includes(keyword) || code.includes(keyword);
      const matchesGroup = !group || priceList.customerGroup === group;
      const matchesStatus = !status || priceListStatus(priceList) === status;
      return matchesKeyword && matchesGroup && matchesStatus;
    });
  }

  function updateOverview() {
    const activeCount = state.priceLists.filter((item) => priceListStatus(item) === "active").length;
    const approvalCount = state.priceLists.filter((item) => (
      item.items.some((line) => line.sellingPrice < line.floorPrice)
    )).length;
    $("price-list-count").textContent = state.priceLists.length;
    $("active-price-list-count").textContent = activeCount;
    $("approval-price-list-count").textContent = approvalCount;
  }

  function renderPriceLists() {
    updateOverview();

    if (state.loadingPriceLists) {
      $("price-result-summary").textContent = "Đang tải dữ liệu bảng giá…";
      $("price-lists-body").innerHTML = `
        <tr><td class="price-empty" colspan="6">
          <strong>Đang tải bảng giá</strong>
          <span>Vui lòng chờ dữ liệu từ hệ thống.</span>
        </td></tr>`;
      return;
    }

    if (state.loadError) {
      $("price-result-summary").textContent = "Không thể tải dữ liệu bảng giá.";
      $("price-lists-body").innerHTML = `
        <tr><td class="price-empty" colspan="6">
          <strong>Không tải được bảng giá</strong>
          <span>${escapeHtml(state.loadError)}</span>
        </td></tr>`;
      return;
    }

    const priceLists = filteredPriceLists();
    $("price-result-summary").textContent = state.priceLists.length
      ? `Hiển thị ${priceLists.length} / ${state.priceLists.length} bảng giá đã lưu.`
      : "Chưa có bảng giá trong hệ thống.";

    if (!priceLists.length) {
      const hasData = state.priceLists.length > 0;
      $("price-lists-body").innerHTML = `
        <tr><td class="price-empty" colspan="6">
          <strong>${hasData ? "Không có kết quả" : "Chưa có bảng giá"}</strong>
          <span>${hasData
            ? "Không tìm thấy bảng giá phù hợp với bộ lọc."
            : "Chọn “Tạo bảng giá” để thêm bảng giá đầu tiên."}</span>
        </td></tr>`;
      return;
    }

    $("price-lists-body").innerHTML = priceLists.map((priceList) => {
      const status = priceListStatus(priceList);
      const approvalLines = priceList.items.filter((line) => line.sellingPrice < line.floorPrice).length;
      const groupName = customerGroupLabels[priceList.customerGroup]
        || priceList.customerGroupName
        || priceList.customerGroup;
      const actions = priceList.hasOrders
        ? `<button class="price-action" data-price-action="version" data-price-list-id="${priceList.id}" type="button">Tạo phiên bản mới</button>`
        : `<button class="price-action" data-price-action="edit" data-price-list-id="${priceList.id}" type="button">Sửa</button>
           <button class="price-action" data-price-action="version" data-price-list-id="${priceList.id}" type="button">Tạo phiên bản</button>
           <button class="price-action price-action--danger" data-price-action="delete" data-price-list-id="${priceList.id}" type="button">Xóa</button>`;

      return `
        <tr>
          <td>
            <span class="price-list-name">
              <strong>${escapeHtml(priceList.name)}</strong>
              <span>${escapeHtml(priceList.code)} · Phiên bản ${priceList.version}</span>
            </span>
          </td>
          <td><span class="customer-badge">${escapeHtml(groupName)}</span></td>
          <td>
            <span class="effective-period">
              <strong>${formatDate(priceList.startDate)} – ${formatDate(priceList.endDate)}</strong>
              <span>${priceList.hasOrders ? "Đã phát sinh đơn" : "Có thể chỉnh sửa"}</span>
            </span>
          </td>
          <td>
            <strong>${priceList.items.length} SKU</strong>
            ${approvalLines ? `<span class="approval-badge">${approvalLines} dòng cần duyệt</span>` : ""}
          </td>
          <td><span class="price-status price-status--${status}">${statusLabels[status]}</span></td>
          <td><span class="price-actions">${actions}</span></td>
        </tr>`;
    }).join("");
  }

  function renderPriceLines(items = []) {
    if (!state.products.length) {
      $("price-lines-body").innerHTML = '<tr><td class="price-empty" colspan="6">Không có sản phẩm đang kinh doanh.</td></tr>';
      return;
    }

    const itemMap = new Map(items.map((item) => [String(item.productId), item]));
    $("price-lines-body").innerHTML = state.products.map((product) => {
      const existing = itemMap.get(String(product.id));
      const enabled = Boolean(existing);
      const unavailable = product.active === false && !existing;
      const sellingPrice = existing?.sellingPrice ?? product.price ?? "";
      const floorPrice = existing?.floorPrice ?? product.price ?? "";
      const searchText = `${product.code || ""} ${product.name || ""}`.toLocaleLowerCase("vi");
      return `
        <tr class="price-line ${enabled ? "price-line--enabled" : ""}" data-product-id="${product.id}" data-search="${escapeHtml(searchText)}" data-current-price="${Number(product.price) || 0}">
          <td class="line-check"><input class="line-enabled" type="checkbox" ${enabled ? "checked" : ""} ${unavailable ? "disabled" : ""} aria-label="Áp dụng giá cho ${escapeHtml(product.name)}" /></td>
          <td><span class="product-cell"><strong>${escapeHtml(product.name)}</strong><span>${escapeHtml(product.code)}${product.active === false ? " · Ngừng kinh doanh" : ""}</span></span></td>
          <td><span class="current-price">${formatCurrency(product.price)}</span></td>
          <td><input class="price-line-input line-sale-price" type="number" min="0" step="1000" value="${escapeHtml(sellingPrice)}" ${enabled ? "" : "disabled"} aria-label="Giá bán ${escapeHtml(product.name)}" /></td>
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

    if (checkbox.disabled) {
      row.classList.remove("price-line--enabled");
      saleInput.disabled = true;
      floorInput.disabled = true;
      rule.className = "line-rule";
      rule.textContent = "Ngừng kinh doanh";
      return;
    }

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
      rule.textContent = "Thấp hơn giá sàn";
    } else {
      rule.className = "line-rule line-rule--ok";
      rule.textContent = "Đạt giá sàn";
    }
  }

  function updateSelectedLineCount() {
    const selected = $("price-lines-body").querySelectorAll(".line-enabled:checked").length;
    $("selected-line-count").textContent = selected;
    const checkboxes = [...$("price-lines-body").querySelectorAll(".line-enabled:not(:disabled)")];
    $("select-all-lines").checked = checkboxes.length > 0 && checkboxes.every((item) => item.checked);
    $("select-all-lines").indeterminate = selected > 0 && selected < checkboxes.length;
  }

  function nextVersionCode(code, version) {
    return /-V\d+$/i.test(code)
      ? code.replace(/-V\d+$/i, `-V${version}`)
      : `${code}-V${version}`;
  }

  function openPriceModal(priceList = null, mode = "create") {
    form.reset();
    form.querySelectorAll('[aria-invalid="true"]').forEach((field) => field.removeAttribute("aria-invalid"));
    showFormMessage();
    $("line-product-search").value = "";
    $("select-all-lines").checked = false;
    $("select-all-lines").indeterminate = false;

    state.modalMode = mode;
    state.editingId = mode === "edit" ? priceList?.id ?? null : null;
    state.sourceId = mode === "version" ? priceList?.id ?? null : null;

    const nextVersion = priceList ? priceList.version + 1 : 1;
    const isVersion = mode === "version";
    $("price-list-id").value = state.editingId || state.sourceId || "";
    $("price-list-version").value = isVersion ? nextVersion : priceList?.version || 1;
    $("price-modal-title").textContent = isVersion
      ? "Tạo phiên bản bảng giá mới"
      : mode === "edit" ? "Cập nhật bảng giá" : "Tạo bảng giá";
    $("price-modal-subtitle").textContent = isVersion
      ? "Phiên bản hiện tại được giữ nguyên; thay đổi sẽ được lưu thành phiên bản kế tiếp."
      : "Khai báo phạm vi áp dụng và giá cho từng SKU.";
    $("price-list-code").value = isVersion
      ? nextVersionCode(priceList.code, nextVersion)
      : priceList?.code || "";
    $("price-list-name").value = isVersion
      ? `${priceList.name} (v${nextVersion})`
      : priceList?.name || "";
    $("price-customer-group").value = priceList?.customerGroup || "";
    $("price-customer-group").disabled = isVersion;
    $("price-start-date").value = isVersion ? localDateString() : priceList?.startDate || localDateString();
    $("price-end-date").value = isVersion
      ? (priceList.endDate >= localDateString() ? priceList.endDate : defaultEndDate())
      : priceList?.endDate || defaultEndDate();
    $("price-list-description").value = priceList?.description || "";
    $("save-price-list").textContent = isVersion
      ? "Tạo phiên bản mới"
      : mode === "edit" ? "Lưu thay đổi" : "Tạo bảng giá";
    renderPriceLines(priceList?.items || []);
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    $("price-list-code").focus();
  }

  function closePriceModal() {
    modal.hidden = true;
    state.editingId = null;
    state.sourceId = null;
    state.modalMode = "create";
    $("price-customer-group").disabled = false;
    document.body.style.overflow = "";
  }

  function collectPriceItems() {
    const rows = [...$("price-lines-body").querySelectorAll(".price-line")]
      .filter((row) => row.querySelector(".line-enabled").checked);
    if (!rows.length) throw new Error("Chọn ít nhất một sản phẩm để áp dụng bảng giá.");

    return rows.map((row) => {
      const saleInput = row.querySelector(".line-sale-price");
      const floorInput = row.querySelector(".line-floor-price");
      const sellingPrice = Number(saleInput.value);
      const floorPrice = Number(floorInput.value);
      if (saleInput.value === "" || !Number.isFinite(sellingPrice) || sellingPrice < 0) {
        saleInput.setAttribute("aria-invalid", "true");
        throw new Error("Giá bán áp dụng phải là số không âm.");
      }
      if (floorInput.value === "" || !Number.isFinite(floorPrice) || floorPrice < 0) {
        floorInput.setAttribute("aria-invalid", "true");
        throw new Error("Giá sàn phải là số không âm.");
      }
      if (sellingPrice < floorPrice) {
        saleInput.setAttribute("aria-invalid", "true");
        floorInput.setAttribute("aria-invalid", "true");
        throw new Error("Giá bán áp dụng không được thấp hơn giá sàn.");
      }
      const product = state.products.find((item) => String(item.id) === row.dataset.productId);
      return {
        productId: Number(row.dataset.productId),
        productCode: product?.code || "",
        sellingPrice,
        floorPrice,
      };
    });
  }

  async function savePriceList(event) {
    event.preventDefault();
    form.querySelectorAll('[aria-invalid="true"]').forEach((field) => field.removeAttribute("aria-invalid"));
    showFormMessage();

    const requiredIds = [
      "price-list-code",
      "price-list-name",
      "price-customer-group",
      "price-start-date",
      "price-end-date",
    ];
    const missing = requiredIds.map($).filter((field) => !field.value.trim());
    if (missing.length) {
      missing.forEach((field) => field.setAttribute("aria-invalid", "true"));
      showFormMessage("Nhập đầy đủ mã, tên, nhóm khách hàng và thời gian hiệu lực.");
      return;
    }
    if ($("price-end-date").value < $("price-start-date").value) {
      $("price-end-date").setAttribute("aria-invalid", "true");
      showFormMessage("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
      return;
    }

    let items;
    try {
      items = collectPriceItems();
    } catch (error) {
      showFormMessage(error.message);
      return;
    }

    const existing = state.priceLists.find((item) => item.id === state.editingId);
    const commonData = {
      code: $("price-list-code").value.trim().toUpperCase(),
      name: $("price-list-name").value.trim(),
      customerGroup: $("price-customer-group").value,
      startDate: $("price-start-date").value,
      endDate: $("price-end-date").value,
      description: $("price-list-description").value.trim(),
      items,
    };
    const saveButton = $("save-price-list");
    const defaultButtonText = saveButton.textContent;
    saveButton.disabled = true;
    saveButton.textContent = "Đang lưu…";

    try {
      let response;
      if (state.modalMode === "edit") {
        response = await Auth.apiFetch(`/price-lists/${state.editingId}`, {
          method: "PUT",
          body: JSON.stringify({ ...commonData, active: existing?.active !== false }),
        });
      } else if (state.modalMode === "version") {
        response = await Auth.apiFetch(`/price-lists/${state.sourceId}/new-version`, {
          method: "POST",
          body: JSON.stringify({
            newCode: commonData.code,
            newName: commonData.name,
            startDate: commonData.startDate,
            endDate: commonData.endDate,
            description: commonData.description,
            items: commonData.items,
          }),
        });
      } else {
        response = await Auth.apiFetch("/price-lists", {
          method: "POST",
          body: JSON.stringify({ ...commonData, active: true }),
        });
      }

      const result = await parseApiResponse(response, "Không lưu được bảng giá.");
      closePriceModal();
      await loadPriceLists();
      showToast(result.message || "Đã lưu bảng giá.", "success");
    } catch (error) {
      showFormMessage(error.message || "Không lưu được bảng giá.");
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = defaultButtonText;
    }
  }

  async function deletePriceList(priceList, button) {
    if (priceList.hasOrders) {
      showToast("Bảng giá đã phát sinh đơn, không thể xóa. Hãy tạo phiên bản mới.", "warning");
      return;
    }
    if (!window.confirm(`Xóa bảng giá “${priceList.name}”? Hành động này không thể hoàn tác.`)) return;

    const defaultButtonText = button.textContent;
    button.disabled = true;
    button.textContent = "Đang xóa…";
    try {
      const response = await Auth.apiFetch(`/price-lists/${priceList.id}`, { method: "DELETE" });
      const result = await parseApiResponse(response, "Không xóa được bảng giá.");
      await loadPriceLists();
      showToast(result.message || "Đã xóa bảng giá.", "success");
    } catch (error) {
      showToast(error.message || "Không xóa được bảng giá.", "error");
      button.disabled = false;
      button.textContent = defaultButtonText;
    }
  }

  form.addEventListener("submit", savePriceList);
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
      if (row.querySelector(".line-enabled").disabled) return;
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
    const button = event.target.closest("[data-price-action]");
    if (!button) return;
    const priceList = state.priceLists.find((item) => item.id === Number(button.dataset.priceListId));
    if (!priceList) return;

    if (button.dataset.priceAction === "edit") openPriceModal(priceList, "edit");
    if (button.dataset.priceAction === "version") openPriceModal(priceList, "version");
    if (button.dataset.priceAction === "delete") deletePriceList(priceList, button);
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

  Promise.allSettled([loadCustomerGroups(), loadProducts(), loadPriceLists()]);
})();

(() => {
  if (!Auth.requireLogin()) return;

  const canManage = Auth.hasRole("ROLE_SALES_MANAGER");
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
        ? `<td class="cost-column"><span class="product-price">${product.costPrice == null ? "—" : formatCurrency(product.costPrice)}</span>${product.costPrice == null ? '<span class="product-detail">API chưa trả giá vốn</span>' : ""}</td>`
        : "";
      const actions = canManage
        ? `<button class="product-edit" type="button" data-edit-product="${product.id}">Sửa</button>
           <button class="product-edit" type="button" data-toggle-product="${product.id}">${active ? "Ngừng bán" : "Kích hoạt"}</button>
           ${product.canDelete ? `<button class="product-edit" type="button" data-delete-product="${product.id}">Xoá</button>` : ""}`
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
          <td><div class="product-actions">${actions}</div></td>
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
      ? "Backend chưa trả giá vốn hiện tại; cần nhập lại để cập nhật an toàn."
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

  loadCatalog();
})();

/**
 * product-groups.js — S2-06 Quản lý nhóm hàng nhiều cấp.
 *
 * Tính năng:
 * - Hiển thị cây nhóm hàng nhiều cấp (tối thiểu 3 cấp)
 * - Thêm / sửa / xoá nhóm (nhóm còn sản phẩm không xoá được)
 * - Chọn nhóm → xem chi tiết + danh sách sản phẩm
 * - Hiển thị doanh số trực tiếp và tổng doanh số toàn nhánh
 * - Chuyển sản phẩm giữa các nhóm
 * - Tìm kiếm theo tên (highlight + hiện tổ tiên)
 *
 * API endpoints:
 *   GET    /categories/tree
 *   CRUD   /categories
 *   GET    /products?categoryId=:id
 *   PUT    /categories/products/:id/move?targetCategoryId=:categoryId
 */
(() => {
  /* ── Auth guard ────────────────────────────────────────── */
  if (!Auth.requireLogin()) return;

  const CAN_MANAGE = Auth.hasPermission('PRODUCT_GROUP_MANAGE');

  /* ── Helpers ───────────────────────────────────────────── */
  const $ = (id) => document.getElementById(id);
  const escapeHtml = (v) =>
    String(v ?? '').replace(/[&<>'"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c],
    );
  const formatCurrency = (value) => new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value) || 0);

  /* ── State ─────────────────────────────────────────────── */
  const state = {
    /** @type {Array<{id:number,name:string,parentId:number|null,productCount:number,description:string}>} */
    flat: [],
    /** Map id → node augmented with children[] */
    nodeMap: {},
    /** Root nodes */
    roots: [],
    /** Currently selected group id */
    selectedId: null,
    /** Expanded set (ids) */
    expanded: new Set(),
    /** Current search query */
    searchQuery: '',
    /** Products loaded for selected group */
    products: [],
  };

  /* ── Toast ─────────────────────────────────────────────── */
  function showToast(message, type = 'success') {
    const icons = {
      success: '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>',
      error:   '<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>',
      warning: '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
    };
    const container = $('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__icon"><svg width="16" height="16" viewBox="0 0 24 24">${icons[type] || icons.success}</svg></span>${escapeHtml(message)}`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('toast--visible'));
    setTimeout(() => {
      toast.classList.add('toast--exit');
      setTimeout(() => toast.remove(), 300);
    }, 3800);
  }

  /* ── Build tree from flat list ─────────────────────────── */
  function buildTree(flat) {
    const map = {};
    flat.forEach((g) => {
      map[g.id] = { ...g, children: [], level: 0 };
    });

    const roots = [];
    flat.forEach((g) => {
      const node = map[g.id];
      if (g.parentId == null) {
        node.level = 0;
        roots.push(node);
      } else if (map[g.parentId]) {
        node.level = map[g.parentId].level + 1;
        map[g.parentId].children.push(node);
      } else {
        // Orphan — treat as root
        node.level = 0;
        roots.push(node);
      }
    });

    // Fix levels recursively (parentId resolution order may vary)
    function fixLevels(nodes, level) {
      nodes.forEach((n) => {
        n.level = level;
        fixLevels(n.children, level + 1);
      });
    }
    fixLevels(roots, 0);

    state.nodeMap = map;
    state.roots   = roots;
  }

  function flattenCategoryTree(nodes, parentId = null) {
    return (nodes || []).flatMap((node) => {
      const { children = [], ...category } = node;
      const normalized = {
        ...category,
        parentId: category.parentId ?? parentId,
      };
      return [normalized, ...flattenCategoryTree(children, node.id)];
    });
  }

  /* ── Render tree ───────────────────────────────────────── */
  function renderTree() {
    const container = $('tree-container');

    if (!state.roots.length) {
      container.innerHTML = '<div class="pg-empty"><p>Chưa có nhóm hàng nào. Bấm «Thêm nhóm gốc» để bắt đầu.</p></div>';
      return;
    }

    const html = [];
    renderNodes(state.roots, html);
    container.innerHTML = html.join('');

    // Attach event listeners after render
    container.querySelectorAll('.tree-node').forEach((el) => {
      const id = Number(el.dataset.id);

      el.addEventListener('click', (e) => {
        // Prevent select when clicking action buttons or toggle
        if (e.target.closest('.tree-node__actions') || e.target.closest('.tree-node__toggle')) return;
        selectNode(id);
      });
    });

    container.querySelectorAll('.tree-node__toggle').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = Number(btn.closest('.tree-node').dataset.id);
        toggleExpand(id);
      });
    });

    container.querySelectorAll('[data-action="edit"]').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = Number(btn.dataset.id);
        openGroupModal(state.nodeMap[id]);
      });
    });

    container.querySelectorAll('[data-action="add-child"]').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = Number(btn.dataset.id);
        openGroupModal(null, id);
      });
    });

    container.querySelectorAll('[data-action="delete"]').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = Number(btn.dataset.id);
        deleteGroup(id);
      });
    });

    applySearchFilter();
    applyExpandState();
  }

  function renderNodes(nodes, html) {
    nodes.forEach((node) => {
      const hasChildren   = node.children.length > 0;
      const isSelected    = node.id === state.selectedId;
      const isExpanded    = state.expanded.has(node.id);
      const hasProducts   = node.productCount > 0;

      const levelClass    = `tree-node--level-${Math.min(node.level, 4)}`;
      const selectedClass = isSelected ? 'tree-node--selected' : '';
      const collapseClass = hasChildren && !isExpanded ? 'tree-node--collapsed' : '';

      const toggleHtml = hasChildren
        ? `<button class="tree-node__toggle" title="${isExpanded ? 'Thu gọn' : 'Mở rộng'}">
             <svg viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>
           </button>`
        : `<span class="tree-node__toggle tree-node__toggle--leaf"></span>`;

      const actionsHtml = CAN_MANAGE
        ? `<div class="tree-node__actions">
             <button class="tree-node__btn" data-action="edit" data-id="${node.id}" title="Sửa nhóm">
               <svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
             </button>
             <button class="tree-node__btn" data-action="add-child" data-id="${node.id}" title="Thêm nhóm con">
               <svg viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
             </button>
             <button class="tree-node__btn tree-node__btn--danger${hasProducts || node.children.length ? ' tree-node__btn--disabled' : ''}"
                     data-action="delete" data-id="${node.id}" title="${hasProducts ? 'Nhóm còn sản phẩm, không thể xoá' : 'Xoá nhóm'}">
               <svg viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>
             </button>
           </div>`
        : '';

      html.push(
        `<div class="tree-node ${levelClass} ${selectedClass} ${collapseClass}"
              data-id="${node.id}" data-level="${node.level}" data-parent="${node.parentId ?? ''}">
           ${toggleHtml}
           <span class="tree-node__name" title="${escapeHtml(node.name)}">${escapeHtml(node.name)}</span>
           <span class="tree-node__count" title="${node.productCount} sản phẩm">${node.productCount}</span>
           ${actionsHtml}
         </div>`,
      );

      if (node.children.length) {
        renderNodes(node.children, html);
      }
    });
  }

  /* ── Expand/collapse ───────────────────────────────────── */
  function toggleExpand(id) {
    if (state.expanded.has(id)) {
      state.expanded.delete(id);
    } else {
      state.expanded.add(id);
    }
    applyExpandState();
  }

  function applyExpandState() {
    const container = $('tree-container');
    container.querySelectorAll('.tree-node').forEach((el) => {
      const id       = Number(el.dataset.id);
      const parentId = el.dataset.parent ? Number(el.dataset.parent) : null;
      const node     = state.nodeMap[id];
      if (!node) return;

      const hasChildren = node.children.length > 0;

      // Collapse styling
      if (hasChildren) {
        if (state.expanded.has(id)) {
          el.classList.remove('tree-node--collapsed');
        } else {
          el.classList.add('tree-node--collapsed');
        }
      }

      // Visibility: hidden if any ancestor is collapsed
      if (parentId !== null) {
        const hidden = isAncestorCollapsed(id);
        el.classList.toggle('tree-node--hidden', hidden);
      } else {
        el.classList.remove('tree-node--hidden');
      }
    });
  }

  function isAncestorCollapsed(id) {
    const node = state.nodeMap[id];
    if (!node || node.parentId == null) return false;
    if (!state.expanded.has(node.parentId)) return true;
    return isAncestorCollapsed(node.parentId);
  }

  /* ── Search filter ─────────────────────────────────────── */
  function applySearchFilter() {
    const q = state.searchQuery.toLowerCase().trim();
    const container = $('tree-container');

    if (!q) {
      container.querySelectorAll('.tree-node').forEach((el) => {
        el.classList.remove('tree-node--match');
        el.classList.remove('tree-node--hidden');
      });
      applyExpandState();
      return;
    }

    // Find matching ids
    const matchIds = new Set();
    state.flat.forEach((g) => {
      if (g.name.toLowerCase().includes(q)) matchIds.add(g.id);
    });

    // Include ancestors of matched nodes
    const visibleIds = new Set(matchIds);
    matchIds.forEach((id) => {
      let cur = state.nodeMap[id];
      while (cur && cur.parentId != null) {
        visibleIds.add(cur.parentId);
        cur = state.nodeMap[cur.parentId];
        if (cur) state.expanded.add(cur.id);
      }
    });

    container.querySelectorAll('.tree-node').forEach((el) => {
      const id = Number(el.dataset.id);
      const visible = visibleIds.has(id);
      el.classList.toggle('tree-node--hidden', !visible);
      el.classList.toggle('tree-node--match', matchIds.has(id));
      if (visible) el.classList.remove('tree-node--collapsed');
    });
  }

  /* ── Select node ───────────────────────────────────────── */
  function selectNode(id) {
    state.selectedId = id;

    // Update selected style
    $('tree-container').querySelectorAll('.tree-node').forEach((el) => {
      el.classList.toggle('tree-node--selected', Number(el.dataset.id) === id);
    });

    // Auto-expand if needed
    if (!state.expanded.has(id)) {
      state.expanded.add(id);
      applyExpandState();
    }

    renderDetail(id);
  }

  /* ── Detail panel ──────────────────────────────────────── */
  function renderDetail(id) {
    const node = state.nodeMap[id];
    if (!node) return;

    const breadcrumb = buildBreadcrumb(id);
    const levelLabel = `Cấp ${node.level + 1}`;

    $('detail-panel').innerHTML = `
      <div class="detail-header">
        <div class="detail-header__info">
          <div class="detail-breadcrumb">${breadcrumb.map((n) => `<span>${escapeHtml(n.name)}</span>`).join('')}</div>
          <h2>${escapeHtml(node.name)}</h2>
          <span class="level-badge">Cấp ${node.level + 1}</span>
        </div>
        ${CAN_MANAGE ? `
        <div class="detail-header__actions">
          <button class="btn btn--secondary" id="detail-edit-btn">Sửa</button>
          <button class="btn btn--primary" id="detail-add-child-btn">Thêm nhóm con</button>
        </div>` : ''}
      </div>
      <div class="detail-stats">
        <div class="detail-stat">
          <span class="detail-stat__label">Cấp</span>
          <span class="detail-stat__value">${node.level + 1}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat__label">Sản phẩm trực tiếp</span>
          <span class="detail-stat__value detail-stat__value--teal">${node.productCount}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat__label">Nhóm con</span>
          <span class="detail-stat__value">${node.children.length}</span>
        </div>
        <div class="detail-stat detail-stat--revenue">
          <span class="detail-stat__label">Doanh số trực tiếp</span>
          <span class="detail-stat__value detail-stat__value--currency">${formatCurrency(node.directRevenue)}</span>
        </div>
        <div class="detail-stat detail-stat--revenue detail-stat--revenue-total">
          <span class="detail-stat__label">Tổng doanh số toàn nhánh</span>
          <span class="detail-stat__value detail-stat__value--currency detail-stat__value--teal">${formatCurrency(node.totalRevenue)}</span>
        </div>
      </div>
      <div class="detail-description">
        ${node.description ? escapeHtml(node.description) : '<em>Chưa có mô tả</em>'}
      </div>
      <div class="detail-tabs">
        <span class="detail-tab detail-tab--active" data-tab="products">Sản phẩm (${node.productCount})</span>
        ${node.children.length ? `<span class="detail-tab" data-tab="subgroups">Nhóm con (${node.children.length})</span>` : ''}
      </div>
      <div class="detail-tab-content" id="detail-tab-content">
        <div class="detail-loading">Đang tải sản phẩm…</div>
      </div>
    `;

    // Tab switching
    $('detail-panel').querySelectorAll('.detail-tab').forEach((tab) => {
      tab.addEventListener('click', () => {
        $('detail-panel').querySelectorAll('.detail-tab').forEach((t) => t.classList.remove('detail-tab--active'));
        tab.classList.add('detail-tab--active');
        if (tab.dataset.tab === 'products') {
          loadProducts(id);
        } else {
          renderSubgroups(node);
        }
      });
    });

    // Header action buttons
    if (CAN_MANAGE) {
      $('detail-edit-btn')?.addEventListener('click', () => openGroupModal(node));
      $('detail-add-child-btn')?.addEventListener('click', () => openGroupModal(null, id));
    }

    // Load products by default
    loadProducts(id);
  }

  function buildBreadcrumb(id) {
    const path = [];
    let cur = state.nodeMap[id];
    while (cur) {
      path.unshift(cur);
      cur = cur.parentId != null ? state.nodeMap[cur.parentId] : null;
    }
    return path;
  }

  /* ── Load products in detail ───────────────────────────── */
  async function loadProducts(groupId) {
    const contentEl = $('detail-tab-content');
    if (!contentEl) return;
    contentEl.innerHTML = '<div class="detail-loading">Đang tải sản phẩm…</div>';

    try {
      const res    = await Auth.apiFetch(`/products?categoryId=${encodeURIComponent(groupId)}`);
      const result = await res.json();
      if (!res.ok) throw new Error(result.message || 'Không tải được danh sách sản phẩm');

      const products = result.data || [];
      state.products = products;

      if (!products.length) {
        contentEl.innerHTML = '<div class="pg-empty"><p>Nhóm này chưa có sản phẩm nào.</p></div>';
        return;
      }

      contentEl.innerHTML = products.map((p) => `
        <div class="product-row">
          <div class="product-row__info">
            <div class="product-row__name">${escapeHtml(p.name)}</div>
            <div class="product-row__code">${escapeHtml(p.code || '')}</div>
          </div>
          ${CAN_MANAGE ? `<button class="product-row__btn" data-move="${p.id}" data-name="${escapeHtml(p.name)}">Chuyển nhóm</button>` : ''}
        </div>
      `).join('');

      contentEl.querySelectorAll('[data-move]').forEach((btn) => {
        btn.addEventListener('click', () => {
          openMoveModal(Number(btn.dataset.move), btn.dataset.name, groupId);
        });
      });
    } catch (e) {
      contentEl.innerHTML = `<div class="pg-empty"><p>${escapeHtml(e.message)}</p></div>`;
    }
  }

  /* ── Render sub-groups in detail ───────────────────────── */
  function renderSubgroups(node) {
    const contentEl = $('detail-tab-content');
    if (!contentEl) return;

    if (!node.children.length) {
      contentEl.innerHTML = '<div class="pg-empty"><p>Không có nhóm con.</p></div>';
      return;
    }

    contentEl.innerHTML = node.children.map((child) => `
      <div class="subgroup-row" data-nav="${child.id}">
        <div class="subgroup-row__icon">
          <svg viewBox="0 0 24 24"><path d="M3 6h18M3 12h12M3 18h8"/></svg>
        </div>
        <span class="subgroup-row__name">${escapeHtml(child.name)}</span>
        <span class="subgroup-row__count">${child.productCount} sản phẩm · ${child.children.length} nhóm con</span>
      </div>
    `).join('');

    contentEl.querySelectorAll('[data-nav]').forEach((row) => {
      row.addEventListener('click', () => selectNode(Number(row.dataset.nav)));
    });
  }

  /* ── Load all groups ───────────────────────────────────── */
  async function loadTree() {
    $('tree-container').innerHTML = '<div class="pg-empty">Đang tải danh mục…</div>';

    try {
      const res    = await Auth.apiFetch('/categories/tree');
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Không tải được dữ liệu');

      state.flat = flattenCategoryTree(result.data).map((group) => ({
        ...group,
        productCount: Number(group.directProductCount ?? 0),
        totalProductCount: Number(group.totalProductCount ?? group.directProductCount ?? 0),
        directRevenue: Number(group.directRevenue ?? 0),
        totalRevenue: Number(group.totalRevenue ?? group.directRevenue ?? 0),
      }));
      buildTree(state.flat);
      renderTree();

      // Re-select current node if still exists
      if (state.selectedId && state.nodeMap[state.selectedId]) {
        renderDetail(state.selectedId);
      } else if (state.selectedId) {
        // Node was deleted — clear detail
        state.selectedId = null;
        $('detail-panel').innerHTML = `
          <div class="pg-empty pg-empty--detail">
            <svg width="48" height="48" viewBox="0 0 24 24" stroke-width="1.2"><path d="M3 6h18M3 12h12M3 18h8"/></svg>
            <p>Chọn một nhóm hàng để xem chi tiết</p>
          </div>`;
      }
    } catch (e) {
      $('tree-container').innerHTML = `<div class="pg-empty"><p>${escapeHtml(e.message || 'Lỗi tải dữ liệu')}</p></div>`;
      showToast(e.message || 'Không tải được dữ liệu nhóm hàng', 'error');
    }
  }

  /* ── Group modal ───────────────────────────────────────── */
  function populateParentSelect(excludeId = null) {
    const sel = $('group-parent');
    sel.innerHTML = '<option value="">— Nhóm gốc (cấp 1) —</option>';

    // Flat list for select (depth-first, with indent)
    function addOptions(nodes, depth) {
      nodes.forEach((node) => {
        if (node.id === excludeId) return; // Can't set self as parent
        const prefix = '\u00A0\u00A0\u00A0\u00A0'.repeat(depth);
        const opt    = document.createElement('option');
        opt.value       = node.id;
        opt.textContent = `${prefix}${node.name}`;
        sel.appendChild(opt);
        if (node.children.length) addOptions(node.children, depth + 1);
      });
    }
    addOptions(state.roots, 0);
  }

  function populateMoveTargetSelect(excludeGroupId) {
    const sel = $('move-target-group');
    sel.innerHTML = '<option value="">— Chọn nhóm —</option>';

    function addOptions(nodes, depth) {
      nodes.forEach((node) => {
        if (node.id === excludeGroupId) return;
        const prefix = '\u00A0\u00A0\u00A0\u00A0'.repeat(depth);
        const opt    = document.createElement('option');
        opt.value       = node.id;
        opt.textContent = `${prefix}${node.name}`;
        sel.appendChild(opt);
        if (node.children.length) addOptions(node.children, depth + 1);
      });
    }
    addOptions(state.roots, 0);
  }

  /**
   * @param {object|null} group  – existing group (edit) or null (create)
   * @param {number|null} presetParentId – pre-select parent when adding child
   */
  function openGroupModal(group = null, presetParentId = null) {
    if (!CAN_MANAGE) { showToast('Bạn không có quyền quản lý nhóm hàng.', 'error'); return; }

    $('group-form').reset();
    $('group-form-message').hidden = true;

    if (group) {
      $('group-modal-title').textContent = 'Sửa nhóm hàng';
      $('group-modal-sub').textContent   = 'Cập nhật thông tin nhóm hàng.';
      $('group-id').value                = group.id;
      $('group-code').value              = group.code || '';
      $('group-name').value              = group.name;
      $('group-desc').value              = group.description || '';
      populateParentSelect(group.id);
      $('group-parent').value = group.parentId ?? '';
    } else {
      $('group-modal-title').textContent = 'Thêm nhóm hàng';
      $('group-modal-sub').textContent   = 'Tạo nhóm hàng mới trong danh mục.';
      $('group-id').value                = '';
      $('group-code').value              = '';
      populateParentSelect(null);
      if (presetParentId != null) $('group-parent').value = presetParentId;
    }

    $('group-modal').hidden = false;
    $('group-name').focus();
  }

  function closeGroupModal() {
    $('group-modal').hidden = true;
  }

  $('group-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id       = $('group-id').value;
    const parentId = $('group-parent').value ? Number($('group-parent').value) : null;
    const payload  = {
      code:        $('group-code').value.trim().toUpperCase(),
      name:        $('group-name').value.trim(),
      parentId,
      description: $('group-desc').value.trim(),
    };

    if (!payload.code || !payload.name) {
      showFormError('group-form-message', 'Vui lòng nhập mã và tên nhóm.');
      return;
    }

    const btn       = $('save-group');
    btn.disabled    = true;
    btn.textContent = 'Đang lưu…';

    try {
      const res    = await Auth.apiFetch(
        id ? `/categories/${id}` : '/categories',
        { method: id ? 'PUT' : 'POST', body: JSON.stringify(payload) },
      );
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Không lưu được nhóm hàng');

      closeGroupModal();
      showToast(id ? 'Đã cập nhật nhóm hàng.' : 'Đã tạo nhóm hàng mới.', 'success');
      await loadTree();

      // Select newly created/edited group
      const savedId = result.data?.id ?? (id ? Number(id) : null);
      if (savedId && state.nodeMap[savedId]) {
        selectNode(savedId);
      }
    } catch (err) {
      showFormError('group-form-message', err.message || 'Lỗi không xác định');
    } finally {
      btn.disabled    = false;
      btn.textContent = 'Lưu nhóm';
    }
  });

  /* ── Delete group ──────────────────────────────────────── */
  async function deleteGroup(id) {
    if (!CAN_MANAGE) { showToast('Bạn không có quyền quản lý nhóm hàng.', 'error'); return; }

    const node = state.nodeMap[id];
    if (!node) return;

    if (node.productCount > 0 || node.children.length > 0) {
      showToast('Nhóm này còn sản phẩm hoặc nhóm con, không thể xoá.', 'error');
      return;
    }

    if (!confirm(`Xoá nhóm «${node.name}»? Hành động này không thể hoàn tác.`)) return;

    try {
      const res    = await Auth.apiFetch(`/categories/${id}`, { method: 'DELETE' });
      if (res.status === 400 || res.status === 409) {
        showToast('Nhóm này còn sản phẩm, không thể xoá.', 'error');
        return;
      }
      const result = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(result.message || 'Không xoá được nhóm hàng');

      showToast('Đã xoá nhóm hàng.', 'success');
      if (state.selectedId === id) state.selectedId = null;
      await loadTree();
    } catch (err) {
      showToast(err.message || 'Lỗi khi xoá nhóm hàng', 'error');
    }
  }

  /* ── Move product modal ────────────────────────────────── */
  function openMoveModal(productId, productName, currentGroupId) {
    if (!CAN_MANAGE) { showToast('Bạn không có quyền chuyển sản phẩm.', 'error'); return; }

    $('move-product-id').value          = productId;
    $('move-product-name').textContent  = productName;
    $('move-form-message').hidden       = true;
    populateMoveTargetSelect(currentGroupId);
    $('move-target-group').value        = '';
    $('move-modal').hidden              = false;
  }

  function closeMoveModal() {
    $('move-modal').hidden = true;
  }

  $('confirm-move').addEventListener('click', async () => {
    const productId     = Number($('move-product-id').value);
    const targetGroupId = Number($('move-target-group').value);

    if (!targetGroupId) {
      showFormError('move-form-message', 'Vui lòng chọn nhóm đích.');
      return;
    }

    const btn       = $('confirm-move');
    btn.disabled    = true;
    btn.textContent = 'Đang chuyển…';

    try {
      const res    = await Auth.apiFetch(`/categories/products/${productId}/move?targetCategoryId=${encodeURIComponent(targetGroupId)}`, {
        method: 'PUT',
      });
      const result = await res.json().catch(() => ({}));
      if (!res.ok || !result.success) throw new Error(result.message || 'Không chuyển được sản phẩm');

      closeMoveModal();
      showToast('Đã chuyển sản phẩm sang nhóm mới.', 'success');
      await loadTree();
      if (state.selectedId) renderDetail(state.selectedId);
    } catch (err) {
      showFormError('move-form-message', err.message || 'Lỗi không xác định');
    } finally {
      btn.disabled    = false;
      btn.textContent = 'Chuyển nhóm';
    }
  });

  /* ── Form error helper ─────────────────────────────────── */
  function showFormError(elId, text) {
    const el     = $(elId);
    el.hidden    = false;
    el.textContent = text;
  }

  /* ── Event bindings ────────────────────────────────────── */

  // Add root group
  $('add-root-group').addEventListener('click', () => openGroupModal(null, null));

  // Search
  let searchTimer;
  $('pg-search').addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.searchQuery = $('pg-search').value;
      applySearchFilter();
    }, 200);
  });

  // Modal close buttons (group modal)
  document.querySelectorAll('[data-close]').forEach((btn) =>
    btn.addEventListener('click', closeGroupModal),
  );

  // Modal close buttons (move modal)
  document.querySelectorAll('[data-close-move]').forEach((btn) =>
    btn.addEventListener('click', closeMoveModal),
  );

  // Click outside modal to close
  $('group-modal').addEventListener('click', (e) => {
    if (e.target === $('group-modal')) closeGroupModal();
  });
  $('move-modal').addEventListener('click', (e) => {
    if (e.target === $('move-modal')) closeMoveModal();
  });

  /* ── Init ──────────────────────────────────────────────── */
  loadTree();
})();

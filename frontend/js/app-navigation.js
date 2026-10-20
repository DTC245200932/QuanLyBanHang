/** Sidebar và breadcrumb dùng cùng một cấu hình trang trên toàn ứng dụng. */
const AppNavigation = (() => {
  function renderSidebar(user) {
    const nav = document.getElementById('sidebar-nav');
    if (!nav) return;
    const fragment = document.createDocumentFragment();
    let lastSection = null;
    for (const item of MenuConfig.getMenuForUser(user)) {
      if (item.section && item.section !== lastSection) {
        lastSection = item.section;
        const section = document.createElement('div');
        section.className = 'sidebar__section-label';
        section.textContent = item.section;
        fragment.append(section);
      }
      const link = document.createElement('a');
      link.className = 'sidebar__link';
      link.href = item.href;
      link.dataset.page = item.id;
      link.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${item.icon}</svg><span></span>`;
      link.querySelector('span').textContent = item.label;
      fragment.append(link);
    }
    nav.replaceChildren(fragment);
  }

  function renderBreadcrumb(page) {
    const container = document.querySelector('.topbar__breadcrumb');
    if (!container || !page) return;
    const nav = document.createElement('nav');
    nav.className = 'app-breadcrumb';
    nav.setAttribute('aria-label', 'Đường dẫn điều hướng');
    const list = document.createElement('ol');
    for (const [index, crumb] of MenuConfig.getBreadcrumbs(page).entries()) {
      const item = document.createElement('li');
      if (index) {
        const separator = document.createElement('span');
        separator.className = 'app-breadcrumb__separator';
        separator.setAttribute('aria-hidden', 'true');
        separator.textContent = '›';
        item.append(separator);
      }
      const label = document.createElement(crumb.href ? 'a' : 'span');
      label.textContent = crumb.label;
      if (crumb.href) label.href = crumb.href;
      if (crumb.current) label.setAttribute('aria-current', 'page');
      item.append(label);
      list.append(item);
    }
    nav.append(list);
    container.replaceChildren(nav);
  }

  function refresh() {
    const page = MenuConfig.getPageForLocation(window.location);
    document.querySelectorAll('#sidebar-nav [data-page]').forEach((link) => {
      const active = link.dataset.page === page?.id;
      link.classList.toggle('sidebar__link--active', active);
      if (active) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    renderBreadcrumb(page);
  }

  async function initialize() {
    if (!Auth.isLoggedIn()) return;
    // Nạp lại permissions từ DB trước khi dựng menu để thay đổi
    // ma trận không tạo ra tình trạng menu hiện nhưng API trả 403 (hoặc ngược lại).
    await Auth.ready;
    renderSidebar(Auth.getUser());
    refresh();
    enforcePageAccess();
  }

  function enforcePageAccess() {
    const page = MenuConfig.getPageForLocation(window.location);
    if (page && !['dashboard', 'profile'].includes(page.id)
        && !MenuConfig.getMenuForUser(Auth.getUser()).some(item => item.id === page.id)) {
      window.location.href = '/error?status=403';
    }
  }

  const actionPermissions = {
    products: [
      ['#create-product', 'PRODUCT_CREATE'],
      ['#btn-import-excel', 'PRODUCT_CREATE', 'PRODUCT_EDIT'],
    ],
    'price-lists': [['[data-price-action], #save-price-list', 'PRODUCT_EDIT']],
    'discount-policies': [['[data-action="edit"], [data-action="delete"], #save-disc-policy', 'PRODUCT_EDIT']],
    dealers: [
      ['#btn-add-dealer', 'CUSTOMER_CREATE'],
      ['[data-action="edit"], [data-action="assign"], [data-action="lock"], [data-action="unlock"], [data-action="activate"], [data-action="deactivate"], #btn-add-delivery-point', 'CUSTOMER_EDIT'],
      ['[data-action="orders"], [data-action="order-history"]', 'ORDER_VIEW'],
    ],
    users: [
      ['#create-user, #import-users, #confirm-user-import', 'USER_CREATE', 'USER_ASSIGN_ROLE'],
      ['[data-edit]', 'USER_EDIT'],
      ['[data-toggle]', 'USER_LOCK'],
    ],
    'dealer-orders': [
      ['#btn-reorder-from-detail, #btn-submit-reorder, [data-action="reorder"]', 'ORDER_CREATE', 'PRODUCT_VIEW'],
    ],
  };
  const pageId = MenuConfig.getPageForLocation(window.location)?.id;
  (actionPermissions[pageId] || []).forEach(binding => Auth.bindPermissions(...binding));

  initialize();
  window.addEventListener('auth:permissions-changed', () => {
    const container = document.getElementById('sidebar-nav');
    const scrollTop = container?.scrollTop || 0;
    renderSidebar(Auth.getUser());
    if (container) container.scrollTop = scrollTop;
    refresh();
    enforcePageAccess();
  });
  window.addEventListener('pageshow', refresh);
  window.addEventListener('popstate', refresh);
  return { refresh };
})();

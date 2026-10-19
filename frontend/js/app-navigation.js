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
    // Nạp lại permissions từ refresh endpoint trước khi dựng menu để thay đổi
    // ma trận không tạo ra tình trạng menu hiện nhưng API trả 403 (hoặc ngược lại).
    await Auth.refreshPermissions();
    renderSidebar(Auth.getUser());
    refresh();
  }

  initialize();
  window.addEventListener('pageshow', refresh);
  window.addEventListener('popstate', refresh);
  return { refresh };
})();

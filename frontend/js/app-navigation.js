/** Sidebar và breadcrumb dùng cùng một cấu hình trang trên toàn ứng dụng. */
const AppNavigation = (() => {
  const SIDEBAR_SCROLL_PARAM = 'sidebarScroll';

  function restoreSidebarScroll(nav) {
    const url = new URL(window.location.href);
    const rawScrollTop = url.searchParams.get(SIDEBAR_SCROLL_PARAM);
    if (rawScrollTop === null) return;

    const scrollTop = Number.parseInt(rawScrollTop, 10);
    url.searchParams.delete(SIDEBAR_SCROLL_PARAM);
    window.history.replaceState(
      window.history.state,
      document.title,
      `${url.pathname}${url.search}${url.hash}`,
    );

    window.requestAnimationFrame(() => {
      nav.scrollTop = Number.isFinite(scrollTop) ? Math.max(0, scrollTop) : 0;
    });
  }

  function rememberSidebarScroll(nav, link) {
    const target = new URL(link.href, window.location.href);
    const scrollTop = Math.round(nav.scrollTop);
    if (scrollTop > 0) target.searchParams.set(SIDEBAR_SCROLL_PARAM, String(scrollTop));
    else target.searchParams.delete(SIDEBAR_SCROLL_PARAM);
    link.href = target.href;
  }

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

    if (!nav.dataset.scrollTransferBound) {
      nav.addEventListener('click', (event) => {
        const link = event.target.closest('a[href]');
        if (link) rememberSidebarScroll(nav, link);
      });
      nav.dataset.scrollTransferBound = 'true';
    }
    restoreSidebarScroll(nav);
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

  if (Auth.isLoggedIn()) {
    renderSidebar(Auth.getUser());
    refresh();
  }
  window.addEventListener('pageshow', refresh);
  window.addEventListener('popstate', refresh);
  return { refresh };
})();

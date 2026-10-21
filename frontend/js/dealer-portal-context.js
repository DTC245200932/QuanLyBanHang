/** Ngữ cảnh đại lý trên cổng: ADMIN chọn đại lý; DEALER dùng liên kết do server xác định. */
const DealerPortalContext = (() => {
  const isAdmin = (Auth.getUser()?.roles || []).includes('ROLE_ADMIN');
  let dealerId = null;

  function url(path) {
    if (!isAdmin) return path;
    if (!dealerId) throw new Error('Vui lòng chọn đại lý.');
    return `${path}${path.includes('?') ? '&' : '?'}dealerId=${encodeURIComponent(dealerId)}`;
  }

  async function init(wrapperId, selectId) {
    if (!isAdmin) return true;
    const wrapper = document.getElementById(wrapperId);
    const select = document.getElementById(selectId);
    wrapper.hidden = false;
    select.disabled = true;
    const response = await Auth.apiFetch('/portal/dealers');
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || 'Không tải được danh sách đại lý. Vui lòng tải lại trang.');
    }
    const dealers = payload.data || [];
    const requested = new URLSearchParams(window.location.search).get('dealerId');
    dealerId = dealers.some(dealer => String(dealer.id) === requested) ? requested : null;
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = dealers.length ? 'Chọn đại lý để tiếp tục' : 'Chưa có hồ sơ đại lý';
    select.replaceChildren(placeholder);
    for (const dealer of dealers) {
      const option = document.createElement('option');
      option.value = String(dealer.id);
      option.textContent = `${dealer.code} — ${dealer.name}`;
      select.append(option);
    }
    select.value = dealerId || '';
    select.disabled = !dealers.length;
    select.addEventListener('change', () => {
      const query = new URLSearchParams(window.location.search);
      if (select.value) query.set('dealerId', select.value);
      else query.delete('dealerId');
      window.location.href = `${window.location.pathname}${query.size ? `?${query}` : ''}`;
    });
    return Boolean(dealerId);
  }

  return { isAdmin, init, url };
})();

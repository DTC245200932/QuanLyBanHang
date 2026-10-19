/** Xem và in đúng bản PDF được backend tạo từ đơn đã lưu. */
const OrderPdf = (() => {
  let objectUrl = null;
  const element = (id) => document.getElementById(id);

  function close() {
    const preview = element('order-print-area-preview');
    if (preview) preview.replaceChildren();
    const modal = element('print-modal');
    if (modal) modal.hidden = true;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }

  async function open(order) {
    if (!Auth.hasPermission('ORDER_VIEW')) return;
    const response = await Auth.apiFetch(`/orders/${encodeURIComponent(order.id)}/pdf`);
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || 'Không tải được bản PDF. Bạn có thể mở lại đơn từ danh sách.');
    }
    const pdf = await response.blob();
    if (!pdf.type.includes('application/pdf')) throw new Error('Máy chủ chưa trả về bản PDF hợp lệ.');
    close();
    objectUrl = URL.createObjectURL(pdf);
    const frame = document.createElement('iframe');
    frame.title = `Đơn hàng ${order.orderCode || order.id}`;
    frame.src = objectUrl;
    frame.style.cssText = 'width:100%;height:65vh;border:0';
    element('order-print-area-preview').replaceChildren(frame);
    element('print-modal-title').textContent = `Đơn ${order.orderCode || order.id} đã được tạo`;
    element('print-modal').hidden = false;
  }

  element('btn-close-print-modal')?.addEventListener('click', close);
  element('btn-print-order')?.addEventListener('click', () => {
    if (objectUrl) window.open(objectUrl, '_blank', 'noopener');
  });
  return { open, close };
})();

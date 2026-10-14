/** S3-07: thao tác giao dịch được lưu và kiểm quyền tại backend. */
const DealerTransactions = (() => {
  async function request(path, options = {}) {
    const response = await Auth.apiFetch(path, options);
    const result = await response.json().catch(() => null);
    if (!response.ok || result?.success !== true) {
      throw new Error(result?.message || 'Không thể xử lý giao dịch. Vui lòng thử lại.');
    }
    return result.data;
  }

  async function lock(dealerId, reason) {
    const normalizedReason = String(reason ?? '').trim();
    if (!normalizedReason) throw new Error('Vui lòng nhập lý do khoá giao dịch.');
    if (normalizedReason.length > 500) throw new Error('Lý do khoá tối đa 500 ký tự.');
    return request(`/dealers/${dealerId}/lock`, {
      method: 'POST', body: JSON.stringify({ reason: normalizedReason }),
    });
  }

  async function unlock(dealerId) {
    return request(`/dealers/${dealerId}/unlock`, { method: 'POST' });
  }

  async function getInProgressOrders(dealerId) {
    const rows = await request(`/dealers/${dealerId}/in-progress-orders`);
    if (!Array.isArray(rows)) throw new Error('Danh sách đơn hàng không hợp lệ.');
    return rows;
  }

  async function processOrder(orderId, action) {
    if (!['approve', 'reject'].includes(action)) throw new Error('Thao tác đơn hàng không hợp lệ.');
    return request(`/orders/${orderId}/${action}`, { method: 'PUT' });
  }

  async function cancelOrder(orderId, reason) {
    const normalizedReason = String(reason ?? '').trim();
    if (!normalizedReason) throw new Error('Vui lòng nhập lý do huỷ đơn hàng.');
    if (normalizedReason.length > 500) throw new Error('Lý do huỷ tối đa 500 ký tự.');
    return request(`/orders/${orderId}/cancel`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: normalizedReason }),
    });
  }

  return { lock, unlock, getInProgressOrders, processOrder, cancelOrder };
})();

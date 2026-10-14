"""Probe Sprint 4 against a disposable H2 server seeded with demo data.

This audit creates test dealers/orders. Use the isolated server on port 18184.
Findings marked FAIL describe missing acceptance criteria, not runner errors.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    base = 'http://127.0.0.1:18184/api'
    findings = []

    def call(path, token=None, method='GET', payload=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = f'Bearer {token}'
        req = Request(base + path, data=json.dumps(payload).encode() if payload is not None else None,
                      headers=headers, method=method)
        try:
            response = urlopen(req, timeout=15)
        except HTTPError as error:
            response = error
        raw = response.read()
        try:
            body = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            body = {'message': raw[:300].decode('utf-8', errors='replace')}
        return response.status if hasattr(response, 'status') else response.code, body

    def record(story, check, passed, evidence):
        findings.append({'story': story, 'check': check, 'result': 'PASS' if passed else 'FAIL', 'evidence': evidence})

    for attempt in range(30):
        try:
            if call('/test/public')[0] == 200:
                break
        except (URLError, TimeoutError):
            time.sleep(1)
    else:
        raise RuntimeError('Isolated audit server did not start on port 18184')

    def login(username):
        for attempt in range(10):
            status, body = call('/auth/login', method='POST', payload={'username': username, 'password': 'sprint4_Audit123!'})
            if status == 200:
                return body['accessToken']
            # The public endpoint becomes ready before the demo seeder finishes.
            if status != 401:
                break
            time.sleep(0.5)
        raise RuntimeError(f'Could not log in test user {username}: {body.get("message")}')

    admin, sales = login('admin'), login('sales.demo')
    dealers = call('/dealers/active', admin)[1]['data']
    dealer = next(row for row in dealers if row['code'] == 'DEMO-DL-MINHPHAT')
    products = call('/products', admin)[1]['data']
    product = next(row for row in products if row['code'] == 'DEMO-SP-KEN330')
    price_lists = call('/price-lists', admin)[1]['data']
    price_list = next(row for row in price_lists if row['customerGroup'] == dealer['customerGroup'])
    other_price_list = next(row for row in price_lists if row['customerGroup'] != dealer['customerGroup'])
    stamp = datetime.now().strftime('%H%M%S')

    def order(code, selected=dealer, quantity=1, extra=None):
        body = {'orderCode': f'S4-AUDIT-{stamp}-{code}', 'dealerId': selected['id'],
                'priceListId': price_list['id'], 'note': 'Isolated Sprint 4 audit',
                'items': [{'productId': product['id'], 'unit': product['unit'], 'quantity': quantity}]}
        body.update(extra or {})
        return call('/orders', admin, 'POST', body)

    items = [{'productId': products[index % len(products)]['id'],
              'unit': products[index % len(products)]['unit'], 'quantity': 1} for index in range(15)]
    preview_status, preview = call('/orders/calculate', sales, 'POST',
                                   {'dealerId': dealer['id'], 'priceListId': price_list['id'], 'items': items})
    create_status, created = order('15LINES', extra={'items': items})
    matches = preview_status == 200 and create_status == 201 and preview['data']['totalAmount'] == created['data']['totalAmount']
    record('S4-01', '15 dòng: số tiền xem trước và số tiền lưu khớp nhau', matches,
           {'previewHttp': preview_status, 'createHttp': create_status,
            'previewTotal': preview.get('data', {}).get('totalAmount'), 'savedTotal': created.get('data', {}).get('totalAmount')})

    floor_status, floor = call('/orders/line-item/pricing', sales, 'POST',
                              {'dealerId': dealer['id'], 'productId': product['id'], 'quantity': 1, 'manualPrice': 1})
    record('S4-01', 'Giá thủ công dưới giá sàn được đánh dấu cần duyệt',
           floor_status == 200 and floor['data'].get('requiresApproval') is True,
           {'http': floor_status, 'requiresApproval': floor.get('data', {}).get('requiresApproval')})

    status, wrong = call('/orders/calculate', sales, 'POST',
                         {'dealerId': dealer['id'], 'priceListId': other_price_list['id'], 'items': items[:1]})
    record('S4-01', 'Server từ chối bảng giá thuộc nhóm khác với đại lý', status == 400,
           {'http': status, 'dealerGroup': dealer['customerGroup'], 'submittedPriceGroup': other_price_list['customerGroup']})

    status, info = call(f'/dealers/{dealer["id"]}/debt', sales)
    record('S4-02', 'SALES đọc được DTO công nợ dùng trên màn hình tạo đơn',
           status == 200 and all(key in info.get('data', {}) for key in ['currentDebt', 'debtLimit', 'remainingLimit', 'hasOverdueDebt', 'isBlocked']),
           {'http': status, 'data': info.get('data')})

    status, check = call('/orders/check-debt', sales, 'POST',
                         {'dealerId': dealer['id'], 'orderAmount': float(dealer['debtLimit']) + 1})
    record('S4-02', 'Đơn vượt hạn mức được đánh dấu cần duyệt',
           status == 200 and check.get('data', {}).get('requiresApproval') is True,
           {'http': status, 'requiresApproval': check.get('data', {}).get('requiresApproval')})

    status, _ = call('/warehouse/inventory', sales)
    record('S4-03', 'SALES đọc được API tồn mà module frontend đang gọi', status == 200, {'http': status})
    warehouses = call('/warehouse/options', admin)[1]['data']
    warehouse = next(row for row in warehouses if row['code'] == 'DEMO-KHO-HCM')
    inventories = call(f'/warehouse/inventory?warehouseId={warehouse["id"]}', admin)[1]['data']
    physical = next(float(row['baseQuantity']) for row in inventories if row['productId'] == product['id'])
    requested = physical * 0.75
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda code: order(code, quantity=requested, extra={'warehouseId': warehouse['id']}), ['CONCURRENT-A', 'CONCURRENT-B']))
    accepted = sum(status == 201 for status, _ in results)
    record('S4-03', 'Hai đơn cùng chốt không được giữ chỗ vượt tồn thực tế', accepted <= 1,
           {'physicalBase': physical, 'requestedBasePerOrder': requested,
            'http': [status for status, _ in results], 'statuses': [body.get('data', {}).get('status') for _, body in results],
            'acceptedOrders': accepted})

    status, history = call(f'/dealers/{dealer["id"]}/purchase-history?months=3', sales)
    record('S4-04', 'API lịch sử mua hàng do frontend gọi tồn tại', status == 200,
           {'http': status, 'message': history.get('message')})

    status, waiting = order('REJECT', extra={'items': [{'productId': product['id'], 'unit': product['unit'], 'quantity': 1, 'price': 1}]})
    if status != 201:
        raise RuntimeError(f'Could not create below-floor fixture: {waiting.get("message")}')
    status, _ = call(f'/orders/{waiting["data"]["id"]}/reject', admin, 'PUT')
    record('S4-05', 'Từ chối đơn bắt buộc có ý kiến', status == 400, {'httpWithoutReason': status})

    status, cancel_target = order('CANCEL')
    if status != 201:
        raise RuntimeError(f'Could not create cancellation fixture: {cancel_target.get("message")}')
    target_id = cancel_target['data']['id']
    reason = 'Khách yêu cầu huỷ trong kiểm tra Sprint 4'
    status, _ = call(f'/orders/{target_id}/cancel', sales, 'PUT', {'reason': reason})
    _, cancelled = call(f'/orders/{target_id}', admin)
    _, logs = call('/admin/audit-logs?entityType=INVOICE', admin)
    record('S4-06', 'Lý do huỷ gửi từ frontend được lưu trong DB hoặc nhật ký',
           reason in json.dumps(cancelled.get('data', {}), ensure_ascii=False) or reason in json.dumps(logs.get('data', {}), ensure_ascii=False),
           {'http': status, 'savedStatus': cancelled.get('data', {}).get('status'), 'savedNote': cancelled.get('data', {}).get('note')})
    status, _ = call(f'/orders/{created["data"]["id"]}/cancel', sales, 'PUT')
    record('S4-06', 'Server chặn huỷ đơn khi không nhập lý do', status == 400, {'httpWithoutReason': status})

    status, unassigned = call('/dealers', admin, 'POST', {'code': f'S4-AUDIT-{stamp}-UNASSIGNED',
            'name': 'Đại lý chưa phân công (kiểm tra)', 'customerGroup': dealer['customerGroup'], 'active': True, 'debtLimit': 100000000, 'maxDebtDays': 30})
    if status != 200 or not unassigned.get('success'):
        raise RuntimeError(f'Could not create unassigned dealer fixture: {unassigned.get("message")}')
    unassigned = unassigned['data']
    status, hidden_order = order('OTHER-DEALER', selected=unassigned)
    if status != 201:
        raise RuntimeError(f'Could not create other dealer order: {hidden_order.get("message")}')
    status, debt_denied = call(f'/dealers/{unassigned["id"]}/debt', sales)
    record('S4-02', 'Công nợ đại lý chưa phân công bị từ chối với SALES', status == 403, {'http': status})
    status, order_list = call('/orders', sales)
    leak = any(row['dealerId'] == unassigned['id'] for row in order_list.get('data', []))
    record('S4-07', 'SALES chỉ thấy đơn của đại lý mình phụ trách', status == 200 and not leak,
           {'http': status, 'otherDealerOrderVisible': leak})
    status, filtered = call(f'/orders?dealerId={unassigned["id"]}&status=RECORDED', admin)
    record('S4-07', 'Bộ lọc đại lý/trạng thái được xử lý phía server',
           status == 200 and all(row['dealerId'] == unassigned['id'] and row['status'] == 'RECORDED' for row in filtered.get('data', [])),
           {'http': status, 'returnedRows': len(filtered.get('data', [])), 'requestedDealerId': unassigned['id']})

    report = {'runAt': datetime.now(timezone(timedelta(hours=7))).isoformat(), 'baseUrl': base,
              'environment': 'H2 in memory, isolated demo fixtures; no production DB',
              'checks': findings, 'passed': sum(row['result'] == 'PASS' for row in findings),
              'failedAcceptanceCriteria': sum(row['result'] == 'FAIL' for row in findings)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'passed': report['passed'], 'failedAcceptanceCriteria': report['failedAcceptanceCriteria'],
                      'output': str(args.output)}, ensure_ascii=True))


if __name__ == '__main__':
    main()

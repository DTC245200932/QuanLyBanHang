/** Dashboard stock alerts: no role fallback, no client-side threshold calculation. */
(() => {
  const section=document.getElementById('dashboard-stock-alerts');if(!section || !Auth.requireLogin())return;
  const $=id=>document.getElementById(id);
  const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const quantity=value=>new Intl.NumberFormat('vi-VN',{maximumFractionDigits:4}).format(Number(value));
  const allowed=()=>Auth.hasPermission('WAREHOUSE_VIEW') && new URLSearchParams(location.search).get('view')!=='debt-limits';
  let controller=null,version=0,started=false,timer=null;
  function clear(){controller?.abort();++version;section.hidden=true;section.classList.remove('has-low-stock');$('dashboard-stock-alert-rows').innerHTML='';$('dashboard-stock-alert-state').textContent='';$('dashboard-stock-alert-error').hidden=true;}
  async function load(){
    if(!allowed()){clear();return;}
    controller?.abort();controller=new AbortController();const current=++version,signal=controller.signal;
    section.hidden=false;$('dashboard-stock-alert-error').hidden=true;$('dashboard-stock-alert-state').textContent='Đang kiểm tra tồn kho…';
    try{
      const response=await Auth.apiFetch('/warehouse/stock-alerts?page=0&size=5',{signal,cache:'no-store'});
      const payload=await response.json().catch(()=>null);
      if(!response.ok || !payload?.success)throw new Error(payload?.message || 'Không tải được cảnh báo tồn kho. Chọn Tải lại để thử lại.');
      if(current!==version || !allowed())return;
      const data=payload.data;
      if(!Array.isArray(data?.content) || !Number.isFinite(data.totalElements) || data.content.some(row=>row.belowMinimum!==true || row.minimumQuantity==null))throw new Error('Dữ liệu cảnh báo tồn kho chưa đầy đủ.');
      section.classList.toggle('has-low-stock',data.totalElements>0);
      $('dashboard-stock-alert-state').textContent=data.totalElements?`${data.totalElements} dòng SKU–kho dưới mức tối thiểu${data.totalElements>data.content.length?` · hiển thị ${data.content.length} dòng đầu`:''}`:'Không có SKU dưới mức tối thiểu đã khai báo trong phạm vi kho của bạn.';
      $('dashboard-stock-alert-rows').innerHTML=data.content.map(row=>{
        const query=new URLSearchParams({warehouseId:row.warehouseId,keyword:row.productCode,belowMinimum:'true'});
        return `<a class="dashboard-stock-alert-link" href="/warehouse/inventory?${esc(query)}"><div><strong>${esc(row.productName)}</strong><small>${esc(row.productCode)} · ${esc(row.warehouseName)}</small></div><div class="dashboard-stock-alert-quantities">Khả dụng ${quantity(row.availableQuantity)} ${esc(row.baseUnit)}<small>Mức tối thiểu ${quantity(row.minimumQuantity)} ${esc(row.minimumUnit)}</small></div></a>`;
      }).join('');
    }catch(e){if(e.name!=='AbortError' && current===version && allowed()){$('dashboard-stock-alert-state').textContent='Chưa xác định được số SKU dưới ngưỡng';$('dashboard-stock-alert-rows').innerHTML='';section.classList.remove('has-low-stock');$('dashboard-stock-alert-error').textContent=e.message;$('dashboard-stock-alert-error').hidden=false;}}
  }
  function refresh(){if(started && !document.hidden)load();}
  function schedule(){if(timer==null)timer=setInterval(refresh,30000);}
  $('dashboard-stock-alert-reload').addEventListener('click',()=>load());
  window.addEventListener('auth:permissions-changed',()=>{if(started){if(!allowed())clear();else load();}});
  window.addEventListener('focus',refresh);document.addEventListener('visibilitychange',()=>{if(document.hidden){controller?.abort();++version;}else refresh();});
  window.addEventListener('pagehide',()=>{controller?.abort();++version;clearInterval(timer);timer=null;});
  window.addEventListener('pageshow',()=>{if(started){schedule();refresh();}});
  window.addEventListener('popstate',refresh);
  Promise.resolve(Auth.ready).then(verified=>{started=true;if(verified){schedule();load();}});
})();

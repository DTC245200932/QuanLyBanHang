(() => {
  if (!Auth.requireLogin()) return;
  if (!Auth.hasRole("ROLE_ADMIN")) { location.href = "../error.html?status=403"; return; }
  const roleLabels = {ROLE_ADMIN:"Quản trị hệ thống",ROLE_SALES_MANAGER:"Quản lý kinh doanh",ROLE_WAREHOUSE_MANAGER:"Quản lý kho",ROLE_SALES:"Nhân viên kinh doanh",ROLE_WAREHOUSE_STAFF:"Thủ kho",ROLE_ACCOUNTANT:"Kế toán",ROLE_VIEWER:"Chỉ xem"};
  const state = { page:0, totalPages:1, users:[], options:{roles:[],warehouses:[],territories:[]} };
  const $ = (id) => document.getElementById(id);
  const escapeHtml = (v) => String(v ?? "").replace(/[&<>'"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[c]);
  const checkedValues = (name) => [...document.querySelectorAll(`input[name="${name}"]:checked`)].map(i => name === "roles" ? i.value : Number(i.value));
  const showFormError = (text) => { $("form-message").hidden = false; $("form-message").textContent = text; };

  async function loadOptions() {
    const response = await Auth.apiFetch("/admin/assignment-options");
    const result = await response.json(); state.options = result.data;
    $("role-filter").insertAdjacentHTML("beforeend", state.options.roles.map(r => `<option value="${r}">${roleLabels[r]}</option>`).join(""));
    $("role-options").innerHTML = state.options.roles.map(r => choice("roles", r, roleLabels[r])).join("");
    $("warehouse-options").innerHTML = state.options.warehouses.map(w => choice("warehouses", w.id, `${w.code} · ${w.name}`)).join("") || "<span>Chưa có kho hoạt động</span>";
    $("territory-options").innerHTML = state.options.territories.map(t => choice("territories", t.id, `${t.code} · ${t.name}`)).join("") || "<span>Chưa có địa bàn hoạt động</span>";
  }
  function choice(name,value,label){return `<label class="choice"><input type="checkbox" name="${name}" value="${escapeHtml(value)}"><span>${escapeHtml(label)}</span></label>`}

  async function loadUsers() {
    $("users-body").innerHTML = '<tr><td colspan="5" class="empty">Đang tải danh sách…</td></tr>';
    const params = new URLSearchParams({page:state.page,size:20});
    if ($("search").value.trim()) params.set("search", $("search").value.trim());
    if ($("role-filter").value) params.set("role", $("role-filter").value);
    if ($("status-filter").value) params.set("active", $("status-filter").value);
    try {
      const response = await Auth.apiFetch(`/admin/users?${params}`); const result = await response.json();
      if (!response.ok) throw new Error(result.message);
      const page = result.data; state.users = page.content; state.totalPages = page.totalPages || 1;
      renderUsers(); $("page-summary").textContent = `${page.totalElements} tài khoản · Trang ${page.number + 1}/${state.totalPages}`;
      $("prev-page").disabled = page.first; $("next-page").disabled = page.last;
    } catch(e) { $("users-body").innerHTML = `<tr><td colspan="5" class="empty">${escapeHtml(e.message || "Không tải được dữ liệu")}</td></tr>`; }
  }
  function renderUsers(){
    if(!state.users.length){$("users-body").innerHTML='<tr><td colspan="5" class="empty">Không tìm thấy tài khoản phù hợp.</td></tr>';return}
    $("users-body").innerHTML=state.users.map(u=>{const scopes=[...(u.warehouses||[]),...(u.territories||[])],displayName=u.fullName||u.username,initial=displayName.trim().charAt(0).toUpperCase()||"U",avatarAttribute=u.avatarThumbnailUrl?` data-avatar-url="${escapeHtml(u.avatarThumbnailUrl)}"`:"";return `<tr><td><div class="user-identity"><span class="user-list-avatar"${avatarAttribute}><span>${escapeHtml(initial)}</span><img alt="" hidden></span><span><span class="user-name">${escapeHtml(displayName)}</span><span class="user-meta">${escapeHtml(u.username)} · ${escapeHtml(u.phone||u.email)}</span></span></div></td><td><div class="tags">${u.roles.map(r=>`<span class="tag">${escapeHtml(roleLabels[r])}</span>`).join("")}</div></td><td><div class="tags">${scopes.length?scopes.map(s=>`<span class="tag">${escapeHtml(s.name)}</span>`).join(""):'<span class="user-meta">Toàn hệ thống</span>'}</div></td><td><span class="status ${u.active?'':'status--locked'}">${u.active?'Hoạt động':'Đã khoá'}</span>${u.assignedDealerCount?`<span class="user-meta">${u.assignedDealerCount} đại lý cần theo dõi</span>`:''}</td><td><div class="row-actions"><button class="row-btn" data-edit="${u.id}">Sửa</button><button class="row-btn ${u.active?'row-btn--danger':''}" data-toggle="${u.id}">${u.active?'Khoá':'Mở khoá'}</button></div></td></tr>`}).join("");
    document.querySelectorAll("[data-avatar-url]").forEach(async(container)=>{const image=container.querySelector("img"),fallback=container.querySelector("span");if(await Auth.setProtectedImage(image,container.dataset.avatarUrl)){image.hidden=false;fallback.hidden=true}});
  }

  function openModal(user=null){
    $("user-form").reset(); $("form-message").hidden=true; $("user-id").value=user?.id||""; $("user-modal-title").textContent=user?"Cập nhật tài khoản":"Tạo tài khoản";
    $("full-name").value=user?.fullName||""; $("username").value=user?.username||""; $("email").value=user?.email||""; $("phone").value=user?.phone||"";
    const selected={roles:user?.roles||[],warehouses:(user?.warehouses||[]).map(x=>String(x.id)),territories:(user?.territories||[]).map(x=>String(x.id))};
    Object.entries(selected).forEach(([name,values])=>document.querySelectorAll(`input[name="${name}"]`).forEach(input=>input.checked=values.includes(input.value)));
    $("user-modal").hidden=false; $("full-name").focus();
  }
  function closeModal(){$("user-modal").hidden=true}

  $("user-form").addEventListener("submit",async(e)=>{e.preventDefault();const id=$("user-id").value;const payload={fullName:$("full-name").value.trim(),username:$("username").value.trim(),email:$("email").value.trim(),phone:$("phone").value.trim(),roles:checkedValues("roles"),warehouseIds:checkedValues("warehouses"),territoryIds:checkedValues("territories")};if(!payload.roles.length)return showFormError("Chọn ít nhất một vai trò.");const btn=$("save-user");btn.disabled=true;try{const response=await Auth.apiFetch(id?`/admin/users/${id}`:"/admin/users",{method:id?"PUT":"POST",body:JSON.stringify(payload)});const result=await response.json();if(!response.ok||!result.success)throw new Error(result.message||"Không lưu được tài khoản");closeModal();await loadUsers()}catch(err){showFormError(err.message)}finally{btn.disabled=false}});
  $("users-body").addEventListener("click",async(e)=>{const edit=e.target.closest("[data-edit]"),toggle=e.target.closest("[data-toggle]");if(edit){openModal(state.users.find(u=>String(u.id)===edit.dataset.edit));return}if(!toggle)return;const user=state.users.find(u=>String(u.id)===toggle.dataset.toggle);if(user.active){const reason=prompt(`Nhập lý do khoá tài khoản ${user.username}:`);if(!reason?.trim())return;const response=await Auth.apiFetch(`/admin/users/${user.id}/lock`,{method:"POST",body:JSON.stringify({reason:reason.trim()})});const result=await response.json();if(!response.ok){alert(result.message);return}if(result.data.assignedDealerCount){$("handover-strip").hidden=false;$("handover-message").textContent=`${result.data.fullName} đang phụ trách ${result.data.assignedDealerCount} đại lý. Hãy phân công bàn giao.`}}else{const response=await Auth.apiFetch(`/admin/users/${user.id}/unlock`,{method:"POST"});if(!response.ok){const result=await response.json();alert(result.message);return}}await loadUsers()});
  $("create-user").addEventListener("click",()=>openModal());document.querySelectorAll("[data-close]").forEach(b=>b.addEventListener("click",closeModal));$("filters").addEventListener("submit",e=>{e.preventDefault();state.page=0;loadUsers()});$("prev-page").addEventListener("click",()=>{if(state.page>0){state.page--;loadUsers()}});$("next-page").addEventListener("click",()=>{if(state.page<state.totalPages-1){state.page++;loadUsers()}});
  loadOptions().then(loadUsers).catch(e=>{$("users-body").innerHTML=`<tr><td colspan="5" class="empty">${escapeHtml(e.message)}</td></tr>`});
})();

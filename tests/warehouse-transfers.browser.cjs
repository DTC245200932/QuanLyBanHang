// Actual HTML and scripts in Chrome; HTTP mocked, no real database touched.
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.NAV_TEST_URL || 'http://127.0.0.1:5527';
const output = path.join(require('node:os').tmpdir(), 'qlbh-s507');
const token = 'test.' + Buffer.from(JSON.stringify({exp: Math.floor(Date.now()/1000)+3600})).toString('base64url') + '.test';
const warehouses = [{id:1,code:'HN',name:'Kho Hà Nội'},{id:2,code:'HCM',name:'Kho TP.HCM'}];
const statuses = [{value:'IN_TRANSIT',label:'Đang trên đường'},{value:'COMPLETED',label:'Đã nhận hàng'},{value:'CANCELLED',label:'Đã hủy'}];
const stock = {productId:101, productCode:'SKU-101',productName:'Nước <img src=x onerror="window.xss=true">',unit:'lon',physicalQuantity:20,reservedQuantity:5,availableQuantity:15};
const pageData = (content,total=content.length,page=0,size=20) => ({content,totalElements:total,totalPages:Math.ceil(total/size),number:page,last:(page+1)*size>=total});

(async () => {
  fs.mkdirSync(output,{recursive:true});
  const browser = await chromium.launch({headless:true,channel:'chrome'});
  const errors=[];
  async function setup(roles=['ROLE_WAREHOUSE_STAFF'], permissions=['WAREHOUSE_TRANSFER']) {
    const rights={permissions}, rows=[], writes=[], calls=[], flags={failReceive:false,failList:false};
    const user={id:12,username:'warehouse-test',fullName:'Nhân viên kiểm thử',roles,permissions,warehouses};
    const context=await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce'});
    await context.addInitScript(({user,token})=>{sessionStorage.setItem('user',JSON.stringify(user));sessionStorage.setItem('accessToken',token);},{user,token});
    await context.route('http://localhost:8080/api/**',async route=>{
      const req=route.request(), url=new URL(req.url()), endpoint=url.pathname.replace('/api',''); calls.push(endpoint);
      const write=roles.includes('ROLE_ADMIN') || rights.permissions.includes('WAREHOUSE_TRANSFER');
      let data=[],status=200,message='Kiểm thử';
      if(endpoint==='/auth/permissions') data={roles,permissions:rights.permissions};
      else if(endpoint==='/warehouse/transfers/options') data={sourceWarehouses:warehouses,destinationWarehouses:warehouses,statuses,canCreate:write};
      else if(endpoint==='/warehouse/transfers/products') {await new Promise(r=>setTimeout(r,url.searchParams.get('keyword')==='slow'?160:10)); data=pageData([stock]);}
      else if(endpoint==='/warehouse/transfers/search') {if(flags.failList){status=500;message='Lỗi tải phiếu kiểm thử';} data=pageData(rows.filter(row=>!url.searchParams.get('status') || row.status===url.searchParams.get('status')));}
      else if(endpoint==='/warehouse/transfers' && req.method()==='POST') {
        const body=req.postDataJSON(); writes.push({kind:'create',body}); await new Promise(r=>setTimeout(r,40));
        const id=rows.length+1; data={id,transferCode:'PCK-'+id,fromWarehouseId:1,fromWarehouseCode:'HN',fromWarehouseName:'Kho Hà Nội',toWarehouseId:2,toWarehouseCode:'HCM',toWarehouseName:'Kho TP.HCM',status:'IN_TRANSIT',statusDescription:'Đang trên đường',canReceive:true,canCancel:true,createdBy:user.username,dispatchedAt:'2026-10-20T12:00:00',note:body.note,
          items:body.items.map((item,index)=>({...item,id:id*10+index,quantity:Number(item.quantity),productCode:stock.productCode,productName:stock.productName,baseQuantity:Number(item.quantity)}))}; rows.unshift(data);
      } else if(/^\/warehouse\/transfers\/\d+$/.test(endpoint)) data=rows.find(row=>row.id===Number(endpoint.split('/').pop()));
      else if(endpoint.endsWith('/receive')) {
        const body=req.postDataJSON(); writes.push({kind:'receive',body}); await new Promise(r=>setTimeout(r,40)); data=rows.find(row=>row.id===Number(endpoint.split('/').at(-2)));
        if(flags.failReceive){status=400;message='Không thể nhận phiếu kiểm thử';} else {data.status='COMPLETED';data.statusDescription='Đã nhận hàng';data.canReceive=data.canCancel=false;data.receivedAt='2026-10-20T13:00:00';for(const line of body.items){const item=data.items.find(item=>item.id===line.itemId);Object.assign(item,{receivedQuantity:Number(line.receivedQuantity),discrepancyQuantity:item.quantity-Number(line.receivedQuantity),discrepancyReason:line.discrepancyReason});}}
      } else if(endpoint.endsWith('/cancel')) {
        const body=req.postDataJSON(); writes.push({kind:'cancel',body});data=rows.find(row=>row.id===Number(endpoint.split('/').at(-2)));Object.assign(data,{status:'CANCELLED',statusDescription:'Đã hủy',cancelReason:body.reason,canReceive:false,canCancel:false});
      } else {status=404; message='Unexpected API '+endpoint;}
      if(req.method()==='POST' && !write) status=403;
      await route.fulfill({status,contentType:'application/json',headers:{'Access-Control-Allow-Origin':base,'Access-Control-Allow-Headers':'Authorization,Content-Type','Access-Control-Allow-Methods':'GET,POST,OPTIONS'},body:JSON.stringify({success:status===200,message,data})}).catch(()=>{});
    });
    const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
    return {context,page,rights,rows,writes,calls,flags};
  }
  try {
    const s=await setup(); await s.page.goto(base+'/warehouse/transfers'); await s.page.locator('#tr-new').waitFor({state:'visible',timeout:10000}).catch(async error=>{
      console.error({errors,calls:s.calls,state:await s.page.evaluate(()=>({error:document.getElementById('tr-error')?.textContent,user:Auth.getUser(),ready:typeof Auth.ready,canTransfer:Auth.hasPermission('WAREHOUSE_TRANSFER')}))});throw error;
    });
    assert.equal(await s.page.locator('#sidebar-nav [aria-current=page]').getAttribute('data-page'),'warehouse-transfers');
    async function create(quantity) {
      await s.page.locator('#tr-new').click(); await s.page.locator('#tr-source').selectOption('1'); await s.page.locator('#tr-target').selectOption('2');
      await s.page.locator('[data-add="101"]:not([disabled])').waitFor(); await s.page.locator('[data-add="101"]').click();
      await s.page.locator('[data-quantity="101"]').fill(quantity); await s.page.locator('#tr-create-submit').click();
      await s.page.waitForFunction(()=>document.querySelector('#tr-detail').open && document.querySelector('#tr-detail-rows [data-received]'));
    }
    await create('3.5');
    assert.equal(s.writes.filter(w=>w.kind==='create').length,1);
    assert.deepEqual(s.writes[0].body.items,[{productId:101,unit:'lon',quantity:'3.5'}]);
    assert.equal(await s.page.locator('#tr-detail-rows img').count(),0); assert.equal(await s.page.evaluate(()=>window.xss),undefined);
    const actual=s.page.locator('[data-received]'); await actual.fill('3'); await s.page.locator('#tr-receive-submit').click();
    assert.equal(s.writes.filter(w=>w.kind==='receive').length,0,'Missing discrepancy reason must block submission');
    await s.page.locator('[data-reason]').fill('Thiếu 0,5 lon khi nhận'); s.flags.failReceive=true;
    await s.page.locator('#tr-receive-submit').click(); await s.page.locator('#tr-detail-error').waitFor({state:'visible'});
    await s.page.waitForFunction(()=>!document.querySelector('#tr-receive-submit').disabled);
    assert.equal(await actual.inputValue(),'3');assert.equal(await s.page.locator('[data-reason]').inputValue(),'Thiếu 0,5 lon khi nhận');
    s.flags.failReceive=false; await s.page.locator('#tr-receive-submit').click();
    await s.page.waitForFunction(()=>document.querySelector('#tr-receive-submit').hidden);
    assert.match(await s.page.locator('#tr-detail-info').innerText(),/Đã nhận hàng/);
    assert.match(await s.page.locator('#tr-detail-rows').innerText(),/Thiếu 0,5 lon/);
    await s.page.locator('#tr-detail-close').click(); await create('1');
    await s.page.locator('#tr-cancel-reason').fill('Không còn nhu cầu điều chuyển'); await s.page.locator('#tr-cancel-submit').click();
    await s.page.waitForFunction(()=>document.querySelector('#tr-cancel-form').hidden);
    assert.match(await s.page.locator('#tr-detail-info').innerText(),/Không còn nhu cầu điều chuyển/); await s.page.locator('#tr-detail-close').click();
    for(const width of [1920,1440,1024,768,390,320]){
      await s.page.setViewportSize({width,height:900}); await s.page.evaluate(()=>window.scrollTo(0,0));
      await s.page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth+1, null, {timeout: 3000}).catch(async error=>{
        console.error(await s.page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,wide:[...document.querySelectorAll('body *')].filter(el=>el.getBoundingClientRect().right>innerWidth+2).map(el=>({tag:el.tagName,id:el.id,class:el.className,right:el.getBoundingClientRect().right})).slice(0,15)})));throw error;
      });
      assert.ok(await s.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No full-page horizontal overflow at '+width);
      if(width===1440 || width===390) await s.page.screenshot({path:path.join(output,`transfers-${width}.png`)});
    }
    await s.page.setViewportSize({width:1440,height:900});
    await s.page.locator('#tr-new').click(); await s.page.locator('#tr-source').selectOption('1');
    await s.page.locator('#tr-product-search').fill('slow'); await s.page.locator('#tr-product-find').click(); await s.page.locator('#tr-source').selectOption('');
    await s.page.waitForTimeout(220); assert.match(await s.page.locator('#tr-products').innerText(),/Chọn kho đi/); await s.page.locator('#tr-create-close').click();
    s.flags.failList=true;await s.page.locator('#tr-reload').click();await s.page.locator('#tr-error').waitFor({state:'visible'});
    s.flags.failList=false;await s.page.locator('#tr-reload').click();await s.page.locator('#tr-rows [data-detail]').first().waitFor();
    await create('1');await s.page.locator('#tr-detail-close').click();
    s.rights.permissions=['WAREHOUSE_VIEW'];await s.page.evaluate(()=>Auth.refreshPermissions());await s.page.waitForFunction(()=>document.querySelector('#tr-new').hidden);
    await s.page.locator('#tr-rows [data-detail]').first().click();await s.page.waitForFunction(()=>document.querySelector('#tr-detail-info').textContent.includes('Kho đi'));
    assert.equal(await s.page.locator('#tr-receive-submit').isVisible(),false);assert.equal(await s.page.locator('#tr-cancel-form').isVisible(),false);await s.page.locator('#tr-detail-close').click();
    s.rights.permissions=[];await s.page.evaluate(()=>Auth.refreshPermissions());await s.page.waitForURL('**/error?status=403');
    assert.ok(s.calls.every(call=>call==='/auth/permissions' || call.startsWith('/warehouse/transfers')),'No unrelated permission dependencies');
    await s.context.close();
    const custom=await setup(['ROLE_VIEWER'],['WAREHOUSE_TRANSFER']);await custom.page.goto(base+'/warehouse/transfers');await custom.page.locator('#tr-new').waitFor({state:'visible'});await custom.context.close();
    const denied=await setup(['ROLE_WAREHOUSE_MANAGER'],[]);await denied.page.goto(base+'/warehouse/transfers');await denied.page.waitForURL('**/error?status=403');await denied.context.close();
    const admin=await setup(['ROLE_ADMIN'],[]);await admin.page.goto(base+'/warehouse/transfers');await admin.page.locator('#tr-new').waitFor({state:'visible'});await admin.context.close();
    assert.deepEqual(errors,[]);console.log('PASS S5-07: create, receive/discrepancy, cancel, failed POST preserves input, stale requests, 6 widths, matrix grant/revoke, Admin. Screenshots:',output);
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

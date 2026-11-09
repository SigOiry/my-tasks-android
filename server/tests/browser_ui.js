/* Run via a browser's awaited Runtime.evaluate on the loopback QA page only.
   Requires an authenticated scratch session; does not accept or contain tokens.
   Never distributed with the app. All writes are to the QA server's scratch DB. */
(async () => {
 if (location.hostname !== '127.0.0.1' || location.port !== '9138') throw new Error('Refusing browser QA outside the designated loopback scratch service.');
 const results=[];
 const assert=(condition,label)=>{if(!condition)throw new Error(label);results.push(label);};
 const wait=async(predicate)=>{for(let i=0;i<100;i++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,50));}throw new Error('UI state timed out.');};
 const $=id=>document.getElementById(id);
 const change=(selector,value)=>{const e=document.querySelector(selector);e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};
 const session=await(await fetch('/auth/session')).json();
 assert(session.authenticated,'real cookie-authenticated scratch session');
 const api=async(path,method='GET',body)=>{
  const response=await fetch(path,{method,headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrf},body:body?JSON.stringify(body):undefined});
  const data=await response.json();if(!response.ok)throw new Error(method+' '+path+': '+response.status);return data;
 };
 const before=await api('/api/tasks');
 const summaryTask=before.tasks.find(t=>t.notification?.unread&&!t.deleted);
 if(summaryTask){
  document.querySelector(`.task-row[data-task-id="${summaryTask.id}"] .task-open`).click();
  await wait(()=>$('updates-dialog').open);
  assert(!$('task-dialog').open,'unread summary appears before ordinary editor');
  assert((await api('/api/tasks/'+summaryTask.id)).notification.unread,'opening summary does not acknowledge');
  $('updates-ack').click();await wait(()=>$('task-dialog').open);
  assert(!(await api('/api/tasks/'+summaryTask.id)).notification.unread,'explicit OK persists exact acknowledgement');
  $('task-dialog').querySelector('.close-dialog').click();
 }
 const title='Browser acceptance '+Date.now();
 $('new-task').click();await wait(()=>$('task-dialog').open);
 change('#title',title);change('[name=project]','Browser QA');change('#notes','Private scratch notes');change('[name=priority]','high');
 $('task-form').requestSubmit();
 await wait(()=>$('detail-heading').textContent===title);
 const created=(await api('/api/tasks')).tasks.find(t=>t.title===title);
 assert(!!created&&created.notes==='Private scratch notes'&&created.priority==='high','UI create persists notes and priority');
 change('#title','Keep this unsaved draft');
 await api('/api/tasks/'+created.id,'PATCH',{revision:created.revision,notes:'Concurrent scratch edit'});
 $('task-form').requestSubmit();await wait(()=>!$('conflict').hidden);
 assert($('title').value==='Keep this unsaved draft','stale409 preserves unsaved form');
 assert($('save-task').disabled,'conflict requires explicit review rather than overwrite');
 const originalConfirm=window.confirm;window.confirm=()=>true;
 try{$('reload-task').click();await wait(()=>$('conflict').hidden);}finally{window.confirm=originalConfirm;}
 assert($('notes').value==='Concurrent scratch edit','explicit reload fetches actual latest task');
 $('delete-task').click();assert($('confirm-dialog').open,'soft deletion requires visible confirmation');
 $('delete-cancel').click();assert(!(await api('/api/tasks/'+created.id)).deleted,'cancel deletion leaves canonical task intact');
 $('delete-task').click();$('delete-confirm').click();await wait(()=>!$('task-dialog').open);
 assert((await api('/api/tasks/'+created.id)).deleted,'confirmed deletion is recoverable trash');
 document.querySelector('[data-view=trash]').click();
 document.querySelector(`.task-row[data-task-id="${created.id}"] .task-open`).click();await wait(()=>$('task-dialog').open);
 $('restore-task').click();await wait(()=>$('restore-task').hidden);
 assert(!(await api('/api/tasks/'+created.id)).deleted,'restore persists through real backend');
 $('task-dialog').querySelector('.close-dialog').click();
 document.querySelector('[data-view=active]').click();change('#search',title);
 assert(document.querySelectorAll('.task-row').length===1,'search filters actual tasks');
 change('#search','');
 assert(document.documentElement.scrollWidth<=innerWidth,'responsive layout has no horizontal overflow');
 const forbidden=[];
 for(const key of Object.keys(localStorage)){
  const value=localStorage.getItem(key)||'';
  if(value.includes(title)||value.includes('Private scratch notes'))forbidden.push(key);
 }
 assert(forbidden.length===0,'private task fields never enter persistent browser storage');
 return {passed:results.length,results};
})()

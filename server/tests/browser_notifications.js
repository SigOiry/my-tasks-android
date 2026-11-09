/* Real backend notification acceptance; synthetic fixture only, never production. */
(async()=>{
 if(location.origin!=='http://127.0.0.1:9138')throw new Error('Scratch loopback browser only.');
 const $=id=>document.getElementById(id),results=[];
 const assert=(ok,label)=>{if(!ok)throw new Error(label);results.push(label);};
 const wait=async fn=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,60));}throw new Error('Notification UI timeout');};
 for(const d of document.querySelectorAll('dialog[open]'))d.close();
 const session=await(await fetch('/auth/session')).json();
 const api=async(path,method='GET',body)=>{const r=await fetch(path,{method,headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrf},body:body?JSON.stringify(body):undefined});if(!r.ok)throw new Error('API '+r.status);return r.json();};
 const task=(await api('/api/tasks')).tasks.find(t=>t.title==='QA canonical prose'&&t.notification?.unread);assert(!!task?.notification.unread,'canonical prose fixture is unread');
 $('nav-tasks').click();$('refresh').click();await wait(()=>document.querySelector(`[data-task-id="${task.id}"] .task-open`));document.querySelector(`[data-task-id="${task.id}"] .task-open`).click();await wait(()=>$('updates-dialog').open);
 assert(!$('task-dialog').open,'notification gates editor');const popup=$('updates-summary');
 assert(popup.querySelector('strong')?.textContent==='new date','prose bold is rendered');assert(popup.querySelector('em')?.textContent==='review','prose emphasis is rendered');assert(!!popup.querySelector('li code'),'list and code rendered safely');
 assert([...popup.querySelectorAll('h3')].map(x=>x.textContent).join('|')==='Context|New','canonical section order; empty next steps omitted');
 assert(!popup.querySelector('a,img,script,iframe,svg'),'no active links images or raw HTML in popup');assert(popup.textContent.includes('<img src=x onerror=alert(1)>'),'raw HTML stays literal text');assert(!popup.textContent.includes('Technical diff sentinel'),'legacy technical summary not mixed with prose');assert(!popup.textContent.includes('https://'),'markdown URL is not exposed as a source action');
 await api('/api/tasks/'+task.id,'PATCH',{revision:task.revision,description:'Newer context arrived during review.'});
 $('updates-ack').click();await wait(()=>!$('updates-refresh').hidden);assert($('updates-dialog').open,'stale exact-token409 leaves summary open');assert((await api('/api/tasks/'+task.id)).notification.unread,'stale acknowledgement never marks newer update read');
 $('updates-refresh').click();await wait(()=>$('updates-refresh').hidden);$('updates-ack').click();await wait(()=>$('task-dialog').open);assert(!(await api('/api/tasks/'+task.id)).notification.unread,'fresh summary explicit acknowledgement persisted');
 $('task-dialog').querySelector('.close-dialog').click();return {passed:results.length,results};
})()

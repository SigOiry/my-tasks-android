/* Evaluate only in an authenticated, isolated scratch QA page on port 9138.
   Uses real backend contract; no transport/proxy mocks, secrets or production writes. */
(async()=>{
 if(location.hostname!=='127.0.0.1'||location.port!=='9138')throw new Error('Scratch loopback service required.');
 const $=id=>document.getElementById(id),results=[];
 const assert=(ok,label)=>{if(!ok)throw new Error(label);results.push(label);};
 const wait=async(fn,ms=12000)=>{const end=Date.now()+ms;while(Date.now()<end){if(fn())return;await new Promise(r=>setTimeout(r,80));}throw new Error('UI timeout');};
 const change=(id,value)=>{const e=$(id);e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};
 const session=await(await fetch('/auth/session')).json();assert(session.authenticated,'cookie-authenticated scratch session');
 const api=async path=>{const r=await fetch(path,{cache:'no-store'});if(!r.ok)throw new Error(path+' '+r.status);return r.json();};
 $('nav-discussions').click();await wait(()=>!$('discussions-workspace').hidden);
 $('new-free').click();await wait(()=>$('chat-dialog').open&&!$('chat-send').disabled);
 const created=(await api('/api/conversations/'+encodeURIComponent($('chat-dialog').dataset.conversationId))).conversation;assert(!!created&&!created.task_id,'new discussion is server-persisted and free');
 const content='Scratch QA '+Date.now()+' <img src=x onerror=alert(1)> — Explain why the sky is blue. No tools.';
 change('chat-message',content);$('chat-form').requestSubmit();change('chat-message','My follow-up stays editable');
 await wait(()=>$('chat-log').textContent.includes(content));
 assert($('chat-message').value==='My follow-up stays editable','typed follow-up survives asynchronous admission');
 assert(!$('chat-log').querySelector('img'),'untrusted message is text, never HTML');
 const detail=await api('/api/conversations/'+created.id+'?limit=40');
 assert(detail.messages.some(m=>m.role==='user'&&m.content===content),'readback confirms persisted exact user message');
 assert(!!detail.conversation.latest_request,'accepted request identity is server-recoverable');
 const rid=detail.conversation.latest_request.id,request=await api('/api/conversations/'+created.id+'/requests/'+rid);
 assert(['queued','running','succeeded','failed','interrupted'].includes(request.status),'request endpoint returns contract status');
 $('chat-close').click();await wait(()=>!$('chat-dialog').open);$('discussion-refresh').click();
 await wait(()=>document.querySelector(`[data-conversation-id="${created.id}"]`));document.querySelector(`[data-conversation-id="${created.id}"]`).click();await wait(()=>$('chat-log').textContent.includes(content));
 assert($('chat-message').value==='My follow-up stays editable','close/reopen preserves per-conversation draft');
 $('chat-rename').click();await wait(()=>$('rename-dialog').open);change('conversation-title','Scratch durable discussion');$('rename-form').requestSubmit();await wait(()=>!$('rename-dialog').open);
 assert((await api('/api/conversations/'+created.id)).conversation.title==='Scratch durable discussion','rename persisted, verified by exact readback');
 $('chat-archive').click();await wait(()=>$('chat-archive').textContent==='Reopen');
 assert((await api('/api/conversations/'+created.id)).conversation.archived,'archive remains recoverable');
 $('chat-archive').click();await wait(()=>$('chat-archive').textContent==='Archive');
 assert(!(await api('/api/conversations/'+created.id)).conversation.archived,'reopen persisted');
 assert(document.documentElement.scrollWidth<=innerWidth,'no horizontal overflow');
 for(const storage of [localStorage,sessionStorage])for(const key of Object.keys(storage))assert(!String(storage.getItem(key)).includes(content),'private messages absent from browser storage');
 $('chat-close').click();$('nav-tasks').click();
 const taskRow=[...document.querySelectorAll('.task-row')].find(e=>!e.classList.contains('unread'));assert(!!taskRow,'saved task available for bound discussions');
 taskRow.querySelector('.task-open').click();await wait(()=>$('task-dialog').open);$('hermes-tab').click();
 await wait(()=>!$('task-thread-new').disabled);$('task-thread-new').click();await wait(()=>$('chat-dialog').open&&!$('chat-send').disabled);
 const first=$('chat-dialog').dataset.conversationId;change('chat-message','First bound draft');$('chat-close').click();
 $('task-thread-new').click();await wait(()=>$('chat-dialog').open&&!$('chat-send').disabled&&$('chat-dialog').dataset.conversationId!==first);
 const second=$('chat-dialog').dataset.conversationId;change('chat-message','Second bound draft');$('chat-close').click();
 await wait(()=>[...$('task-thread').options].some(o=>o.value===first)&&[...$('task-thread').options].some(o=>o.value===second));
 const one=(await api('/api/conversations/'+first)).conversation,two=(await api('/api/conversations/'+second)).conversation;
 assert(one.task_id===taskRow.dataset.taskId&&two.task_id===one.task_id,'two independent discussions persist for the same saved task');
 change('task-thread',first);$('task-thread-open').click();await wait(()=>$('chat-dialog').open&&!$('chat-send').disabled);assert($('chat-message').value==='First bound draft','task picker restores the correct per-thread draft');
 $('chat-close').click();$('task-dialog').querySelector('.close-dialog').click();$('nav-discussions').click();
 const fixtures=(await api('/api/conversations?scope=free&limit=30')).conversations,older=fixtures.find(c=>c.title==='QA · Questions libres');
 if(older){await wait(()=>document.querySelector(`[data-conversation-id="${older.id}"]`));document.querySelector(`[data-conversation-id="${older.id}"]`).click();await wait(()=>!$('chat-older').hidden);const count=$('chat-log').querySelectorAll('[data-message-id]').length;$('chat-older').click();await wait(()=>$('chat-log').querySelectorAll('[data-message-id]').length>count);const ids=[...$('chat-log').querySelectorAll('[data-message-id]')].map(e=>e.dataset.messageId);assert(ids.length===48&&new Set(ids).size===48,'older persisted history loads without duplicates');$('chat-close').click();}
 return {passed:results.length,request_status:request.status,conversation_id:created.id,results};
})()

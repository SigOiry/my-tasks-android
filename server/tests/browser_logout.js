/* Destructive only to the scratch browser session; never to task data. */
(async()=>{
 if(location.origin!=='http://127.0.0.1:9138')throw new Error('Scratch loopback browser only.');
 const $=id=>document.getElementById(id);
 for(const d of document.querySelectorAll('dialog[open]'))d.close();
 // Open an ordinary task so its private category is populated in the inspector.
 document.querySelector('[data-view=active]').click();
 const row=Array.from(document.querySelectorAll('.task-row')).find(e=>!e.classList.contains('unread'));
 if(!row)throw new Error('A read task is required.');
 row.querySelector('.task-open').click();
 const wait=async f=>{for(let i=0;i<80;i++){if(f())return;await new Promise(r=>setTimeout(r,50));}throw new Error('Timeout');};
 await wait(()=>$('task-dialog').open);
 if(!$('detail-context').textContent)throw new Error('Inspector was not populated.');
 $('task-dialog').querySelector('.close-dialog').click();
 const settings=innerWidth<=700?$('mobile-settings'):$('settings-button');settings.click();
 await wait(()=>$('settings-dialog').open);$('logout').click();
 await wait(()=>!$('login').hidden);
 const session=await(await fetch('/auth/session')).json();
 if(session.authenticated)throw new Error('Session not revoked.');
 if((await fetch('/api/conversations')).status!==401)throw new Error('Durable history must require a new authenticated session.');
 for(const id of ['discussion-list','task-thread','chat-title','chat-context','chat-status','project-filter','project-options','projects','detail-context','detail-heading','chat-log','task-list','updates-heading','updates-summary','updates-title','rename-error','source-links','source-accounts']){
  if($(id).textContent.trim())throw new Error('Private DOM remains in '+id);
 }
 for(const e of document.querySelectorAll('input,textarea'))if(e.value)throw new Error('Private form value remains.');
 if(document.querySelector('dialog[open]'))throw new Error('A private dialog remains open.');
 if($('chat-dialog').dataset.conversationId||$('rename-dialog').dataset.conversationId)throw new Error('Private dialog bindings remain.');
 if(Object.keys(localStorage).some(k=>k.startsWith('mytasks.web.')))throw new Error('Workspace storage remains.');
 return {passed:true,checks:['server revocation','private DOM cleared','form values cleared','workspace cursor/preferences cleared','public login restored']};
})()

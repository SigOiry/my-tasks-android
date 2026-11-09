/* Authenticated UI states, no credentials. Scratch service only. */
(async()=>{
 if(location.origin!=='http://127.0.0.1:9138')throw new Error('Scratch loopback only.');
 const $=id=>document.getElementById(id),results=[];
 const assert=(ok,label)=>{if(!ok)throw new Error(label);results.push(label);};
 const wait=async fn=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,50));}throw new Error('State UI timeout');};
 for(const d of document.querySelectorAll('dialog[open]'))d.close();
 $('mobile-settings').click();await wait(()=>$('settings-dialog').open);
 for(const id of ['motion','transparency']){$(id).value='reduce';$(id).dispatchEvent(new Event('change',{bubbles:true}));assert(document.documentElement.dataset[id]==='reduce',id+' preference applied');}
 assert(getComputedStyle(document.querySelector('.destinations')).backdropFilter==='none','reduced transparency uses opaque navigation');
 assert(getComputedStyle(document.querySelector('.destinations button')).transitionDuration==='0s','reduced motion removes control transitions');
 $('change-password').click();assert(!$('password-pane').hidden&&$('app').hidden,'normal password-change pane gates the visible workspace');
 assert($('password-cancel').textContent==='Cancel','ordinary password change remains cancellable');$('password-cancel').click();assert(!$('app').hidden&&$('password-pane').hidden,'cancel restores the full workspace');
 $('nav-discussions').click();
 const listed=await(await fetch('/api/conversations?scope=free&limit=30')).json();
 for(const state of ['failed','interrupted']){
  const fixture=listed.conversations.find(c=>c.title==='QA fixture '+state);if(!fixture)throw new Error('Explicit persisted '+state+' fixture required');
  await wait(()=>document.querySelector(`.discussion-row[data-conversation-id="${fixture.id}"]`));document.querySelector(`.discussion-row[data-conversation-id="${fixture.id}"]`).click();
  await wait(()=>$('chat-error').textContent.includes('Synthetic fixture: '+state)&&$('chat-log').textContent.includes('Synthetic QA request')&&!$('chat-retry').disabled);
  assert(!$('chat-retry').hidden&&!$('chat-retry').disabled,state+' state offers an explicit retry, not automatic work');assert($('chat-log').textContent.includes('Synthetic QA request'),state+' state preserves the stored user message');$('chat-close').click();
 }
 assert(document.documentElement.scrollWidth<=innerWidth,'state views have no horizontal overflow');return {passed:results.length,results};
})()

import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInContext} from 'node:vm';
import {harness} from './description_harness.mjs';

const flatten=e=>[e,...(e.children||[]).flatMap(flatten)];
const text=e=>flatten(e).map(n=>n.textContent||'').join(' ');
const rows=h=>flatten(h.get('task-list')).filter(e=>e.attributes?.['data-task-id']);
const ids=h=>rows(h).map(e=>e.attributes['data-task-id']);
const task=(id,extra={})=>({id,title:'Review '+id,status:'todo',priority:'medium',project:'Research',due_date:'2026-11-18',revision:'rev-'+id,notification:{unread:true,token:'exact:'+id},...extra});
async function list(tasks=[]){
 const h=await harness();h.get('task-dialog').close();h.context.document.activeElement=null;
 h.state.data={tasks,today:'2026-11-16',week_end:'2026-11-22'};
 h.get('sort').value='due';h.get('sort-direction').value='asc';
 return h;
}

test('Updates only narrows the selected ordered list without acknowledging or admitting deleted tasks',async()=>{
 const tasks=[task('late',{due_date:'2026-11-20'}),task('read',{notification:{unread:false,token:'read'}}),task('early',{due_date:'2026-11-17'}),task('other',{project:'Teaching'}),task('waiting',{status:'waiting'}),task('miss',{title:'Unrelated'}),task('deleted',{deleted:true})];
 const h=await list(tasks);h.state.view='all';h.state.project='Research';h.get('search').value='Review';h.get('status-filter').value='todo';
 h.state.updatesOnly=true;h.render();
 assert.deepEqual(ids(h),['early','late']);
 assert.equal(h.get('updates-only').getAttribute('aria-pressed'),'true');
 assert.match(h.get('filter-summary').textContent,/2 tasks/);
 for(const value of ['All tasks','Research','Review','To do','Updates only'])assert.ok(h.get('filter-summary').textContent.includes(value),value);
 assert.equal(h.calls.length,0);assert.equal(tasks[0].notification.token,'exact:late');
 h.state.view='trash';h.render();assert.deepEqual(ids(h),[]);
 assert.equal(h.get('updates-only').hidden,false,'zero results must retain the way out');
 h.get('updates-only').listeners.click();assert.deepEqual(ids(h),['deleted']);
 assert.equal(h.state.view,'trash');assert.equal(h.calls.length,0);
});

test('Clear filters removes narrowing only, retains scope/preferences, and returns focus to the persistent toggle',async()=>{
 const h=await list([task('deleted',{deleted:true})]);h.state.view='trash';h.state.project='Unknown';h.state.updatesOnly=true;
 h.get('search').value='private query';h.get('status-filter').value='waiting';
 h.get('sort').value='created';h.get('sort-direction').value='desc';h.get('color-mode').value='neutral';
 h.render();
 assert.equal(h.get('clear-task-filters').hidden,false);
 h.get('clear-task-filters').listeners.click();
 assert.equal(h.state.view,'trash');assert.equal(h.state.project,'');assert.equal(h.state.updatesOnly,false);
 assert.equal(h.get('search').value,'');assert.equal(h.get('status-filter').value,'');
 assert.equal(h.get('project-filter').value,'');assert.equal(h.get('sort').value,'created');
 assert.equal(h.get('sort-direction').value,'desc');assert.equal(h.get('color-mode').value,'neutral');
 assert.deepEqual(ids(h),['deleted']);assert.equal(h.calls.length,0);
 assert.equal(h.context.document.activeElement,h.get('updates-only'));
 assert.equal(h.get('clear-task-filters').hidden,true);
});

test('zero-result states explain the current scope and offer reversible, non-mutating recovery',async()=>{
 const h=await list();
 const cases=[['active','No active tasks','Create a task'],['today','Nothing due today','Show all tasks'],['week','Nothing due this week','Show all tasks'],['all','No tasks yet','Create a task'],['trash','Nothing in Trash','Show all tasks']];
 for(const [view,heading,action] of cases){
  h.state.view=view;h.render();assert.match(text(h.get('task-list')),new RegExp(heading));
  assert.ok(flatten(h.get('task-list')).some(e=>e.tagName==='button'&&e.textContent===action));
  assert.equal(h.state.view,view);
 }
 h.state.view='today';h.render();flatten(h.get('task-list')).find(e=>e.tagName==='button').listeners.click();
 assert.equal(h.state.view,'all');assert.equal(h.calls.length,0);
 h.state.view='week';h.state.updatesOnly=true;h.render();
 assert.match(text(h.get('task-list')),/No unread updates/);
 flatten(h.get('task-list')).find(e=>e.tagName==='button').listeners.click();
 assert.equal(h.state.updatesOnly,false);assert.equal(h.state.view,'week');
 h.state.project='Missing';h.get('search').value='No match';h.render();
 assert.match(text(h.get('task-list')),/No matching tasks/);
 flatten(h.get('task-list')).find(e=>e.tagName==='button').listeners.click();
 assert.equal(h.state.project,'');assert.equal(h.get('search').value,'');assert.equal(h.state.view,'week');
 assert.equal(h.calls.length,0);
 h.state.view='active';h.render();flatten(h.get('task-list')).find(e=>e.tagName==='button').listeners.click();
 assert.equal(h.get('task-dialog').open,true);assert.equal(h.get('task-editor').hidden,false);assert.equal(h.calls.length,0);
});

test('initial loading is not an empty list; failed load offers retry through the existing refresh controller',async()=>{
 const h=await list();h.state.data=null;let release;
 h.context.fetch=async()=>new Promise(resolve=>{release=()=>resolve({ok:false,status:503,json:async()=>({detail:'Synthetic unavailable'})});});
 const pending=h.refreshTasks();
 assert.match(h.get('task-list-status').textContent,/Loading tasks/);
 assert.equal(h.get('task-list').getAttribute('aria-busy'),'true');
 assert.equal(h.get('filter-summary').hidden,true);
 release();await assert.rejects(pending,/Synthetic unavailable/);
 assert.match(h.get('task-list-status').textContent,/Could not load tasks/);
 assert.equal(h.get('task-list-retry').hidden,false);assert.equal(h.state.data,null);
 assert.equal(text(h.get('task-list')).trim(),'');
 h.context.fetch=async(path,options)=>{h.calls.push({path,options});return {ok:true,status:200,json:async()=>path==='/api/tasks'?{tasks:[task('fresh')],today:'2026-11-16',week_end:'2026-11-22'}:{events:[],cursor:0,has_more:false}};};
 await h.get('task-list-retry').listeners.click();
 assert.deepEqual(ids(h),['fresh']);assert.equal(h.get('task-list-retry').hidden,true);
 assert.equal(h.get('task-list').getAttribute('aria-busy'),'false');
 assert.ok(h.calls.some(c=>c.path.startsWith('/api/events')));assert.ok(h.calls.some(c=>c.path==='/api/tasks'));
 assert.ok(h.calls.every(c=>c.options.method==='GET'));
});

test('cached refresh failure retains rows and writing, reports offline honestly, and denies task mutation',async()=>{
 const t=task('cached',{notification:{unread:false,token:'keep-exact'}}),h=await list([t]);h.render();
 const previous=h.state.data;
 h.context.fetch=async()=>{throw new Error('Synthetic transport failure');};
 await h.backgroundRefresh();
 assert.match(h.get('task-list-status').textContent,/Offline/);
 assert.match(h.get('task-list-status').textContent,/last loaded tasks/);
 assert.equal(h.get('task-list-retry').hidden,false);assert.equal(h.state.data,previous);
 assert.deepEqual(ids(h),['cached']);assert.equal(h.state.online,false);
 const check=flatten(rows(h)[0]).find(e=>e.className==='task-check');assert.equal(check.disabled,true);
 await h.quickComplete(t,check);assert.equal(h.calls.length,0);assert.equal(t.notification.token,'keep-exact');
 h.showTask(t);h.get('task-edit').listeners.click();h.get('notes').value='Do not replace this draft';h.get('task-form').listeners.input();
 const generation=h.state.editorGeneration,oldRows=rows(h);
 await h.backgroundRefresh();
 assert.equal(h.get('notes').value,'Do not replace this draft');assert.equal(h.state.editorGeneration,generation);assert.equal(h.state.dirty,true);
 assert.equal(rows(h)[0],oldRows[0]);
});

test('rows retain complete safe titles, explicit status/project/date metadata, unread labels and existing action targets',async()=>{
 const title='<img src=x onerror=alert(1)> '+ 'UnbrokenTitle'.repeat(24);
 const h=await list([task('inbox',{title,status:'inbox',project:'',due_date:null}),task('todo',{notification:{unread:false,token:'read'}})]);
 h.get('sort').value='project';h.render();
 const row=rows(h).find(e=>e.attributes['data-task-id']==='inbox'),nodes=flatten(row);
 assert.equal(nodes.find(e=>e.className==='task-title').textContent,title);
 assert.equal(nodes.find(e=>e.className==='row-status')?.textContent,'Inbox');
 assert.equal(nodes.find(e=>e.className==='row-project')?.textContent,'Unassigned');
 assert.equal(nodes.find(e=>e.className==='due')?.textContent,'No due date');
 assert.equal(nodes.find(e=>e.className==='unread-badge')?.textContent,'New update');
 assert.equal(nodes.some(e=>e.tagName==='img'),false);
 assert.ok(nodes.find(e=>e.className==='task-check').listeners.click);
 assert.ok(nodes.find(e=>e.className?.startsWith('task-more ')).listeners.click);
 assert.equal(flatten(rows(h).find(e=>e.attributes['data-task-id']==='todo')).find(e=>e.className==='row-status')?.textContent,'To do');
 assert.equal(h.calls.length,0);
});

test('update review leads with task identity, preserves supplied prose safely, and separates review from dismissal',async()=>{
 const t=task('review',{title:'Exact <script>task</script>',notification:{unread:true,token:' MixedCase:token/+== ',headline:'Supplied headline',sections:{context:'Original **context**',update:'<img src=x onerror=alert(1)>',next_steps:'1. Read the evidence\n2. Reply when ready'}}});
 const h=await list([t]);h.showUpdates(t);
 assert.equal(h.get('updates-dialog').getAttribute('aria-labelledby'),'updates-title');
 assert.equal(h.get('updates-title').textContent,t.title);assert.equal(h.get('updates-heading').textContent,'Supplied headline');
 assert.match(text(h.get('updates-summary')),/Original.*context/);assert.match(text(h.get('updates-summary')),/Read the evidence/);
 assert.equal(flatten(h.get('updates-summary')).some(e=>['img','script'].includes(e.tagName)),false);
 assert.equal(h.state.gate.token,' MixedCase:token/+== ');assert.equal(h.calls.length,0);
 const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
 assert.match(html,/<h2 id="updates-title">/);
 assert.match(html,/id="updates-review-help"[^>]*>Mark reviewed acknowledges only the update shown here\./);
 assert.match(html,/Opening the task, asking Hermès, replying or closing leaves it unread\./);
 assert.equal(h.get('updates-ack').getAttribute('aria-describedby'),'updates-review-help');
 h.get('updates-close').listeners.click();assert.equal(h.state.gate,null);assert.equal(t.notification.unread,true);assert.equal(h.calls.length,0);
});

test('logout resets in-memory triage and scrubs its DOM; a delayed refresh cannot revive it',async()=>{
 const h=await list([task('private')]);h.state.updatesOnly=true;h.state.project='Private project';h.get('search').value='Private query';h.render();
 let release;h.context.fetch=async()=>new Promise(resolve=>{release=()=>resolve({ok:true,status:200,json:async()=>({tasks:[task('late private')],today:'2026-11-16'})});});
 const pending=h.refreshTasks();h.clearPrivate();
 assert.equal(h.state.updatesOnly,false);assert.equal(h.state.taskLoading,false);assert.equal(h.state.taskLoadFailed,false);
 assert.equal(h.get('updates-only').getAttribute('aria-pressed'),'false');
 assert.equal(text(h.get('filter-summary')),'');assert.equal(text(h.get('task-list-status')),'');
 assert.equal(h.get('task-list-retry').hidden,true);assert.equal(h.get('clear-task-filters').hidden,true);
 assert.equal(h.get('task-list').getAttribute('aria-busy'),'false');
 release();await assert.rejects(pending,/Session ended/);
 assert.equal(h.state.data,null);assert.equal(text(h.get('task-list')),'');assert.equal(text(h.get('task-list-status')),'');
 h.get('updates-only').listeners.click();h.get('clear-task-filters').listeners.click();await h.get('task-list-retry').listeners.click();
 assert.equal(h.state.updatesOnly,false);assert.equal(h.calls.length,0);
});

test('refresh preserves the focused priority action and the visible row scroll anchor',async()=>{
 const h=await list([task('anchor')]),doc=h.context.document,create=doc.createElement;
 let top=100;
 const matches=(e,selector)=>selector==='[data-task-id]'?!!e.attributes?.['data-task-id']:e.className?.split(' ').includes(selector.slice(1));
 const dom=e=>{
  e.querySelectorAll=selector=>flatten(e).slice(1).filter(n=>matches(n,selector));e.querySelector=selector=>e.querySelectorAll(selector)[0]||null;
  e.closest=selector=>rows(h).find(row=>flatten(row).includes(e)&&matches(row,selector))||null;
  e.classList={contains:name=>e.className?.split(' ').includes(name),remove(){}};
  e.getBoundingClientRect=()=>({top:e._top??top,bottom:(e._top??top)+68});
  Object.defineProperty(e,'isConnected',{get:()=>flatten(h.get('task-list')).includes(e)});
  const set=e.setAttribute;e.setAttribute=(key,value)=>{set.call(e,key,value);if(key==='data-task-id')e.dataset.taskId=value;};return e;
 };
 doc.createElement=tag=>dom(create(tag));dom(h.get('task-list'));
 const all=doc.querySelectorAll;doc.querySelectorAll=selector=>selector==='.task-row'?rows(h):all(selector);
 h.render();const row=rows(h)[0];row._top=100;
 const more=flatten(row).find(e=>e.className?.startsWith('task-more '));more.focus();
 let offset;h.context.window.scrollBy=(x,y)=>{offset=y;};top=140;h.render();
 assert.equal(doc.activeElement.className,more.className);
 assert.notEqual(doc.activeElement,more);assert.equal(doc.activeElement.isConnected,true);
 assert.equal(offset,40);
});

const turn=()=>new Promise(resolve=>setImmediate(resolve));
function deferredRefreshTransport(h,{holdEvents=false}={}) {
 const pending=[],requests=[];
 h.context.fetch=(path,options)=>{
  requests.push({path,options});
  if(path.startsWith('/api/events')&&!holdEvents)return Promise.resolve({ok:true,status:200,json:async()=>({events:[],cursor:0,has_more:false})});
  return new Promise((resolve,reject)=>pending.push({path,options,settle(outcome,id='fresh'){
   if(outcome==='offline'){reject(new TypeError('Synthetic offline '+id));return;}
   if(outcome==='abort'){reject(Object.assign(new Error('Synthetic abort '+id),{name:'AbortError'}));return;}
   resolve({ok:outcome==='success',status:outcome==='success'?200:503,json:async()=>outcome==='success'?(path==='/api/tasks'?{tasks:[task(id)],today:'2026-11-16',week_end:'2026-11-22'}:{events:[],cursor:0,has_more:false}):{detail:'Synthetic unavailable '+id}});
  }}));
 };
 return {pending,requests};
}
const refreshFeedback=h=>({busy:h.state.taskLoading,ariaBusy:h.get('task-list').getAttribute('aria-busy'),failed:h.state.taskLoadFailed,status:h.get('task-list-status').textContent,retryHidden:h.get('task-list-retry').hidden,globalHidden:h.get('global-error').hidden});

test('overlapping refresh regression: older 503 cannot end busy or leave failure after newer success',async()=>{
 const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h);
 const a=h.backgroundRefresh();await turn();const b=h.backgroundRefresh();await turn();
 assert.equal(transport.pending.length,2,'each refresh must reach its own task GET');
 transport.pending[0].settle('failure','older');await a;
 const whilePending=refreshFeedback(h);
 transport.pending[1].settle('success','newer');await b;
 assert.deepEqual({whilePending:{busy:whilePending.busy,ariaBusy:whilePending.ariaBusy},final:refreshFeedback(h)}, {
  whilePending:{busy:true,ariaBusy:'true'},
  final:{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true}
 });
 assert.deepEqual(ids(h),['newer']);
});

const startRefresh=(h,caller)=>h[caller]().then(()=>({}),error=>({error}));
function login(h) {
 h.context.crypto={subtle:{digest:async()=>new Uint8Array(32)}};h.context.setInterval=()=>0;
 return runInContext('beginSession({csrf:"fixture-relogin",password_change_required:false})',h.context);
}

test('refresh ownership fences initial-session errors after a newer task refresh succeeds',async t=>{
 for(const phase of ['tasks','events'])for(const outcome of ['failure','offline'])await t.test(`${phase} ${outcome}`,async()=>{
  const h=await list();h.clearPrivate();const transport=deferredRefreshTransport(h,{holdEvents:true});
  const initial=login(h);await turn();
  assert.equal(transport.pending[0].path,'/api/tasks');
  if(phase==='events'){
   transport.pending[0].settle('success','initial');await turn();
   assert.ok(transport.pending[1].path.startsWith('/api/events'));
  }
  const stale=transport.pending.at(-1),newer=startRefresh(h,'refreshTasks');await turn();
  transport.pending.at(-1).settle('success','newer');await newer;
  assert.equal(h.state.taskLoading,true,'initial refresh work remains pending');
  stale.settle(outcome,'initial-stale');await initial;
  assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});
  assert.equal(h.state.online,true);assert.deepEqual(ids(h),['newer']);
 });
});

test('refresh ownership matrix: newest task GET owns feedback in either completion order',async t=>{
 for(const callers of [['backgroundRefresh','backgroundRefresh'],['refreshTasks','backgroundRefresh'],['backgroundRefresh','refreshTasks'],['refreshTasks','refreshTasks']])
 for(const order of [[0,1],[1,0]])for(const older of ['success','failure','offline'])for(const newer of ['success','failure','offline']){
  await t.test(`${callers.join('/')} ${older}/${newer} completes ${order.join(',')}`,async()=>{
   const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h);
   const work=[startRefresh(h,callers[0])];await turn();work.push(startRefresh(h,callers[1]));await turn();
   assert.equal(transport.pending.length,2,'overlap must issue independent GETs, not reuse in-flight data');
   const outcomes=[older,newer],results=[];
   for(const [index,which] of order.entries()){
    transport.pending[which].settle(outcomes[which],which?'newer':'older');results[which]=await work[which];
    assert.equal(h.state.taskLoading,index===0,'busy lasts until every current-session request settles');
    assert.equal(h.get('task-list').getAttribute('aria-busy'),String(index===0));
    assert.equal(h.get('task-list-retry').disabled,index===0);
   }
   for(let i=0;i<2;i++)assert.equal(!!results[i].error,callers[i]==='refreshTasks'&&outcomes[i]!=='success','direct failures still reject');
   assert.equal(h.state.taskLoadFailed,newer!=='success');assert.equal(h.state.online,newer!=='offline');
   assert.equal(h.get('task-list-retry').hidden,newer==='success');
   assert.deepEqual(ids(h),[newer==='success'?'newer':'cached'],'an older response cannot replace newer request data');
   assert.equal(h.get('global-error').hidden,!(newer!=='success'&&callers[1]==='backgroundRefresh'));
   if(newer==='success')assert.equal(h.get('task-list-status').textContent,'');
   else {
    assert.match(h.get('task-list-status').textContent,newer==='offline'?/Offline.*last loaded tasks/:/Could not refresh tasks.*last loaded tasks/);
    if(callers[1]==='backgroundRefresh'){
     assert.ok(h.get('global-error').textContent);assert.doesNotMatch(h.get('global-error').textContent,/older/);
     if(newer==='failure')assert.match(h.get('global-error').textContent,/newer/);
    }
   }
  });
 }
});

test('refresh ownership includes pending event work and fences its stale errors',async t=>{
 for(const outcome of ['failure','offline'])await t.test(outcome,async()=>{
  const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h,{holdEvents:true});
  const background=startRefresh(h,'backgroundRefresh');await turn();
  assert.equal(transport.pending[0].path.startsWith('/api/events'),true);
  assert.equal(h.get('task-list').getAttribute('aria-busy'),'true','event polling is pending refresh work');
  const direct=startRefresh(h,'refreshTasks');await turn();transport.pending[1].settle('success','newer');await direct;
  assert.equal(h.state.taskLoading,true);
  transport.pending[0].settle(outcome,'older-event');await background;
  assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});
  assert.equal(h.state.online,true);assert.deepEqual(ids(h),['newer']);
  assert.equal(transport.requests.filter(r=>r.path==='/api/tasks').length,1,'failed event poll cannot proceed to tasks');
 });
});

test('refresh ownership survives logout and actual relogin without old completions touching new feedback',async t=>{
 for(const caller of ['refreshTasks','backgroundRefresh'])for(const oldOutcome of ['success','failure','offline'])
 for(const newOutcome of ['success','failure','offline'])for(const oldFirst of [true,false])await t.test(`${caller} ${oldOutcome}/${newOutcome} oldFirst=${oldFirst}`,async()=>{
  const h=await list([task('old-private')]);h.render();const transport=deferredRefreshTransport(h);
  const old=startRefresh(h,caller);await turn();const oldRequest=transport.pending[0];
  h.clearPrivate();assert.equal(oldRequest.options.signal.aborted,true);
  assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});
  const fresh=login(h);await turn();const newRequest=transport.pending[1];assert.equal(newRequest.path,'/api/tasks');
  if(oldFirst){
   const before=refreshFeedback(h);oldRequest.settle(oldOutcome,'old-private');await old;
   assert.deepEqual(refreshFeedback(h),before);assert.equal(h.state.data,null);assert.equal(h.state.taskLoading,true);
  }
  newRequest.settle(newOutcome,'new-session');await fresh;
  const before=refreshFeedback(h);
  if(!oldFirst){oldRequest.settle(oldOutcome,'old-private');await old;assert.deepEqual(refreshFeedback(h),before);}
  assert.equal(h.state.authenticated,true);assert.equal(h.state.taskLoading,false);assert.equal(h.get('task-list').getAttribute('aria-busy'),'false');
  assert.equal(h.state.taskLoadFailed,newOutcome!=='success');assert.equal(h.state.online,newOutcome!=='offline');
  assert.equal(h.get('global-error').hidden,newOutcome==='success');
  assert.deepEqual(ids(h),newOutcome==='success'?['new-session']:[]);
  assert.doesNotMatch(text(h.get('task-list'))+' '+h.get('global-error').textContent,/old-private/);
 });
});

test('logout fences pending event preflight before relogin, including late transport failures',async t=>{
 for(const outcome of ['success','failure','offline'])await t.test(outcome,async()=>{
  const h=await list([task('old-private')]);const transport=deferredRefreshTransport(h,{holdEvents:true});
  const old=startRefresh(h,'backgroundRefresh');await turn();h.clearPrivate();
  const fresh=login(h);await turn();
  transport.pending[1].settle('success','new-session');await turn();
  assert.ok(transport.pending[2].path.startsWith('/api/events'));
  const before=refreshFeedback(h);transport.pending[0].settle(outcome,'old-event');await old;
  assert.deepEqual(refreshFeedback(h),before);assert.equal(h.state.polling,true,'old finally cannot release the new event poll');
  transport.pending[2].settle('success');await fresh;
  assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});
  assert.deepEqual(ids(h),['new-session']);assert.equal(transport.requests.filter(r=>r.path==='/api/tasks').length,1);
 });
});

test('refresh ownership follows task GET start order after a delayed event preflight',async()=>{
 const h=await list([task('cached')]);const transport=deferredRefreshTransport(h,{holdEvents:true});
 const background=startRefresh(h,'backgroundRefresh');await turn();const direct=startRefresh(h,'refreshTasks');await turn();
 transport.pending[0].settle('success');await turn();
 assert.equal(transport.pending[2].path,'/api/tasks','preflight must still issue a fresh GET');
 transport.pending[2].settle('success','latest-task-get');await background;
 assert.equal(h.state.taskLoading,true);transport.pending[1].settle('failure','older-task-get');await direct;
 assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});
 assert.deepEqual(ids(h),['latest-task-get']);
});

// Preflight is provisional feedback work, not a dispatched task-list GET.
// Within one GET generation, its newer preflight failure and that GET's success
// apply as they settle; a later actual GET permanently fences both older owners.
test('preflight boundary: a failing event preflight cannot discard the only successful task GET',async t=>{
 for(const eventFirst of [false,true])for(const eventSettlesFirst of [false,true])for(const outcome of ['failure','offline','abort'])await t.test(`eventFirst=${eventFirst} eventSettlesFirst=${eventSettlesFirst} ${outcome}`,async()=>{
  const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h,{holdEvents:true}),work={};
  for(const name of eventFirst?['event','direct']:['direct','event']){work[name]=startRefresh(h,name==='event'?'backgroundRefresh':'refreshTasks');await turn();}
  const event=transport.pending.find(r=>r.path.startsWith('/api/events')),direct=transport.pending.find(r=>r.path==='/api/tasks');
  assert.ok(event&&direct);assert.equal(transport.pending.length,2);
  const order=eventSettlesFirst?['event','direct']:['direct','event'];
  for(const [index,name] of order.entries()){
   (name==='event'?event:direct).settle(name==='event'?outcome:'success',name==='event'?'event-preflight':'only-task-get');await work[name];
   assert.equal(h.state.taskLoading,index===0);assert.equal(h.get('task-list').getAttribute('aria-busy'),String(index===0));
   assert.equal(h.get('task-list-retry').disabled,index===0);
   if(name==='direct')assert.deepEqual(ids(h),['only-task-get'],'preflight has not dispatched any task GET, so it cannot suppress the only task response');
   const applicableEventFailure=!eventFirst&&name==='event';
   assert.equal(h.state.taskLoadFailed,applicableEventFailure);
   assert.equal(h.get('global-error').hidden,!applicableEventFailure);
   assert.equal(h.state.online,!(applicableEventFailure&&outcome!=='failure'));
   assert.equal(h.get('task-list-retry').hidden,!applicableEventFailure);
  }
  assert.deepEqual(ids(h),['only-task-get']);assert.equal(transport.requests.filter(r=>r.path==='/api/tasks').length,1);
  if(!eventFirst&&!eventSettlesFirst)assert.match(h.get('global-error').textContent,outcome==='abort'?/timed out/:outcome==='failure'?/event-preflight/:/offline|Cannot reach/);
  else assert.equal(h.get('task-list-status').textContent,'');
 });
});

test('preflight boundary: standalone event failures still report and a later task success recovers',async t=>{
 for(const outcome of ['failure','offline','abort'])await t.test(outcome,async()=>{
  const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h,{holdEvents:true});
  const event=startRefresh(h,'backgroundRefresh');await turn();transport.pending[0].settle(outcome,'standalone');await event;
  assert.equal(transport.requests.filter(r=>r.path==='/api/tasks').length,0);
  assert.deepEqual(ids(h),['cached']);assert.equal(h.state.taskLoadFailed,true);assert.equal(h.get('global-error').hidden,false);
  assert.equal(h.state.online,outcome==='failure');assert.equal(h.get('task-list-retry').hidden,false);
  assert.equal(h.get('task-list').getAttribute('aria-busy'),'false');
  // A pending preflight is not recovery: it must not silently clear that failure.
  const retry=startRefresh(h,'backgroundRefresh');await turn();assert.equal(h.state.taskLoadFailed,true);assert.equal(h.get('global-error').hidden,false);
  const direct=startRefresh(h,'refreshTasks');await turn();transport.pending.at(-1).settle('success','recovered');await direct;
  assert.deepEqual(ids(h),['recovered']);assert.equal(h.state.online,true);assert.equal(h.state.taskLoadFailed,false);assert.equal(h.get('global-error').hidden,true);
  transport.pending[1].settle(outcome,'stale-retry');await retry;
  assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});assert.equal(h.state.online,true);
 });
});

test('preflight boundary: successful delayed events transfer data ownership only at their task GET dispatch',async t=>{
 for(const eventFirst of [false,true])for(const eventTaskSettlesFirst of [false,true])for(const outcome of ['success','failure','offline','abort'])await t.test(`eventFirst=${eventFirst} eventTaskSettlesFirst=${eventTaskSettlesFirst} ${outcome}`,async()=>{
  const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h,{holdEvents:true}),work={};
  for(const name of eventFirst?['event','direct']:['direct','event']){work[name]=startRefresh(h,name==='event'?'backgroundRefresh':'refreshTasks');await turn();}
  const event=transport.pending.find(r=>r.path.startsWith('/api/events')),direct=transport.pending.find(r=>r.path==='/api/tasks');
  event.settle('success');await turn();const latest=transport.pending.at(-1);
  assert.equal(latest.path,'/api/tasks');assert.notEqual(latest,direct);assert.equal(transport.requests.filter(r=>r.path==='/api/tasks').length,2);
  for(const [index,name] of (eventTaskSettlesFirst?['event','direct']:['direct','event']).entries()){
   (name==='event'?latest:direct).settle(name==='event'?outcome:'success',name==='event'?'newest-get':'older-get');await work[name];
   assert.equal(h.state.taskLoading,index===0);assert.equal(h.get('task-list').getAttribute('aria-busy'),String(index===0));
   assert.deepEqual(ids(h),[name==='event'&&outcome==='success'||eventTaskSettlesFirst&&outcome==='success'?'newest-get':'cached']);
  }
  assert.equal(h.state.taskLoadFailed,outcome!=='success');assert.equal(h.state.online,!['offline','abort'].includes(outcome));
  assert.equal(h.get('global-error').hidden,outcome==='success');assert.equal(h.get('task-list-retry').hidden,outcome==='success');
 });
});

test('preflight boundary: a later task GET permanently fences earlier preflight and task feedback',async t=>{
 for(const eventSettlesFirst of [false,true])for(const eventOutcome of ['failure','offline','abort'])for(const latestOutcome of ['success','failure','offline'])await t.test(`eventSettlesFirst=${eventSettlesFirst} ${eventOutcome}/${latestOutcome}`,async()=>{
  const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h,{holdEvents:true});
  const older=startRefresh(h,'refreshTasks');await turn();const preflight=startRefresh(h,'backgroundRefresh');await turn();
  const latest=h.refreshTasks({reportError:true}).catch(error=>({error}));await turn();
  transport.pending[0].settle('success','older-get');await older;
  assert.deepEqual(ids(h),['cached']);assert.equal(h.state.taskLoading,true);
  for(const [index,name] of (eventSettlesFirst?['event','latest']:['latest','event']).entries()){
   transport.pending[name==='event'?1:2].settle(name==='event'?eventOutcome:latestOutcome,name);await (name==='event'?preflight:latest);
   assert.equal(h.state.taskLoading,index===0);assert.equal(h.get('task-list').getAttribute('aria-busy'),String(index===0));
  }
  assert.deepEqual(ids(h),[latestOutcome==='success'?'latest':'cached']);assert.equal(h.state.taskLoadFailed,latestOutcome!=='success');
  assert.equal(h.state.online,latestOutcome!=='offline');assert.equal(h.get('global-error').hidden,latestOutcome==='success');
  if(latestOutcome==='failure')assert.match(h.get('global-error').textContent,/latest/);
  assert.equal(transport.requests.filter(r=>r.path==='/api/tasks').length,2);
 });
});

test('preflight boundary: initial eventsAfter failures are applicable until a newer task GET is dispatched',async t=>{
 for(const newer of [false,true])for(const outcome of ['failure','offline','abort'])await t.test(`newer=${newer} ${outcome}`,async()=>{
  const h=await list();h.clearPrivate();const transport=deferredRefreshTransport(h,{holdEvents:true});const initial=login(h);await turn();
  transport.pending[0].settle('success','initial');await turn();const event=transport.pending[1];assert.ok(event.path.startsWith('/api/events'));
  assert.deepEqual(ids(h),['initial']);assert.equal(h.state.taskLoading,true);
  if(newer){const direct=startRefresh(h,'refreshTasks');await turn();transport.pending[2].settle('success','newer');await direct;}
  event.settle(outcome,'events-after');await initial;
  assert.deepEqual(ids(h),[newer?'newer':'initial']);assert.equal(h.state.taskLoadFailed,!newer);
  assert.equal(h.state.online,newer||outcome==='failure');assert.equal(h.get('global-error').hidden,newer);assert.equal(h.get('task-list').getAttribute('aria-busy'),'false');
 });
});

test('post-mutation refresh issues a new GET and cannot reuse or be replaced by pre-mutation data',async()=>{
 const original=task('edited',{notification:{unread:false,token:'before'}}),saved=task('edited',{title:'Saved after mutation',revision:'after',notification:{unread:false,token:'after'}});
 const h=await list([original]);h.render();const transport=deferredRefreshTransport(h),fetch=h.context.fetch;
 h.context.fetch=(path,options)=>options.method==='PATCH'?Promise.resolve({ok:true,status:200,json:async()=>saved}):fetch(path,options);
 const before=startRefresh(h,'backgroundRefresh');await turn();
 const result=await h.mutate('/api/tasks/edited','PATCH',{revision:original.revision,title:saved.title});
 assert.equal(result.revision,'after');assert.equal(h.state.data.tasks[0].title,saved.title);
 const after=startRefresh(h,'refreshTasks');await turn();
 assert.equal(transport.pending.length,2,'post-mutation refresh cannot reuse the old pending GET');
 transport.pending[1].settle('success','after-mutation');await after;
 assert.equal(h.state.taskLoading,true);transport.pending[0].settle('success','before-mutation');await before;
 assert.deepEqual(ids(h),['after-mutation']);assert.equal(h.state.own.get('edited'),'after');
 assert.deepEqual(refreshFeedback(h),{busy:false,ariaBusy:'false',failed:false,status:'',retryHidden:true,globalHidden:true});
});

test('refresh ownership preserves authentication and restriction guards without relying only on epoch',async t=>{
 for(const guard of ['authenticated','restricted'])await t.test(guard,async()=>{
  const h=await list([task('cached')]);h.render();const transport=deferredRefreshTransport(h);
  const pending=startRefresh(h,'refreshTasks');await turn();
  h.state[guard]=guard==='restricted';const before=refreshFeedback(h),data=h.state.data;
  transport.pending[0].settle('success','must-not-apply');await pending;
  assert.equal(h.state.data,data);assert.deepEqual(refreshFeedback(h),before);
  await h.refreshTasks();await h.backgroundRefresh();assert.equal(transport.requests.length,1);
  h.clearPrivate();assert.equal(h.state.taskLoading,false);assert.equal(h.state.data,null);
 });
});

test('HTTP 503 retains last-loaded data without claiming an offline transport',async()=>{
 const h=await list([task('retained')]);h.render();const data=h.state.data;
 h.context.fetch=async(path)=>({ok:path!=='/api/tasks',status:path==='/api/tasks'?503:200,json:async()=>path==='/api/tasks'?{detail:'Synthetic task service unavailable'}:{events:[],cursor:0,has_more:false}});
 await h.backgroundRefresh();
 assert.equal(h.state.online,true);assert.equal(h.state.data,data);assert.deepEqual(ids(h),['retained']);
 assert.match(h.get('task-list-status').textContent,/Could not refresh tasks.*last loaded tasks/);
 assert.doesNotMatch(h.get('task-list-status').textContent,/Offline|read-only|synced|\d{2}:\d{2}/i);
 assert.equal(h.get('task-list-retry').hidden,false);
});

test('triage controls never persist private filters or disturb a deferred editor/filter render',async()=>{
 const h=await list([task('keep')]),writes=[];
 h.context.localStorage.setItem=(...args)=>writes.push(args);h.render();const before=rows(h)[0];
 h.get('notes').value='Untouched draft';h.state.dirty=true;
 h.get('updates-only').listeners.click();assert.equal(rows(h)[0],before);assert.equal(h.get('notes').value,'Untouched draft');
 h.state.dirty=false;const query=h.context.document.querySelector;
 h.context.document.querySelector=selector=>selector==='.filters[open]'?h.get('filters'):query(selector);
 h.state.project='Hidden filter';h.render();assert.equal(rows(h)[0],before);
 h.context.document.querySelector=query;h.get('clear-task-filters').listeners.click();
 assert.equal(h.state.updatesOnly,false);assert.equal(h.state.project,'');assert.deepEqual(writes,[]);assert.equal(h.calls.length,0);
});

test('all nine saved sort modes and both directions retain their bucket/group ordering under Updates only',async()=>{
 const tasks=[task('a',{title:'Zebra',project:'B',priority:'high',due_date:'2026-11-17',created_at:'2026-11-01T12:00:00Z',updated_at:'2026-11-10T12:00:00Z'}),task('b',{title:'Alpha',project:'A',priority:'low',due_date:'2026-11-18',created_at:'2026-11-02T12:00:00Z',updated_at:'2026-11-12T12:00:00Z'}),task('w',{status:'waiting',project:'B',priority:'high',due_date:'2026-11-01'}),task('c',{status:'completed',project:'C'}),task('read',{notification:{unread:false,token:'read'}}),task('deleted',{deleted:true})];
 const h=await list(tasks);h.state.view='all';h.state.updatesOnly=true;
 h.context.Date=class extends Date {constructor(...args){super(...(args.length?args:['2026-11-16T12:00:00Z']));}};
 const expected={smart:[['a','b','w','c'],['b','a','w','c']],importance:[['a','b','w','c'],['b','a','w','c']],due:[['a','b','w','c'],['b','a','w','c']],created:[['a','b','w','c'],['b','a','w','c']],updated:[['b','a','w','c'],['a','b','w','c']],project:[['b','a','w','c'],['a','w','b','c']],title:[['b','a','w','c'],['a','w','b','c']],priority:[['a','w','b','c'],['b','a','w','c']],date:[['a','w','b','c'],['b','a','w','c']]};
 for(const [sort,orders] of Object.entries(expected))for(const [index,direction] of ['asc','desc'].entries()){
  h.get('sort').value=sort;h.get('sort-direction').value=direction;h.render();assert.deepEqual(ids(h),orders[index],sort+' '+direction);
 }
 assert.equal(h.calls.length,0);assert.ok(tasks.every(t=>t.notification.token===(['read'].includes(t.id)?'read':'exact:'+t.id)));
});

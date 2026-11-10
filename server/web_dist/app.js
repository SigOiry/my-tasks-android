import {localCalendarDate,projectPalette,UpdateBreaths,selectTasks,groupTasks,statuses,active,safeSource,shouldNotify,updateGate,acknowledged,editPayload,passwordError,safeMarkdown} from './policy.mjs';
import {ConversationStore,pendingRequest} from './conversations.mjs';
import {mountEmailWorkspace} from './email-workspace.mjs';
import {renderCitations} from './citations.mjs';

// Consume ownership proof before any asynchronous work; never persist or log it.
let activationCode=null;
if(typeof location!=='undefined' && location.hash.startsWith('#activate=')) {
 const fragment=location.hash.slice(10);
 history.replaceState(null,'',location.pathname+location.search);
 activationCode=/^[a-fA-F0-9]{64}$/.test(fragment)?fragment:'';
}

const $ = id => document.getElementById(id);
const form = $('task-form');
const views = {active:['Active','Active tasks','The things that still need your attention.','◫'],today:['Today','Today','Due today or overdue. A clear place to start.','◷'],week:['Week','This week','Upcoming deadlines through Sunday, with overdue work in view.','▦'],all:['All','All tasks','Your complete workspace, including finished work.','☷'],trash:['Trash','Trash','Kept safely until you decide what comes next.','⌑']};
const storagePrefix = 'mytasks.web.';
const readLocal = key => { try { return localStorage.getItem(storagePrefix+key); } catch { return null; } };
const writeLocal = (key,value) => { try { localStorage.setItem(storagePrefix+key,value); } catch { /* Private mode can disable storage. */ } };
const removeLocal = key => { try { localStorage.removeItem(storagePrefix+key); } catch { /* Optional persistence. */ } };
const state = {authenticated:false,csrf:'',epoch:0,editorGeneration:0,data:null,view:'active',project:'',selected:null,gate:null,dirty:false,snapshot:'',online:true,cursor:null,cursorKey:null,polling:false,mutating:false,own:new Map(),emphasis:new Map(),notices:new Set(),controllers:new Set(),info:null,recognition:null};
const chats = new ConversationStore(request);
const emailWorkspace=typeof mountEmailWorkspace==='function'?mountEmailWorkspace({request,copy:copyText}):null;
const discussion = {destination:'tasks',active:null,rows:[],cursor:null,loading:false,generation:0,taskGeneration:0,taskId:null,taskRows:[],taskCursor:null};
let pollTimer, chatTimer, toastTimer, accentObserver, taskOpening=false;
const breaths=new UpdateBreaths(2);
let accentTimer,renderTimer,interactionUntil=0,pointerBusy=false,renderPending=false;

function node(tag,attributes={},text) {
 const element = document.createElement(tag);
 for (const [key,value] of Object.entries(attributes)) {
  if (key === 'class') element.className = value;
  else element.setAttribute(key,value);
 }
 if (text !== undefined) element.textContent=text;
 return element;
}
function uiIcon(name) {
 if(!document.createElementNS)return node('span',{'aria-hidden':'true'});
 const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg'),use=document.createElementNS(ns,'use');
 svg.setAttribute('class','ui-icon');svg.setAttribute('aria-hidden','true');svg.setAttribute('focusable','false');use.setAttribute('href','#icon-'+name);svg.append(use);return svg;
}
function show(id,visible=true) { $(id).hidden=!visible; }
function message(id,value='') { $(id).textContent=value; }
function toast(text) { message('toast',text);show('toast');clearTimeout(toastTimer);toastTimer=setTimeout(()=>show('toast',false),4000); }
function errorText(error) { return error?.message || 'Something went wrong. Please try again.'; }
function setConnection(online,text) {
 state.online=online;document.documentElement.dataset.online=String(online);show('connection-bar',!online);
 message('connection',text || (online?'Connected · private':'Offline · read-only'));
 $('save-task').disabled=!online || !!state.selected?.deleted || !$('conflict').hidden;
 $('chat-send').disabled=!online || chatBlocked();
 $('updates-ack').disabled=!online || !state.gate || !$('updates-refresh').hidden;
 if (!online && state.gate) message('updates-error','Offline. Reconnect before acknowledging this update.');
 else if (online && $('updates-error').textContent==='Offline. Reconnect before acknowledging this update.') message('updates-error');
 if(discussion.active&&$('chat-dialog').open)renderChat();
}
function applyPreferences() {
 const theme=readLocal('theme'),density=readLocal('density');
 for(const [id,key,fallback] of [['sort','sort','smart'],['sort-direction','direction','asc'],['color-mode','color','project']]){const saved=readLocal(key);$(id).value=[...$(id).options].some(option=>option.value===saved)?saved:fallback;}
 document.documentElement.dataset.color=$('color-mode').value;
 document.documentElement.dataset.theme=['light','dark'].includes(theme)?theme:'system';
 document.documentElement.dataset.density=density==='compact'?'compact':'comfortable';
 $('theme').value=document.documentElement.dataset.theme;
 $('density').value=document.documentElement.dataset.density;
 for(const key of ['motion','transparency']) {const value=readLocal(key)==='reduce'?'reduce':'system';document.documentElement.dataset[key]=value;$(key).value=value;}
 watchUpdateAccents();
}
applyPreferences();

async function request(path,{method='GET',body,timeout=25000}={}) {
 if(state.restricted&&path.startsWith('/api/'))throw new Error('Change your password before opening the workspace.');
 const controller=new AbortController(),epoch=state.epoch;
 state.controllers.add(controller);
 const timer=setTimeout(()=>controller.abort(),timeout);
 try {
  const headers={};
  if (body!==undefined) headers['Content-Type']='application/json';
  if (!['GET','HEAD'].includes(method) && state.csrf) headers['X-CSRF-Token']=state.csrf;
  const response=await fetch(path,{method,body:typeof body==='string'?body:body===undefined?undefined:JSON.stringify(body),headers,credentials:'same-origin',cache:'no-store',signal:controller.signal});
  let result;
  try{result=await response.json();}catch{throw new Error('The server returned an unreadable response. Refresh to reconnect; nothing was automatically resent.');}
  if (epoch!==state.epoch) throw new Error('Session ended.');
  if (!response.ok) {
   if (response.status===401 && state.authenticated && path!=='/auth/password') clearPrivate('Your session ended. Please log in again.');
   const error=new Error(typeof result.detail==='string'?result.detail:response.status===422?'Please check the submitted fields.':'The request could not be completed.');
   error.status=response.status;
   if(response.status===428)error.message='Activation required. Open your private activation link or use your existing owner session, then change your password.';
   if(response.status===429)error.message='Too many attempts. Please wait before trying again.';
   throw error;
  }
  return result;
 } catch (error) {
  if (!error.status && epoch===state.epoch) {
   setConnection(false);
   if (error.name==='AbortError') throw new Error(method==='GET'?'Request timed out. Refresh to reconnect.':path==='/api/chat'?'The request timed out. Provider work may still be running. Nothing was resent; resending may duplicate provider work.':'Connection interrupted. The change may have reached the server. Refresh and review before trying again.');
   if (error instanceof TypeError) throw new Error(method==='GET'?'Cannot reach the server. Cached tasks are read-only until you reconnect.':'Connection interrupted. The request may have reached the server. Review before trying again; nothing was automatically resent.');
  }
  throw error;
 } finally {clearTimeout(timer);state.controllers.delete(controller);}
}

function clearPrivate(reason='') {
 state.epoch++;
 emailWorkspace?.clear();
 taskOpening=false;
 for(const id of ['delete-confirm','restore-task'])$(id).disabled=false;
 state.authenticated=false;
 state.csrf='';state.data=null;state.selected=null;state.gate=null;state.dirty=false;state.snapshot='';state.view='active';state.project='';state.cursor=null;state.cursorKey=null;state.info=null;
 chats.clear();state.own.clear();state.emphasis.clear();breaths.clear();clearTimeout(accentTimer);clearTimeout(renderTimer);renderPending=false;accentObserver?.disconnect();state.polling=false;state.mutating=false;
 discussion.active=null;discussion.rows=[];discussion.taskRows=[];discussion.cursor=null;discussion.taskCursor=null;discussion.loading=false;discussion.generation++;discussion.taskGeneration++;
 clearTimeout(chatTimer);discussion.taskId=null;discussion.destination='tasks';document.documentElement.dataset.destination='tasks';
 for(const id of ['chat-log','discussion-list','task-thread','discussion-task','discussion-list-status','discussion-error','task-thread-status','task-thread-error','chat-title','chat-context','chat-status','chat-error','rename-error'])$(id).replaceChildren();
 $('chat-message').value='';$('rename-dialog').dataset.conversationId='';$('chat-dialog').dataset.conversationId='';$('chat-log').dataset.signature='';$('chat-log').dataset.threadId='';
 show('tasks-workspace');show('discussions-workspace',false);$('nav-tasks').setAttribute('aria-current','page');$('nav-discussions').setAttribute('aria-current','false');
 for (const controller of state.controllers) controller.abort();
 state.controllers.clear();
 for (const notice of state.notices) notice.close();
 state.notices.clear();
 state.recognition?.abort();state.recognition=null;
 clearInterval(pollTimer);clearTimeout(toastTimer);message('toast');show('toast',false);
 for (const dialog of document.querySelectorAll('dialog[open]')) dialog.close();
 for (const input of document.querySelectorAll('input,textarea')) input.value='';
 for (const id of ['task-list','projects','project-options','views','chat-log','source-links','source-accounts','updates-summary','updates-heading','updates-title','detail-heading','detail-context','project-filter','sync-state','filter-summary','version-status','detail-error','chat-error','global-error','updates-error']) $(id).replaceChildren();
 form.reset();$('chat-form').reset();
 try { for (const key of Object.keys(localStorage)) if (key.startsWith(storagePrefix)) localStorage.removeItem(key); } catch { /* Nothing private is persisted. */ }
 applyPreferences();syncTaskPresentation();
 state.restricted=false;show('password-pane',false);show('app',false);show('login');message('login-error',reason);
}
async function beginSession(session) {
 const epoch=state.epoch;
 state.authenticated=true;state.csrf=session.csrf;state.restricted=!!session.password_change_required;
 show('login',false);message('login-error');
 if(state.restricted){openPassword(true);return;}
 show('password-pane',false);show('app');
 // Scope the durable transport cursor to this opaque server session, never to the bearer token.
 const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(session.csrf));
 if(epoch!==state.epoch||!state.authenticated)return;
 const scope=Array.from(new Uint8Array(digest)).map(b=>b.toString(16).padStart(2,'0')).join('').slice(0,24);
 state.cursorKey='cursor.'+scope;
 const stored=readLocal(state.cursorKey);
 state.cursor=stored && /^\d+$/.test(stored) && Number.isSafeInteger(Number(stored))?Number(stored):null;
 try {await refreshTasks();if(epoch!==state.epoch)return;await pollEvents();} catch(error) {if(epoch===state.epoch){message('global-error',errorText(error));show('global-error');}}
 if(epoch!==state.epoch||!state.authenticated)return;
 clearInterval(pollTimer);
 pollTimer=setInterval(()=>{if(state.authenticated&&!state.restricted) backgroundRefresh();},20000);
}
function currentSession(epoch) {return epoch===state.epoch && state.authenticated && !state.restricted;}
function requireSession(epoch) {if(!currentSession(epoch))throw new Error('Session ended.');}
async function refreshTasks() {
 const epoch=state.epoch;
 const data=await request('/api/tasks');
 if (!currentSession(epoch)) return;
 state.data=data;setConnection(true);render();show('global-error',false);
}
function options(overrides={}) {
 return {view:state.view,project:state.project,query:$('search').value,status:$('status-filter').value,sort:$('sort').value,direction:$('sort-direction').value,today:localToday(),week_end:state.data?.week_end,...overrides};
}
function localToday(){return localCalendarDate(new Date(),'Europe/Rome');}
function paletteStyle(element,task){const p=projectPalette(task);element.style.setProperty('--project-light',p.light);element.style.setProperty('--project-dark',p.dark);element.dataset.importance=task.priority||'low';}
function dateLabel(date) {
 if (!date) return 'No due date';
 try {return new Intl.DateTimeFormat(undefined,{day:'numeric',month:'short'}).format(new Date(date+'T12:00:00'));} catch {return date;}
}
function updateEmphasisAge(task) {
 breaths.observe(task.id,task.notification?.token,task.notification?.unread&&state.own.get(task.id)!==task.notification?.token);
 return breaths.age(task.id)<6400?breaths.age(task.id):null;
}
function watchUpdateAccents() {
 clearTimeout(accentTimer);accentObserver?.disconnect();
 const rows=[...document.querySelectorAll('.update-emphasis')];
 const reduce=document.documentElement.dataset.motion==='reduce'||window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
 const visible=!document.hidden&&!reduce?rows.filter(row=>{const r=row.getBoundingClientRect();return r.bottom>0&&r.top<window.innerHeight;}).map(row=>row.dataset.taskId):[];
 const running=new Set(breaths.tick(visible,Date.now()));
 for(const row of rows){const age=breaths.age(row.dataset.taskId);if(!row.dataset.accentBound){row.style.setProperty('--update-age',-age+'ms');row.dataset.accentBound='true';}row.style.animationPlayState=running.has(row.dataset.taskId)?'running':'paused';if(age>=6400)row.classList.remove('update-emphasis');}

 if(running.size)accentTimer=setTimeout(watchUpdateAccents,100);
}

function render() {
 if (!state.data) return;
 const focused=document.activeElement,focusTask=focused?.closest?.('[data-task-id]')?.dataset.taskId,focusClass=focused?.classList?.contains('task-check')?'task-check':'task-open',focusView=focused?.dataset?.view,focusProject=focused?.closest?.('#projects')?focused.textContent:null;
 if(pointerBusy||Date.now()<interactionUntil||state.dirty||document.querySelector('dialog[open]')||document.querySelector('.filters[open]')){renderPending=true;clearTimeout(renderTimer);renderTimer=setTimeout(render,250);return;}
 renderPending=false;
 const anchor=[...document.querySelectorAll('.task-row')].find(row=>row.getBoundingClientRect().bottom>0),anchorId=anchor?.dataset.taskId,anchorTop=anchor?.getBoundingClientRect().top;
 const tasks=state.data.tasks,visible=selectTasks(tasks,options()),view=views[state.view];
 for(const task of tasks)breaths.observe(task.id,task.notification?.token,task.notification?.unread&&state.own.get(task.id)!==task.notification?.token);
 $('views').replaceChildren();$('extra-views').replaceChildren();
 const scopeButton=key=>{
  const button=node('button',{class:'nav-button',type:'button','data-view':key,'aria-current':key===state.view?'page':'false'},views[key][0]);
  button.addEventListener('click',()=>{state.view=key;$('status-filter').value='';const filters=document.querySelector('.filters');if(filters)filters.open=false;destination('tasks');render();});return button;
 };
 for(const key of ['active','today','all'])$('views').append(scopeButton(key));
 for(const key of ['week','trash']){if(key===state.view)$('views').append(scopeButton(key));$('extra-views').append(scopeButton(key));}
 const projects=[...new Set(tasks.filter(t=>!t.deleted).map(t=>t.project||'Unassigned'))].sort((a,b)=>a.localeCompare(b));
 $('projects').replaceChildren();$('project-options').replaceChildren();$('project-filter').replaceChildren(node('option',{value:''},'All projects'));
 for (const project of projects) {
  const button=node('button',{class:'nav-button'+(state.project===project?' selected':''),type:'button','aria-pressed':String(state.project===project)});
  paletteStyle(button,tasks.find(t=>(t.project||'Unassigned')===project)||{});
  button.append(node('span',{class:'project-dot','aria-hidden':'true'}),node('span',{class:'project-name'},project),node('span',{class:'nav-count'},String(tasks.filter(t=>active(t)&&(t.project||'Unassigned')===project).length)));
  button.addEventListener('click',()=>{state.project=state.project===project?'':project;destination('tasks');render();});
  $('projects').append(button);
  $('project-options').append(node('option',{value:project}));
  $('project-filter').append(node('option',{value:project},project));
 }
 $('project-filter').value=state.project;
 message('breadcrumb-view',discussion.destination==='tasks'?view[0]:'Discussions');
 $('view-title').replaceChildren(document.createTextNode(state.project||'Tasks'),node('span',{class:'count sr-only',id:'view-count'},String(visible.length)));
 message('view-description',view[2]);
 message('workspace-date',new Intl.DateTimeFormat(undefined,{weekday:'long',day:'numeric',month:'long',timeZone:'Europe/Rome'}).format(new Date(state.data.today+'T12:00:00+01:00')).toUpperCase());
 const unread=visible.filter(t=>t.notification?.unread).length;
 const context=[$('search').value?'Search: '+$('search').value:'',$('status-filter').value?statuses[$('status-filter').value]:'','Sorted by '+($('sort').selectedOptions?.[0]?.textContent||$('sort').value)].filter(Boolean).join(' · ');message('filter-summary',context);show('filter-summary',!!context);
 $('task-list').replaceChildren();
 if (!visible.length) {
  const empty=node('div',{class:'empty-state'});
  empty.append(node('h2',{},state.view==='trash'?'Nothing in Trash.':'A clear view.'),node('p',{},$('search').value||state.project||$('status-filter').value?'No tasks match these filters. Try a broader view.':state.view==='active'?'No active tasks right now. Add a task when something needs your attention.':'No tasks to show in this view.'));
  const button=node('button',{type:'button'},'Reset filters');button.addEventListener('click',()=>{state.view='active';state.project='';$('search').value='';$('status-filter').value='';render();});empty.append(button);$('task-list').append(empty);
 }
 const grouped=$('sort').value==='project'||['priority','date','title'].includes($('sort').value);
 const groups=grouped?groupTasks(visible):[{name:'',tasks:visible}];
 for (const group of groups) {
  const section=node('section',{class:'task-group'});
  const heading=node('h2',{class:'group-heading'});heading.append(document.createTextNode(group.name),node('span',{class:'group-count'},String(group.tasks.length)),node('span',{class:'group-line','aria-hidden':'true'}));if(group.name&&!state.project)section.append(heading);
  for (const task of group.tasks) {
   const accent=breaths.age(task.id)<6400?breaths.age(task.id):null;
   const row=node('article',{class:'task-row'+(task.notification?.unread?' unread':'')+(task.status==='completed'?' is-completed':'')+(accent!==null?' update-emphasis':''),'data-task-id':task.id});
   paletteStyle(row,task);
   const check=node('button',{class:'task-check',type:'button','aria-label':task.status==='completed'?'Reopen '+task.title:'Complete '+task.title,title:task.notification?.unread?'Review update before changing status':'Change completion status'});
   check.append(node('span',{class:'check-circle','aria-hidden':'true'},task.status==='completed'?'✓':''));
   check.disabled=task.deleted||!state.online;check.addEventListener('click',()=>quickComplete(task,check));
   const open=node('button',{class:'task-open',type:'button'});
   open.append(node('span',{class:'task-title'},task.title));
   const meta=node('span',{class:'task-meta'});
   if(task.notification?.unread) meta.append(node('span',{class:'unread-badge'},'New update'));
   if(!grouped&&task.project)meta.append(node('span',{class:'row-project'},task.project));
   if (['waiting','in_progress','completed','cancelled'].includes(task.status)) meta.append(node('span',{},statuses[task.status]));
   open.append(meta);open.addEventListener('click',()=>openTask(task.id));
   const tail=node('div',{class:'task-tail'}),overdue=task.due_date && task.due_date<localToday() && active(task);
   if(task.due_date)meta.append(node('span',{class:'due'+(overdue?' overdue':'')},(overdue?'Overdue · ':task.due_date===localToday()?'Today · ':'')+dateLabel(task.due_date)));
   const more=node('button',{type:'button',class:'task-more icon-button priority-'+task.priority,'aria-label':task.priority+' priority. Open details and actions for '+task.title});
   more.append(uiIcon('flag'),node('span',{class:'priority-label'},task.priority==='high'?'High':task.priority==='medium'?'Medium':'Low'));more.addEventListener('click',()=>openTask(task.id));tail.append(more);
   row.append(check,open,tail);section.append(row);
  }
  $('task-list').append(section);
 }
 if(anchorId){const next=[...document.querySelectorAll('.task-row')].find(row=>row.dataset.taskId===anchorId);if(next)window.scrollBy?.(0,next.getBoundingClientRect().top-anchorTop);}
 watchUpdateAccents();syncTaskPresentation();
 // Only display synchronization facts actually supplied by the backend.
 const sync=state.data.sync;
 message('sync-state',sync?.message && typeof sync.message==='string'?sync.message:'');
 if(focused&&!focused.isConnected){
  const target=focusTask?[...$('task-list').querySelectorAll('[data-task-id]')].find(e=>e.dataset.taskId===focusTask)?.querySelector('.'+focusClass):focusView?[...$('views').children].find(e=>e.dataset.view===focusView):focusProject?[...$('projects').children].find(e=>e.textContent===focusProject):null;
  target?.focus({preventScroll:true});
 }
}

function updateLocal(task) {
 if (!state.data) return;
 const index=state.data.tasks.findIndex(t=>t.id===task.id);
 if (index<0) state.data.tasks.push(task);else state.data.tasks[index]=task;
 render();
}
async function openTask(id) {
 const epoch=state.epoch;
 if (!currentSession(epoch) || taskOpening || !allowClose()) return;
 taskOpening=true;
 try {
  const task=state.online?await request('/api/tasks/'+encodeURIComponent(id)):state.data.tasks.find(t=>t.id===id);
  if(!currentSession(epoch))return;
  if (!task) throw new Error('Task unavailable.');
  if (task.notification?.unread && !task.deleted) showUpdates(task);
  else showTask(task);
 } catch(error) {if(currentSession(epoch))toast(errorText(error));} finally {if(currentSession(epoch))taskOpening=false;}
}
function markdownNodes(text) {
 const render=part=>{if(typeof part==='string')return document.createTextNode(part);const element=node(part.tag);if(part.tag==='ol'&&Number.isSafeInteger(part.start))element.setAttribute('start',String(part.start));element.append(...part.children.map(render));return element;};
 return safeMarkdown(text).map(render);
}
function showUpdates(task) {
 state.editorGeneration++;
 $('task-dialog').close();updateLocal(task);state.gate=updateGate(task);
 message('updates-heading',state.gate.headline||'Task updated');message('updates-title',state.gate.title);message('updates-error');
 $('updates-summary').replaceChildren();
 if(state.gate.sections!==null) {
  for(const section of state.gate.sections){const group=node('section',{});group.append(node('h3',{},section.heading),...markdownNodes(section.text));$('updates-summary').append(group);}
 }else $('updates-summary').append(...state.gate.summary.map(text=>node('p',{},text)));
 if(!$('updates-summary').children.length)$('updates-summary').append(node('p',{},'This task has new source context.'));
 $('updates-ack').disabled=!state.online;show('updates-refresh',false);
 if (!$('updates-dialog').open) $('updates-dialog').showModal();
}
async function openReviewedTask(chat=false) {
 const epoch=state.epoch,gate=state.gate&&{...state.gate};if(!currentSession(epoch)||!gate)return;
 try{
  const task=state.online?await request('/api/tasks/'+encodeURIComponent(gate.id)):state.data?.tasks.find(item=>item.id===gate.id);
  if(!currentSession(epoch)||state.gate?.id!==gate.id||state.gate?.token!==gate.token)return;
  if(!task)throw new Error('Task unavailable.');
  $('updates-dialog').close();state.gate=null;showTask(task);if(chat)switchPanel('hermes');
 }catch(error){if(currentSession(epoch)&&state.gate?.id===gate.id&&state.gate?.token===gate.token)message('updates-error',errorText(error));}
}
async function acknowledge() {
 const epoch=state.epoch;
 if (!currentSession(epoch) || !state.gate || !state.online) return;
 const gate={...state.gate};$('updates-ack').disabled=true;message('updates-error');
 try {
  const task=await request('/api/tasks/'+encodeURIComponent(gate.id)+'/updates/read',{method:'POST',body:{token:gate.token}});
  if (!currentSession(epoch)||state.gate?.id!==gate.id||state.gate?.token!==gate.token) return;
  const known=state.data?.tasks.find(item=>item.id===gate.id);
  if(known?.notification?.token&&known.notification.token!==gate.token){
   message('updates-error','A newer update arrived. The current task and unread marker are retained; review the newer snapshot.');show('updates-refresh');$('updates-ack').disabled=true;return;
  }
  if (!acknowledged(gate,task)) throw new Error('A newer update arrived. Review the current summary before opening the editor.');
  $('updates-dialog').close();state.gate=null;updateLocal(task);showTask(task);
 } catch(error) {
  if (!currentSession(epoch)||state.gate?.id!==gate.id||state.gate?.token!==gate.token) return;
  message('updates-error',error.status===409?'A newer update arrived. Review it before acknowledging; nothing newer was marked read.':errorText(error)+' Nothing was marked read in this page. Review the current summary to retry.');
  show('updates-refresh');
 }
}
function fields() {return Object.fromEntries(new FormData(form).entries());}
function allowClose() {
 if (!state.dirty || !state.authenticated) return true;
 return window.confirm('Discard your unsaved edits? They have not been saved.');
}
function syncTaskPresentation() {
 const dialog=$('task-dialog'),opened=dialog.open;document.documentElement.dataset.taskOpen=String(opened);
 if(document.body)document.body.dataset.taskOpen=String(opened);
 for(const row of document.querySelectorAll('.task-row')){const selected=opened&&row.dataset.taskId===state.selected?.id;row.dataset.selected=String(selected);row.querySelector('.task-open')?.setAttribute('aria-current',selected?'true':'false');}
}
function presentTaskDialog() {
 const dialog=$('task-dialog');
 if(dialog.open&&document.querySelector?.('dialog:modal:not(#task-dialog)')){syncTaskPresentation();return;}
 const wide=Boolean(window.matchMedia?.('(min-width: 1100px)').matches),presentation=wide?'inspector':'sheet';
 if(dialog.open&&dialog.dataset.presentation===presentation){syncTaskPresentation();return;}
 const focused=document.activeElement,top=dialog.scrollTop;if(dialog.open)dialog.close();dialog.dataset.presentation=presentation;
 if(wide)dialog.show();else dialog.showModal();dialog.scrollTop=top;
 if(focused?.isConnected&&dialog.contains(focused))focused.focus({preventScroll:true});syncTaskPresentation();
}
function closeTask() {
 if (!allowClose()) return;
 state.editorGeneration++;
 state.dirty=false;$('task-dialog').close();state.selected=null;syncTaskPresentation();state.recognition?.abort();state.recognition=null;
}
function showTask(task=null) {
 state.editorGeneration++;
 state.selected=task;state.dirty=false;form.reset();
 for (const key of ['title','description','notes','project','due_date','status','priority']) {
  form.elements.namedItem(key).value=task?.[key]??(key==='status'?'todo':key==='priority'?'medium':'');
  form.elements.namedItem(key).disabled=!!task?.deleted;
 }
 state.snapshot=JSON.stringify(fields());
 paletteStyle($('task-dialog'),task||{});
 message('detail-heading',task?.title||'New task');message('detail-context',task?.deleted?'IN TRASH':task?.project||'TASK DETAILS');
 message('detail-error');show('conflict',false);
 show('delete-task',!!task&&!task.deleted);show('restore-task',!!task?.deleted);
 show('save-task',!task?.deleted);$('save-task').disabled=!state.online;
 message('save-task',task?'Save changes':'Create task');
 $('hermes-tab').disabled=!task || !!task.deleted;
 $('source-links').replaceChildren();
 for (const link of task?.source_links||[]) {
  const url=safeSource(link.url);
  if (url) $('source-links').append(node('a',{class:'button',href:url,target:'_blank',rel:'noopener noreferrer'},link.label+' ↗'));
 }
 if (!$('source-links').children.length) $('source-links').append(node('p',{class:'hint'},task?.source==='manual'||!task?'Manually created tasks have no linked source.':'No reliable source link is available. Source identifiers are retained on the server.'));
 message('source-accounts',(task?.source_accounts||[]).join(' · '));
 show('detail-reply',!!task&&!task.deleted&&task.source!=='manual');
 switchPanel('details');
 presentTaskDialog();
 $('task-dialog').scrollTop=0;
 if (!task) $('title').focus();else $('details-tab').focus();
}
function switchPanel(panel) {
 const details=panel==='details';show('details-panel',details);show('hermes-panel',!details);
 $('details-tab').setAttribute('aria-pressed',String(details));$('hermes-tab').setAttribute('aria-pressed',String(!details));
 if (!details) loadTaskThreads();
}
async function mutate(path,method,body) {
 const epoch=state.epoch;requireSession(epoch);
 if (!state.online) throw new Error('Offline. Reconnect before changing tasks.');
 state.mutating=true;
 try {
  // The entire workflow belongs to one session, including both event polls.
  await pollEvents();requireSession(epoch);
  const task=await request(path,{method,body});requireSession(epoch);
  state.own.set(task.id,task.notification?.token);
  updateLocal(task);
  // A live-session transport failure is recoverable; session loss is never success.
  try {await pollEvents();} catch(error) {
   requireSession(epoch);
   if(error.status===401)throw error;
   toast('Task saved. Alert checks will resume when the connection recovers.');
  }
  requireSession(epoch);
  return task;
 } finally {if(currentSession(epoch))state.mutating=false;}
}
async function saveTask(event) {
 event.preventDefault();
 // Cache writes belong to the session; form continuations belong to this editor.
 const epoch=state.epoch,generation=state.editorGeneration;
 if (!currentSession(epoch) || !state.online || $('save-task').disabled) return;
 $('save-task').disabled=true;message('detail-error');
 const selected=state.selected,payload=editPayload(selected,fields());
 try {
  const task=await mutate(selected?'/api/tasks/'+encodeURIComponent(selected.id):'/api/tasks',selected?'PATCH':'POST',payload);
  if(!currentSession(epoch)||generation!==state.editorGeneration)return;
  state.dirty=false;showTask(task);toast(selected?'Changes saved':'Task created');
 } catch(error) {
  if(!currentSession(epoch)||generation!==state.editorGeneration)return;
  message('detail-error',errorText(error));
  if (error.status===409 && selected) {show('conflict');$('save-task').disabled=true;return;}
 } finally {if(currentSession(epoch)&&generation===state.editorGeneration&&$('conflict').hidden) $('save-task').disabled=!state.online;}
}
async function quickComplete(task,button) {
 const epoch=state.epoch;if(!currentSession(epoch))return;
 if (task.notification?.unread) {await openTask(task.id);return;}
 button.disabled=true;
 try {await mutate('/api/tasks/'+encodeURIComponent(task.id),'PATCH',{revision:task.revision,status:task.status==='completed'?'todo':'completed'});if(currentSession(epoch))toast(task.status==='completed'?'Task reopened':'Task completed');}
 catch(error) {if(!currentSession(epoch))return;toast(errorText(error));if(error.status===409) await refreshTasks();}
 finally {if(currentSession(epoch))button.disabled=false;}
}
async function deleteTask() {
 const epoch=state.epoch,task=state.selected;if(!currentSession(epoch)||!task)return;
 $('delete-confirm').disabled=true;
 try {
  await mutate('/api/tasks/'+encodeURIComponent(task.id),'DELETE',{revision:task.revision});
  if(!currentSession(epoch))return;
  $('confirm-dialog').close();state.dirty=false;$('task-dialog').close();state.selected=null;toast('Moved to Trash. You can restore it any time.');
 } catch(error) {
  if(!currentSession(epoch))return;
  $('confirm-dialog').close();message('detail-error',errorText(error));if(error.status===409){show('conflict');$('save-task').disabled=true;}
 } finally {if(currentSession(epoch))$('delete-confirm').disabled=false;}
}
async function restoreTask() {
 const epoch=state.epoch,task=state.selected;if(!currentSession(epoch)||!task)return;
 $('restore-task').disabled=true;
 try {const restored=await mutate('/api/tasks/'+encodeURIComponent(task.id)+'/restore','POST',{revision:task.revision});if(!currentSession(epoch))return;state.dirty=false;if(restored.notification?.unread)showUpdates(restored);else showTask(restored);toast('Task restored');}
 catch(error){if(!currentSession(epoch))return;message('detail-error',errorText(error));if(error.status===409)show('conflict');}
 finally{if(currentSession(epoch))$('restore-task').disabled=false;}
}

function conversation() {return discussion.active?chats.entry(discussion.active):null;}
function chatBlocked() {const e=conversation();return !e||!e.loaded||e.loading||e.sending||!!e.admission||e.conversation?.archived||pendingRequest(e.conversation?.latest_request);}
function animateSurface(element,frames,duration){if(document.documentElement.dataset.motion==='reduce'||window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)return;element?.getAnimations?.().forEach(animation=>animation.cancel());element?.animate?.(frames,{duration,easing:'cubic-bezier(.2,.8,.2,1)'});}
function destination(value) {
 if(value!==discussion.destination&&$('task-dialog').open){if(!allowClose())return;state.editorGeneration++;state.dirty=false;$('task-dialog').close();state.selected=null;syncTaskPresentation();}
 const changed=discussion.destination!==value;discussion.destination=value;document.documentElement.dataset.destination=value;
 if(changed)animateSurface($(value+'-workspace'),[{opacity:.6,transform:'translateX(6px)'},{opacity:1,transform:'translateX(0)'}],210);show('tasks-workspace',value==='tasks');show('discussions-workspace',value==='discussions');
 $('nav-tasks').setAttribute('aria-current',value==='tasks'?'page':'false');$('nav-discussions').setAttribute('aria-current',value==='discussions'?'page':'false');
 message('breadcrumb-view',value==='tasks'?views[state.view][0]:'Discussions');
 if(value==='discussions') {populateDiscussionTasks();loadDiscussions();}
}
function populateDiscussionTasks() {
 const select=$('discussion-task'),value=select.value;select.replaceChildren(node('option',{value:''},'Choose a task'));
 for(const task of state.data?.tasks||[]) if(!task.deleted)select.append(node('option',{value:task.id},task.title));
 select.value=value;
}
async function loadDiscussions(more=false) {
 const generation=++discussion.generation,epoch=state.epoch;
 discussion.loading=true;message('discussion-error');message('discussion-list-status','Loading discussions…');$('discussion-more').disabled=true;
 const scope=$('discussion-scope').value,task=$('discussion-task').value;show('discussion-task-label',scope==='task');
 if(scope==='task'&&!task){$('discussion-tools').open=true;discussion.loading=false;discussion.rows=[];discussion.cursor=null;renderDiscussions();message('discussion-list-status','Choose a task to see its saved discussions.');return;}
 try {
  const query=new URLSearchParams({scope,archived:String($('discussion-archived').checked),limit:'30'});if(scope==='task')query.set('task_id',task);if(more&&discussion.cursor)query.set('cursor',discussion.cursor);
  const data=await request('/api/conversations?'+query);
  if(epoch!==state.epoch||generation!==discussion.generation)return;
  discussion.rows=more?[...new Map([...discussion.rows,...data.conversations].map(c=>[c.id,c])).values()]:data.conversations;
  discussion.cursor=data.next_cursor;for(const c of data.conversations)chats.metadata(c);renderDiscussions();setConnection(true);
 }catch(error){if(epoch===state.epoch&&generation===discussion.generation){message('discussion-error',errorText(error));message('discussion-list-status',discussion.rows.length?'Showing last loaded discussions. Refresh to reconnect.':'Could not load discussions. Use Refresh to try again.');}}
 finally{if(epoch===state.epoch&&generation===discussion.generation){discussion.loading=false;$('discussion-more').disabled=false;}}
}
function renderDiscussions() {
 const q=$('discussion-search').value.toLocaleLowerCase().trim(),rows=discussion.rows.filter(c=>[c.title,c.last_preview,c.task_title].some(v=>String(v||'').toLocaleLowerCase().includes(q)));
 $('discussion-list').replaceChildren();
 for(const c of rows) {
  const e=chats.entries.get(c.id),metadata=e?.conversation||c,row=node('button',{class:'discussion-row',type:'button','data-conversation-id':c.id});
  row.append(node('strong',{class:'discussion-title'},metadata.title),node('span',{class:'discussion-context'},c.task_id?'Task · '+(c.task_title||'Saved task'):'Free discussion'),node('span',{class:'discussion-preview'},metadata.last_preview||'No messages yet'));
  if(metadata.updated_at){const stamp=new Date(metadata.updated_at);if(!Number.isNaN(stamp.getTime()))row.append(node('time',{class:'discussion-time',datetime:metadata.updated_at},new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit'}).format(stamp)));}
  const status=metadata.latest_request?.status;if(status&&status!=='succeeded')row.append(node('span',{class:'request-badge'},status));
  row.addEventListener('click',()=>openConversation(c.id));$('discussion-list').append(row);
 }
 if(!rows.length)$('discussion-list').append(node('div',{class:'empty-state'},q?'No loaded discussions match this search.':'No discussions here. Create a free discussion, or open a task to start one.'));
 show('discussion-more',!!discussion.cursor);message('discussion-list-status',`${discussion.rows.length} saved discussion${discussion.rows.length===1?'':'s'}${discussion.cursor?' · More available':''}`);
}
async function loadTaskThreads(more=false) {
 const id=state.selected?.id;if(!id)return;
 if(discussion.taskId!==id){discussion.taskId=id;discussion.taskRows=[];discussion.taskCursor=null;$('task-thread').replaceChildren(node('option',{value:''},'Loading discussions…'));show('task-thread-more',false);more=false;}
 const generation=++discussion.taskGeneration,epoch=state.epoch,select=$('task-thread'),previous=select.value;
 message('task-thread-status','Loading saved discussions…');message('task-thread-error');$('task-thread-new').disabled=!state.online;
 try {
  const q=new URLSearchParams({scope:'task',task_id:id,archived:'false',limit:'30'});if(more&&discussion.taskCursor)q.set('cursor',discussion.taskCursor);
  const data=await request('/api/conversations?'+q);
  if(epoch!==state.epoch||state.selected?.id!==id||generation!==discussion.taskGeneration)return;
  discussion.taskRows=more?[...new Map([...discussion.taskRows,...data.conversations].map(c=>[c.id,c])).values()]:data.conversations;discussion.taskCursor=data.next_cursor;
  select.replaceChildren(node('option',{value:''},'Select a discussion'));for(const c of discussion.taskRows){chats.metadata(c);select.append(node('option',{value:c.id},c.title));}
  select.value=discussion.taskRows.some(c=>c.id===previous)?previous:discussion.taskRows[0]?.id||'';
  show('task-thread-more',!!discussion.taskCursor);message('task-thread-status',discussion.taskRows.length?'Saved privately on the server. Archived threads are available in Discussions.':'No saved discussions for this task. Start a new conversation.');
 }catch(error){if(epoch===state.epoch&&state.selected?.id===id)message('task-thread-error',errorText(error));}
}
async function createConversation(taskId=null) {
 if(!state.online)return toast('Offline. Reconnect before creating a discussion.');
 const button=taskId?$('task-thread-new'):$('new-free'),epoch=state.epoch;button.disabled=true;
 try {const c=await request('/api/conversations',{method:'POST',body:{task_id:taskId}});if(epoch!==state.epoch)return;chats.metadata(c);await openConversation(c.id);if(taskId===state.selected?.id)loadTaskThreads();else if(discussion.destination==='discussions')loadDiscussions();}
 catch(error){if(epoch===state.epoch)toast(errorText(error)+' Review saved discussions before creating another.');}
 finally{button.disabled=!state.online;}
}
async function openConversation(id) {
 state.recognition?.abort();discussion.active=id;$('chat-dialog').dataset.conversationId=id;clearTimeout(chatTimer);
 const e=chats.entry(id);$('chat-log').dataset.signature='';renderChat(true);if(!$('chat-dialog').open)$('chat-dialog').showModal();
 try{await chats.load(id);if(discussion.active===id&&state.authenticated){setConnection(true);renderChat(true);scheduleChatPoll();}}
 catch(error){if(discussion.active===id&&state.authenticated){e.error=errorText(error);renderChat();}}
}
function syncVisualViewport() {
 const style=document.documentElement.style;if(!style)return;
 const id=discussion.active,e=conversation(),follow=e?.nearBottom,epoch=state.epoch;
 style.setProperty('--visual-height',window.visualViewport?.height?window.visualViewport.height+'px':'100dvh');
 style.setProperty('--visual-top',(window.visualViewport?.offsetTop||0)+'px');
 const settle=()=>{if(!currentSession(epoch)||id!==discussion.active||!$('chat-dialog').open)return;const log=$('chat-log');if(follow)log.scrollTop=Math.max(0,log.scrollHeight-log.clientHeight);syncChatLatest();};
 if(typeof requestAnimationFrame==='function')requestAnimationFrame(settle);else settle();
}
function resizeComposer() {
 const input=$('chat-message');if(!input.style)return;
 const log=$('chat-log'),follow=log.scrollHeight-log.scrollTop-log.clientHeight<70;
 input.style.height='auto';input.style.height=Math.max(48,Math.min(144,input.scrollHeight))+'px';
 if(follow)log.scrollTop=log.scrollHeight;
}
function syncChatLatest() {const log=$('chat-log'),e=conversation();if(e)e.nearBottom=log.scrollHeight-log.scrollTop-log.clientHeight<=70;show('chat-latest',!!e?.messages.length&&!e.nearBottom);}
function renderChat(restoreDraft=false) {
 const e=conversation();if(!e)return;
 const c=e.conversation,r=c?.latest_request,log=$('chat-log');
 if(discussion.destination==='discussions')renderDiscussions();
 for(const option of $('task-thread').children){const metadata=chats.entries.get(option.value)?.conversation;if(metadata)option.textContent=metadata.title;}
 paletteStyle($('chat-dialog'),state.data?.tasks.find(task=>task.id===c?.task_id)||{});
 message('chat-title',c?.title||'Loading discussion…');message('chat-context',c?.task_id?'Task · '+(c.task_title||'Saved task'):'FREE DISCUSSION');
 if(restoreDraft||$('chat-message').value!==e.draft){$('chat-message').value=e.draft;resizeComposer();}$('chat-mode').value=e.mode;
 const signature=discussion.active+':'+e.messages.map(m=>m.id+':'+JSON.stringify(m.citation_metadata??null)).join(',');
 if(log.dataset.signature!==signature) {
  const bottom=log.scrollHeight-log.scrollTop-log.clientHeight<70,top=log.scrollTop,height=log.scrollHeight;
  const sameThread=log.dataset.threadId===discussion.active;
  const previous=sameThread?[...log.children].filter(item=>item.dataset?.messageId):[];
  const first=previous[0]?.dataset.messageId;
  const prepended=!!first&&e.messages.findIndex(turn=>String(turn.id)===first)>0;
  const existing=new Map(previous.map(item=>[item.dataset.messageId,item]));
  if(!sameThread||!previous.length)log.replaceChildren();
  const wanted=new Set(e.messages.map(turn=>String(turn.id)));
  for(const item of previous)if(!wanted.has(item.dataset.messageId))log.removeChild(item);
  for(const [index,turn] of e.messages.entries()) {
   let item=existing.get(String(turn.id));
   const turnSignature=JSON.stringify([turn.content,turn.citation_metadata??null]);
   if(item&&item.dataset.contentSignature!==turnSignature){log.removeChild(item);item=null;}
   if(!item){
    item=node('article',{class:'chat-turn '+turn.role+(sameThread&&previous.length&&!prepended&&bottom&&index>=e.messages.length-2?' is-new':''),'data-message-id':turn.id});
    item.dataset.contentSignature=turnSignature;
    const epoch=state.epoch,citations=typeof renderCitations==='function'?renderCitations(turn,{request,alive:()=>currentSession(epoch)}):null;
    const content=node('div',{class:'message'});content.append(...markdownNodes(citations?.text??turn.content));
    item.append(node('span',{class:'speaker'},turn.role==='user'?'You':'Hermès'),content);
    if(citations?.element.children.length)item.append(citations.element);
    if(turn.created_at){const stamp=new Date(turn.created_at);if(!Number.isNaN(stamp.getTime()))item.append(node('time',{class:'message-time',datetime:turn.created_at},new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit'}).format(stamp)));}
    const copy=node('button',{type:'button',class:'copy-message','aria-label':'Copy '+(turn.role==='user'?'your message':'Hermès response')},'Copy');copy.addEventListener('click',()=>copyText(turn.content));item.append(copy);
   }
   if(log.children[index]!==item)log.insertBefore(item,log.children[index]||null);
  }
  if(!e.messages.length)log.append(node('p',{class:'empty-state'},e.loaded?'Start with a question, analysis or draft.':'Loading saved history…'));
  log.dataset.signature=signature;log.dataset.threadId=discussion.active;
  if(prepended)log.scrollTop=top+(log.scrollHeight-height);else if(bottom||!sameThread||!previous.length)log.scrollTop=log.scrollHeight;else log.scrollTop=top;
 }
 // Admission is a separate, non-durable bubble. Never invent a server message ID.
 let admissionTurn=[...log.children].find(item=>item.dataset?.admissionId);
 if(admissionTurn&&(!e.admission||admissionTurn.dataset.admissionId!==e.admission.payload.request_id)){log.removeChild(admissionTurn);admissionTurn=null;}
 if(e.admission){
  if(!admissionTurn){admissionTurn=node('article',{class:'chat-turn user is-new','data-admission-id':e.admission.payload.request_id});admissionTurn.append(node('div',{class:'message'},e.admission.payload.content),node('small',{class:'message-time'}));log.append(admissionTurn);}
  admissionTurn.children[1].textContent=e.sending?'Sending…':'Delivery uncertain · not confirmed';
 }
 show('chat-older',e.has_more);$('chat-older').disabled=e.loading||!state.online;show('chat-compacted',e.context_compacted);
 message('chat-archive',c?.archived?'Reopen':'Archive');$('chat-archive').disabled=!c||!state.online||e.sending; $('chat-rename').disabled=!c||!state.online;
 const status=e.sending?'Sending…':e.admission?'Delivery uncertain. Nothing will be sent again automatically. Check the request, or explicitly retry the same request safely.':e.loading?'Refreshing saved history…':!state.online?'Offline · last loaded history is readable. Reconnect, then Refresh.':pendingRequest(r)?(r.status==='queued'?'Queued':'Hermès is replying')+' · Saved on the server; you can leave this discussion.':r?.status==='interrupted'?'Interrupted by a server restart. Nothing was automatically rerun.':r?.status==='failed'?'The saved request failed. You can explicitly retry.':c?.archived?'Archived · history remains readable. Reopen to continue.':e.loaded?'':'Loading…';
 message('chat-status',status);
 $('chat-status').dataset.running=String(r?.status==='running'&&!e.sending&&!e.admission&&state.online&&!document.hidden&&$('chat-dialog').open);
 if($('chat-status').dataset.running==='true'){const wave=node('span',{class:'typing-wave','aria-hidden':'true'});for(let i=0;i<3;i++)wave.append(node('i'));$('chat-status').append(wave);}
 message('chat-error',e.error||r?.error||'');show('chat-request-state',!!(status||e.error||r?.error));
 show('chat-check',!!e.admission);show('chat-replay',!!e.admission);show('chat-retry',['failed','interrupted'].includes(r?.status)&&!e.admission);
 for(const id of ['chat-check','chat-replay','chat-retry'])$(id).disabled=!state.online||e.sending||e.loading||!!c?.archived;
 $('chat-send').disabled=!state.online||chatBlocked();$('chat-refresh').disabled=e.loading;
 if(e.followLatest){log.scrollTop=log.scrollHeight;e.followLatest=false;}syncChatLatest();syncVisualViewport();
}
function scheduleChatPoll() {
 clearTimeout(chatTimer);const id=discussion.active,e=conversation();if(!id||!$('chat-dialog').open||document.hidden||!state.online)return;
 chatTimer=setTimeout(async()=>{
  if(discussion.active!==id||!$('chat-dialog').open||document.hidden||!state.authenticated)return;
  try{if(pendingRequest(e?.conversation?.latest_request))await chats.poll(id);else await chats.load(id);if(discussion.active===id&&state.authenticated)renderChat();}
  catch(error){if(discussion.active===id&&state.authenticated){chats.entry(id).error=errorText(error);renderChat();}}
  scheduleChatPoll();
 },pendingRequest(e?.conversation?.latest_request)?1800:8000);
}
async function refreshConversation() {
 const id=discussion.active;if(!id)return;const epoch=state.epoch;
 try {await chats.load(id);if(epoch===state.epoch&&discussion.active===id){setConnection(true);renderChat();scheduleChatPoll();}}
 catch(error){if(epoch===state.epoch&&discussion.active===id){chats.entry(id).error=errorText(error);renderChat();}}
}
async function copyText(value) {
 try {await navigator.clipboard.writeText(value);toast('Copied');}
 catch {toast('Clipboard unavailable. Select the text and copy it manually.');}
}
async function sendChat(event,options={}) {
 event?.preventDefault();if(!state.online||!discussion.active)return;
 const id=discussion.active,epoch=state.epoch;
 try {const sending=chats.send(id,options);chats.entry(id).followLatest=true;renderChat();await sending;}
 catch(error){if(epoch===state.epoch){chats.entry(id).error=errorText(error)+' Nothing was automatically resent.';if(error.status===409)chats.entry(id).error+=' Refresh and review the saved discussion before sending again.';}}
 finally{if(epoch===state.epoch&&discussion.active===id){renderChat();scheduleChatPoll();}}
}
async function startVoice() {
 const Recognition=window.SpeechRecognition||window.webkitSpeechRecognition;
 if(!Recognition){message('chat-error','Speech recognition is not supported in this browser. Type your message, or use your keyboard’s dictation.');return;}
 if(state.recognition){state.recognition.stop();return;}
 if(!window.confirm('Enable voice transcription? Your browser may send audio to its speech service. The transcript stays editable and is never sent to Hermes automatically.'))return;
 const recognition=new Recognition();state.recognition=recognition;
 recognition.lang=navigator.language||'en-US';recognition.interimResults=false;recognition.continuous=false;
 const conversationId=discussion.active,epoch=state.epoch;
 recognition.onresult=event=>{
  if(conversationId!==discussion.active||epoch!==state.epoch||!state.authenticated||!$('chat-dialog').open)return;
  const text=Array.from(event.results).map(result=>result[0].transcript).join(' ');
  $('chat-message').value=[$('chat-message').value,text].filter(Boolean).join(' ');
  if(conversationId)chats.setDraft(conversationId,$('chat-message').value);
  $('chat-message').focus();
 };
 recognition.onerror=event=>{if(conversationId===discussion.active&&epoch===state.epoch&&$('chat-dialog').open)message('chat-error',event.error==='not-allowed'?'Microphone permission was not granted. You can continue by typing.':'Voice transcription stopped. You can continue by typing.');};
 recognition.onend=()=>{if(state.recognition===recognition){state.recognition=null;message('voice','Microphone');}};
 try {recognition.start();message('voice','Stop listening');}catch{state.recognition=null;message('chat-error','Microphone unavailable. Continue by typing.');}
}

async function pollEvents() {
 if(!state.authenticated||state.restricted||state.polling)return;
 state.polling=true;
 const baseline=state.cursor===null,epoch=state.epoch;
 let cursor=state.cursor||0;
 try {
  let hasMore;
  do {
   const feed=await request('/api/events?after='+cursor+'&limit=100');
   if(!currentSession(epoch))return;
   hasMore=feed.has_more;
   const latest=new Map();for(const event of feed.events)latest.set(event.task_id,event);
   if(!baseline)for(const event of latest.values()) {
    let task;
    try {task=await request('/api/tasks/'+encodeURIComponent(event.task_id));}catch(error){if(error.status===404)continue;throw error;}
    if(epoch!==state.epoch)return;
    if(shouldNotify(event,task,{baseline,tracking:feed.notification_tracking,ownToken:state.own.get(task.id)}) && readLocal('notifications')==='on' && 'Notification' in window && Notification.permission==='granted') {
     const notice=new Notification('My Tasks · '+(event.kind==='created'?'New task':'Task updated'),{body:task.title,tag:'mytasks-'+task.id,icon:'/assets/icon.svg'});
     state.notices.add(notice);notice.onclose=()=>state.notices.delete(notice);
     notice.onclick=()=>{window.focus();notice.close();if(state.authenticated)openTask(task.id);};
    }
   }
   // Cursor advances only after all current-page notification work succeeds.
   cursor=feed.cursor;state.cursor=cursor;if(state.cursorKey)writeLocal(state.cursorKey,String(cursor));
  } while(hasMore && epoch===state.epoch);
 } finally {if(epoch===state.epoch)state.polling=false;}
}
async function backgroundRefresh() {
 if(!state.authenticated||state.restricted||state.mutating||state.polling)return;
 const epoch=state.epoch;
 try{await pollEvents();if(!currentSession(epoch))return;await refreshTasks();}
 catch(error){if(currentSession(epoch)){message('global-error',errorText(error));show('global-error');}}
}
function notificationStatus() {
 if(!('Notification' in window)){message('notification-status','This browser does not support desktop notifications.');$('notifications').disabled=true;return;}
 const enabled=readLocal('notifications')==='on';
 message('notifications',enabled?'Disable browser notifications':'Enable browser notifications');
 message('notification-status',`Browser permission: ${Notification.permission}. ${enabled?'Alerts enabled while this workspace is open.':'Alerts are off for this workspace.'} Permission can be revoked in browser site settings.`);
}
async function toggleNotifications() {
 if(!('Notification' in window))return;
 try {
  if(readLocal('notifications')==='on'){removeLocal('notifications');for(const notice of state.notices)notice.close();state.notices.clear();}
  else {
   const permission=await Notification.requestPermission();
   if(permission==='granted') {
    // Enabling notifications never drains historical activity into alerts.
    removeLocal(state.cursorKey);state.cursor=null;await pollEvents();writeLocal('notifications','on');
   }
  }
  notificationStatus();
 } catch(error){message('notification-status',errorText(error));}
}
async function appInfo(check=false) {
 const info=await request('/app-info');
 if(check && state.info && info.version!==state.info.version)message('version-status','A new web version is available. Save your work, then reload. The latest Android release is linked above.');
 else message('version-status',`Web version ${info.version}. Connected to ${info.default_server}. Android releases are published on GitHub; open Latest Android release to check the newest APK.`);
 state.info=info;
 // This is a fixed, server-supplied public release destination, never an arbitrary URL.
 if(info.android_release_url==='https://github.com/SigOiry/my-tasks-android/releases/latest')$('release-link').href=info.android_release_url;
}
async function openSettings() {
 const filters=document.querySelector?.('.filters');if(filters)filters.open=false;
 message('settings-error');notificationStatus();$('settings-dialog').showModal();
 try{await appInfo();}catch(error){message('version-status',errorText(error));}
}

function openPassword(required=false) {
 for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();
 show('app',false);show('login',false);show('password-pane');
 $('password-form').reset();message('password-error');
 message('password-context',required?'Ownership verified. Replace your initial password before accessing tasks or discussions.':'Choose a new password. All other sessions will be signed out.');
 message('password-cancel',required?'Sign out':'Cancel');$('current-password').focus();
}
$('login-form').addEventListener('submit',async event=>{
 event.preventDefault();const submit=event.currentTarget.querySelector('button');submit.disabled=true;message('login-error');
 const body=JSON.stringify({username:$('username').value,password:$('password').value});$('password').value='';
 try {const session=await request('/auth/login',{method:'POST',body});setConnection(true);await beginSession(session);}
 catch(error){message('login-error',errorText(error));}
 finally{submit.disabled=false;}
});
$('password-form').addEventListener('submit',async event=>{
 event.preventDefault();if($('password-save').disabled)return;
 const validation=passwordError($('new-password').value,$('confirm-password').value);
 if(validation){message('password-error',validation);return;}
 const body=JSON.stringify({current_password:$('current-password').value,new_password:$('new-password').value});
 for(const id of ['current-password','new-password','confirm-password'])$(id).value='';
 $('password-save').disabled=true;message('password-error');
 try{const session=await request('/auth/password',{method:'POST',body});setConnection(true);await beginSession(session);toast('Password changed. Other sessions have been signed out.');}
 catch(error){if(error.status===401){try{const current=await request('/auth/session');if(!current.authenticated){clearPrivate('Your session ended. Please sign in again.');return;}}catch{/* Keep the pane for explicit recovery when offline. */}}message('password-error',errorText(error)+(!error.status?' Do not retry automatically. Sign out and try signing in with the new password; if it fails, use the previous password.':''));}
 finally{$('password-save').disabled=false;}
});
$('change-password').addEventListener('click',()=>openPassword());
$('password-cancel').addEventListener('click',async()=>{
 if(!state.restricted){show('password-pane',false);$('password-form').reset();for(const id of ['current-password','new-password','confirm-password'])$(id).value='';show('app');return;}
 try{await request('/auth/logout',{method:'POST'});clearPrivate();}catch(error){message('password-error',errorText(error)+' Sign-out was not confirmed.');}
});
$('search-toggle').addEventListener('click',()=>{document.querySelector('.filters').open=true;$('search').focus();});
$('search').addEventListener('input',render);
window.addEventListener('scroll',()=>{interactionUntil=Date.now()+250;watchUpdateAccents();},{passive:true});
document.addEventListener('pointerdown',()=>{pointerBusy=true;},{passive:true});
for(const event of ['pointerup','pointercancel'])document.addEventListener(event,()=>{pointerBusy=false;interactionUntil=Date.now()+160;});
document.querySelector('.filters').addEventListener('toggle',()=>{if(!document.querySelector('.filters').open&&renderPending)render();});
$('status-filter').addEventListener('change',render);
$('sort').addEventListener('change',()=>{writeLocal('sort',$('sort').value);render();});
$('sort-direction').addEventListener('change',()=>{writeLocal('direction',$('sort-direction').value);render();});
$('color-mode').addEventListener('change',()=>{writeLocal('color',$('color-mode').value);document.documentElement.dataset.color=$('color-mode').value;render();});
$('project-filter').addEventListener('change',()=>{state.project=$('project-filter').value;render();});
$('clear-project').addEventListener('click',()=>{state.project='';render();});
$('refresh').addEventListener('click',()=>{if(discussion.destination==='discussions')loadDiscussions();else backgroundRefresh();});
for(const id of ['new-task','new-mobile'])$(id).addEventListener('click',()=>{if(allowClose())showTask();});
for(const element of document.querySelectorAll('.close-dialog'))element.addEventListener('click',closeTask);
$('task-dialog').addEventListener('cancel',event=>{event.preventDefault();closeTask();});
$('task-dialog').addEventListener('close',syncTaskPresentation);
for(const dialog of document.querySelectorAll('dialog:not(#task-dialog)'))dialog.addEventListener('close',()=>{if(currentSession(state.epoch)&&$('task-dialog').open)presentTaskDialog();});
window.addEventListener('resize',()=>{if($('task-dialog').open)presentTaskDialog();syncVisualViewport();});
window.visualViewport?.addEventListener('resize',syncVisualViewport);
window.visualViewport?.addEventListener('scroll',syncVisualViewport);
form.addEventListener('input',()=>{state.dirty=JSON.stringify(fields())!==state.snapshot;});form.addEventListener('change',()=>{state.dirty=JSON.stringify(fields())!==state.snapshot;});
form.addEventListener('submit',saveTask);
$('details-tab').addEventListener('click',()=>switchPanel('details'));$('hermes-tab').addEventListener('click',()=>switchPanel('hermes'));
$('updates-close').addEventListener('click',()=>{$('updates-dialog').close();state.gate=null;});
$('updates-dialog').addEventListener('cancel',()=>{state.gate=null;});
$('updates-dialog').addEventListener('close',()=>{if(!$('updates-dialog').open)state.gate=null;});
$('updates-ack').addEventListener('click',acknowledge);
$('updates-reply').addEventListener('click',()=>{if(state.gate)emailWorkspace?.open(state.gate.id,state.gate.token);});
$('detail-reply').addEventListener('click',()=>{if(state.selected)emailWorkspace?.open(state.selected.id);});
$('updates-open').addEventListener('click',()=>openReviewedTask());$('updates-discuss').addEventListener('click',()=>openReviewedTask(true));
$('updates-refresh').addEventListener('click',async()=>{const epoch=state.epoch;if(!currentSession(epoch)||!state.gate)return;try{const task=await request('/api/tasks/'+encodeURIComponent(state.gate.id));if(!currentSession(epoch))return;if(task.notification?.unread)showUpdates(task);else{$('updates-dialog').close();showTask(task);}}catch(error){if(currentSession(epoch))message('updates-error',errorText(error));}});
$('delete-task').addEventListener('click',()=>{$('confirm-dialog').showModal();});
$('delete-cancel').addEventListener('click',()=>$('confirm-dialog').close());$('delete-confirm').addEventListener('click',deleteTask);$('restore-task').addEventListener('click',restoreTask);
$('copy-edits').addEventListener('click',()=>{const values=fields();copyText(Object.entries(values).map(([key,value])=>key.replace('_',' ')+':\n'+value).join('\n\n'));});
$('reload-task').addEventListener('click',async()=>{const epoch=state.epoch;if(!currentSession(epoch)||!state.selected)return;if(!window.confirm('Load the latest version? Your current form edits will be replaced. Copy them first if you want to keep them.'))return;try{const task=await request('/api/tasks/'+encodeURIComponent(state.selected.id));if(!currentSession(epoch))return;state.dirty=false;if(task.notification?.unread)showUpdates(task);else showTask(task);}catch(error){if(currentSession(epoch))message('detail-error',errorText(error));}});
$('chat-form').addEventListener('submit',sendChat);$('voice').addEventListener('click',startVoice);
$('nav-tasks').addEventListener('click',()=>destination('tasks'));$('nav-discussions').addEventListener('click',()=>destination('discussions'));
$('new-free').addEventListener('click',()=>createConversation());
for(const id of ['discussion-scope','discussion-task','discussion-archived'])$(id).addEventListener('change',()=>loadDiscussions());
$('discussion-search').addEventListener('input',renderDiscussions);$('discussion-refresh').addEventListener('click',()=>loadDiscussions());$('discussion-more').addEventListener('click',()=>loadDiscussions(true));
$('task-thread-new').addEventListener('click',()=>createConversation(state.selected?.id));$('task-thread-more').addEventListener('click',()=>loadTaskThreads(true));
$('task-thread-open').addEventListener('click',()=>{if($('task-thread').value)openConversation($('task-thread').value);});
$('chat-close').addEventListener('click',()=>$('chat-dialog').close());
$('chat-dialog').addEventListener('close',()=>{$('chat-status').dataset.running='false';clearTimeout(chatTimer);state.recognition?.abort();if(state.authenticated&&discussion.destination==='discussions')loadDiscussions();});
$('chat-refresh').addEventListener('click',refreshConversation);
$('chat-older').addEventListener('click',async()=>{const id=discussion.active,epoch=state.epoch;try{const loading=chats.load(id,{older:true});renderChat();await loading;}catch(error){if(epoch===state.epoch)chats.entry(id).error=errorText(error);}finally{if(epoch===state.epoch&&discussion.active===id)renderChat();}});
$('chat-check').addEventListener('click',async()=>{const id=discussion.active,epoch=state.epoch;try{await chats.recover(id);if(epoch===state.epoch)setConnection(true);}catch(error){if(epoch===state.epoch)chats.entry(id).error=error.status===404?'No admission found yet. Refresh, or explicitly retry the same request.':errorText(error);}finally{if(epoch===state.epoch&&discussion.active===id){renderChat();scheduleChatPoll();}}});
$('chat-replay').addEventListener('click',()=>sendChat(null,{repeat:true}));
$('chat-retry').addEventListener('click',()=>{const e=conversation(),r=e?.conversation?.latest_request,last=e?.messages.findLast(m=>m.role==='user'&&m.request_id===r?.id);if(last&&window.confirm('Retry this failed message as a new request?'))sendChat(null,{content:last.content,mode:'question'});else if(!last)toast('Load the original user message before retrying.');});
$('chat-rename').addEventListener('click',()=>{const e=conversation();if(!e?.conversation)return;$('rename-dialog').dataset.conversationId=discussion.active;$('conversation-title').value=e.conversation.title;message('rename-error');$('rename-dialog').showModal();$('conversation-title').focus();});
$('rename-cancel').addEventListener('click',()=>$('rename-dialog').close());
$('rename-form').addEventListener('submit',async event=>{event.preventDefault();const id=$('rename-dialog').dataset.conversationId,epoch=state.epoch;$('rename-save').disabled=true;try{await chats.edit(id,{title:$('conversation-title').value});if(epoch!==state.epoch)return;await chats.load(id);$('rename-dialog').close();if(discussion.active===id)renderChat();}catch(error){if(epoch===state.epoch)message('rename-error',errorText(error)+(error.status===409?' Refresh the discussion before renaming.':''));}finally{$('rename-save').disabled=false;}});
$('chat-archive').addEventListener('click',async()=>{const id=discussion.active,epoch=state.epoch,e=conversation();if(!e?.conversation)return;$('chat-archive').disabled=true;try{await chats.edit(id,{archived:!e.conversation.archived});if(epoch!==state.epoch)return;await chats.load(id);if(state.selected?.id===e.conversation.task_id)loadTaskThreads();}catch(error){if(epoch===state.epoch)e.error=errorText(error)+(error.status===409?' Refresh before changing archive status.':'');}finally{if(epoch===state.epoch&&discussion.active===id)renderChat();}});
window.matchMedia?.('(prefers-reduced-motion: reduce)').addEventListener?.('change',watchUpdateAccents);
document.addEventListener('visibilitychange',()=>{watchUpdateAccents();if(document.hidden){clearTimeout(chatTimer);$('chat-status').dataset.running='false';}else if(state.authenticated&&$('chat-dialog').open)refreshConversation();});
$('chat-message').addEventListener('input',()=>{if(discussion.active)chats.setDraft(discussion.active,$('chat-message').value);resizeComposer();syncChatLatest();});
$('chat-log').addEventListener('scroll',syncChatLatest,{passive:true});
$('chat-latest').addEventListener('click',()=>{$('chat-log').scrollTop=$('chat-log').scrollHeight;syncChatLatest();});
$('chat-mode').addEventListener('change',()=>{if(discussion.active)chats.setMode(discussion.active,$('chat-mode').value);});
for(const id of ['settings-button','mobile-settings'])$(id).addEventListener('click',openSettings);
$('settings-close').addEventListener('click',()=>$('settings-dialog').close());
for(const id of ['theme','density','motion','transparency'])$(id).addEventListener('change',()=>{writeLocal(id,$(id).value);applyPreferences();});
$('notifications').addEventListener('click',toggleNotifications);
$('forget-cursor').addEventListener('click',async()=>{removeLocal(state.cursorKey);state.cursor=null;try{await pollEvents();toast('Alert cursor reset. Historic activity stayed quiet.');}catch(error){message('notification-status',errorText(error));}});
$('check-release').addEventListener('click',async()=>{
 $('check-release').disabled=true;
 try {
  await appInfo(true);
  const webText=$('version-status').textContent;
  const release=await request('/api/release-status');
  message('version-status',webText+' Latest Android: '+release.tag+(release.published_at?' · published '+new Date(release.published_at).toLocaleDateString():'')+'.');
 }catch(error){message('version-status',errorText(error));}
 finally{$('check-release').disabled=false;}
});
$('logout').addEventListener('click',async()=>{if(!allowClose())return;try{await request('/auth/logout',{method:'POST'});clearPrivate();}catch(error){message('settings-error',errorText(error)+' The server session has not been confirmed revoked.');}});
document.addEventListener('keydown',event=>{
 if(event.key==='Escape'&&!document.querySelector('dialog:modal')){const filters=document.querySelector('details.filters[open],details.discussion-utilities[open]');if(filters){event.preventDefault();filters.open=false;filters.querySelector('summary').focus();return;}}
 if(event.key==='Escape'&&$('task-dialog').open&&$('task-dialog').dataset.presentation==='inspector'&&!document.querySelector('dialog:modal')){event.preventDefault();closeTask();return;}
 if(!state.authenticated||state.restricted||event.metaKey||event.ctrlKey||event.altKey||event.target.closest('input,textarea,select,[contenteditable=true]')||document.querySelector('dialog[open]'))return;
 if(event.key==='/'){event.preventDefault();if(discussion.destination==='discussions')$('discussion-search').focus();else{document.querySelector('.filters').open=true;$('search').focus();}}
 if(event.key.toLowerCase()==='n'){event.preventDefault();showTask();}
});
document.addEventListener('pointerdown',event=>{for(const filters of document.querySelectorAll('details.filters[open],details.discussion-utilities[open]'))if(!filters.contains(event.target)&&!event.target.closest('#search-toggle'))filters.open=false;});
window.addEventListener('beforeunload',event=>{if(state.dirty||emailWorkspace?.store.dirty){event.preventDefault();event.returnValue='';}});
window.addEventListener('offline',()=>{setConnection(false);if(state.data)render();});
window.addEventListener('online',backgroundRefresh);
window.addEventListener('pagehide',()=>{state.recognition?.abort();for(const notice of state.notices)notice.close();});
if(!('SpeechRecognition' in window)&&!('webkitSpeechRecognition' in window)){$('voice').disabled=true;message('voice-hint','Voice recognition is not available in this browser. Type your message or use your keyboard’s dictation.');}

(async()=>{
 try{
  let session;
  if(activationCode!==null){const code=activationCode;activationCode=null;if(!code)throw new Error('This activation link is not valid. Use the complete private link.');session=await request('/auth/activate',{method:'POST',body:{activation_code:code}});}
  else session=await request('/auth/session');
  if(session.authenticated)await beginSession(session);else{show('login');$('username').focus();}
 }catch(error){activationCode=null;show('login');message('login-error',errorText(error));}
})();

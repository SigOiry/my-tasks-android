import {selectTasks,groupTasks,statuses,active,safeSource,chatBody,shouldNotify,updateGate,acknowledged,editPayload} from './policy.mjs';

const $ = id => document.getElementById(id);
const form = $('task-form');
const views = {active:['Active','Active tasks','The things that still need your attention.','◫'],today:['Today','Today','Due today or overdue. A clear place to start.','◷'],week:['Week','This week','Upcoming deadlines through Sunday, with overdue work in view.','▦'],all:['All','All tasks','Your complete workspace, including finished work.','☷'],trash:['Trash','Trash','Kept safely until you decide what comes next.','⌑']};
const storagePrefix = 'mytasks.web.';
const readLocal = key => { try { return localStorage.getItem(storagePrefix+key); } catch { return null; } };
const writeLocal = (key,value) => { try { localStorage.setItem(storagePrefix+key,value); } catch { /* Private mode can disable storage. */ } };
const removeLocal = key => { try { localStorage.removeItem(storagePrefix+key); } catch { /* Optional persistence. */ } };
const state = {authenticated:false,csrf:'',epoch:0,data:null,view:'active',project:'',selected:null,gate:null,dirty:false,snapshot:'',online:true,cursor:null,cursorKey:null,polling:false,mutating:false,chatBusy:false,chat:new Map(),own:new Map(),rejected:new Map(),notices:new Set(),controllers:new Set(),info:null,recognition:null};
let pollTimer, toastTimer, taskOpening=false;

function node(tag,attributes={},text) {
 const element = document.createElement(tag);
 for (const [key,value] of Object.entries(attributes)) {
  if (key === 'class') element.className = value;
  else element.setAttribute(key,value);
 }
 if (text !== undefined) element.textContent=text;
 return element;
}
function show(id,visible=true) { $(id).hidden=!visible; }
function message(id,value='') { $(id).textContent=value; }
function toast(text) { message('toast',text);show('toast');clearTimeout(toastTimer);toastTimer=setTimeout(()=>show('toast',false),4000); }
function errorText(error) { return error?.message || 'Something went wrong. Please try again.'; }
function setConnection(online,text) {
 state.online=online;
 message('connection',text || (online?'Connected · private':'Offline · read-only'));
 $('save-task').disabled=!online || !!state.selected?.deleted || !$('conflict').hidden;
 $('chat-send').disabled=!online || state.chatBusy;
 $('updates-ack').disabled=!online || !state.gate || !$('updates-refresh').hidden;
 if (!online && state.gate) message('updates-error','Offline. Reconnect before acknowledging this update.');
 else if (online && $('updates-error').textContent==='Offline. Reconnect before acknowledging this update.') message('updates-error');
}
function applyPreferences() {
 const theme=readLocal('theme'),density=readLocal('density');
 document.documentElement.dataset.theme=['light','dark'].includes(theme)?theme:'system';
 document.documentElement.dataset.density=density==='compact'?'compact':'comfortable';
 $('theme').value=document.documentElement.dataset.theme;
 $('density').value=document.documentElement.dataset.density;
}
applyPreferences();

async function request(path,{method='GET',body,timeout=25000}={}) {
 const controller=new AbortController(),epoch=state.epoch;
 state.controllers.add(controller);
 const timer=setTimeout(()=>controller.abort(),timeout);
 try {
  const headers={};
  if (body!==undefined) headers['Content-Type']='application/json';
  if (!['GET','HEAD'].includes(method) && state.csrf) headers['X-CSRF-Token']=state.csrf;
  const response=await fetch(path,{method,body:typeof body==='string'?body:body===undefined?undefined:JSON.stringify(body),headers,credentials:'same-origin',cache:'no-store',signal:controller.signal});
  const result=await response.json();
  if (epoch!==state.epoch) throw new Error('Session ended.');
  if (!response.ok) {
   if (response.status===401 && state.authenticated) clearPrivate('Your session ended. Please log in again.');
   const error=new Error(typeof result.detail==='string'?result.detail:response.status===422?'Please check the submitted fields.':'The request could not be completed.');
   error.status=response.status;
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
 state.authenticated=false;
 state.csrf='';state.data=null;state.selected=null;state.gate=null;state.dirty=false;state.snapshot='';state.view='active';state.project='';state.cursor=null;state.cursorKey=null;state.info=null;
 state.chat.clear();state.own.clear();state.rejected.clear();state.chatBusy=false;state.polling=false;state.mutating=false;
 for (const controller of state.controllers) controller.abort();
 state.controllers.clear();
 for (const notice of state.notices) notice.close();
 state.notices.clear();
 state.recognition?.abort();state.recognition=null;
 clearInterval(pollTimer);
 for (const dialog of document.querySelectorAll('dialog[open]')) dialog.close();
 for (const input of document.querySelectorAll('input,textarea')) input.value='';
 for (const id of ['task-list','projects','project-options','views','chat-log','source-links','source-accounts','updates-summary','updates-title','detail-heading','detail-context','project-filter','sync-state','filter-summary','version-status','detail-error','chat-error','global-error','updates-error']) $(id).replaceChildren();
 form.reset();$('chat-form').reset();
 try { for (const key of Object.keys(localStorage)) if (key.startsWith(storagePrefix)) localStorage.removeItem(key); } catch { /* Nothing private is persisted. */ }
 applyPreferences();
 show('app',false);show('login');message('login-error',reason);
}
async function beginSession(session) {
 state.authenticated=true;state.csrf=session.csrf;
 show('login',false);show('app');message('login-error');
 // Scope the durable transport cursor to this opaque server session, never to the bearer token.
 const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(session.csrf));
 const scope=Array.from(new Uint8Array(digest)).map(b=>b.toString(16).padStart(2,'0')).join('').slice(0,24);
 state.cursorKey='cursor.'+scope;
 const stored=readLocal(state.cursorKey);
 state.cursor=stored && /^\d+$/.test(stored) && Number.isSafeInteger(Number(stored))?Number(stored):null;
 try {await refreshTasks();await pollEvents();} catch(error) {message('global-error',errorText(error));show('global-error');}
 clearInterval(pollTimer);
 pollTimer=setInterval(()=>{if(state.authenticated) backgroundRefresh();},20000);
}
async function refreshTasks() {
 const data=await request('/api/tasks');
 if (!state.authenticated) return;
 state.data=data;setConnection(true);render();show('global-error',false);
}
function options(overrides={}) {
 return {view:state.view,project:state.project,query:$('search').value,status:$('status-filter').value,sort:$('sort').value,today:state.data?.today,week_end:state.data?.week_end,...overrides};
}
function dateLabel(date) {
 if (!date) return 'No due date';
 try {return new Intl.DateTimeFormat(undefined,{day:'numeric',month:'short'}).format(new Date(date+'T12:00:00'));} catch {return date;}
}
function render() {
 if (!state.data) return;
 const tasks=state.data.tasks,visible=selectTasks(tasks,options()),view=views[state.view];
 $('views').replaceChildren();
 for (const [key,[label,,,symbol]] of Object.entries(views)) {
  const button=node('button',{class:'nav-button',type:'button','data-view':key,'aria-current':key===state.view?'page':'false'});
  button.append(node('span',{class:'nav-symbol','aria-hidden':'true'},symbol),node('span',{},label),node('span',{class:'nav-count'},String(selectTasks(tasks,options({view:key,project:'',query:'',status:''})).length)));
  button.addEventListener('click',()=>{state.view=key;$('status-filter').value='';render();});
  $('views').append(button);
 }
 const projects=[...new Set(tasks.filter(t=>!t.deleted).map(t=>t.project||'Unassigned'))].sort((a,b)=>a.localeCompare(b));
 $('projects').replaceChildren();$('project-options').replaceChildren();$('project-filter').replaceChildren(node('option',{value:''},'All projects'));
 for (const project of projects) {
  const button=node('button',{class:'nav-button'+(state.project===project?' selected':''),type:'button','aria-pressed':String(state.project===project)});
  button.append(node('span',{class:'project-dot','aria-hidden':'true'}),node('span',{class:'project-name'},project),node('span',{class:'nav-count'},String(tasks.filter(t=>active(t)&&(t.project||'Unassigned')===project).length)));
  button.addEventListener('click',()=>{state.project=state.project===project?'':project;render();});
  $('projects').append(button);
  $('project-options').append(node('option',{value:project}));
  $('project-filter').append(node('option',{value:project},project));
 }
 $('project-filter').value=state.project;
 message('breadcrumb-view',view[0]);
 $('view-title').replaceChildren(document.createTextNode(view[1]),node('span',{class:'count',id:'view-count'},String(visible.length)));
 message('view-description',view[2]);
 message('workspace-date',new Intl.DateTimeFormat(undefined,{weekday:'long',day:'numeric',month:'long',timeZone:'Europe/Rome'}).format(new Date(state.data.today+'T12:00:00+01:00')).toUpperCase());
 const unread=visible.filter(t=>t.notification?.unread).length;
 message('filter-summary',[state.project||'All projects',`${visible.length} task${visible.length===1?'':'s'}`,unread?`${unread} unread update${unread===1?'':'s'}`:'',!state.online?'Offline copy · read-only':''].filter(Boolean).join('  ·  '));
 $('task-list').replaceChildren();
 if (!visible.length) {
  const empty=node('div',{class:'empty-state'});
  empty.append(node('h2',{},state.view==='trash'?'Nothing in Trash.':'A clear view.'),node('p',{},$('search').value||state.project||$('status-filter').value?'No tasks match these filters. Try a broader view.':state.view==='active'?'No active tasks right now. Add a task when something needs your attention.':'No tasks to show in this view.'));
  const button=node('button',{type:'button'},'Reset filters');button.addEventListener('click',()=>{state.view='active';state.project='';$('search').value='';$('status-filter').value='';render();});empty.append(button);$('task-list').append(empty);
 }
 for (const group of groupTasks(visible)) {
  const section=node('section',{class:'task-group'});
  const heading=node('h2',{class:'group-heading'});heading.append(document.createTextNode(group.name),node('span',{class:'group-count'},String(group.tasks.length)),node('span',{class:'group-line','aria-hidden':'true'}));section.append(heading);
  for (const task of group.tasks) {
   const row=node('article',{class:'task-row'+(task.notification?.unread?' unread':'')+(task.status==='completed'?' is-completed':''),'data-task-id':task.id});
   const check=node('button',{class:'task-check',type:'button','aria-label':task.status==='completed'?'Reopen '+task.title:'Complete '+task.title,title:task.notification?.unread?'Review update before changing status':'Change completion status'});
   check.append(node('span',{class:'check-circle','aria-hidden':'true'},task.status==='completed'?'✓':''));
   check.disabled=task.deleted||!state.online;check.addEventListener('click',()=>quickComplete(task,check));
   const open=node('button',{class:'task-open',type:'button'});
   open.append(node('span',{class:'task-title'},task.title));
   const meta=node('span',{class:'task-meta'});meta.append(node('span',{},statuses[task.status]||task.status));
   if(task.notification?.unread) meta.append(node('span',{class:'unread-badge'},'Unread update'));
   if (task.source && task.source!=='manual') meta.append(node('span',{class:'source-label'},task.source));
   open.append(meta);open.addEventListener('click',()=>openTask(task.id));
   const tail=node('div',{class:'task-tail'}),overdue=task.due_date && task.due_date<state.data.today && active(task);
   tail.append(node('span',{class:'priority priority-'+task.priority},(task.priority||'medium').replace(/^./,c=>c.toUpperCase())),node('span',{class:'due'+(overdue?' overdue':'')},task.due_date?(overdue?'Overdue · ':task.due_date===state.data.today?'Today · ':'')+dateLabel(task.due_date):'No due date'));
   row.append(check,open,tail);section.append(row);
  }
  $('task-list').append(section);
 }
 // Only display synchronization facts actually supplied by the backend.
 const sync=state.data.sync;
 message('sync-state',sync?.message && typeof sync.message==='string'?sync.message:'Source synchronization is managed by your server.');
}

function updateLocal(task) {
 if (!state.data) return;
 const index=state.data.tasks.findIndex(t=>t.id===task.id);
 if (index<0) state.data.tasks.push(task);else state.data.tasks[index]=task;
 render();
}
async function openTask(id) {
 if (taskOpening || !allowClose()) return;
 taskOpening=true;
 try {
  const task=state.online?await request('/api/tasks/'+encodeURIComponent(id)):state.data.tasks.find(t=>t.id===id);
  if (!task) throw new Error('Task unavailable.');
  if (task.notification?.unread && !task.deleted) showUpdates(task);
  else showTask(task);
 } catch(error) {toast(errorText(error));} finally {taskOpening=false;}
}
function showUpdates(task) {
 $('task-dialog').close();state.gate=updateGate(task);
 message('updates-heading',state.gate.headline||'Task updated');message('updates-title',state.gate.title);message('updates-error');
 $('updates-summary').replaceChildren(...state.gate.summary.map(text=>node('li',{},text)));
 if (!state.gate.summary.length) $('updates-summary').append(node('li',{},'This task has new source context.'));
 $('updates-ack').disabled=!state.online;show('updates-refresh',false);
 if (!$('updates-dialog').open) $('updates-dialog').showModal();
}
async function acknowledge() {
 if (!state.gate || !state.online) return;
 const gate={...state.gate};$('updates-ack').disabled=true;message('updates-error');
 try {
  const task=await request('/api/tasks/'+encodeURIComponent(gate.id)+'/updates/read',{method:'POST',body:{token:gate.token}});
  if (!acknowledged(gate,task)) throw new Error('A newer update arrived. Review the current summary before opening the editor.');
  $('updates-dialog').close();state.gate=null;updateLocal(task);showTask(task);
 } catch(error) {
  message('updates-error',error.status===409?'A newer update arrived. Review it before acknowledging; nothing newer was marked read.':errorText(error)+' Nothing was marked read in this page. Review the current summary to retry.');
  show('updates-refresh');
 }
}
function fields() {return Object.fromEntries(new FormData(form).entries());}
function allowClose() {
 if (!state.dirty || !state.authenticated) return true;
 return window.confirm('Discard your unsaved edits? They have not been saved.');
}
function closeTask() {
 if (!allowClose()) return;
 state.dirty=false;$('task-dialog').close();state.selected=null;state.recognition?.abort();state.recognition=null;
}
function showTask(task=null) {
 state.selected=task;state.dirty=false;form.reset();
 for (const key of ['title','description','notes','project','due_date','status','priority']) {
  form.elements.namedItem(key).value=task?.[key]??(key==='status'?'todo':key==='priority'?'medium':'');
  form.elements.namedItem(key).disabled=!!task?.deleted;
 }
 state.snapshot=JSON.stringify(fields());
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
 switchPanel('details');renderChat();
 if (!$('task-dialog').open) $('task-dialog').showModal();
 $('task-dialog').scrollTop=0;
 if (!task) $('title').focus();else $('details-tab').focus();
}
function switchPanel(panel) {
 const details=panel==='details';show('details-panel',details);show('hermes-panel',!details);
 $('details-tab').setAttribute('aria-pressed',String(details));$('hermes-tab').setAttribute('aria-pressed',String(!details));
 if (!details) {renderChat();$('chat-message').focus();}
}
async function mutate(path,method,body) {
 if (!state.online) throw new Error('Offline. Reconnect before changing tasks.');
 state.mutating=true;
 try {
  // Flush prior external changes before marking this response token as our own.
  await pollEvents();
  const task=await request(path,{method,body});
  state.own.set(task.id,task.notification?.token);
  updateLocal(task);
  // Consume the resulting own-event cursor before a reload can lose the in-memory suppression.
  try {await pollEvents();} catch {toast('Task saved. Alert checks will resume when the connection recovers.');}
  return task;
 } finally {state.mutating=false;}
}
async function saveTask(event) {
 event.preventDefault();
 if (!state.online || $('save-task').disabled) return;
 $('save-task').disabled=true;message('detail-error');
 const selected=state.selected,payload=editPayload(selected,fields());
 try {
  const task=await mutate(selected?'/api/tasks/'+encodeURIComponent(selected.id):'/api/tasks',selected?'PATCH':'POST',payload);
  state.dirty=false;showTask(task);toast(selected?'Changes saved':'Task created');
 } catch(error) {
  message('detail-error',errorText(error));
  if (error.status===409 && selected) {show('conflict');$('save-task').disabled=true;return;}
 } finally {if($('conflict').hidden) $('save-task').disabled=!state.online;}
}
async function quickComplete(task,button) {
 if (task.notification?.unread) {await openTask(task.id);return;}
 button.disabled=true;
 try {await mutate('/api/tasks/'+encodeURIComponent(task.id),'PATCH',{revision:task.revision,status:task.status==='completed'?'todo':'completed'});toast(task.status==='completed'?'Task reopened':'Task completed');}
 catch(error) {toast(errorText(error));if(error.status===409) await refreshTasks();}
 finally {button.disabled=false;}
}
async function deleteTask() {
 const task=state.selected;if(!task)return;
 $('delete-confirm').disabled=true;
 try {
  await mutate('/api/tasks/'+encodeURIComponent(task.id),'DELETE',{revision:task.revision});
  $('confirm-dialog').close();state.dirty=false;$('task-dialog').close();state.selected=null;toast('Moved to Trash. You can restore it any time.');
 } catch(error) {
  $('confirm-dialog').close();message('detail-error',errorText(error));if(error.status===409){show('conflict');$('save-task').disabled=true;}
 } finally {$('delete-confirm').disabled=false;}
}
async function restoreTask() {
 const task=state.selected;if(!task)return;
 $('restore-task').disabled=true;
 try {const restored=await mutate('/api/tasks/'+encodeURIComponent(task.id)+'/restore','POST',{revision:task.revision});state.dirty=false;if(restored.notification?.unread)showUpdates(restored);else showTask(restored);toast('Task restored');}
 catch(error){message('detail-error',errorText(error));if(error.status===409)show('conflict');}
 finally{$('restore-task').disabled=false;}
}

function conversation() {
 const id=state.selected?.id;
 if (!id) return null;
 if (!state.chat.has(id)) state.chat.set(id,{turns:[],draft:'',draftRevision:0,mode:'question'});
 return state.chat.get(id);
}
function renderChat() {
 const chat=conversation();$('chat-log').replaceChildren();
 $('chat-message').value=chat?.draft||'';$('chat-mode').value=chat?.mode||'question';
 for(const turn of chat?.turns||[]) {
  const item=node('article',{class:'chat-turn '+turn.role});
  item.append(node('span',{class:'speaker'},turn.role==='user'?'You':'Hermes · draft-only'),node('div',{class:'message'},turn.content));
  if(turn.role==='assistant') {const copy=node('button',{type:'button'},'Copy response');copy.addEventListener('click',()=>copyText(turn.content));item.append(copy);}
  $('chat-log').append(item);
 }
 if(state.chatBusy)$('chat-log').append(node('div',{class:'chat-working'},'Hermes is thinking. This may take a few minutes…'));
 $('chat-send').disabled=!state.online||state.chatBusy;
}
async function copyText(value) {
 try {await navigator.clipboard.writeText(value);toast('Copied');}
 catch {toast('Clipboard unavailable. Select the text and copy it manually.');}
}
async function sendChat(event) {
 event.preventDefault();
 if(state.chatBusy||!state.online||!state.selected)return;
 const chat=conversation(),taskId=state.selected.id,current=$('chat-message').value,mode=$('chat-mode').value,epoch=state.epoch,draftRevision=chat.draftRevision;
 let body;
 try {body=chatBody(taskId,current,mode,chat.turns);}catch(error){message('chat-error',errorText(error));return;}
 if(state.rejected.get(taskId)===body){message('chat-error','This exact request was rejected as too large. Shorten or change it before sending again.');return;}
 state.chatBusy=true;message('chat-error');renderChat();
 try {
  const result=await request('/api/chat',{method:'POST',body,timeout:205000});
  if(epoch!==state.epoch)return;
  chat.turns.push({role:'user',content:current},{role:'assistant',content:result.reply});
  // Bound private memory as well as provider history; nothing persists across logout/reload.
  if(chat.turns.length>24)chat.turns.splice(0,chat.turns.length-24);
  // Only this submission's unchanged draft is ours to clear. Later edits,
  // including edits back to identical text, belong to the next message.
  if(chat.draftRevision===draftRevision)chat.draft='';
  state.rejected.delete(taskId);
 } catch(error) {
  if(error.status===413)state.rejected.set(taskId,body);
  if(epoch===state.epoch)message('chat-error',errorText(error)+(error.status===413?' Shorten the message before resending.':' Nothing was automatically resent.'));
 } finally {state.chatBusy=false;if(epoch===state.epoch)renderChat();}
}
async function startVoice() {
 const Recognition=window.SpeechRecognition||window.webkitSpeechRecognition;
 if(!Recognition){message('chat-error','Speech recognition is not supported in this browser. Type your message, or use your keyboard’s dictation.');return;}
 if(state.recognition){state.recognition.stop();return;}
 if(!window.confirm('Enable voice transcription? Your browser may send audio to its speech service. The transcript stays editable and is never sent to Hermes automatically.'))return;
 const recognition=new Recognition();state.recognition=recognition;
 recognition.lang=navigator.language||'en-US';recognition.interimResults=false;recognition.continuous=false;
 const taskId=state.selected?.id;
 recognition.onresult=event=>{
  if(taskId!==state.selected?.id||!state.authenticated)return;
  const text=Array.from(event.results).map(result=>result[0].transcript).join(' ');
  $('chat-message').value=[$('chat-message').value,text].filter(Boolean).join(' ');
  const chat=conversation();if(chat){chat.draft=$('chat-message').value;chat.draftRevision++;}
  $('chat-message').focus();
 };
 recognition.onerror=event=>message('chat-error',event.error==='not-allowed'?'Microphone permission was not granted. You can continue by typing.':'Voice transcription stopped. You can continue by typing.');
 recognition.onend=()=>{state.recognition=null;message('voice','Microphone');};
 try {recognition.start();message('voice','Stop listening');}catch{state.recognition=null;message('chat-error','Microphone unavailable. Continue by typing.');}
}

async function pollEvents() {
 if(!state.authenticated||state.polling)return;
 state.polling=true;
 const baseline=state.cursor===null,epoch=state.epoch;
 let cursor=state.cursor||0;
 try {
  let hasMore;
  do {
   const feed=await request('/api/events?after='+cursor+'&limit=100');hasMore=feed.has_more;
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
 } finally {state.polling=false;}
}
async function backgroundRefresh() {
 if(!state.authenticated||state.mutating||state.polling)return;
 try{await pollEvents();await refreshTasks();}
 catch(error){if(state.authenticated){message('global-error',errorText(error));show('global-error');}}
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
 message('settings-error');notificationStatus();$('settings-dialog').showModal();
 try{await appInfo();}catch(error){message('version-status',errorText(error));}
}

$('login-form').addEventListener('submit',async event=>{
 event.preventDefault();const submit=event.currentTarget.querySelector('button');submit.disabled=true;message('login-error');
 const body=JSON.stringify({token:$('access-token').value});$('access-token').value='';
 try {const session=await request('/auth/login',{method:'POST',body});await beginSession(session);}
 catch(error){message('login-error',errorText(error));}
 finally{submit.disabled=false;}
});
$('search').addEventListener('input',render);
$('status-filter').addEventListener('change',render);$('sort').addEventListener('change',render);
$('project-filter').addEventListener('change',()=>{state.project=$('project-filter').value;render();});
$('clear-project').addEventListener('click',()=>{state.project='';render();});
$('refresh').addEventListener('click',backgroundRefresh);
for(const id of ['new-task','new-mobile'])$(id).addEventListener('click',()=>{if(allowClose())showTask();});
for(const element of document.querySelectorAll('.close-dialog'))element.addEventListener('click',closeTask);
$('task-dialog').addEventListener('cancel',event=>{event.preventDefault();closeTask();});
form.addEventListener('input',()=>{state.dirty=JSON.stringify(fields())!==state.snapshot;});form.addEventListener('change',()=>{state.dirty=JSON.stringify(fields())!==state.snapshot;});
form.addEventListener('submit',saveTask);
$('details-tab').addEventListener('click',()=>switchPanel('details'));$('hermes-tab').addEventListener('click',()=>switchPanel('hermes'));
$('updates-close').addEventListener('click',()=>{$('updates-dialog').close();state.gate=null;});
$('updates-ack').addEventListener('click',acknowledge);
$('updates-refresh').addEventListener('click',async()=>{try{const task=await request('/api/tasks/'+encodeURIComponent(state.gate.id));if(task.notification?.unread)showUpdates(task);else{$('updates-dialog').close();showTask(task);}}catch(error){message('updates-error',errorText(error));}});
$('delete-task').addEventListener('click',()=>{$('confirm-dialog').showModal();});
$('delete-cancel').addEventListener('click',()=>$('confirm-dialog').close());$('delete-confirm').addEventListener('click',deleteTask);$('restore-task').addEventListener('click',restoreTask);
$('copy-edits').addEventListener('click',()=>{const values=fields();copyText(Object.entries(values).map(([key,value])=>key.replace('_',' ')+':\n'+value).join('\n\n'));});
$('reload-task').addEventListener('click',async()=>{if(!window.confirm('Load the latest version? Your current form edits will be replaced. Copy them first if you want to keep them.'))return;try{const task=await request('/api/tasks/'+encodeURIComponent(state.selected.id));state.dirty=false;if(task.notification?.unread)showUpdates(task);else showTask(task);}catch(error){message('detail-error',errorText(error));}});
$('chat-form').addEventListener('submit',sendChat);$('voice').addEventListener('click',startVoice);
$('chat-message').addEventListener('input',()=>{const chat=conversation();if(chat){chat.draft=$('chat-message').value;chat.draftRevision++;}});$('chat-mode').addEventListener('change',()=>{const chat=conversation();if(chat)chat.mode=$('chat-mode').value;});
for(const id of ['settings-button','mobile-settings'])$(id).addEventListener('click',openSettings);
$('settings-close').addEventListener('click',()=>$('settings-dialog').close());
for(const id of ['theme','density'])$(id).addEventListener('change',()=>{writeLocal(id,$(id).value);applyPreferences();});
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
 if(!state.authenticated||event.metaKey||event.ctrlKey||event.altKey||event.target.closest('input,textarea,select,[contenteditable=true]')||document.querySelector('dialog[open]'))return;
 if(event.key==='/'){event.preventDefault();$('search').focus();}
 if(event.key.toLowerCase()==='n'){event.preventDefault();showTask();}
});
window.addEventListener('beforeunload',event=>{if(state.dirty){event.preventDefault();event.returnValue='';}});
window.addEventListener('offline',()=>{setConnection(false);if(state.data)render();});
window.addEventListener('online',backgroundRefresh);
window.addEventListener('pagehide',()=>{state.recognition?.abort();for(const notice of state.notices)notice.close();});
if(!('SpeechRecognition' in window)&&!('webkitSpeechRecognition' in window)){$('voice').disabled=true;message('voice-hint','Voice recognition is not available in this browser. Type your message or use your keyboard’s dictation.');}

(async()=>{
 try{const session=await request('/auth/session');if(session.authenticated)await beginSession(session);else{show('login');$('access-token').focus();}}
 catch(error){show('login');message('login-error',errorText(error));}
})();

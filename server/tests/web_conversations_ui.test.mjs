import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';
import {ConversationStore,pendingRequest} from '../web/conversations.mjs';

function harness({transport}={}){
 const elements=new Map(),calls=[],focusLosses=[];
 const element=(tag='div')=>({tagName:String(tag).toUpperCase(),options:[],style:{setProperty(){}},value:'',textContent:'',hidden:true,disabled:false,open:false,dataset:{},children:[],listeners:{},scrollHeight:100,scrollTop:0,clientHeight:100,
  addEventListener(name,fn){this.listeners[name]=fn;},attributes:{},setAttribute(name,value){this.attributes[name]=String(value);if(name.startsWith('data-'))this.dataset[name.slice(5).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=String(value);},replaceChildren(...children){this.children=children;},append(...children){this.children.push(...children);},insertBefore(child,before){if(child===before)return;const previous=this.children.indexOf(child);if(previous>=0)this.removeChild(child);const index=before?this.children.indexOf(before):this.children.length;this.children.splice(index,0,child);},removeChild(child){this.children.splice(this.children.indexOf(child),1);if(context.document.activeElement===child){focusLosses.push(child);context.document.activeElement=context.document.body;}},focus(options){(this.focusCalls??=[]).push(options);context.document.activeElement=this;},reset(){this.value='';},close(){this.open=false;},showModal(){this.open=true;}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 let resolve;const pending=new Promise(yes=>{resolve=yes;});
 const context=vm.createContext({...policy,ConversationStore,pendingRequest,AbortController,setTimeout,clearTimeout,clearInterval,TextEncoder,URLSearchParams,
 localStorage:{getItem(){return null;}},navigator:{},document:{querySelector(selector){return selector==='.filters'?get('filters'):null;},getElementById:get,documentElement:{dataset:{},style:{setProperty(){}}},querySelectorAll(){return [];},addEventListener(){},createElement:element,createTextNode:text=>({nodeType:3,textContent:text,children:[]})},window:{addEventListener(){},SpeechRecognition:function(){}},
 fetch(path,options){if(path==='/auth/session')return Promise.resolve({ok:true,json:async()=>({authenticated:false})});calls.push({path,options});return transport?transport(path,options):pending;}});
 context.document.body=element('body');context.document.activeElement=context.document.body;
 const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/gm,'');
 vm.runInContext(source+'\nglobalThis.app={state,chats,discussion,sendChat,renderChat,clearPrivate,loadTaskThreads,loadDiscussions,renderDiscussions,openConversation,refreshConversation,syncChatLatest,syncVisualViewport,updateEmphasisAge:typeof updateEmphasisAge===\"function\"?updateEmphasisAge:null};',context);
 const app=context.app;app.state.authenticated=true;
 for(const id of ['a','b']) {const e=app.chats.entry(id);e.loaded=true;e.conversation={id,title:'Title '+id,revision:1,archived:false,task_id:null,latest_request:null};}
 app.discussion.active='a';app.renderChat(true);
 const input=text=>{get('chat-message').value=text;get('chat-message').listeners.input();};
 const succeed=()=>{const payload=JSON.parse(calls[0].options.body);resolve({ok:true,json:async()=>({conversation:{id:'a',title:'Title a',revision:2,task_id:null,archived:false},request:{id:payload.request_id,status:'queued'},user_message:{id:1,request_id:payload.request_id,role:'user',content:payload.content}})});};
 return {...app,get,input,calls,succeed,focusLosses,document:context.document};
}
const response=(data,status=200)=>({ok:status<400,status,json:async()=>data});
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};
test('actual empty history transitions out of loading and failed load offers explicit refresh',async()=>{
 const waiting=deferred(),h=harness({transport:()=>waiting.promise});h.discussion.active='new';h.renderChat();assert.match(h.get('chat-log').children[0].textContent,/Loading/);
 const loading=h.chats.load('new');h.renderChat();waiting.resolve(response({conversation:{id:'new',title:'Empty',revision:1},messages:[],has_more:false}));await loading;h.renderChat();assert.match(h.get('chat-log').children[0].textContent,/Start with/);assert.doesNotMatch(h.get('chat-status').textContent,/Loading/);
 const e=h.chats.entry('failed');e.error='Unavailable';h.discussion.active='failed';h.renderChat();assert.match(h.get('chat-log').children[0].textContent,/Refresh/);assert.doesNotMatch(h.get('chat-status').textContent,/Loading/);assert.doesNotMatch(h.get('chat-title').textContent,/Loading/);assert.doesNotMatch(h.get('chat-context').textContent,/Free discussion/);h.state.online=false;h.renderChat();assert.match(h.get('chat-status').textContent,/history has not loaded/);
});
test('task thread failure ends loading, disables Open and retries only on explicit action',async()=>{
 let count=0;const h=harness({transport:async()=>++count===1?response({detail:'Unavailable'},503):response({conversations:[],next_cursor:null})});h.state.selected={id:'task'};await h.loadTaskThreads();assert.equal(h.get('task-thread-open').disabled,true);assert.doesNotMatch(h.get('task-thread-status').textContent,/Loading/);assert.equal(h.get('task-thread-retry').hidden,false);assert.equal(h.get('task-thread').children[0].textContent,'Discussions unavailable');assert.equal(count,1);
 await h.get('task-thread-retry').listeners.click();assert.equal(count,2);assert.match(h.get('task-thread-status').textContent,/No saved discussions/);assert.equal(h.get('task-thread-retry').hidden,true);assert.equal(h.get('task-thread-open').disabled,true);
});
test('failed changed discussion scope never labels previous rows as current search results',async()=>{
 let count=0;const h=harness({transport:async()=>++count===1?response({conversations:[{id:'old',title:'Old task',task_id:'t',revision:1}],next_cursor:'next'}):response({detail:'Unavailable'},503)});h.get('discussion-scope').value='all';await h.loadDiscussions();assert.equal(h.discussion.rows.length,1);
 h.get('discussion-scope').value='free';await h.loadDiscussions();h.get('discussion-search').value='old';h.renderDiscussions();assert.equal(h.discussion.rows.length,0);assert.match(h.get('discussion-list-status').textContent,/Could not load/);assert.equal(h.get('discussion-more').hidden,true);assert.equal(h.discussion.loading,false);
});
test('task thread retry response cannot replace a different task or signed-out workspace',async()=>{
 for(const logout of [false,true]){const a=deferred(),b=deferred();let count=0;const h=harness({transport:()=>++count===1?a.promise:b.promise});h.state.selected={id:'old'};const old=h.loadTaskThreads();if(logout)h.clearPrivate();else {h.state.selected={id:'new'};const fresh=h.loadTaskThreads();b.resolve(response({conversations:[{id:'fresh',title:'Fresh',revision:1}],next_cursor:null}));await fresh;}a.resolve(response({conversations:[{id:'stale',title:'Stale',revision:1}],next_cursor:null}));await old;assert.ok(!h.get('task-thread').children.some(n=>n.value==='stale'));}
});
test('saved history rows retain focused identity, show dated provenance and memory-only draft state',()=>{
 const h=harness(),c={id:'a',title:'Saved title',revision:1,updated_at:'2026-11-10T12:30:00Z'};h.discussion.rows=[c];h.chats.metadata(c);h.chats.setDraft('a','Unsent words');h.renderDiscussions();const row=h.get('discussion-list').children[0];row.focus();h.renderDiscussions();assert.equal(h.get('discussion-list').children[0],row);assert.equal(h.document.activeElement,row);
 const time=row.children.find(n=>n.tagName==='TIME');assert.match(time.textContent,/2026/);assert.match(time.attributes['aria-label'],/2026/);assert.equal(time.attributes.datetime,c.updated_at);assert.ok(row.children.some(n=>/Unsent writing · this session only/.test(n.textContent)));
 h.get('discussion-search').value='does not match';h.renderDiscussions();assert.equal(h.document.activeElement,h.get('discussion-search'));assert.match(h.get('discussion-list').children[0].textContent,/loaded discussions/);
});
test('actual discussion reorder restores the moved focused row after native-style insertBefore blur without scrolling',()=>{
 const h=harness(),a={id:'a',title:'Newer'},b={id:'b',title:'Older'};h.discussion.rows=[a,b];h.renderDiscussions();
 const list=h.get('discussion-list'),row=list.children[1];row.focus();h.discussion.rows=[b,a];h.renderDiscussions();
 assert.equal(list.children[0],row,'updated older discussion moves first without replacement');
 assert.equal(h.focusLosses.length,1,'DOM boundary must drop focus when insertBefore detaches the focused row');assert.equal(h.focusLosses[0],row);
 assert.equal(h.document.activeElement,row,'native move must not leave focus on BODY');assert.equal(row.focusCalls.length,2);assert.equal(row.focusCalls[1].preventScroll,true);
});
test('actual unchanged discussion row keeps focus without a redundant focus call',()=>{
 const h=harness();h.discussion.rows=[{id:'a',title:'Saved'}];h.renderDiscussions();const row=h.get('discussion-list').children[0];row.focus();h.renderDiscussions();
 assert.equal(h.document.activeElement,row);assert.equal(h.focusLosses.length,0);assert.equal(row.focusCalls.length,1);
});
test('actual discussion reorder keeps focus when only a neighboring row is moved',()=>{
 const h=harness(),a={id:'a',title:'A'},b={id:'b',title:'B'};h.discussion.rows=[a,b];h.renderDiscussions();const row=h.get('discussion-list').children[0];row.focus();h.discussion.rows=[b,a];h.renderDiscussions();
 assert.equal(h.get('discussion-list').children[1],row);assert.equal(h.document.activeElement,row);assert.equal(h.focusLosses.length,0);assert.equal(row.focusCalls.length,1);
});
test('actual removed focused discussion row retains search fallback without scrolling',()=>{
 const h=harness();h.discussion.rows=[{id:'a',title:'A'},{id:'b',title:'B'}];h.renderDiscussions();const row=h.get('discussion-list').children[0];row.focus();h.discussion.rows=[h.discussion.rows[1]];h.renderDiscussions();
 assert.equal(h.focusLosses[0],row);assert.equal(h.document.activeElement,h.get('discussion-search'));assert.equal(h.get('discussion-search').focusCalls[0].preventScroll,true);assert.equal(row.focusCalls.length,1);
});
test('actual discussion reorder or removal does not steal external or modal focus',()=>{
 for(const target of ['discussion-search','chat-message'])for(const remove of [false,true]){
  const h=harness(),a={id:'a',title:'A'},b={id:'b',title:'B'};h.discussion.rows=[a,b];h.renderDiscussions();const original=[...h.get('discussion-list').children];
  h.get('chat-dialog').open=target==='chat-message';const focused=h.get(target);focused.focus();h.discussion.rows=remove?[b]:[b,a];h.renderDiscussions();
  assert.equal(h.document.activeElement,focused);assert.equal(focused.focusCalls.length,1);assert.equal(h.focusLosses.length,0);assert.ok(original.every(row=>!row.focusCalls));
 }
});
test('discussion empty states distinguish loading, failed, archived and task scope',()=>{
 const h=harness(),text=()=>h.get('discussion-list').children[0].textContent;
 h.discussion.loading=true;h.renderDiscussions();assert.match(text(),/Loading/);h.discussion.loading=false;h.discussion.loadFailed=true;h.renderDiscussions();assert.match(text(),/Refresh/);h.discussion.loadFailed=false;
 h.get('discussion-archived').checked=true;h.renderDiscussions();assert.match(text(),/No archived/);h.get('discussion-archived').checked=false;h.get('discussion-scope').value='task';h.get('discussion-task').value='';h.renderDiscussions();assert.match(text(),/Choose a task/);
});
test('closing discussion restores its initiating row and refresh keeps that focus',async()=>{
 const h=harness({transport:async(path)=>response(path.includes('?scope=')?{conversations:[{id:'a',title:'Title a',revision:1}],next_cursor:null}:{conversation:{id:'a',title:'Title a',revision:1},messages:[],has_more:false})});h.document.hidden=true;h.discussion.destination='discussions';h.get('discussion-scope').value='all';await h.loadDiscussions();const row=h.get('discussion-list').children[0];row.focus();await h.openConversation('a');h.get('chat-close').focus();h.get('chat-dialog').open=false;h.get('chat-dialog').listeners.close();assert.equal(h.document.activeElement,row);await new Promise(r=>setTimeout(r,0));assert.equal(h.get('discussion-list').children[0],row);assert.equal(h.document.activeElement,row);h.clearPrivate();
});
test('saved message timestamps include readable dates and accessible exact provenance',()=>{const h=harness();h.chats.entry('a').messages=[{id:42,role:'assistant',content:'Saved reply',created_at:'2026-11-10T12:30:00Z'}];h.renderChat();const time=h.get('chat-log').children[0].children.find(n=>n.tagName==='TIME');assert.match(time.textContent,/2026/);assert.match(time.attributes['aria-label'],/2026/);assert.equal(time.attributes.datetime,'2026-11-10T12:30:00Z');});
test('discussion email mode is text-only and unsent writing is explicitly session-only',()=>{
 const h=harness();h.get('chat-mode').value='email_draft';h.get('chat-mode').listeners.change();assert.match(h.get('chat-mode-hint').textContent,/discussion text only/);assert.match(h.get('chat-mode-hint').textContent,/Reply \/ open draft/);assert.equal(h.calls.length,0);
 h.input('Keep my own words');assert.equal(h.get('chat-draft-state').hidden,false);assert.match(h.get('chat-draft-state').textContent,/this page session only/);h.discussion.active='b';h.renderChat(true);assert.equal(h.get('chat-draft-state').hidden,true);assert.equal(h.chats.entry('a').draft,'Keep my own words');h.clearPrivate();assert.equal(h.get('chat-draft-state').hidden,true);
});
test('three-dot wave reflects running only, never queued or terminal',()=>{
 const h=harness(),e=h.chats.entry('a');h.get('chat-dialog').open=true;
 for(const status of ['queued','running','succeeded','failed','interrupted']){
  e.conversation.latest_request={id:'request',status};h.renderChat();
  assert.equal(h.get('chat-status').dataset.running,String(status==='running'));
 }
});
test('admission bubble is visibly Sending until the durable response replaces it',async()=>{
 const h=harness();h.input('Pending bubble');const sending=h.sendChat({preventDefault(){}});
 const pending=h.get('chat-log').children.find(item=>item.dataset?.admissionId);
 assert.ok(pending,'pending outgoing bubble appears before admission');
 assert.equal(pending.children.find(item=>item.tagName==='SMALL').textContent,'Sending…');
 h.succeed();await sending;
 assert.ok(!h.get('chat-log').children.some(item=>item.dataset?.admissionId));
 assert.ok(h.get('chat-log').children.some(item=>item.dataset?.messageId==='1'));
});
test('production submit/input/render integration keeps follow-up in its original thread across deferred admission',async()=>{
 const h=harness();h.input('Original');const sending=h.sendChat({preventDefault(){}});h.input('A follow-up');
 h.discussion.active='b';h.renderChat(true);h.input('B draft');h.succeed();await sending;
 assert.equal(h.chats.entry('a').draft,'A follow-up');assert.equal(h.chats.entry('b').draft,'B draft');assert.equal(h.get('chat-message').value,'B draft');
 assert.equal(h.get('chat-title').textContent,'Title b');assert.equal(h.chats.entry('b').messages.length,0);
 const payload=JSON.parse(h.calls[0].options.body);assert.deepEqual(Object.keys(payload).sort(),['content','expected_revision','mode','request_id']);
 assert.equal(h.calls[0].path,'/api/conversations/a/messages');assert.equal(h.calls.length,1);
});
test('production logout clears private cache and late deferred admission cannot repopulate DOM',async()=>{
 const h=harness();h.get('updates-heading').children=['Private notification headline'];h.input('Sensitive text');const sending=h.sendChat({preventDefault(){}});h.clearPrivate();h.succeed();await sending;
 assert.equal(h.get('updates-heading').children.length,0);
 assert.equal(h.chats.entries.size,0);assert.equal(h.discussion.active,null);assert.equal(h.get('chat-message').value,'');assert.equal(h.get('chat-log').children.length,0);assert.equal(h.get('chat-title').children.length,0);
});
test('actual conversation renderer formats Markdown while keeping links and raw HTML inert',()=>{
 const h=harness();h.chats.entry('a').messages=[{id:21,role:'assistant',content:'A **bold** and *quiet* response with `code`.\n\n[mail](https://mail.google.com/mail/u/0/#inbox) <img src=x onerror=alert(1)>'}];h.renderChat();
 const visit=e=>[e,...(e.children||[]).flatMap(visit)];const nodes=h.get('chat-log').children.flatMap(visit);const tags=nodes.map(e=>e.tagName).filter(Boolean);
 assert.ok(tags.includes('STRONG'));assert.ok(tags.includes('EM'));assert.ok(tags.includes('CODE'));assert.ok(!tags.some(t=>['A','IMG','SCRIPT'].includes(t)));
 assert.ok(nodes.some(n=>n.nodeType===3&&n.textContent.includes('<img src=x onerror=alert(1)>')));
});
test('new replies retain existing message nodes and do not move a reader away from older turns',()=>{
 const h=harness(),log=h.get('chat-log'),entry=h.chats.entry('a');
 Object.defineProperty(log,'scrollHeight',{get(){return this.children.length*100;}});
 entry.messages=Array.from({length:8},(_,i)=>({id:i+1,role:i%2?'assistant':'user',content:'Saved turn '+i}));h.renderChat();
 const first=log.children[0];log.scrollTop=150;
 entry.messages.push({id:9,role:'assistant',content:'New reply below the reader'});h.renderChat();
 assert.equal(log.children[0],first,'stable message identity preserves selection/focus');
 assert.equal(log.scrollTop,150,'an appended reply must not apply prepend-height compensation');
});
test('loading older history compensates only for the prepended content',()=>{
 const h=harness(),log=h.get('chat-log'),entry=h.chats.entry('a');
 Object.defineProperty(log,'scrollHeight',{get(){return this.children.length*100;}});
 entry.messages=[{id:2,role:'user',content:'Known'},{id:3,role:'assistant',content:'Known reply'},{id:4,role:'user',content:'Next'}];h.renderChat();
 const anchor=log.children[0];log.scrollTop=40;entry.messages.unshift({id:1,role:'assistant',content:'Earlier turn'});h.renderChat();
 assert.equal(log.children[1],anchor);assert.equal(log.scrollTop,140);
});
for(const reading of [false,true])test('viewport resize '+(reading?'preserves an older reading anchor':'follows the latest messages when previously at the end'),()=>{
 const h=harness(),log=h.get('chat-log'),entry=h.chats.entry('a');h.get('chat-dialog').open=true;
 Object.defineProperty(log,'scrollHeight',{get(){return this.children.length*100;}});
 entry.messages=Array.from({length:8},(_,i)=>({id:i+1,role:'assistant',content:'Saved turn '+i}));h.renderChat();
 log.scrollTop=reading?150:700;h.syncChatLatest();log.clientHeight=50;h.syncVisualViewport();
 assert.equal(log.scrollTop,reading?150:750);
});
test('update emphasis is token-bound, finite, and suppresses own changes',()=>{
 const h=harness();assert.equal(typeof h.updateEmphasisAge,'function');
 const task={id:'task-a',notification:{unread:true,token:'first'}};
 assert.equal(h.updateEmphasisAge(task,1000),0);assert.equal(h.updateEmphasisAge(task,3000),0);
 // Row binds and polls do not consume animation time; the visible scheduler owns it.
 assert.equal(h.updateEmphasisAge(task,6000),0);assert.equal(h.updateEmphasisAge(task,7000),0);
 task.notification.token='newer';assert.equal(h.updateEmphasisAge(task,8000),0);
 h.state.own.set(task.id,'own');task.notification.token='own';assert.equal(h.updateEmphasisAge(task,9000),null);
});

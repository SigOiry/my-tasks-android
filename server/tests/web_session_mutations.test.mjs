import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';
import {ConversationStore,pendingRequest} from '../web/conversations.mjs';
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const task={id:'private',title:'Private title',description:'Private description',notes:'Private notes',project:'Secret',status:'todo',priority:'medium',revision:1,deleted:false,notification:{unread:false,token:'own'}};
const response=(data,status=200)=>({ok:status===200,status,json:async()=>data});
async function harness(){
 const elements=new Map(),calls=[];
 const element=()=>({value:'',textContent:'',hidden:true,disabled:false,open:false,dataset:{},children:[],listeners:{},addEventListener(k,fn){this.listeners[k]=fn;},setAttribute(){},replaceChildren(...v){this.textContent='';this.children=v;},append(...v){this.children.push(...v);},focus(){},reset(){},close(){this.open=false;},showModal(){this.open=true;}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 const keys=['title','description','notes','project','due_date','status','priority'];
 get('task-form').elements={namedItem:get};get('task-form').reset=()=>keys.forEach(k=>{get(k).value='';});
 const context=vm.createContext({...policy,ConversationStore,pendingRequest,AbortController,TextEncoder,URLSearchParams,setTimeout:()=>0,clearTimeout,clearInterval,
 FormData:class{entries(){return keys.map(k=>[k,get(k).value]);}},
 localStorage:{getItem(){return null;}},navigator:{},window:{addEventListener(){},confirm:()=>true,SpeechRecognition:function(){}},
 document:{getElementById:get,documentElement:{dataset:{}},querySelectorAll(selector){return selector==='dialog[open]'?[...elements].filter(([id,e])=>id.endsWith('dialog')&&e.open).map(([,e])=>e):selector==='input,textarea'?keys.map(get):[];},createElement:element,createTextNode:text=>({textContent:text}),addEventListener(){}},
 fetch:async()=>response({authenticated:false})});
 vm.runInContext(readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/gm,'')+'\nglobalThis.app={state,chats,clearPrivate,showTask,saveTask,quickComplete,deleteTask,restoreTask,showUpdates,acknowledge,backgroundRefresh,refreshTasks,mutate};',context);
 await tick();const app=context.app;
 Object.assign(app.state,{authenticated:true,online:true,cursor:0,data:{tasks:[task],today:'2026-11-09',week_end:'2026-11-15'}});
 get('sort').value='due';app.showTask(task);get('app').hidden=false;get('login').hidden=true;
 context.fetch=async(path,options)=>{calls.push({path,options});return response(path.startsWith('/api/events')?{events:[],cursor:0,has_more:false}:task);};
 return {...app,context,get,calls};
}
function scrubbed(h){
 assert.equal(h.get('task-dialog').open,false,'private task dialog stays closed');
 for(const key of ['title','description','notes','project'])assert.equal(h.get(key).value,'',key+' stays scrubbed');
 assert.equal(h.get('detail-heading').textContent,'');assert.equal(h.get('detail-error').textContent,'');assert.equal(h.get('toast').textContent,'');
 assert.equal(h.state.selected,null);assert.equal(h.state.data,null);assert.equal(h.state.own.size,0);assert.equal(h.chats.entries.size,0);assert.equal(h.get('login').hidden,false);
}
const workflows={save:h=>h.saveTask({preventDefault(){}}),quick:h=>h.quickComplete(task,h.get('check')),delete:h=>h.deleteTask(),restore:h=>h.restoreTask()};
for(const scheduling of ['success-before-refresh-401','direct-401'])test(`production acknowledge: ${scheduling} JSON continuation cannot revive private UI`,async()=>{
 const h=await harness();h.showUpdates({...task,notification:{unread:true,token:'own'}});
 let resolveAck,resolveExpiry;
 h.context.fetch=async path=>path.endsWith('/updates/read')?{ok:scheduling!=='direct-401',status:scheduling==='direct-401'?401:200,json:()=>new Promise(resolve=>{resolveAck=resolve;})}:{ok:false,status:401,json:()=>new Promise(resolve=>{resolveExpiry=resolve;})};
 const ack=h.acknowledge();const refresh=scheduling==='success-before-refresh-401'?h.refreshTasks().catch(()=>{}):Promise.resolve();
 await tick();assert.equal(typeof resolveAck,'function');
 resolveAck(scheduling==='direct-401'?{detail:'Private acknowledgement error'}:task);
 if(scheduling==='success-before-refresh-401'){assert.equal(typeof resolveExpiry,'function');resolveExpiry({detail:'Private refresh error'});}
 await Promise.all([ack,refresh]);scrubbed(h);
 assert.equal(h.state.authenticated,false);assert.equal(h.get('updates-dialog').open,false);
 assert.equal(h.get('updates-error').textContent,'');assert.equal(h.get('updates-refresh').hidden,true);
});
for(const status of [200,401,409])test(`production acknowledge: delayed ${status} JSON cannot overwrite a replacement session gate`,async()=>{
 const h=await harness();h.showUpdates({...task,notification:{unread:true,token:'own'}});
 let release;h.context.fetch=async()=>({ok:status===200,status,json:()=>new Promise(resolve=>{release=resolve;})});
 const ack=h.acknowledge();await tick();h.clearPrivate();h.state.authenticated=true;
 const replacement={...task,id:'replacement',title:'Replacement title',notification:{unread:true,token:'replacement-token',summary:['Replacement summary']}};
 h.showUpdates(replacement);const gate=h.state.gate;h.get('updates-error').textContent='Replacement error';
 release(status===200?task:{detail:'Private old-session error'});await ack;
 assert.equal(h.state.gate,gate);assert.equal(h.get('updates-dialog').open,true);assert.equal(h.get('updates-title').textContent,'Replacement title');
 assert.equal(h.get('updates-error').textContent,'Replacement error');assert.equal(h.get('updates-refresh').hidden,true);assert.equal(h.get('updates-ack').disabled,false);
 assert.equal(h.get('task-dialog').open,false);assert.equal(h.state.selected,null);assert.equal(h.state.data,null);assert.equal(h.get('title').value,'');
});
test('production acknowledge: exact displayed token, 409 fresh review, and successful editor opening',async()=>{
 const h=await harness(),displayed={...task,notification:{unread:true,token:'displayed-token',summary:['Displayed summary']}};
 h.showUpdates(displayed);let posted;
 h.context.fetch=async(path,options)=>{posted={path,body:JSON.parse(options.body)};return response({detail:'Newer update'},409);};
 await h.acknowledge();assert.equal(posted.path,'/api/tasks/private/updates/read');assert.deepEqual(posted.body,{token:'displayed-token'});
 assert.equal(h.state.gate.token,'displayed-token');assert.equal(h.get('updates-dialog').open,true);assert.equal(h.get('task-dialog').open,false);
 assert.equal(h.get('updates-refresh').hidden,false);assert.equal(h.get('updates-ack').disabled,true);assert.match(h.get('updates-error').textContent,/nothing newer was marked read/);
 const fresh={...task,notification:{unread:true,token:'fresh-token',summary:['Fresh summary']}};
 h.context.fetch=async()=>response(fresh);await h.get('updates-refresh').listeners.click();
 assert.equal(h.state.gate.token,'fresh-token');assert.equal(h.get('updates-refresh').hidden,true);assert.equal(h.get('updates-ack').disabled,false);
 const accepted={...fresh,notification:{unread:false,token:'fresh-token'}};
 h.context.fetch=async(path,options)=>{posted={path,body:JSON.parse(options.body)};return response(accepted);};
 await h.acknowledge();assert.deepEqual(posted.body,{token:'fresh-token'});assert.equal(h.state.gate,null);assert.equal(h.state.selected,accepted);
 assert.equal(h.get('updates-dialog').open,false);assert.equal(h.get('task-dialog').open,true);assert.equal(h.get('title').value,task.title);assert.equal(h.get('updates-error').textContent,'');
});
for(const [name,run] of Object.entries(workflows)){
 test(`production ${name}: successful write followed by poll 401 cannot revive private UI`,async()=>{
  const h=await harness();let polls=0;
  h.context.fetch=async(path,options)=>{h.calls.push({path,options});if(path.startsWith('/api/events'))return ++polls===2?response({detail:'Session expired'},401):response({events:[],cursor:0,has_more:false});return response(task);};
  await run(h);assert.equal(polls,2);scrubbed(h);
 });
 for(const phase of ['pre-poll','write','post-poll'])test(`production ${name}: logout during ${phase} prevents old-session effects`,async()=>{
  const h=await harness();let polls=0,release,arrived;
  const waiting=new Promise(resolve=>{arrived=resolve;});
  h.context.fetch=async(path,options)=>{h.calls.push({path,options});const poll=path.startsWith('/api/events');if(poll)polls++;const data=poll?{events:[],cursor:0,has_more:false}:task;
   if((phase==='pre-poll'&&poll&&polls===1)||(phase==='write'&&!poll)||(phase==='post-poll'&&poll&&polls===2)){arrived();return new Promise(resolve=>{release=()=>resolve(response(data));});}return response(data);};
  const running=run(h);await waiting;h.clearPrivate();release();await running;scrubbed(h);
  if(phase==='pre-poll')assert.equal(h.calls.length,1,'no write after logout');
 });
}
for(const id of ['reload-task','updates-refresh'])test(`production ${id} ignores ended-session refresh errors`,async()=>{
 const h=await harness();h.state.gate={id:task.id};h.context.fetch=async()=>response({detail:'Session expired'},401);
 await h.get(id).listeners.click();scrubbed(h);assert.equal(h.get('updates-error').textContent,'');
});
for(const [name,run] of Object.entries(workflows))test(`production ${name} ignores logout between mutation completion and caller continuation`,async()=>{
 const h=await harness();vm.runInContext('const originalMutate=mutate;mutate=async(...args)=>{const result=await originalMutate(...args);clearPrivate();return result;};',h.context);
 await run(h);scrubbed(h);
});
test('production unread quick-complete does not toast after review fetch expires the session',async()=>{
 const h=await harness();h.context.fetch=async()=>response({detail:'Session expired'},401);
 await h.quickComplete({...task,notification:{unread:true}},h.get('check'));scrubbed(h);
});
test('production save retains revision conflict gating and unsaved notes',async()=>{
 const h=await harness();h.context.fetch=async path=>path.startsWith('/api/events')?response({events:[],cursor:0,has_more:false}):response({detail:'Revision changed'},409);
 await workflows.save(h);assert.equal(h.get('conflict').hidden,false);assert.equal(h.get('save-task').disabled,true);assert.equal(h.get('notes').value,task.notes);assert.equal(h.get('task-dialog').open,true);
});
test('production save still succeeds after a live-session post-write transport failure',async()=>{
 const h=await harness();let polls=0;h.context.fetch=async path=>{if(path.startsWith('/api/events')){if(++polls===2)throw new TypeError('offline');return response({events:[],cursor:0,has_more:false});}return response(task);};
 await workflows.save(h);assert.equal(h.state.authenticated,true);assert.equal(h.get('task-dialog').open,true);assert.equal(h.get('notes').value,task.notes);assert.equal(h.get('toast').textContent,'Changes saved');
});
test('an old-session poll cannot clear a replacement session polling/mutation lock',async()=>{
 const h=await harness();let release,arrived;const waiting=new Promise(resolve=>{arrived=resolve;});
 h.context.fetch=()=>{arrived();return new Promise(resolve=>{release=()=>resolve(response({events:[],cursor:0,has_more:false}));});};
 const saving=workflows.save(h);await waiting;h.clearPrivate();h.state.authenticated=true;h.state.polling=true;h.state.mutating=true;release();await saving;
 assert.equal(h.state.polling,true);assert.equal(h.state.mutating,true);assert.equal(h.state.data,null);assert.equal(h.get('task-dialog').open,false);assert.equal(h.get('toast').textContent,'');
});
test('session cleanup removes an already displayed old-session toast',async()=>{
 const h=await harness();h.get('toast').textContent='Private previous error';h.get('toast').hidden=false;h.clearPrivate();
 scrubbed(h);assert.equal(h.get('toast').hidden,true);
});
test('production background refresh stops after session ends in event poll',async()=>{
 const h=await harness();h.context.fetch=async(path,options)=>{h.calls.push({path,options});return response({detail:'Session expired'},401);};
 await h.backgroundRefresh();assert.equal(h.calls.length,1,'no task refresh after expired event poll');scrubbed(h);
});
test('production task refresh drops a response after logout and a replacement session',async()=>{
 const h=await harness();let release;h.context.fetch=()=>new Promise(resolve=>{release=()=>resolve(response({tasks:[task],today:'2026-11-09'}));});
 const loading=h.refreshTasks();h.clearPrivate();h.state.authenticated=true;release();await loading.catch(()=>{});assert.equal(h.state.data,null);assert.equal(h.get('task-dialog').open,false);
});

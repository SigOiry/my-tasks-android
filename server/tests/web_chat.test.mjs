import test from 'node:test';
import assert from 'node:assert/strict';
import {ConversationStore} from '../web/conversations.mjs';
const conv=(id='a',revision=1,latest_request=null)=>({id,title:'Discussion '+id,task_id:null,task_title:null,revision,archived:false,latest_request});
const detail=(id='a',revision=1,messages=[],latest_request=null)=>({conversation:conv(id,revision,latest_request),messages,has_more:false,before_cursor:null,context_compacted:false});
const pageServer=()=>{
 let total=40;const calls=[];
 const messages=Array.from({length:220},(_,i)=>({id:i+1,role:i%2?'assistant':'user',content:'Server original '+(i+1)}));
 const store=new ConversationStore(async path=>{
  const url=new URL(path,'https://fixture.invalid'),before=url.searchParams.get('before');calls.push(before);
  const end=before?Number(before.slice(7))-1:total,start=Math.max(0,end-40);
  return {...detail('a',total,messages.slice(start,end)),has_more:start>0,before_cursor:start>0?'cursor-'+(start+1):null};
 });
 return {store,messages,calls,grow:n=>{total=n;}};
};
test('disconnected latest page retains a reachable gap cursor and loads all 100 original messages',async()=>{
 const h=pageServer(),e=await h.store.load('a');assert.equal(e.has_more,false);
 h.grow(100);await h.store.load('a');assert.equal(e.has_more,true);assert.equal(e.before_cursor,'cursor-61');
 await h.store.load('a',{older:true});assert.equal(e.has_more,true);assert.equal(e.before_cursor,'cursor-21');
 await h.store.load('a',{older:true});assert.deepEqual(e.messages,h.messages.slice(0,100));assert.equal(e.has_more,false);assert.equal(e.before_cursor,null);
});

test('multiple disconnected latest windows survive repeated refreshes until every gap is paged',async()=>{
 const h=pageServer(),e=await h.store.load('a');
 h.grow(100);await h.store.load('a');await h.store.load('a');assert.equal(e.before_cursor,'cursor-61');
 h.grow(160);await h.store.load('a');assert.equal(e.before_cursor,'cursor-121');
 await h.store.load('a',{older:true});assert.equal(e.before_cursor,'cursor-81');
 await h.store.load('a');assert.equal(e.before_cursor,'cursor-81','overlapping latest refresh cannot lose a pending gap');
 let pages=1;while(e.has_more&&pages<10){await h.store.load('a',{older:true});pages++;}
 assert.equal(pages,3);assert.deepEqual(e.messages,h.messages.slice(0,160));assert.equal(e.has_more,false);assert.equal(e.before_cursor,null);
 await h.store.load('a');assert.equal(e.has_more,false,'completed history stays completed on overlapping refresh');
});
test('an admitted user-message delta cannot disguise a disconnected latest page',async()=>{
 const h=pageServer(),e=await h.store.load('a'),request=h.store.request;
 h.store.uuid=()=> 'gap-request';h.store.request=async(path,options)=>options?{conversation:conv('a',100),request:{id:'gap-request',status:'succeeded'},user_message:h.messages[99]}:request(path);
 h.store.setDraft('a',h.messages[99].content);await h.store.send('a');
 h.grow(100);await h.store.load('a');assert.equal(e.has_more,true);assert.equal(e.before_cursor,'cursor-61');
 while(e.has_more)await h.store.load('a',{older:true});assert.deepEqual(e.messages,h.messages.slice(0,100));
});
test('a connected latest window preserves exhausted history and original server content',async()=>{
 const h=pageServer(),e=await h.store.load('a');h.grow(45);await h.store.load('a');
 assert.deepEqual(e.messages,h.messages.slice(0,45));assert.equal(e.has_more,false);assert.equal(e.before_cursor,null);
});

const user={id:1,request_id:'request-1',role:'user',content:'Original question'};
const accepted={conversation:conv('a',2,{id:'request-1',status:'queued'}),request:{id:'request-1',status:'queued'},user_message:user};
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};

test('terminal poll during an older-page fetch still refreshes latest persisted answer',async()=>{
 const old=deferred();let done=false;
 const answer={id:2,request_id:'request-1',role:'assistant',content:'Answer'};
 const store=new ConversationStore(async path=>{if(path.includes('before='))return old.promise;if(path.includes('/requests/')){done=true;return {id:'request-1',status:'succeeded'};}return {...detail('a',done?3:2,done?[user,answer]:[user],{id:'request-1',status:done?'succeeded':'running'}),has_more:true,before_cursor:'opaque'};});
 await store.load('a');const older=store.load('a',{older:true}),poll=store.poll('a');old.resolve(detail('a',2,[],{id:'request-1',status:'running'}));await older;await poll;
 assert.deepEqual(store.entry('a').messages.map(m=>m.id),[1,2]);
});

test('durable admission preserves a newly typed follow-up and never sends client history',async()=>{
 const pending=deferred(),calls=[];
 const store=new ConversationStore(async(path,options)=>{calls.push({path,options});return options?pending.promise:detail();},()=> 'request-1');
 await store.load('a');store.setDraft('a','Original question');
 const sending=store.send('a');store.setDraft('a','New follow-up');
 assert.equal(store.entry('a').admission.payload.request_id,'request-1');
 assert.equal(calls[1].path,'/api/conversations/a/messages');
 assert.deepEqual(calls[1].options.body,{request_id:'request-1',content:'Original question',mode:'question',expected_revision:1});
 pending.resolve(accepted);await sending;
 assert.equal(store.entry('a').draft,'New follow-up');assert.equal(store.entry('a').messages.length,1);
 assert.equal(store.entry('a').conversation.latest_request.status,'queued');
});
test('network uncertainty retains exact identity; explicit retry is idempotent',async()=>{
 const calls=[];let fail=true;
 const store=new ConversationStore(async(path,options)=>{calls.push({path,options});if(!options)return detail();if(fail)throw new Error('Offline');return accepted;},()=> 'request-1');
 await store.load('a');store.setDraft('a','Original question');
 await assert.rejects(store.send('a'),/Offline/);await assert.rejects(store.send('a'),/uncertain/);assert.equal(calls.length,2);
 store.setDraft('a','New follow-up');fail=false;await store.send('a',{repeat:true});
 assert.deepEqual(calls[1].options.body,calls[2].options.body);assert.equal(store.entry('a').draft,'New follow-up');
});
test('reload recovers running request and terminal poll merges without duplicate turns',async()=>{
 const running={id:'request-1',status:'running'},answer={id:2,request_id:'request-1',role:'assistant',content:'Answer'};let complete=false;
 const store=new ConversationStore(async path=>{if(path.includes('/requests/')){complete=true;return {id:'request-1',status:'succeeded',assistant_message:answer};}return detail('a',complete?3:2,complete?[user,answer]:[user],complete?{id:'request-1',status:'succeeded'}:running);});
 await store.load('a');assert.equal(store.entry('a').conversation.latest_request.status,'running');await store.poll('a');await store.load('a');
 assert.deepEqual(store.entry('a').messages.map(m=>m.id),[1,2]);assert.equal(store.entry('a').conversation.latest_request.status,'succeeded');
});
test('late acceptance after logout cannot recreate private entries',async()=>{
 const pending=deferred();const store=new ConversationStore(async(path,o)=>o?pending.promise:detail(),()=> 'request-1');
 await store.load('a');store.setDraft('a','Original question');const sending=store.send('a');store.clear();pending.resolve(accepted);await sending;
 assert.equal(store.entries.size,0);
});
test('switching threads isolates replies and preserves edited-away-and-back draft',async()=>{
 const pending=deferred();const store=new ConversationStore(async(path,o)=>o?pending.promise:detail(path.includes('/b?')?'b':'a'),()=> 'request-1');
 await store.load('a');await store.load('b');store.setDraft('a','Original question');const sending=store.send('a');
 store.setDraft('a','Changed');store.setDraft('a','Original question');store.setDraft('b','Other thread');pending.resolve(accepted);await sending;
 assert.equal(store.entry('a').draft,'Original question');assert.equal(store.entry('b').draft,'Other thread');assert.equal(store.entry('b').messages.length,0);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';
import {ConversationStore,pendingRequest} from '../web/conversations.mjs';

function harness(){
 const elements=new Map(),calls=[];
 const element=(tag='div')=>({tagName:String(tag).toUpperCase(),value:'',textContent:'',hidden:true,disabled:false,open:false,dataset:{},children:[],listeners:{},scrollHeight:100,scrollTop:0,clientHeight:100,
  addEventListener(name,fn){this.listeners[name]=fn;},setAttribute(){},replaceChildren(...children){this.children=children;},append(...children){this.children.push(...children);},focus(){},reset(){this.value='';},close(){this.open=false;},showModal(){this.open=true;}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 let resolve;const pending=new Promise(yes=>{resolve=yes;});
 const context=vm.createContext({...policy,ConversationStore,pendingRequest,AbortController,setTimeout,clearTimeout,clearInterval,TextEncoder,URLSearchParams,
 localStorage:{getItem(){return null;}},navigator:{},document:{getElementById:get,documentElement:{dataset:{}},querySelectorAll(){return [];},addEventListener(){},createElement:element,createTextNode:text=>({nodeType:3,textContent:text,children:[]})},window:{addEventListener(){},SpeechRecognition:function(){}},
 fetch(path,options){if(path==='/auth/session')return Promise.resolve({ok:true,json:async()=>({authenticated:false})});calls.push({path,options});return pending;}});
 const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/gm,'');
 vm.runInContext(source+'\nglobalThis.app={state,chats,discussion,sendChat,renderChat,clearPrivate};',context);
 const app=context.app;app.state.authenticated=true;
 for(const id of ['a','b']) {const e=app.chats.entry(id);e.loaded=true;e.conversation={id,title:'Title '+id,revision:1,archived:false,task_id:null,latest_request:null};}
 app.discussion.active='a';app.renderChat(true);
 const input=text=>{get('chat-message').value=text;get('chat-message').listeners.input();};
 const succeed=()=>{const payload=JSON.parse(calls[0].options.body);resolve({ok:true,json:async()=>({conversation:{id:'a',title:'Title a',revision:2,task_id:null,archived:false},request:{id:payload.request_id,status:'queued'},user_message:{id:1,request_id:payload.request_id,role:'user',content:payload.content}})});};
 return {...app,get,input,calls,succeed};
}
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

import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';

// Execute production app, request, render, sendChat and registered input handler.
// Only browser DOM/transport are doubles; no copied chat implementation.
function harness() {
 const elements=new Map(),calls=[];
 const element=()=>({value:'',textContent:'',hidden:true,disabled:false,dataset:{},children:[],listeners:{},
  addEventListener(name,fn){this.listeners[name]=fn;},setAttribute(){},replaceChildren(...children){this.children=children;},
  append(...children){this.children.push(...children);},focus(){}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 let resolve,reject;
 const pending=new Promise((yes,no)=>{resolve=yes;reject=no;});
 const context=vm.createContext({...policy,AbortController,setTimeout,clearTimeout,clearInterval,TextEncoder,
  localStorage:{getItem(){return null;}},navigator:{},
  document:{getElementById:get,documentElement:{dataset:{}},querySelectorAll(){return [];},addEventListener(){},createElement:element},
  window:{addEventListener(){},SpeechRecognition:function(){}},
  fetch(path,options){if(path==='/auth/session')return Promise.resolve({ok:true,json:async()=>({authenticated:false})});calls.push({path,options});return pending;}});
 const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/,'');
 vm.runInContext(source+'\nglobalThis.app={state,conversation,sendChat};',context);
 const {state,conversation,sendChat}=context.app;
 state.authenticated=true;state.selected={id:'task-1'};
 const chat=conversation();
 const input=text=>{get('chat-message').value=text;get('chat-message').listeners.input();};
 input('Original question');get('chat-mode').value='question';
 return {state,chat,input,get,calls,send:()=>sendChat({preventDefault(){}}),
  succeed:()=>resolve({ok:true,json:async()=>({reply:'Original answer'})}),
  fail:()=>resolve({ok:false,status:503,json:async()=>({detail:'Provider unavailable'})}),
  interrupt:()=>reject(Object.assign(new Error('aborted'),{name:'AbortError'}))};
}

for (const outcome of ['succeed','fail','interrupt']) test(`pending chat preserves new follow-up on ${outcome}, without autosending`,async()=>{
 const h=harness(),sending=h.send();
 assert.equal(h.calls.length,1);
 assert.equal(JSON.parse(h.calls[0].options.body).message,'Original question');
 // A second submit during pending work must not start another provider request.
 h.input('New follow-up');await h.send();
 h[outcome]();await sending;
 assert.equal(h.chat.draft,'New follow-up');
 assert.equal(h.get('chat-message').value,'New follow-up');
 assert.equal(h.calls.length,1);
 assert.equal(h.state.chatBusy,false);
 assert.deepEqual(JSON.parse(JSON.stringify(h.chat.turns)),outcome==='succeed'?
  [{role:'user',content:'Original question'},{role:'assistant',content:'Original answer'}]:[]);
 if(outcome!=='succeed')assert.match(h.get('chat-error').textContent,/Nothing was automatically resent/);
});

test('a draft edited away and back while pending still belongs to the user',async()=>{
 const h=harness(),sending=h.send();h.input('Changed');h.input('Original question');
 h.succeed();await sending;
 assert.equal(h.chat.draft,'Original question');
 assert.equal(h.get('chat-message').value,'Original question');
 assert.equal(h.chat.turns.length,2);assert.equal(h.calls.length,1);
});

test('an untouched submitted draft clears only on success and remains retryable on failure',async()=>{
 for(const outcome of ['succeed','fail','interrupt']) {
  const h=harness(),sending=h.send();h[outcome]();await sending;
  assert.equal(h.chat.draft,outcome==='succeed'?'':'Original question');
  assert.equal(h.get('chat-message').value,h.chat.draft);assert.equal(h.calls.length,1);
 }
});

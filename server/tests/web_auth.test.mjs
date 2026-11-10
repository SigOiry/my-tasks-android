import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';
import {ConversationStore,pendingRequest} from '../web/conversations.mjs';
const tick=()=>new Promise(r=>setImmediate(r));
function harness(session={authenticated:false},hash='') {
 const elements=new Map(),calls=[],replaced=[];
 const element=()=>({options:[],style:{setProperty(){}},value:'',textContent:'',hidden:true,disabled:false,open:false,dataset:{},children:[],listeners:{},addEventListener(k,v){this.listeners[k]=v;},setAttribute(){},replaceChildren(...v){this.children=v;},append(...v){this.children.push(...v);},focus(){},reset(){},close(){this.open=false;},showModal(){this.open=true;},querySelector(){return element();}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 const context=vm.createContext({...policy,ConversationStore,pendingRequest,TextEncoder,URLSearchParams,AbortController,setTimeout,clearTimeout,clearInterval,
 location:{hash,pathname:'/',search:''},history:{replaceState(...v){replaced.push(v);}},localStorage:{getItem(){return null;}},navigator:{},document:{querySelector(selector){return selector==='.filters'?get('filters'):null;},getElementById:get,documentElement:{dataset:{}},querySelectorAll(){return [];},addEventListener(){},createElement:element},window:{addEventListener(){},SpeechRecognition:function(){}},
 fetch:async(path,options)=>{calls.push({path,options});if(path==='/auth/password')return {ok:false,status:401,json:async()=>({detail:'Invalid current password'})};return {ok:true,json:async()=>path==='/auth/activate'?{authenticated:true,csrf:'fixture-csrf',password_change_required:true}:session};}});
 vm.runInContext(readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/gm,'')+'\nglobalThis.app={beginSession,state,request,clearPrivate};',context);
 return {context,get,calls,replaced};
}
test('restricted owner session gates all private APIs and shows mandatory password pane',async()=>{
 const h=harness({authenticated:true,password_change_required:true,csrf:'fixture-csrf'});await tick();
 assert.equal(h.get('password-pane').hidden,false);assert.equal(h.get('app').hidden,true);assert.deepEqual(h.calls.map(x=>x.path),['/auth/session']);
 await assert.rejects(h.context.app.request('/api/tasks'),/password/i);assert.deepEqual(h.calls.map(x=>x.path),['/auth/session']);
});
test('activation fragment is removed synchronously before network and exchanged only once',async()=>{
 const code='a'.repeat(64),h=harness({authenticated:false},'#activate='+code);
 assert.equal(h.replaced.length,1);assert.equal(h.replaced[0][2],'/');await tick();
 assert.equal(h.calls[0].path,'/auth/activate');assert.deepEqual(JSON.parse(h.calls[0].options.body),{activation_code:code});assert.equal(h.get('password-pane').hidden,false);
});
test('fresh login posts exact username/password and never token or trimmed secret',async()=>{
 const h=harness();await tick();h.get('username').value='oiry-s';h.get('password').value='  fixture password  ';
 await h.get('login-form').listeners.submit({preventDefault(){},currentTarget:h.get('login-form')});
 const call=h.calls.find(x=>x.path==='/auth/login');assert.deepEqual(JSON.parse(call.options.body),{username:'oiry-s',password:'  fixture password  '});assert.equal(h.get('password').value,'');
});
test('public bootstrap login explains activation_required without opening the workspace',async()=>{
 const h=harness();await tick();h.context.fetch=async()=>({ok:false,status:428,json:async()=>({detail:'activation_required'})});
 h.get('username').value='oiry-s';h.get('password').value='fixture only';await h.get('login-form').listeners.submit({preventDefault(){},currentTarget:h.get('login-form')});
 assert.match(h.get('login-error').textContent,/Activation required/);assert.equal(h.get('app').hidden,true);assert.equal(h.context.app.state.authenticated,false);
});
test('non-JSON server failures use a readable recovery message without response fragments',async()=>{
 const h=harness();await tick();h.context.fetch=async()=>({ok:false,status:502,json:async()=>{throw new SyntaxError('private proxy HTML fragment');}});
 await assert.rejects(h.context.app.request('/api/tasks'),error=>/server.*response/i.test(error.message)&&!error.message.includes('private proxy'));
});
test('logout invalidates a full-session startup paused during async cursor hashing',async()=>{
 const h=harness();await tick();let finish;h.context.crypto={subtle:{digest:()=>new Promise(r=>{finish=r;})}};
 const starting=h.context.app.beginSession({authenticated:true,csrf:'fixture-csrf',password_change_required:false});h.context.app.clearPrivate();finish(new Uint8Array(32));await starting.catch(()=>{});
 assert.deepEqual(h.calls.map(c=>c.path),['/auth/session']);assert.equal(h.context.app.state.cursorKey,null);assert.equal(h.context.app.state.authenticated,false);
});
test('new password codepoint/byte/confirmation validation preserves exact spaces',()=>{
 assert.equal(policy.passwordError('🦊'.repeat(15),'🦊'.repeat(15)),'');assert.match(policy.passwordError('short','short'),/15/);assert.match(policy.passwordError('a'.repeat(15),'different'),/match/);assert.match(policy.passwordError('a'.repeat(129),'a'.repeat(129)),/128/);assert.equal(policy.passwordError('  long passphrase  ','  long passphrase  '),'');
});

test('incorrect current password preserves the restricted owner pane instead of logging out a valid session',async()=>{
 const h=harness({authenticated:true,password_change_required:true,csrf:'fixture-csrf'});await tick();
 h.get('current-password').value='incorrect fixture';h.get('new-password').value='a different long phrase';h.get('confirm-password').value='a different long phrase';
 await h.get('password-form').listeners.submit({preventDefault(){}});
 assert.equal(h.get('password-pane').hidden,false);assert.equal(h.context.app.state.authenticated,true);assert.match(h.get('password-error').textContent,/Invalid current password/);
 assert.equal(h.get('new-password').value,'');
});

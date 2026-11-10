import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';
import {ConversationStore,pendingRequest} from '../web/conversations.mjs';
import {renderCitations} from '../web/citations.mjs';
function harness(){
 const elements=new Map(),opens=[],copies=[],calls=[];
 const element=(tag='div')=>({tagName:tag.toUpperCase(),options:[],style:{setProperty(){}},value:'',textContent:'',hidden:true,disabled:false,open:false,dataset:{},children:[],listeners:{},scrollHeight:100,scrollTop:0,clientHeight:100,
 addEventListener(k,f){this.listeners[k]=f;},setAttribute(k,v){if(k.startsWith('data-'))this.dataset[k.slice(5).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=String(v);},replaceChildren(...c){this.children=c;},append(...c){this.children.push(...c);},insertBefore(c,b){const old=this.children.indexOf(c);if(old>=0)this.children.splice(old,1);this.children.splice(b?this.children.indexOf(b):this.children.length,0,c);},removeChild(c){this.children.splice(this.children.indexOf(c),1);},focus(){},reset(){},close(){this.open=false;},showModal(){this.open=true;}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 const document={querySelector:s=>s==='.filters'?get('filters'):null,getElementById:get,documentElement:{dataset:{},style:{setProperty(){}}},querySelectorAll(){return [];},addEventListener(){},createElement:element,createTextNode:text=>({nodeType:3,textContent:text,children:[]})};
 const context=vm.createContext({...policy,ConversationStore,pendingRequest,mountEmailWorkspace:()=>({open:(...args)=>opens.push(args),clear(){},store:{dirty:false}}),renderCitations:(turn,options)=>renderCitations(turn,{...options,document}),AbortController,TextEncoder,URLSearchParams,setTimeout:()=>0,clearTimeout(){},clearInterval(){},localStorage:{getItem(){return null;}},navigator:{clipboard:{writeText:async text=>copies.push(text)}},document,window:{addEventListener(){},SpeechRecognition:function(){}},fetch:async path=>{calls.push(path);return {ok:true,json:async()=>({authenticated:false})};}});
 vm.runInContext(readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/gm,'')+'\nglobalThis.app={state,chats,discussion,renderChat};',context);
 const app=context.app;app.state.authenticated=true;const e=app.chats.entry('a');e.loaded=true;e.conversation={id:'a',title:'Initial',revision:1,archived:false,task_id:null};app.discussion.active='a';return {...app,get,opens,copies,calls};
}
test('production update Reply passes the displayed exact token without acknowledgement or closing update review',()=>{
 const h=harness();h.state.gate={id:'task',token:'exact/displayed'};h.get('updates-dialog').open=true;h.get('updates-reply').listeners.click();assert.deepEqual(h.opens,[['task','exact/displayed']]);assert.equal(h.get('updates-dialog').open,true);assert.equal(h.state.gate.token,'exact/displayed');assert.ok(!h.calls.some(p=>p.includes('/updates/read')));h.state.selected={id:'detail'};h.get('detail-reply').listeners.click();assert.deepEqual(h.opens[1],['detail']);
});
test('actual message renderer retains original Copy and refreshes changed additive citation metadata',async()=>{
 const h=harness(),e=h.chats.entry('a'),original='Original **answer** citeunresolved';e.messages=[{id:1,role:'assistant',content:original}];h.renderChat();const first=h.get('chat-log').children[0];const visit=n=>[n,...(n.children||[]).flatMap(visit)];assert.ok(visit(first).some(n=>n.textContent?.includes('Source unavailable')));await first.children.find(n=>n.className==='copy-message').listeners.click();assert.deepEqual(h.copies,[original]);e.messages[0].citation_metadata={version:1,sources:[{id:'a',kind:'web',label:'Read source',url:'https://example.test',verification:'page_read'}]};h.renderChat();const updated=h.get('chat-log').children[0];assert.notEqual(first,updated);assert.ok(visit(updated).some(n=>n.textContent==='1 · Read source'));assert.ok(!visit(updated).some(n=>n.textContent?.includes('')));
});
test('actual message renderer displays bounded validated persisted warnings separately even without sources',()=>{
 const h=harness(),e=h.chats.entry('a'),content='All claims verified.',warning='Public web search failed; no sources were retrieved.';e.messages=[{id:2,role:'assistant',content,citation_metadata:{sources:[],warnings:[warning,null,{},'',42,'x'.repeat(1500),...Array(20).fill('More warnings')]}}];h.renderChat();const visit=n=>[n,...(n.children||[]).flatMap(visit)],nodes=visit(h.get('chat-log').children[0]),warnings=nodes.filter(n=>n.className==='citation-warning');assert.equal(warnings[0]?.textContent,warning);assert.equal(warnings.length,8);assert.ok(warnings.every(n=>n.textContent.length<=1000));assert.ok(nodes.some(n=>n.textContent===content));assert.equal(e.messages[0].content,content);
});
test('actual renderer retains unavailable provider marker beside unrelated legitimate saved source chips',()=>{
 const h=harness(),e=h.chats.entry('a');e.messages=[{id:3,role:'assistant',content:'Claim citeNONEXISTENT',citation_metadata:{sources:[{id:'saved',kind:'task',resource_id:'task',verification:'saved_context',label:'Saved task'}]}}];h.renderChat();const visit=n=>[n,...(n.children||[]).flatMap(visit)],nodes=visit(h.get('chat-log').children[0]);assert.ok(nodes.some(n=>n.textContent?.includes('Source unavailable')));assert.ok(nodes.some(n=>n.textContent==='1 · Saved task'));assert.ok(!nodes.some(n=>n.tagName==='A'));assert.equal(e.messages[0].content,'Claim citeNONEXISTENT');
});
test('server-generated title revision refreshes open header and task selector; stale automatic title cannot undo rename',()=>{
 const h=harness();h.get('task-thread').children=[{value:'a',textContent:'Initial'}];h.chats.metadata({id:'a',revision:2,title:'Generated topic',title_origin:'automatic',title_revision:2});h.renderChat();assert.equal(h.get('chat-title').textContent,'Generated topic');assert.equal(h.get('task-thread').children[0].textContent,'Generated topic');h.chats.metadata({id:'a',revision:3,title:'My rename',title_origin:'manual',title_revision:3});h.chats.metadata({id:'a',revision:4,title:'Late result',title_origin:'automatic',title_revision:2});h.renderChat();assert.equal(h.get('chat-title').textContent,'My rename');
});

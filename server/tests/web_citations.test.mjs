import test from 'node:test';
import assert from 'node:assert/strict';
import {citationPresentation,renderCitations} from '../web/citations.mjs';
import {ConversationStore} from '../web/conversations.mjs';
import {safeMarkdown} from '../web/policy.mjs';
test('canonical multiple sources render metadata while original text stays exact',()=>{
 const content='Answer citeturn0search0turn1search2';const turn={content,citation_metadata:{version:1,sources:[{id:'a',kind:'web',label:'One',url:'https://example.test/a_(b)',verification:'verified'},{id:'b',kind:'email',resource_id:'src_abc',task_id:'task',sender:'Person',subject:'Original',available:true}]}};const view=citationPresentation(turn);assert.equal(turn.content,content);assert.ok(!view.text.includes('turn0search0'));assert.equal(view.sources.length,2);assert.equal(view.sources[0].url,'https://example.test/a_(b)');
});
test('unresolved markers and malformed metadata become unavailable; arbitrary markdown URLs remain inert',()=>{
 for(const citation_metadata of [undefined,null,{sources:'bad'},{sources:[null,{}, {id:'x',url:'javascript:alert(1)'},{id:'malformed',url:'https://example.test',verification:{}}]}]){const p=citationPresentation({content:'Fact citebad',citation_metadata});assert.ok(p.text.includes('Source unavailable'));assert.ok(!p.text.includes(''));assert.ok(p.sources.every(s=>!s.url));}
 assert.equal(citationPresentation({content:'[Legacy](https://mail.google.com/mail/u/0/#inbox)'}).sources.length,1);
 assert.equal(citationPresentation({content:'[Untrusted](https://evil.test)'}).sources.length,0);
});
test('saved task context is a metadata source, not an invented public URL',()=>{const p=citationPresentation({content:'Based on your saved task.',citation_metadata:{sources:[{id:'task_canonical',kind:'task',resource_id:'task',task_id:'task',label:'Saved task',verification:'saved_context'}]}});assert.equal(p.sources[0].available,true);assert.equal(p.sources[0].kind,'task');assert.equal(p.sources[0].url,null);});
test('independent email source panels fetch authenticated metadata and contain malicious markup as text',async()=>{
 const make=tag=>({tagName:tag,children:[],attributes:{},listeners:{},open:false,textContent:'',append(...n){this.children.push(...n);},replaceChildren(...n){this.children=n;},setAttribute(k,v){this.attributes[k]=v;},addEventListener(k,f){this.listeners[k]=f;}});const calls=[],pending=[];const request=path=>{calls.push(path);return new Promise(resolve=>pending.push(resolve));};
 const {element}=renderCitations({content:'Fact',citation_metadata:{sources:['one','two'].map(id=>({id,kind:'email',resource_id:id,task_id:'task /id',available:true,verification:'saved_context'}))}},{document:{createElement:make},request});
 const a=element.children[0],b=element.children[1];a.open=b.open=true;const loadingA=a.listeners.toggle(),loadingB=b.listeners.toggle();pending[0]({source:{subject:'<img onerror=alert(1)>',sender:'First',excerpt:'Private first'}});pending[1]({source:{subject:'Second',sender:'Second',excerpt:'Private second'}});await loadingA;await loadingB;
 assert.equal(calls[0],'/api/tasks/task%20%2Fid/email-sources/one');assert.ok(a.children[1].children.some(e=>e.textContent==='Private first'));assert.ok(b.children[1].children.some(e=>e.textContent==='Private second'));assert.ok(a.children[1].children.every(e=>e.tagName!=='img'));
});
function sourceDOM(){
 const document={body:{tagName:'body'},activeElement:null,focusEvents:[],disabledBlurs:[],createElement(tag){return {
  tagName:tag,children:[],attributes:{},listeners:{},open:false,hidden:false,_disabled:false,textContent:'',
  get disabled(){return this._disabled;},
  set disabled(value){this._disabled=!!value;if(this._disabled&&document.activeElement===this){document.disabledBlurs.push(this);document.activeElement=document.body;}},
  append(...n){this.children.push(...n);},replaceChildren(...n){this.children=n;},setAttribute(k,v){this.attributes[k]=v;},addEventListener(k,f){this.listeners[k]=f;},
  focus(options){if(this.disabled||this.hidden)return;document.focusEvents.push({node:this,options});document.activeElement=this;}
 };}};
 document.activeElement=document.body;return document;
}
const emailTurn={content:'Exact answer citeunmapped',citation_metadata:{sources:[{id:'one',kind:'email',resource_id:'source /one',task_id:'task /id',available:true,verification:'saved_context'}]}};
test('failed citation fetch has explicit retry, busy/status semantics and no automatic reopen retry',async()=>{
 const document=sourceDOM(),calls=[];let release;const {element}=renderCitations(emailTurn,{document,request:async path=>{calls.push(path);if(calls.length===1)throw new Error('Unavailable 503');return new Promise(r=>{release=r;});}}),details=element.children[0];details.open=true;await details.listeners.toggle();const retry=details.children.find(n=>n.tagName==='button');assert.ok(retry,'failure offers Retry source');assert.equal(retry.textContent,'Retry source');assert.equal(retry.hidden,false);assert.ok(details.children.some(n=>n.attributes.role==='status'));details.open=false;await details.listeners.toggle();details.open=true;await details.listeners.toggle();assert.equal(calls.length,1);
 retry.focus();const fetching=retry.listeners.click();assert.equal(retry.disabled,true);assert.equal(details.children[1].attributes['aria-busy'],'true');await details.listeners.toggle();assert.equal(calls.length,2);release({source:{subject:'Recovered source',excerpt:'<script>inert</script>'}});await fetching;assert.equal(retry.hidden,true);assert.equal(details.children[1].attributes['aria-busy'],'false');assert.equal(document.activeElement,details.children[0]);assert.ok(details.children[1].children.some(n=>n.textContent==='<script>inert</script>'));assert.deepEqual(calls,['/api/tasks/task%20%2Fid/email-sources/source%20%2Fone','/api/tasks/task%20%2Fid/email-sources/source%20%2Fone']);
});
test('citation retry fences late session results and refuses new work after logout',async()=>{
 const document=sourceDOM();let alive=true,release,count=0;const {element}=renderCitations(emailTurn,{document,alive:()=>alive,request:async()=>{if(++count===1)throw new Error('503');return new Promise(r=>{release=r;});}}),details=element.children[0];details.open=true;await details.listeners.toggle();const retry=details.children.find(n=>n.tagName==='button');assert.ok(retry);const loading=retry.listeners.click();alive=false;release({source:{excerpt:'Late private content'}});await loading;assert.ok(!details.children[1].children.some(n=>n.textContent==='Late private content'));await retry.listeners.click();assert.equal(count,2);
});
async function failedSource(){
 const document=sourceDOM(),calls=[],pending=[];let live=true;
 const {element}=renderCitations(emailTurn,{document,alive:()=>live,request:path=>{
  calls.push(path);if(calls.length===1)return Promise.reject(new Error('Synthetic source 503'));
  return new Promise((resolve,reject)=>pending.push({resolve,reject}));
 }});
 const details=element.children[0], [summary,panel,status,retry]=details.children;
 details.open=true;await details.listeners.toggle();
 assert.equal(retry.hidden,false);assert.equal(retry.disabled,false);
 return {document,calls,pending,details,summary,panel,status,retry,logout(){live=false;}};
}
test('focused citation retry transfers focus synchronously before native disabled blur',async()=>{
 const {document,retry,summary,pending,panel,status}=await failedSource();
 retry.focus();const loading=retry.listeners.click();
 assert.equal(document.activeElement,summary,'native disable must not leave focus on BODY before the response');
 assert.equal(document.disabledBlurs.length,0,'focus must transfer before disabling Retry');
 assert.equal(retry.disabled,true);assert.equal(panel.attributes['aria-busy'],'true');
 assert.deepEqual(document.focusEvents.at(-1),{node:summary,options:{preventScroll:true}});
 const focusCount=document.focusEvents.length;
 pending[0].resolve({source:{subject:'Recovered',excerpt:'Synthetic source text'}});await loading;
 assert.equal(document.activeElement,summary);assert.equal(document.focusEvents.length,focusCount,'completion must not refocus');
 assert.equal(retry.hidden,true);assert.equal(retry.disabled,false);assert.equal(panel.attributes['aria-busy'],'false');
 assert.equal(status.textContent,'Source loaded.');assert.ok(panel.children.some(n=>n.textContent==='Synthetic source text'));
});
test('failed focused citation retry leaves focus on summary and permits deliberate retry',async()=>{
 const {document,retry,summary,pending,panel,status}=await failedSource();
 retry.focus();const loading=retry.listeners.click();assert.equal(document.activeElement,summary);
 const focusCount=document.focusEvents.length;pending[0].reject(new Error('Still unavailable 503'));await loading;
 assert.equal(document.activeElement,summary);assert.equal(document.focusEvents.length,focusCount);
 assert.equal(retry.hidden,false);assert.equal(retry.disabled,false);assert.equal(panel.attributes['aria-busy'],'false');
 assert.equal(status.textContent,'Could not load the source. Retry when ready.');
 retry.focus();const again=retry.listeners.click();assert.equal(document.activeElement,summary);
 pending[1].resolve({source:{subject:'Recovered on next attempt'}});await again;assert.equal(retry.hidden,true);
});
for(const outcome of ['success','failure'])for(const moved of ['before','during']){
 test(`citation retry preserves external focus moved ${moved} request on ${outcome}`,async()=>{
  const {document,retry,summary,pending}=await failedSource();const external=document.createElement('textarea');
  if(moved==='before')external.focus();else retry.focus();
  const beforeFocus=document.focusEvents.length,loading=retry.listeners.click();
  assert.equal(document.activeElement,moved==='before'?external:summary);
  if(moved==='before')assert.equal(document.focusEvents.length,beforeFocus,'unfocused Retry must not transfer focus');
  else external.focus();
  const focusCount=document.focusEvents.length;
  if(outcome==='success')pending[0].resolve({source:{subject:'Recovered'}});else pending[0].reject(new Error('Synthetic 503'));
  await loading;assert.equal(document.activeElement,external);assert.equal(document.focusEvents.length,focusCount,'async settlement must not steal focus');
  assert.equal(retry.hidden,outcome==='success');assert.equal(retry.disabled,false);
 });
}
for(const outcome of ['success','failure']){
 test(`citation retry ignores late ${outcome} after logout without moving focus`,async()=>{
  const {document,retry,summary,pending,panel,status,calls,logout}=await failedSource();
  retry.focus();const loading=retry.listeners.click();assert.equal(document.activeElement,summary);
  logout();const external=document.createElement('input');external.focus();
  const focusCount=document.focusEvents.length,children=[...panel.children],message=status.textContent;
  if(outcome==='success')pending[0].resolve({source:{excerpt:'Late private content'}});else pending[0].reject(new Error('Late private error'));
  await loading;assert.equal(document.activeElement,external);assert.equal(document.focusEvents.length,focusCount);
  assert.deepEqual(panel.children,children);assert.equal(status.textContent,message);assert.equal(retry.hidden,false);assert.equal(retry.disabled,true);
  await retry.listeners.click();assert.equal(calls.length,2);assert.equal(document.focusEvents.length,focusCount);
 });
}
test('logged-out focused citation retry starts no request and does not transfer focus',async()=>{
 const {document,retry,calls,logout}=await failedSource();retry.focus();logout();const focusCount=document.focusEvents.length;
 await retry.listeners.click();assert.equal(document.activeElement,retry);assert.equal(document.focusEvents.length,focusCount);
 assert.equal(retry.disabled,false);assert.equal(calls.length,1);
});
test('duplicate citation retry keeps one request owner and does not reclaim focus',async()=>{
 const {document,retry,summary,pending,details,calls}=await failedSource();
 retry.focus();const loading=retry.listeners.click();assert.equal(document.activeElement,summary);
 const external=document.createElement('textarea');external.focus();const focusCount=document.focusEvents.length;
 await retry.listeners.click();details.open=false;await details.listeners.toggle();details.open=true;await details.listeners.toggle();
 assert.equal(calls.length,2);assert.equal(pending.length,1);assert.equal(document.activeElement,external);assert.equal(document.focusEvents.length,focusCount);
 pending[0].resolve({source:{subject:'Only owner'}});await loading;
 await retry.listeners.click();await details.listeners.toggle();assert.equal(calls.length,2);assert.equal(document.activeElement,external);assert.equal(document.focusEvents.length,focusCount);
});
test('title metadata revisions merge independently and manual ownership wins late auto titles',()=>{
 const s=new ConversationStore(()=>{});s.metadata({id:'a',revision:5,title:'Generated',title_revision:2,title_origin:'auto'});s.metadata({id:'a',revision:5,title:'Renamed',title_revision:3,title_origin:'manual'});s.metadata({id:'a',revision:6,title:'Late auto',title_revision:2,title_origin:'auto'});assert.equal(s.entry('a').conversation.title,'Renamed');assert.equal(s.entry('a').conversation.revision,6);s.metadata({id:'a',revision:4,title:'Old',title_revision:1});assert.equal(s.entry('a').conversation.title,'Renamed');
});
test('restricted nested inline emphasis remains a nested AST rather than stray delimiter text',()=>{const first=safeMarkdown('**Bold *and quiet***')[0].children[0];assert.equal(first.tag,'strong');assert.equal(first.children[1].tag,'em');assert.equal(first.children[1].children[0],'and quiet');const second=safeMarkdown('*Quiet **and bold** together*')[0].children[0];assert.equal(second.tag,'em');assert.equal(second.children[1].tag,'strong');assert.equal(second.children[1].children[0],'and bold');});
test('restricted markdown preserves ordered start and nested formatted items without attributes from input',()=>{
 const ast=safeMarkdown('4. **First**\n   - *Nested*\n5. Next');assert.equal(ast[0].tag,'ol');assert.equal(ast[0].start,4);assert.equal(ast[0].children[0].children[0].tag,'strong');assert.equal(ast[0].children[0].children[1].tag,'ul');assert.equal(ast[0].children[0].children[1].children[0].children[0].tag,'em');assert.equal(ast[0].children.length,2);
});

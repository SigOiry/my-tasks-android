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
test('title metadata revisions merge independently and manual ownership wins late auto titles',()=>{
 const s=new ConversationStore(()=>{});s.metadata({id:'a',revision:5,title:'Generated',title_revision:2,title_origin:'auto'});s.metadata({id:'a',revision:5,title:'Renamed',title_revision:3,title_origin:'manual'});s.metadata({id:'a',revision:6,title:'Late auto',title_revision:2,title_origin:'auto'});assert.equal(s.entry('a').conversation.title,'Renamed');assert.equal(s.entry('a').conversation.revision,6);s.metadata({id:'a',revision:4,title:'Old',title_revision:1});assert.equal(s.entry('a').conversation.title,'Renamed');
});
test('restricted nested inline emphasis remains a nested AST rather than stray delimiter text',()=>{const first=safeMarkdown('**Bold *and quiet***')[0].children[0];assert.equal(first.tag,'strong');assert.equal(first.children[1].tag,'em');assert.equal(first.children[1].children[0],'and quiet');const second=safeMarkdown('*Quiet **and bold** together*')[0].children[0];assert.equal(second.tag,'em');assert.equal(second.children[1].tag,'strong');assert.equal(second.children[1].children[0],'and bold');});
test('restricted markdown preserves ordered start and nested formatted items without attributes from input',()=>{
 const ast=safeMarkdown('4. **First**\n   - *Nested*\n5. Next');assert.equal(ast[0].tag,'ol');assert.equal(ast[0].start,4);assert.equal(ast[0].children[0].children[0].tag,'strong');assert.equal(ast[0].children[0].children[1].tag,'ul');assert.equal(ast[0].children[0].children[1].children[0].children[0].tag,'em');assert.equal(ast[0].children.length,2);
});

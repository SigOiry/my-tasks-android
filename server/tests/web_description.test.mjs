import test from 'node:test';
import assert from 'node:assert/strict';
import {harness} from './description_harness.mjs';
const text=e=>e.textContent+(e.children||[]).map(text).join('');
const original='  **Original**\n\n'+('Full context including final action. '.repeat(70))+'\nFinal: keep this.  ';
const task={id:'summary',title:'Summary task',description:original,description_summary:'**Decision:** Review the entire context.\n\n- **Next:** act on the final instruction.',notes:'',status:'todo',priority:'medium',revision:1};
test('task defaults to safe Markdown API summary with accessible expand and collapse, never changing editor fields',async()=>{
 const h=await harness();h.showTask(task);
 assert.match(text(h.get('description-preview')),/Decision:/);
 assert.ok(!text(h.get('description-preview')).includes('Full context'));
 assert.equal(h.get('description').value,original);
 assert.equal(h.get('description-editor').hidden,true);
 const b=h.get('description-toggle');assert.equal(b.attributes.type,'button');assert.equal(b.textContent,'See more');assert.equal(b.attributes['aria-expanded'],'false');assert.equal(b.attributes['aria-controls'],'description-preview');
 b.listeners.click();assert.equal(b.textContent,'See less');assert.equal(b.attributes['aria-expanded'],'true');assert.match(text(h.get('description-preview')),/Final: keep this/);
 b.listeners.click();assert.equal(b.textContent,'See more');assert.equal(h.state.dirty,false);
 assert.equal(h.get('description').value,original);
});

test('explicit edit saves the original draft, then the reader never reuses a stale server summary',async()=>{
 const h=await harness();h.showTask(task);h.get('task-edit').listeners.click();
 const draft='  **Draft context**\n'+('New draft only. '.repeat(80))+'\nfinal draft  ';
 h.get('description').value=draft;h.get('task-form').listeners.input();
 assert.equal(h.get('task-reader').hidden,true);assert.equal(h.get('description').value,draft);assert.equal(h.state.dirty,true);
 h.context.fetch=async(path,options)=>{h.calls.push({path,options});return {ok:true,status:200,json:async()=>path.startsWith('/api/events')?{events:[],cursor:0,has_more:false}:{...task,description:draft,description_summary:null,revision:2}};};
 await h.saveTask({preventDefault(){}});
 assert.equal(h.get('task-reader').hidden,false);assert.match(text(h.get('description-preview')),/Draft context/);assert.ok(!text(h.get('description-preview')).includes('Decision:'));
 h.get('description-toggle').listeners.click();assert.match(text(h.get('description-preview')),/final draft/);
 const posted=h.calls.find(c=>c.options?.method==='PATCH');assert.equal(JSON.parse(posted.options.body).description,draft);assert.ok(!('description_summary' in JSON.parse(posted.options.body)));
});
test('deleted task retains canonical summary and full read-only description despite disabled form controls',async()=>{
 const h=await harness(),canonical=original.replace(/\n/g,'\r\n');h.showTask({...task,deleted:true,description:canonical});
 assert.ok(!Object.fromEntries(new h.context.FormData().entries()).description);
 assert.equal(h.get('description').disabled,true);assert.equal(h.get('task-edit').hidden,true);assert.equal(h.get('description-editor').hidden,true);
 assert.match(text(h.get('description-preview')),/Decision:/);assert.equal(h.get('description-toggle').hidden,false);
 h.get('description-toggle').listeners.click();assert.match(text(h.get('description-preview')),/Final: keep this/);
 assert.equal(h.get('description-toggle').attributes['aria-expanded'],'true');assert.equal(h.get('description').value,canonical);
 h.get('description-toggle').listeners.click();assert.match(text(h.get('description-preview')),/Decision:/);
 assert.equal(h.state.dirty,false);assert.equal(h.calls.length,0);
});

test('private summary is removed on session cleanup',async()=>{
 const h=await harness();h.showTask(task);h.clearPrivate();assert.equal(text(h.get('description-preview')),'');
});

for(const operation of ['open','reload'])test(`delayed ${operation} cannot replace a switched or reopened editor`,async()=>{
 const h=await harness();h.showTask(task);let release;
 h.context.fetch=()=>new Promise(resolve=>{release=()=>resolve({ok:true,status:200,json:async()=>task});});
 const pending=operation==='open'?h.openTask(task.id):h.get('reload-task').listeners.click();
 await new Promise(resolve=>setImmediate(resolve));
 h.showTask({...task,id:'replacement',description_summary:'**Replacement** summary'});h.get('task-edit').listeners.click();h.get('notes').value='Unsaved replacement';h.get('task-form').listeners.input();release();await pending;
 assert.equal(h.state.selected.id,'replacement');assert.equal(h.get('notes').value,'Unsaved replacement');assert.match(text(h.get('description-preview')),/Replacement/);
});

test('switching, closing and reopening reset expansion while ordinary renders preserve drafts',async()=>{
 const h=await harness();h.showTask(task);h.get('description-toggle').listeners.click();h.get('task-edit').listeners.click();h.get('notes').value='Local notes';h.get('task-form').listeners.input();h.render();
 assert.equal(h.get('description-toggle').attributes['aria-expanded'],'true');assert.equal(h.get('notes').value,'Local notes');
 h.showTask({...task,id:'other',description_summary:'**Other task**'});assert.equal(h.get('description-toggle').attributes['aria-expanded'],'false');
 h.get('description-toggle').listeners.click();h.closeTask();h.showTask(task);assert.equal(h.get('description-toggle').attributes['aria-expanded'],'false');assert.match(text(h.get('description-preview')),/Decision:/);
});
for(const description of ['', '  ', '**Short** context.'])test(`empty/short description has no disclosure: ${JSON.stringify(description)}`,async()=>{
 const h=await harness();h.showTask({...task,description,description_summary:'Unnecessary summary'});assert.equal(h.get('description-toggle').hidden,true);assert.equal(h.get('description').value,description);
 if(description.trim())assert.match(text(h.get('description-preview')),/Short/);
});
for(const full of ['x'.repeat(12000), '😀漢字'.repeat(4000), '**unfinished\n```\n'+('context\n'.repeat(500))])test('old server fallback is bounded and Unicode-safe, original survives expansion/editing',async()=>{
 const h=await harness();h.showTask({...task,description:full,description_summary:null});const preview=text(h.get('description-preview'));
 assert.ok(Array.from(preview).length<650);assert.ok(!/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/u.test(preview));assert.equal(h.get('description-toggle').hidden,false);
 h.get('description-toggle').listeners.click();assert.equal(h.get('description').value,full);h.get('task-edit').listeners.click();assert.equal(h.get('description-editor').hidden,false);assert.equal(h.get('description').value,full);
});
test('untrusted summary and full Markdown never create active content',async()=>{
 const h=await harness(),attack='<script>alert(1)</script> <img src=x onerror=alert(1)> [click](javascript:alert(1)) ![pixel](https://tracker.invalid/p) **bold**';
 h.showTask({...task,description:attack.repeat(20),description_summary:attack});
 const walk=e=>[e,...(e.children||[]).flatMap(walk)];
 for(const e of walk(h.get('description-preview')))assert.ok(!['script','img','a','iframe','style','svg'].includes(e.tagName));
 assert.ok(text(h.get('description-preview')).includes('<script>'));assert.ok(!text(h.get('description-preview')).includes('javascript:'));
 h.get('description-toggle').listeners.click();for(const e of walk(h.get('description-preview')))assert.ok(!['script','img','a','iframe','style','svg'].includes(e.tagName));
});

test('unchanged browser-normalized CRLF editor saves the exact canonical original',async()=>{
 const h=await harness(),canonical='  **Original**\r\n\r\nFinal action.  ';
 h.showTask({...task,description:canonical});h.get('task-edit').listeners.click();h.get('description').value=canonical.replace(/\r\n?/g,'\n');
 h.get('notes').value='Notes-only edit';h.get('task-form').listeners.input();await h.saveTask({preventDefault(){}});
 assert.equal(JSON.parse(h.calls.find(c=>c.options?.method==='PATCH').options.body).description,canonical);
});

test('typing during an in-flight save retains newer description draft and preview',async()=>{
 const h=await harness();h.showTask(task);h.get('task-edit').listeners.click();let release;
 h.context.fetch=async(path)=>path.startsWith('/api/events')?{ok:true,status:200,json:async()=>({events:[],cursor:0,has_more:false})}:new Promise(resolve=>{release=()=>resolve({ok:true,status:200,json:async()=>({...task,revision:2})});});
 const saving=h.saveTask({preventDefault(){}});await new Promise(resolve=>setImmediate(resolve));
 const newer='**Newer unsaved draft** '+('more context '.repeat(80));h.get('description').value=newer;h.get('task-form').listeners.input();release();await saving;
 assert.equal(h.get('description').value,newer);assert.equal(h.state.dirty,true);assert.equal(h.get('save-task').disabled,false);
});

test('all backend summary items including its final instruction remain visible without expansion',async()=>{
 const h=await harness(),summary='**Context:** '+('A relevant summarized detail. '.repeat(38))+'\n\n- **Final action:** keep this visible.';
 h.showTask({...task,description_summary:summary});assert.match(text(h.get('description-preview')),/Final action: keep this visible/);
});

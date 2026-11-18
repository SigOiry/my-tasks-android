import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {harness} from './description_harness.mjs';
const text=e=>e.textContent+(e.children||[]).map(text).join('');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const enterEdit=h=>h.get('task-edit').listeners.click();
const task={id:'reader',title:'Read this task',description:'  **Exact original**\r\nFinal context.  ',notes:'Private working notes',project:'Correspondence',due_date:'2026-11-20',status:'in_progress',priority:'high',revision:7,source:'manual'};

test('existing tasks are readable documents until explicit Edit exposes every original field',async()=>{
 const h=await harness();h.showTask(task);
 assert.equal(h.get('task-reader').hidden,false);
 assert.equal(h.get('task-editor').hidden,true);
 assert.equal(h.get('task-edit').hidden,false);
 assert.equal(h.get('save-task').hidden,true);
 assert.equal(h.get('task-edit-actions').hidden,true);
 const css=readFileSync(new URL('../web/app.css',import.meta.url),'utf8');
 assert.match(css,/\.task-read-metadata\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/);
 assert.match(css,/#task-read-notes\s*\{[^}]*white-space:\s*pre-wrap/);
 assert.match(text(h.get('task-read-metadata')),/In progress/);
 assert.match(text(h.get('task-read-metadata')),/Correspondence/);
 assert.equal(h.get('task-read-notes').textContent,task.notes);
 assert.equal(h.get('detail-heading').textContent,task.title);
 h.get('task-edit').listeners.click();
 assert.equal(h.get('task-reader').hidden,true);
 assert.equal(h.get('task-editor').hidden,false);
 assert.equal(h.get('save-task').hidden,false);
 assert.equal(h.get('task-edit-actions').hidden,false);
 for(const key of ['title','description','notes','project','due_date','status','priority'])assert.equal(h.get(key).value,task[key]);
 assert.equal(h.state.dirty,false);assert.equal(h.calls.length,0);
});

test('Cancel refuses without losing edits, then restores canonical read mode without a request',async()=>{
 const h=await harness();h.showTask(task);enterEdit(h);
 h.get('notes').value='Unsaved replacement';h.get('task-form').listeners.input();
 const generation=h.state.editorGeneration,snapshot=h.state.snapshot;
 let prompts=0;h.context.window.confirm=()=>{prompts++;return false;};
 assert.equal(typeof h.get('task-edit-cancel').listeners.click,'function');
 h.get('task-edit-cancel').listeners.click();
 assert.equal(prompts,1);assert.equal(h.state.editorGeneration,generation);assert.equal(h.state.snapshot,snapshot);
 assert.equal(h.get('notes').value,'Unsaved replacement');assert.equal(h.state.dirty,true);assert.equal(h.get('task-editor').hidden,false);
 h.context.window.confirm=()=>true;h.get('task-edit-cancel').listeners.click();
 assert.equal(h.get('task-dialog').open,true);assert.equal(h.get('task-reader').hidden,false);assert.equal(h.state.dirty,false);
 assert.equal(h.get('notes').value,task.notes);assert.equal(h.state.selected.revision,7);assert.equal(h.calls.length,0);
 assert.ok(h.state.editorGeneration>generation);
});

test('Task / Context / Discussions are labelled tabs; activation preserves drafts and uses only saved source/history data',async()=>{
 const h=await harness();h.showTask({...task,source:'email',source_accounts:['synthetic@example.invalid'],source_links:[{label:'Original message',url:'https://mail.google.com/mail/u/0/#inbox/synthetic'},{label:'unsafe',url:'javascript:alert(1)'}]});
 const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
 assert.match(html,/<nav[^>]+class="detail-tabs"[^>]+role="tablist"/);
 for(const id of ['details','source','hermes']){
  assert.equal(h.get(id+'-tab').getAttribute('role'),'tab');
  assert.equal(h.get(id+'-tab').getAttribute('aria-controls'),id+'-panel');
  assert.equal(h.get(id+'-panel').getAttribute('role'),'tabpanel');
  assert.equal(h.get(id+'-panel').getAttribute('aria-labelledby'),id+'-tab');
  assert.equal(h.get(id+'-panel').getAttribute('tabindex'),'0');
 }
 enterEdit(h);h.get('notes').value='Unsaved notes';h.get('notes').selectionStart=3;h.get('notes').selectionEnd=7;h.get('task-form').listeners.input();
 const generation=h.state.editorGeneration;
 h.get('source-tab').listeners.click();assert.equal(h.get('source-panel').hidden,false);assert.equal(h.get('details-panel').hidden,true);
 assert.equal(h.calls.length,0);assert.match(text(h.get('source-links')),/Original message/);assert.ok(!text(h.get('source-links')).includes('unsafe'));
 assert.equal(h.get('source-accounts').textContent,'synthetic@example.invalid');assert.equal(h.get('detail-reply').hidden,false);
 h.context.fetch=async(path,options)=>{h.calls.push({path,options});return {ok:true,status:200,json:async()=>({conversations:[],next_cursor:null})};};
 h.get('hermes-tab').listeners.click();await tick();
 assert.equal(h.calls.length,1);assert.match(h.calls[0].path,/^\/api\/conversations\?/);assert.equal(h.calls[0].options.method,'GET');
 h.get('details-tab').listeners.click();assert.equal(h.get('details-panel').hidden,false);assert.equal(h.get('task-editor').hidden,false);
 assert.equal(h.get('notes').value,'Unsaved notes');assert.equal(h.get('notes').selectionStart,3);assert.equal(h.get('notes').selectionEnd,7);
 assert.equal(h.state.dirty,true);assert.equal(h.state.editorGeneration,generation);
});

test('forced-colors selected tabs retain Highlight outlines without changing pressed scope controls',()=>{
 const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
 const css=readFileSync(new URL('../web/app.css',import.meta.url),'utf8');
 const selectedTab=html.match(/<button\b[^>]*\bid="details-tab"[^>]*>/)?.[0];
 assert.ok(selectedTab,'actual selected tab markup exists');
 const selectedAttribute=selectedTab.match(/\b(aria-selected)="true"/)?.[1];
 assert.equal(selectedAttribute,'aria-selected');
 const forcedColors=css.match(/@media\s*\(forced-colors:\s*active\)\s*\{([\s\S]*?)\n\}/)?.[1];
 assert.ok(forcedColors,'forced-colors branch exists');
 const selectedRule=[...forcedColors.matchAll(/([^{}]+)\{([^{}]*)\}/g)].find(([,selectors])=>selectors.includes(`.detail-tabs button[${selectedAttribute}=true]`));
 assert.ok(selectedRule,'forced-colors selected-tab selector matches the actual HTML selected attribute');
 assert.match(selectedRule[2],/\boutline:\s*2px solid Highlight\s*;/);
 assert.match(selectedRule[2],/\boutline-offset:\s*-3px\s*;/);
 assert.match(selectedRule[1],/\.task-scopes button\[aria-pressed=true\]/);
 assert.match(selectedRule[1],/\.destinations button\[aria-current=page\]/);
 assert.match(selectedRule[1],/\.nav-button\[aria-current=page\]/);
 assert.doesNotMatch(forcedColors,/\.detail-tabs button\[aria-pressed=true\]/);
 assert.match(forcedColors,/\.task-row\.unread\s*\{\s*border-left:\s*3px solid Highlight\s*;/);
});

test('manual keyboard tabs move focus only, wrap and skip disabled; Enter/Space activate without provider work',async()=>{
 const h=await harness();h.showTask(task);
 const press=(id,key)=>{let prevented=false;h.get(id+'-tab').listeners.keydown({key,currentTarget:h.get(id+'-tab'),preventDefault(){prevented=true;}});assert.equal(prevented,true);};
 assert.equal(typeof h.get('details-tab').listeners.keydown,'function');
 press('details','ArrowRight');assert.equal(h.context.document.activeElement,h.get('source-tab'));
 assert.equal(h.get('details-tab').getAttribute('aria-selected'),'true');assert.equal(h.get('source-panel').hidden,true);
 assert.equal(h.get('details-tab').getAttribute('tabindex'),'0');assert.equal(h.get('source-tab').getAttribute('tabindex'),'-1');
 press('source','End');assert.equal(h.context.document.activeElement,h.get('hermes-tab'));
 press('hermes','ArrowRight');assert.equal(h.context.document.activeElement,h.get('details-tab'));
 press('details','ArrowLeft');assert.equal(h.context.document.activeElement,h.get('hermes-tab'));
 press('hermes','Home');assert.equal(h.context.document.activeElement,h.get('details-tab'));assert.equal(h.calls.length,0);
 press('details','ArrowRight');press('source',' ');
 assert.equal(h.get('source-tab').getAttribute('aria-selected'),'true');assert.equal(h.get('source-tab').getAttribute('tabindex'),'0');assert.equal(h.get('details-tab').getAttribute('tabindex'),'-1');assert.equal(h.calls.length,0);
 press('source','Home');press('details','Enter');assert.equal(h.get('details-panel').hidden,false);
 h.showTask({...task,deleted:true});press('details','End');assert.equal(h.context.document.activeElement,h.get('source-tab'));
 press('source','ArrowRight');assert.equal(h.context.document.activeElement,h.get('details-tab'));h.get('hermes-tab').listeners.click();assert.equal(h.get('hermes-panel').hidden,true);
 h.showTask();press('details','End');assert.equal(h.context.document.activeElement,h.get('details-tab'));
 assert.equal(h.calls.length,0);
});

test('read mode cannot submit hidden fields; new tasks edit immediately and Trash remains restore-only',async()=>{
 const h=await harness();h.showTask(task);
 await h.saveTask({preventDefault(){}});assert.equal(h.calls.length,0,'read mode does not submit');
 h.get('task-form').listeners.input();assert.equal(h.state.dirty,false);
 h.showTask({...task,deleted:true});enterEdit(h);await h.saveTask({preventDefault(){}});
 assert.equal(h.get('task-reader').hidden,false);assert.equal(h.get('task-editor').hidden,true);assert.equal(h.get('task-edit').hidden,true);
 assert.equal(h.get('restore-task').hidden,false);assert.equal(h.get('delete-task').hidden,true);assert.equal(h.get('save-task').hidden,true);assert.equal(h.calls.length,0);
 h.showTask();assert.equal(h.get('task-editor').hidden,false);assert.equal(h.get('task-reader').hidden,true);assert.equal(h.get('save-task').textContent,'Create task');
 assert.equal(h.context.document.activeElement,h.get('title'));assert.equal(h.get('source-tab').disabled,true);assert.equal(h.get('hermes-tab').disabled,true);
 h.get('title').value='Unsaved new task';h.get('task-form').listeners.input();h.context.window.confirm=()=>false;h.get('task-edit-cancel').listeners.click();assert.equal(h.get('task-dialog').open,true);
 h.context.window.confirm=()=>true;h.get('task-edit-cancel').listeners.click();assert.equal(h.get('task-dialog').open,false);assert.equal(h.calls.length,0);
});

test('logout scrubs rendered reader metadata and notes, not only hidden editor fields',async()=>{
 const h=await harness();h.showTask(task);enterEdit(h);h.clearPrivate();
 assert.equal(text(h.get('task-read-metadata')),'');assert.equal(h.get('task-read-notes').textContent,'');
 assert.equal(h.state.editing,false);assert.equal(h.get('task-dialog').open,false);
});

for(const status of [200,500])test(`saved Discussions ${status} response cannot write into a reopened task reader`,async()=>{
 const h=await harness();h.showTask(task);let release;
 h.context.fetch=async()=>new Promise(resolve=>{release=()=>resolve({ok:status===200,status,json:async()=>status===200?{conversations:[],next_cursor:null}:{detail:'Old history failure'}});});
 h.get('hermes-tab').listeners.click();await tick();
 h.showTask({...task});h.get('task-thread-status').textContent='Replacement history status';h.get('task-thread-error').textContent='Replacement history error';
 release();await tick();
 assert.equal(h.get('task-thread-status').textContent,'Replacement history status');assert.equal(h.get('task-thread-error').textContent,'Replacement history error');
 assert.equal(h.get('details-panel').hidden,false);
});

for(const status of [200,409,500])test(`Cancel fences delayed save ${status} from changing or reopening the reader`,async()=>{
 const h=await harness();h.showTask(task);enterEdit(h);h.get('notes').value='Submitted notes';h.get('task-form').listeners.input();
 let release;const saved={...task,notes:'Submitted notes',revision:8};
 h.context.fetch=async(path)=>path.startsWith('/api/events')?{ok:true,status:200,json:async()=>({events:[],cursor:0,has_more:false})}:new Promise(resolve=>{release=()=>resolve({ok:status===200,status,json:async()=>status===200?saved:{detail:'Late save failure'}});});
 const pending=h.saveTask({preventDefault(){}});await tick();assert.equal(typeof release,'function');
 h.get('task-edit-cancel').listeners.click();const generation=h.state.editorGeneration;
 release();await pending;
 assert.equal(h.state.editorGeneration,generation);assert.equal(h.get('task-reader').hidden,false);assert.equal(h.get('task-editor').hidden,true);assert.equal(h.state.dirty,false);
 assert.equal(h.get('task-read-notes').textContent,task.notes);assert.equal(h.get('detail-error').textContent,'');assert.equal(h.get('conflict').hidden,true);
 assert.equal(h.get('task-dialog').open,true);assert.equal(h.get('save-task').hidden,true);
 if(status===200)assert.equal(h.state.data.tasks.find(t=>t.id===task.id).revision,8,'canonical cache can finish independently');
});

test('newer typing during save stays editable across Context; Cancel returns to the newly saved revision',async()=>{
 const h=await harness();h.showTask(task);enterEdit(h);h.get('notes').value='Submitted notes';h.get('task-form').listeners.input();
 const saved={...task,notes:'Submitted notes',revision:8,notification:{unread:false,token:'saved-token'}};let release;
 h.context.fetch=async(path)=>path.startsWith('/api/events')?{ok:true,status:200,json:async()=>({events:[],cursor:0,has_more:false})}:new Promise(resolve=>{release=()=>resolve({ok:true,status:200,json:async()=>saved});});
 const pending=h.saveTask({preventDefault(){}});await tick();
 h.get('notes').value='Newer unsaved notes';h.get('task-form').listeners.input();h.get('source-tab').listeners.click();release();await pending;
 assert.equal(h.get('source-panel').hidden,false);h.get('details-tab').listeners.click();
 assert.equal(h.get('task-editor').hidden,false);assert.equal(h.get('notes').value,'Newer unsaved notes');assert.equal(h.state.dirty,true);assert.equal(h.state.selected.revision,8);assert.equal(h.get('save-task').disabled,false);
 h.get('task-edit-cancel').listeners.click();assert.equal(h.get('task-read-notes').textContent,'Submitted notes');assert.equal(h.state.selected.notification.token,'saved-token');
});

test('closing an editor prevents a later form submit from creating a hidden task',async()=>{
 const h=await harness();h.showTask(task);enterEdit(h);h.closeTask();
 await h.saveTask({preventDefault(){}});assert.equal(h.calls.length,0);assert.equal(h.get('task-dialog').open,false);
});

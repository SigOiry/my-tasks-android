import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');

function productionCatalog(){
 const begin=source.indexOf('const workspaceCommands =');
 const end=source.indexOf('// End command catalog',begin);
 assert.ok(begin>=0&&end>begin,'production must define the command catalog');
 const context=vm.createContext({});
 vm.runInContext(source.slice(begin,end)+'\nglobalThis.testApi={workspaceCommands,matchingWorkspaceCommands};',context);
 return context.testApi;
}
test('command menu has a named modal, labelled query and discoverable triggers',()=>{
 for(const id of ['command-dialog','command-title','command-query','command-results','command-empty','command-close','command-trigger','command-mobile'])assert.ok(html.includes(`id="${id}"`),id+' missing');
 assert.match(html,/<dialog[^>]*id="command-dialog"[^>]*aria-labelledby="command-title"/);
 assert.match(html,/<input[^>]*id="command-query"[^>]*aria-label="Find an action"/);
});
test('command catalog only exposes existing navigation and explicitly opened editors',()=>{
 const {workspaceCommands}=productionCatalog();
 assert.deepEqual(Array.from(workspaceCommands,c=>c.id),['tasks','discussions','new-task','search','today','week','all','trash','settings']);
 assert.equal(new Set(Array.from(workspaceCommands,c=>c.id)).size,workspaceCommands.length);
 for(const c of workspaceCommands)assert.ok(c.title&&c.detail&&c.keywords);
 assert.ok(workspaceCommands.every(c=>!['send','acknowledge','delete','complete','ai','sync'].includes(c.id)));
});
test('filter searches action labels and keywords without private task data',()=>{
 const {matchingWorkspaceCommands}=productionCatalog();
 assert.deepEqual(Array.from(matchingWorkspaceCommands('  KEYBOARD '),c=>c.id),['search']);
 assert.deepEqual(Array.from(matchingWorkspaceCommands('deleted'),c=>c.id),['trash']);
 assert.equal(matchingWorkspaceCommands('not-an-action').length,0);
 assert.equal(matchingWorkspaceCommands('').length,9);
});

// This host harness checks focus ownership; it does not simulate browser layout
// or native pointer/Escape event ordering.
function commandFocusHarness(){
 const elements=new Map(),document={body:{id:'body'},activeElement:null};
 const element=id=>{
  if(elements.has(id))return elements.get(id);
  const e={id,hidden:false,open:false,isConnected:true,visible:true,value:'',listeners:{},
   addEventListener(type,fn){this.listeners[type]=fn;},
   focus(){document.activeElement=this;},
   showModal(){this.open=true;},close(){this.open=false;},
   replaceChildren(){this.cleared=true;},getClientRects(){return this.visible?[{}]:[];},
   contains(target){return this.id==='command-dialog'&&target===element('command-query');},
   closest(){return this.id==='command-mobile'&&!filters.open?filters:null;}
  };elements.set(id,e);return e;
 };
 const filters={open:true,querySelector:()=>element('filters-summary')};
 document.activeElement=document.body;
 document.querySelector=selector=>selector==='.filters'?filters:selector==='dialog:modal'?[...elements.values()].find(e=>e.id.endsWith('-dialog')&&e.open)||null:null;
 const discussion={destination:'tasks'};
 const context=vm.createContext({document,$:element,state:{authenticated:true},discussion,
  destination:value=>{discussion.destination=value;},
  openSettings:()=>{element('settings-dialog').showModal();element('settings-close').focus();}
 });
 const begin=source.indexOf('const workspaceCommands =');
 const end=source.indexOf('function node(',begin);
 const handlers=source.slice(source.indexOf("for(const id of ['command-trigger','command-mobile'])"),source.indexOf("$('command-query').addEventListener('input'"));
 vm.runInContext(source.slice(begin,end)+'\nrenderWorkspaceCommands=()=>{};\n'+handlers+'\nglobalThis.api={openWorkspaceCommands,runWorkspaceCommand};',context);
 return {document,element,filters,api:context.api,
  open(){context.api.openWorkspaceCommands({currentTarget:element('command-mobile')});filters.open=false;},
  dismiss(){element('command-dialog').close();document.activeElement=document.body;},
  flush(){element('command-dialog').listeners.close();}
 };
}
test('command dismissal returns focus to closed Filters summary',()=>{
 const h=commandFocusHarness();h.open();h.dismiss();h.flush();
 assert.equal(h.document.activeElement,h.element('filters-summary'));
 assert.equal(h.filters.open,false);
});
test('queued close leaves newer command dialog focus and results intact',()=>{
 const h=commandFocusHarness();h.open();h.dismiss();h.api.openWorkspaceCommands();h.flush();
 assert.equal(h.document.activeElement,h.element('command-query'));
 assert.equal(h.element('command-results').cleared,undefined);
});
test('queued command close never steals focus from another modal',()=>{
 const h=commandFocusHarness();h.open();h.dismiss();h.element('settings-dialog').showModal();h.element('settings-close').focus();h.flush();
 assert.equal(h.document.activeElement,h.element('settings-close'));
});
test('queued command close preserves newer explicitly focused control',()=>{
 const h=commandFocusHarness();h.open();h.dismiss();h.element('nav-discussions').focus();h.flush();
 assert.equal(h.document.activeElement,h.element('nav-discussions'));
});
for(const [action,target] of [['search','search'],['settings','settings-close'],['discussions','discussion-search']]){
 test(`${action} command owns focus after queued command close`,()=>{
  const h=commandFocusHarness();h.open();h.api.runWorkspaceCommand(action);h.flush();
  assert.equal(h.document.activeElement,h.element(target));
 });
}

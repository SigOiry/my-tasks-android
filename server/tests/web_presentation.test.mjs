import test from 'node:test';
import assert from 'node:assert/strict';
import * as p from '../web/policy.mjs';
import {readFileSync} from 'node:fs';

test('update breaths consume visible active time once per exact token and batch concurrency',()=>{
 assert.equal(typeof p.UpdateBreaths,'function');
 const b=new p.UpdateBreaths(2);
 for(const id of ['a','b','c'])b.observe(id,'v1',true);
 assert.deepEqual(b.tick(['a','b','c'],0),['a','b']);
 b.tick(['a','b','c'],1600);b.tick([],2000);
 b.tick(['a'],20000);assert.equal(b.age('a'),2000);
 b.tick(['a'],24400);assert.equal(b.age('a'),6400);
 b.observe('a','v1',true);assert.ok(!b.tick(['a'],25000).includes('a'));
 b.observe('a','v2',true);assert.ok(b.tick(['a'],25001).includes('a'));
 b.observe('a','v2',false);assert.ok(!b.tick(['a'],25002).includes('a'));
});

test('new token does not inherit elapsed time from the replaced active token',()=>{
 const b=new p.UpdateBreaths();b.observe('a','old',true);b.tick(['a'],0);
 b.observe('a','new',true);b.tick(['a'],2000);assert.equal(b.age('a'),0);
 b.observe('a','new',false);b.observe('a','new',true);assert.equal(b.age('a'),6400);
});
test('UI wires persisted modes, global Smart order, exact unread marker and coordinated motion',()=>{
 const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
 const js=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
 const css=readFileSync(new URL('../web/app.css',import.meta.url),'utf8');
 for(const id of ['color-mode','sort-direction','sort-explanation'])assert.ok(html.includes('id="'+id+'"'),id);
 assert.ok(js.includes("writeLocal('sort'"));assert.ok(js.includes("writeLocal('color'"));
 assert.ok(js.includes('New update'));assert.ok(js.includes('UpdateBreaths'));
 assert.match(css,/update-breath 1600ms[^;]*4/);
 assert.match(css,/sheet-in 280ms/);
 assert.ok(css.includes('typing-wave'));assert.ok(js.includes("r?.status==='running'"));
});

test('canonical project palette hashes exact UTF8 identity, never display normalization',()=>{
 assert.equal(typeof p.projectPalette,'function');
 assert.equal(p.projectPalette({project:'hello'}).index,3);
 assert.equal(p.projectPalette({project_id:'hello',project:'renamed'}).index,3);
 assert.equal(p.projectPalette({}).index,null);
 assert.deepEqual(p.projectPalette({project:'hello'}),{index:3,light:'#B26D87',dark:'#DFA2B9'});
});

test('Smart scores calendar deadlines, priority, canonical unread and capped age',()=>{
 assert.equal(typeof p.smartScore,'function');
 assert.equal(p.smartScore({due_date:'2026-11-09',priority:'high',status:'in_progress',notification:{unread:true},created_at:'2026-10-01T00:00:00Z'},'2026-11-10'),96);
 for(const [date,score] of [['2026-11-10',40],['2026-11-11',25],['2026-11-17',10],['2026-11-18',0],['2026-02-30',0],[null,0]])assert.equal(p.smartScore({due_date:date},'2026-11-10'),score);
 assert.equal(p.smartScore({created_at:'invalid'},'2026-11-10'),0);
});
test('Smart ranks across projects with waiting and terminal last and deterministic ties',()=>{
 const t=(id,extras={})=>({id,status:'todo',priority:'low',...extras});
 const tasks=[t('waiting',{status:'waiting',priority:'high',due_date:'2026-01-01'}),t('b',{project:'B',due_date:'2026-11-10'}),t('a',{project:'A',due_date:'2026-11-09'}),t('z',{status:'completed',priority:'high'})];
 const opts={view:'all',today:'2026-11-10'};
 assert.deepEqual(p.selectTasks(tasks,opts).map(t=>t.id),['a','b','waiting','z']);
 assert.deepEqual(p.selectTasks(tasks,{...opts,direction:'desc'}).map(t=>t.id),['b','a','waiting','z']);
 assert.deepEqual(p.selectTasks([t('b'),t('a')],opts).map(t=>t.id),['a','b']);
});

test('nullable date keys stay last in both directions in source and packaged policy',async()=>{
 const packaged=await import('../web_dist/policy.mjs');
 for(const policy of [p,packaged])for(const [sort,key,newestFirst] of [['due','due_date',false],['created','created_at',false],['updated','updated_at',true]]){
  const stamp=date=>sort==='due'?date:date+'T12:00:00Z';
  const tasks=[{id:'missing',status:'todo',priority:'low',[key]:null},{id:'invalid',status:'todo',priority:'low',[key]:'invalid'},{id:'earlier',status:'todo',priority:'low',[key]:stamp('2026-11-08')},{id:'later',status:'todo',priority:'low',[key]:stamp('2026-11-10')}];
  for(const direction of ['asc','desc']){
   const reverse=(direction==='desc')!==newestFirst;
   assert.deepEqual(policy.selectTasks(tasks,{view:'all',sort,direction,today:'2026-11-10'}).map(task=>task.id),[...(reverse?['later','earlier']:['earlier','later']),'invalid','missing'],sort+' '+direction);
  }
 }
});

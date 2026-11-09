import test from 'node:test';
import assert from 'node:assert/strict';
import * as p from '../web/policy.mjs';
const tasks = [
 {id:'1',title:'Alpha',status:'todo',priority:'low',due_date:'2026-11-01',project:'Lab',notes:'banana',notification:{unread:false}},
 {id:'2',title:'Beta',status:'in_progress',priority:'high',due_date:'2026-11-10',project:'Lab',notification:{unread:false}},
 {id:'3',title:'Gamma',status:'completed',priority:'medium',due_date:'2026-11-09',project:'Home',notification:{unread:true}},
 {id:'4',title:'Deleted',status:'cancelled',priority:'high',deleted:true,notification:{unread:false}},
 {id:'5',title:'Unread',status:'inbox',priority:'low',due_date:null,project:'Home',notification:{unread:true}},
];
const base = {view:'active',today:'2026-11-09',week_end:'2026-11-15',project:'',query:'',status:'',sort:'priority'};
test('real task filters use server Rome dates, include overdue, and keep trash separate',()=>{
 assert.deepEqual(p.selectTasks(tasks,base).map(t=>t.id),['5','2','1']);
 assert.deepEqual(p.selectTasks(tasks,{...base,view:'today'}).map(t=>t.id),['1']);
 assert.deepEqual(p.selectTasks(tasks,{...base,view:'week'}).map(t=>t.id),['2','1']);
 assert.equal(p.selectTasks(tasks,{...base,view:'all'}).length,4);
 assert.deepEqual(p.selectTasks(tasks,{...base,view:'trash'}).map(t=>t.id),['4']);
 assert.deepEqual(p.selectTasks(tasks,{...base,project:'Lab',query:'BANANA'}).map(t=>t.id),['1']);
 assert.deepEqual(p.selectTasks(tasks,{...base,view:'all',status:'completed'}).map(t=>t.id),['3']);
 assert.deepEqual(p.selectTasks(tasks,{...base,sort:'date'}).map(t=>t.id),['5','1','2']);
 assert.deepEqual(p.groupTasks(p.selectTasks(tasks,base)).map(g=>g.name),['Home','Lab']);
 assert.equal(tasks[0].priority,'low');
});
test('chat serialization evicts whole old turns within aggregate UTF8 budget and never truncates current message',()=>{
 const message = '😀'.repeat(8000);
 const history = Array.from({length:12},(_,i)=>({role:i%2?'assistant':'user',content:'漢'.repeat(8000)}));
 const body = p.chatBody('task-id',message,'email_draft',history);
 assert.ok(new TextEncoder().encode(body).length<=60000);
 assert.equal(JSON.parse(body).message,message);
 assert.equal(JSON.parse(body).history.length%2,0);
 assert.ok(JSON.parse(body).history.length<=12);
 assert.throws(()=>p.chatBody('id','x'.repeat(8001),'question',[]),/8000/);
 assert.throws(()=>p.chatBody('id','   ','question',[]),/message/i);
 assert.throws(()=>p.chatBody('id','hello','send',[]),/mode/i);
 assert.deepEqual(JSON.parse(p.chatBody('id','hello','question',[{role:'system',content:'bad'}])).history,[]);
});
test('notification delivery is quiet on baseline, read/deleted/own events, unknown tracking; never acknowledges',()=>{
 const t={id:'1',deleted:false,notification:{unread:false,token:'a'}};
 const event={task_id:'1',kind:'updated',token:'a'};
 assert.equal(p.shouldNotify(event,t,{baseline:true,tracking:false,ownToken:null}),false);
 assert.equal(p.shouldNotify(event,t,{baseline:false,tracking:true,ownToken:null}),false);
 assert.equal(p.shouldNotify(event,t,{baseline:false,tracking:undefined,ownToken:null}),false);
 assert.equal(p.shouldNotify(event,t,{baseline:false,tracking:false,ownToken:'a'}),false);
 assert.equal(p.shouldNotify(event,{...t,deleted:true},{baseline:false,tracking:false}),false);
 assert.equal(p.shouldNotify(event,null,{baseline:false,tracking:false}),false);
 assert.equal(p.shouldNotify({...event,kind:'deleted'},t,{baseline:false,tracking:false}),false);
 assert.equal(p.shouldNotify(event,t,{baseline:false,tracking:false}),true);
 assert.equal(p.shouldNotify(event,{...t,notification:{unread:true,token:'a'}},{baseline:false,tracking:true}),true);
 assert.equal(t.notification.unread,false);
});
test('unread summary gate captures exact token, rejects stale/failed acknowledgement, and edits retain original revision',()=>{
 const task={...tasks[0],revision:'old',notification:{unread:true,token:'displayed',summary:['Latest change']}};
 const gate=p.updateGate(task);
 task.notification.token='new';
 assert.equal(gate.token,'displayed');
 assert.equal(p.acknowledged(gate,{notification:{unread:false,token:'displayed'}}),true);
 assert.equal(p.acknowledged(gate,{notification:{unread:true,token:'new'}}),false);
 assert.equal(p.acknowledged(gate,null),false);
 assert.equal(p.editPayload(task,{title:'my unsaved change',due_date:'',project:''}).revision,'old');
 assert.equal(p.editPayload(task,{title:'my unsaved change',due_date:'',project:''}).due_date,null);
});
test('source links accept only reliable backend-provided Gmail and Calendar HTTPS origins',()=>{
 for (const url of ['javascript:alert(1)','https://mail.google.com.evil.test/mail/u/0/#all/ab','https://mail.google.com@evil.test/','https://evil.test','http://mail.google.com/mail/u/0/#all/ab','https://mail.google.com:444/mail/u/0/#all/ab','https://calendar.google.com/not-calendar']) assert.equal(p.safeSource(url),null,url);
 for (const url of ['https://mail.google.com/mail/u/test%40example.com/#all/abc','https://calendar.google.com/calendar/u/0/r/eventedit/abc']) assert.equal(p.safeSource(url),url);
});

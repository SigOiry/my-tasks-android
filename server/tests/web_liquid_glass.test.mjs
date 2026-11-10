import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
test('compact task scopes belong to the task toolbar, not a second global navigation row',()=>{
 const sidebar=html.slice(html.indexOf('<aside'),html.indexOf('</aside>'));
 assert.ok(!sidebar.includes('id="views"'));
 assert.ok(html.indexOf('id="views"')>html.indexOf('id="tasks-workspace"'));
 assert.match(html,/id="search-toggle"[^>]*aria-label="Search tasks"/);
 assert.ok(html.includes('id="extra-views"'));
});
test('update review distinguishes explicit acknowledgement from read-only navigation',()=>{
 assert.match(html,/id="updates-ack"[^>]*>Mark reviewed<\/button>/);
 for(const id of ['updates-open','updates-discuss'])assert.ok(html.includes(`id="${id}"`));
 assert.match(source,/async function openReviewedTask\(chat=false\)/);
 const navigation=source.slice(source.indexOf('async function openReviewedTask('),source.indexOf('async function acknowledge('));
 assert.ok(navigation.includes('currentSession(epoch)'));assert.ok(!navigation.includes('/updates/read'));
});
test('desktop inspector and responsive transitions do not refill the task form',()=>{
 assert.match(source,/function presentTaskDialog\(/);
 const presentation=source.slice(source.indexOf('function presentTaskDialog('),source.indexOf('function closeTask('));
 assert.ok(presentation.includes('.show()'));assert.ok(presentation.includes('.showModal()'));
 assert.ok(!presentation.includes('form.reset'));assert.ok(!presentation.includes('showTask('));
});
test('compact editor reserves its sticky footer for Cancel and Save, retaining trash in the body',()=>{
 const editor=html.slice(html.indexOf('id="task-dialog"'),html.indexOf('id="hermes-panel"'));
 const footer=editor.match(/<footer class="dialog-footer">([\s\S]*?)<\/footer>/)[1];
 assert.ok(footer.includes('id="save-task"'));assert.ok(footer.includes('Cancel'));
 assert.ok(!footer.includes('id="delete-task"'));assert.ok(editor.includes('id="delete-task"'));
});

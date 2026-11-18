import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
test('task discussion copy preserves saved-context boundary without falsely denying bounded tools',()=>{
 assert.ok(html.includes('Unsaved task edits are not sent.'));
 assert.ok(!html.includes('No tools, sending mail or task changes.'));
 assert.ok(html.includes('Message content is not loaded here.'));
 assert.match(html,/<select id="chat-mode" aria-describedby="chat-mode-hint">/);
 assert.match(html,/id="chat-draft-state"/);
 assert.ok(!html.includes('id="chat-context" class="eyebrow"'));
});
test('settings discloses actual email recovery storage and bounded chat capabilities',()=>{
 assert.ok(html.includes('Unsaved email writing is also kept in this browser for recovery'));
 assert.ok(html.includes('bounded public-web research'));
 assert.ok(html.includes('separate email workspace requires your explicit review and confirmation'));
 assert.ok(!html.includes('Hermès has no tools'));
});

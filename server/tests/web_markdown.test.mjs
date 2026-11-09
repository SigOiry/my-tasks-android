import test from 'node:test';
import assert from 'node:assert/strict';
import * as p from '../web/policy.mjs';
const text=n=>typeof n==='string'?n:(n.children||[]).map(text).join('');
const tags=n=>typeof n==='string'?[]:[n.tag,...(n.children||[]).flatMap(tags)];
test('canonical prose snapshots replace technical summary and omit empty optional sections',()=>{
 const gate=p.updateGate({id:'exact',title:'Task',notification:{token:'snapshot',summary:['Technical diff must not appear'],sections:{context:'Background',update:'**Changed** timeline',next_steps:''}}});
 assert.deepEqual(gate.sections,[{heading:'Context',text:'Background'},{heading:'New',text:'**Changed** timeline'}]);assert.deepEqual(gate.summary,[]);assert.equal(gate.token,'snapshot');
});
test('safe markdown supports prose emphasis lists and code without losing unmatched delimiters',()=>{
 const ast=p.safeMarkdown('A **bold** and *quiet* paragraph.\n\n- one\n- `two`\n\n1. first\n2. second\n\n```js\n<unsafe>\n```\n\nUnmatched **bold and `code');
 assert.deepEqual([...new Set(ast.flatMap(tags))].sort(),['code','em','li','ol','p','pre','strong','ul']);assert.ok(ast.map(text).join('').includes('Unmatched **bold and `code'));
});
test('untrusted HTML links and image syntax never become active nodes or attributes',()=>{
 const ast=p.safeMarkdown('<img src=x onerror=alert(1)> **<script>alert(2)</script>** [mail](https://mail.google.com/mail/u/0/#inbox) ![secret](https://tracker.invalid/pixel) [evil](javascript:alert(3))');
 const all=ast.flatMap(tags);assert.ok(all.every(t=>['p','strong','em','code','pre','ul','ol','li'].includes(t)));const plain=ast.map(text).join('');assert.ok(plain.includes('<img src=x onerror=alert(1)>'));assert.ok(plain.includes('mail'));assert.ok(!plain.includes('https://'));assert.ok(!plain.includes('javascript:'));assert.ok(!JSON.stringify(ast).includes('attributes'));
});
test('prose presence never falls back to legacy technical differences, including empty prose',()=>{
 const gate=p.updateGate({id:'id',notification:{token:'t',sections:{context:'',update:'',next_steps:''},summary:['Old diff']}});assert.deepEqual(gate.summary,[]);assert.deepEqual(gate.sections,[]);
});

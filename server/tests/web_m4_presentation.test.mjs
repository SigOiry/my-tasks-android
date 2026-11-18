import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const css=readFileSync(new URL('../web/app.css',import.meta.url),'utf8');
test('citation disclosures have comfortable targets, readable provenance and visible open state',()=>{
 assert.match(css,/\.citation-source > summary\s*\{[^}]*min-height:\s*44px[^}]*font-size:\s*14px/);
 assert.match(css,/\.citation-panel p\s*\{[^}]*font-size:\s*14px/);
 assert.match(css,/\.citation-source\[open\] > summary/);
});
test('saved history dates and draft state have readable dedicated rows',()=>{
 assert.match(css,/\.discussion-time\s*\{[^}]*grid-row:\s*4[^}]*font-size:\s*13px/);
 assert.match(css,/\.discussion-draft\s*\{[^}]*grid-column:\s*1\s*\/\s*-1/);
});
test('email hierarchy uses shared corners, comfortable disclosures and bounded reading measure without moving editor',()=>{
 assert.match(css,/\.email-source-identity\s*\{[^}]*overflow-wrap:\s*anywhere/);
 assert.match(css,/\.email-context summary\s*\{[^}]*min-height:\s*44px/);
 assert.match(css,/\.email-context pre[^}]*max-width:\s*70ch/);
 assert.match(css,/dialog\.email-workspace\s*\{[^}]*border-radius:\s*16px/);
 assert.match(css,/\.email-toolbar button\s*\{[^}]*border-radius:\s*10px/);
 assert.match(css,/@media\s*\(max-width:\s*899px\)/);
});

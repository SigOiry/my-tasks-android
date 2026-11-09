import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
const css=readFileSync(new URL('../web/app.css',import.meta.url),'utf8');
test('every statically referenced UI selector exists exactly once in the shipped markup',()=>{
 const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]),referenced=[...source.matchAll(/\$\('([^']+)'\)/g)].map(m=>m[1]);
 const missing=[...new Set(referenced.filter(id=>!ids.includes(id)))];
 assert.deepEqual(missing,[],'Missing markup required by production app');
 assert.equal(new Set(ids).size,ids.length,'Duplicate HTML IDs');
});
const luminance=hex=>{const rgb=hex.replace('#','').match(/../g).map(c=>parseInt(c,16)/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4);return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;};
const contrast=(a,b)=>{const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);};
test('light/dark text and primary roles meet normal-text 4.5:1 contrast',()=>{
 for(const [fg,bg] of [['#005ac8','#ffffff'],['#63636b','#ffffff'],['#63636b','#f2f2f7'],['#8bbdff','#0b0d12'],['#aaaab3','#1c1c1e'],['#f5f5f7','#1c1c1e']])assert.ok(contrast(fg,bg)>=4.5,fg+' / '+bg);
 assert.ok(css.includes('--primary:#005ac8'));assert.ok(css.includes('--primary:#8bbdff'));
});
test('licensed self-hosted typography and accessible material/motion controls ship together',()=>{
 const build=readFileSync(new URL('../build_web.py',import.meta.url),'utf8');
 assert.ok(build.includes("'inter-variable.woff2'"));assert.ok(build.includes("'INTER-LICENSE.txt'"));
 assert.ok(css.includes('@font-face'));assert.ok(css.includes('/assets/inter-variable.woff2'));
 for(const id of ['motion','transparency','password-pane','username','password'])assert.ok(html.includes(`id="${id}"`));
 assert.ok(!html.includes('access-token'));assert.ok(!html.includes('chat stay in page memory only'));
});
test('new client never posts stateless chat or serializes client history',()=>{
 assert.ok(!source.includes("request('/api/chat'"));assert.ok(!source.includes('chatBody('));
});

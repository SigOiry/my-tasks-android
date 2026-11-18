import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
const file=new URL('../../design/tokens.json',import.meta.url);
const luminance=hex=>{const v=hex.slice(1).match(/../g).map(x=>parseInt(x,16)/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);return v[0]*.2126+v[1]*.7152+v[2]*.0722;};
const contrast=(a,b)=>(Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05);
test('shared foundation is explicit and readable in light and dark',()=>{
 assert.ok(existsSync(file),'shared semantic token manifest missing');
 const tokens=JSON.parse(readFileSync(file,'utf8'));
 for(const theme of ['light','dark']){
  const t=tokens.colors[theme];
  for(const fg of ['ink','muted','primary','danger','warning'])for(const bg of ['canvas','surface'])assert.ok(contrast(t[fg],t[bg])>=4.5,`${theme}: ${fg}/${bg}`);
  for(const bg of ['nav','navSelected'])for(const fg of ['onNav','navMuted'])assert.ok(contrast(t[fg],t[bg])>=4.5,`${theme}: ${fg}/${bg}`);
  assert.ok(contrast(t.onAction,t.action)>=4.5,theme+' primary action');
 }
 assert.ok(tokens.target.web>=44&&tokens.target.android>=48);
});
test('shipping web semantic palette matches shared tokens without shadow overrides',()=>{
 const tokens=JSON.parse(readFileSync(file,'utf8'));
 const css=readFileSync(new URL('../web/app.css',import.meta.url),'utf8');
 const declarations=block=>Object.fromEntries([...block.matchAll(/--([a-z-]+)\s*:\s*(#[a-f0-9]{3,8})/gi)].map(m=>[m[1],m[2].toUpperCase()]));
 for(const theme of ['light','dark']){
  const selector=theme==='light'?/:root\s*\{([^}]+)\}/s:/:root\[data-theme=dark\]\s*\{([^}]+)\}/s;
  const base=declarations(css.match(/:root\s*\{([^}]+)\}/s)[1]);
  const actual={...base,...declarations(css.match(selector)[1])};
  for(const role of ['canvas','surface','ink','muted','primary','action','soft','border','separator','danger','warning'])assert.equal(actual[role],tokens.colors[theme][role],`${theme}.${role}`);
  for(const [property,role] of Object.entries({'nav':'nav','on-nav':'onNav','nav-muted':'navMuted','nav-selected':'navSelected'}))assert.equal(actual[property],tokens.colors[theme][role],`${theme}.${role}`);
 }
 const automatic=declarations(css.match(/:root:not\(\[data-theme=light\]\):not\(\[data-theme=dark\]\)\s*\{([^}]+)\}/s)[1]);
 for(const role of ['canvas','surface','ink','muted','primary','action','soft','border','separator','danger','warning','nav'])assert.equal(automatic[role],tokens.colors.dark[role],`automatic dark.${role}`);
});

import {safeSource} from './policy.mjs';
function safePublicURL(value){try{if(typeof value!=='string'||/[\s\u0000-\u001f]/.test(value))return null;const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password?value:null;}catch{return null;}}
const markers=/(?:cite|filecite|filenav)[^]*(?:|$)|\[\[(?:source|citation):[^\]]*\]\]/g;
export function citationPresentation(turn){
 const original=String(turn?.content??''),metadata=turn?.citation_metadata,rows=Array.isArray(metadata?.sources)?metadata.sources:[],sources=[];
 for(const [index,row] of rows.entries()){if(!row||typeof row!=='object')continue;const unavailable=row.available===false||row.verification==='unavailable';const url=!unavailable&&['verified','page_read','search_result','retrieved','validated','grounded'].includes(row.verification)?safePublicURL(row.url):null;const email=row.kind==='email'||row.kind==='saved_email';const resource_id=typeof row.resource_id==='string'?row.resource_id:null,task_id=typeof row.task_id==='string'?row.task_id:null;sources.push({id:typeof row.id==='string'?row.id:'unavailable-'+index,label:typeof row.label==='string'?row.label:row.subject||'Source',kind:email?'email':row.kind==='task'?'task':'web',url,resource_id,task_id,sender:row.sender,subject:row.subject,date:row.date,excerpt:row.excerpt,available:!unavailable&&!!(url||(email&&resource_id&&task_id)||(row.kind==='task'&&resource_id&&row.verification==='saved_context')),reason:row.reason});}
 // Legacy links stay restricted to already-supported provider destinations. Markdown itself stays inert.
 if(!sources.length){const re=/\[([^\]]+)\]\(/g;let match;while((match=re.exec(original))){let level=1,end=re.lastIndex;for(;end<original.length;end++){if(original[end]==='(')level++;if(original[end]===')'&&!--level)break;}if(level)continue;const url=safeSource(original.slice(re.lastIndex,end));if(url)sources.push({id:'legacy-'+sources.length,label:match[1],kind:'web',url,available:true});re.lastIndex=end+1;}}
 // Source receipts do not map provider marker identities; keep unresolved claims explicit.
 let hadMarker=false;const text=original.replace(markers,()=>{hadMarker=true;return '[Source unavailable]';}).replace(/[^\n]*?(?:|$)/g,'[Source unavailable]');if(hadMarker&&!sources.length)sources.push({id:'unavailable',label:'Source unavailable',available:false});return {text,sources};
}
export function renderCitations(turn,{document=globalThis.document,request,alive=()=>true}={}){
 const presentation=citationPresentation(turn),root=document.createElement('div');root.className='citation-sources';
 const make=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=String(text);return n;};
 const warnings=turn?.citation_metadata?.warnings;
 if(Array.isArray(warnings))for(const warning of warnings.filter(w=>typeof w==='string'&&w.trim()).slice(0,8)){const notice=make('p',warning.trim().slice(0,1000));notice.className='citation-warning';notice.setAttribute('role','status');root.append(notice);}
 for(const [i,source] of presentation.sources.entries()){
  const details=make('details'),chip=make('summary',`${i+1} · ${source.available?source.label:'Source unavailable'}`),panel=make('div');details.className='citation-source';panel.className='citation-panel';chip.setAttribute('aria-label',`Source ${i+1}: ${source.label}`);details.append(chip,panel);
  const display=s=>{panel.replaceChildren();panel.append(make('strong',s.subject||s.label||source.label));for(const field of ['sender','date','excerpt'])if(typeof s[field]==='string'&&s[field])panel.append(make('p',s[field]));if(s.available===false)panel.append(make('p',s.reason||'This source is unavailable or the account is disconnected.'));if(source.url&&s.available!==false){const link=make('a','Open source ↗');link.setAttribute('href',source.url);link.setAttribute('target','_blank');link.setAttribute('rel','noopener noreferrer');panel.append(link);}};display(source);
  if(source.kind==='email'&&source.available&&request){let loaded=false,generation=0;details.addEventListener('toggle',async()=>{if(!details.open||loaded)return;loaded=true;const ticket=++generation;panel.replaceChildren(make('p','Loading private source…'));try{const result=await request('/api/tasks/'+encodeURIComponent(source.task_id)+'/email-sources/'+encodeURIComponent(source.resource_id));if(!alive()||ticket!==generation)return;const row=result?.source;if(!row||typeof row!=='object')throw new Error('Source unavailable.');display({...source,...row});}catch(e){if(alive()&&ticket===generation)display({...source,available:false,reason:e.message||'Source unavailable.'});}});}
  root.append(details);
 }
 return {text:presentation.text,element:root};
}

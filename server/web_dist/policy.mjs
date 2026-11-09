// Shared, side-effect-free presentation and privacy policy. No DOM or storage.
export const statuses = {inbox:'Inbox',todo:'To do',in_progress:'In progress',waiting:'Waiting',completed:'Completed',cancelled:'Cancelled'};
export const priorities = {high:0,medium:1,low:2};
export const active = task => !task.deleted && !['completed','cancelled'].includes(task.status);
export function selectTasks(tasks, options) {
 const {view,today,week_end,project,query='',status,sort='priority'} = options;
 const q = query.toLocaleLowerCase().trim();
 return tasks.filter(t => {
  if (view === 'trash' ? !t.deleted : t.deleted) return false;
  if (['active','today','week'].includes(view) && !active(t)) return false;
  if (view === 'today' && (!t.due_date || t.due_date > today)) return false;
  if (view === 'week' && (!t.due_date || t.due_date > week_end)) return false;
  if (project && (t.project || 'Unassigned') !== project) return false;
  if (status && t.status !== status) return false;
  return !q || [t.title,t.description,t.notes,t.project,...(t.source_accounts||[])].some(v => String(v||'').toLocaleLowerCase().includes(q));
 }).sort((a,b) => {
  const unread = Number(!!b.notification?.unread)-Number(!!a.notification?.unread);
  const priority = (priorities[a.priority]??9)-(priorities[b.priority]??9);
  const date = (a.due_date || '9999').localeCompare(b.due_date || '9999');
  return unread || (sort === 'title' ? a.title.localeCompare(b.title) : sort === 'date' ? date || priority : priority || date) || a.title.localeCompare(b.title) || a.id.localeCompare(b.id);
 });
}
export function chatBody(task_id,message,mode,history=[]) {
 if (!message.trim() || [...message].length > 8000) throw new Error('Message must contain 1–8000 characters.');
 if (!['question','email_draft'].includes(mode)) throw new Error('Invalid chat mode.');
 const valid = history.filter(t=>['user','assistant'].includes(t.role) && typeof t.content==='string' && t.content.length>0 && [...t.content].length<=8000);
 let turns = valid.slice(-12).map(t=>({role:t.role,content:t.content}));
 const encode = () => JSON.stringify({task_id,message,mode,history:turns});
 while (new TextEncoder().encode(encode()).length>60000 && turns.length) turns = turns.slice(2);
 if (new TextEncoder().encode(encode()).length>60000) throw new Error('Message is too large in UTF-8; please shorten it.');
 return encode();
}
export function shouldNotify(event,task,{baseline,tracking,ownToken}={}) {
 return !!(!baseline && task && !task.deleted && ['created','updated','restored'].includes(event.kind) && event.token !== ownToken && (tracking === false || (tracking === true && task.notification?.unread === true)));
}
export function updateGate(task) {
 const prose=task.notification.sections;
 const sections=prose&&typeof prose==='object'?[['context','Context'],['update','New'],['next_steps','Suggested next steps']].filter(([key])=>typeof prose[key]==='string'&&prose[key].trim()).map(([key,heading])=>({heading,text:prose[key]})):null;
 return {id:task.id,token:task.notification.token,title:task.title,headline:task.notification.headline,sections,summary:sections===null?[...(task.notification.summary||[])]:[]};
}
export function acknowledged(gate,task) {
 return !!task && task.notification?.unread === false && task.notification?.token === gate.token;
}
export function editPayload(task,fields) {
 const result = {};
 for (const key of ['title','description','status','priority','due_date','project','notes']) {
  if (Object.hasOwn(fields,key)) result[key] = ['project','due_date'].includes(key) && !fields[key] ? null : fields[key];
 }
 if (task) result.revision=task.revision;
 return result;
}
export function safeSource(value) {
 try {
  const u = new URL(value);
  if (u.protocol !== 'https:' || u.port || u.username || u.password || /\s/.test(value)) return null;
  if ((u.hostname === 'mail.google.com' && u.pathname.startsWith('/mail/')) || (u.hostname === 'calendar.google.com' && u.pathname.startsWith('/calendar/'))) return value;
 } catch { /* Fail closed, never construct a provider URL from guesses. */ }
 return null;
}
export function groupTasks(tasks) {
 const groups = new Map();
 for (const t of tasks) {
  const name = t.project || 'Unassigned';
  if (!groups.has(name)) groups.set(name,[]);
  groups.get(name).push(t);
 }
 return [...groups].map(([name,tasks])=>({name,tasks}));
}

export function passwordError(value,confirmation) {
 const length=Array.from(value).length;
 if(length<15 || length>128)return 'New password must contain 15–128 characters.';
 if(new TextEncoder().encode(value).length>1024)return 'New password must be at most 1024 UTF-8 bytes.';
 if(value!==confirmation)return 'The new passwords do not match.';
 return '';
}

// Restricted presentation AST: no HTML interpretation, attributes, URLs or active links.
export function safeMarkdown(value) {
 const el=(tag,children)=>({tag,children});
 function inline(text,depth=0) {
  if(depth>8)return [text];
  const out=[];let plain='';
  const flush=()=>{if(plain){out.push(plain);plain='';}};
  for(let i=0;i<text.length;) {
   if(text[i]==='\\' && i+1<text.length){plain+=text[i+1];i+=2;continue;}
   const image=text[i]==='!'&&text[i+1]==='[',start=image?i+1:i;
   if(text[start]==='[') {
    const end=text.indexOf('](',start+1);
    if(end!==-1){let close=end+2,level=1;for(;close<text.length;close++){if(text[close]==='(')level++;if(text[close]===')'&&!--level)break;}
     if(level===0){flush();out.push(...inline(text.slice(start+1,end),depth+1));i=close+1;continue;}}
   }
   const mark=text.startsWith('**',i)?'**':text[i]==='*'?'*':text[i]==='`'?'`':null;
   if(mark){const end=text.indexOf(mark,i+mark.length);if(end>i+mark.length){flush();const content=text.slice(i+mark.length,end);out.push(el(mark==='`'?'code':mark==='**'?'strong':'em',mark==='`'?[content]:inline(content,depth+1)));i=end+mark.length;continue;}}
   plain+=text[i++];
  }
  flush();return out;
 }
 const lines=String(value??'').replace(/\r\n?/g,'\n').split('\n'),out=[];
 for(let i=0;i<lines.length;){
  if(!lines[i].trim()){i++;continue;}
  if(lines[i].startsWith('```')){const end=lines.findIndex((line,j)=>j>i&&/^```\s*$/.test(line));if(end!==-1){out.push(el('pre',[el('code',[lines.slice(i+1,end).join('\n')])]));i=end+1;continue;}}
  const list=lines[i].match(/^\s*(?:([-+*])|\d+\.)\s+(.+)$/);
  if(list){const tag=list[1]?'ul':'ol',items=[];while(i<lines.length){const m=lines[i].match(/^\s*(?:([-+*])|\d+\.)\s+(.+)$/);if(!m||(m[1]?'ul':'ol')!==tag)break;items.push(el('li',inline(m[2])));i++;}out.push(el(tag,items));continue;}
  const paragraph=[lines[i++]];while(i<lines.length&&lines[i].trim()&&!/^\s*(?:[-+*]|\d+\.)\s+/.test(lines[i])&&!lines[i].startsWith('```'))paragraph.push(lines[i++]);out.push(el('p',inline(paragraph.join('\n'))));
 }
 return out;
}

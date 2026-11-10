// Shared, side-effect-free presentation and privacy policy. No DOM or storage.
export const statuses = {inbox:'Inbox',todo:'To do',in_progress:'In progress',waiting:'Waiting',completed:'Completed',cancelled:'Cancelled'};
export const priorities = {high:0,medium:1,low:2};
export const projectColors=[['#527FC2','#8FB2E8'],['#3C8B83','#82C8BA'],['#8972BB','#BBA7E5'],['#B26D87','#DFA2B9'],['#A57A35','#D4B26F'],['#6C7E91','#A6B7C9']];
export function projectPalette(task) {
 const key=task.project_id || task.project;
 if(!key)return {index:null,light:'#6C7E91',dark:'#A6B7C9'};
 let hash=2166136261;
 for(const byte of new TextEncoder().encode(key))hash=Math.imul(hash^byte,16777619)>>>0;
 const index=hash%6;return {index,light:projectColors[index][0],dark:projectColors[index][1]};
}
// Animation identity lives beyond row binding; only active visible time counts.
export class UpdateBreaths {
 constructor(limit=2){this.limit=limit;this.records=new Map();this.observed=new Set();this.active=[];this.last=null;}
 observe(id,token,unread){
  const key=JSON.stringify([id,token]);
  if(!token||!unread){this.records.delete(id);return;}
  if(this.records.get(id)?.token===token)return;
  this.active=this.active.filter(activeId=>activeId!==id);
  const seen=this.observed.has(key);this.observed.add(key);this.records.set(id,{token,elapsed:seen?6400:0});
 }
 tick(visible,now){
  if(this.last!==null)for(const id of this.active){const record=this.records.get(id);if(record)record.elapsed=Math.min(6400,record.elapsed+Math.max(0,now-this.last));}
  this.last=now;this.active=visible.filter(id=>this.records.has(id)&&this.age(id)<6400).slice(0,this.limit);return [...this.active];
 }
 age(id){return this.records.get(id)?.elapsed??6400;}
 clear(){this.records.clear();this.observed.clear();this.active=[];this.last=null;}
}
export const active = task => !task.deleted && !['completed','cancelled'].includes(task.status);
export function calendarDay(value) {
 if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value))return null;
 const ms=Date.parse(value+'T00:00:00Z');return Number.isFinite(ms)&&new Date(ms).toISOString().slice(0,10)===value?ms/86400000:null;
}
export function validInstant(value) {
 if(typeof value!=='string')return null;
 const match=value.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:Z|([+-])(\d{2}):(\d{2}))$/);
 if(!match||calendarDay(match[1])===null||Number(match[2])>23||Number(match[3])>59||Number(match[4])>59||Number(match[6]||0)>23||Number(match[7]||0)>59)return null;
 const ms=Date.parse(value);return Number.isFinite(ms)?ms:null;
}
export function localCalendarDate(value=new Date(),timezone='Europe/Rome') {
 const parts=Object.fromEntries(new Intl.DateTimeFormat('en-US',{timeZone:timezone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(value).map(part=>[part.type,part.value]));
 return `${parts.year}-${parts.month}-${parts.day}`;
}
export function smartScore(task,today,timezone='Europe/Rome') {
 const day=calendarDay(today),due=calendarDay(task.due_date),delta=day!==null&&due!==null?due-day:null;
 const deadline=delta===null?0:delta<0?50:delta===0?40:delta===1?25:delta<=7?10:0;
 const instant=validInstant(task.created_at),created=instant===null?null:calendarDay(localCalendarDate(new Date(instant),timezone));
 const age=created!==null&&day!==null?Math.min(4,Math.max(0,Math.floor((day-created)/7))):0;
 return deadline+({high:30,medium:15,low:0}[task.priority]||0)+(task.status==='in_progress'?6:0)+(task.notification?.unread===true?6:0)+age;
}
const compare=(a,b)=>String(a)<String(b)?-1:String(a)>String(b)?1:0;
export function selectTasks(tasks, options) {
 const {view,today,week_end,project,query='',status,sort='smart',direction='asc',timezone='Europe/Rome'} = options;
 const q = query.toLocaleLowerCase().trim();
 return tasks.filter(t => {
  if (view === 'trash' ? !t.deleted : t.deleted) return false;
  if (['active','today','week'].includes(view) && !active(t)) return false;
  if (view === 'today' && (calendarDay(t.due_date)===null || t.due_date > today)) return false;
  if (view === 'week' && (calendarDay(t.due_date)===null || t.due_date > week_end)) return false;
  if (project && (t.project || 'Unassigned') !== project) return false;
  if (status && t.status !== status) return false;
  return !q || [t.title,t.description,t.notes,t.project,...(t.source_accounts||[])].some(v => String(v||'').toLocaleLowerCase().includes(q));
 }).sort((a,b) => {
  const bucket=t=>['completed','cancelled'].includes(t.status)?2:t.status==='waiting'?1:0;
  const priority=(priorities[a.priority]??9)-(priorities[b.priority]??9);
  const date=compare(calendarDay(a.due_date)===null?'9999':a.due_date,calendarDay(b.due_date)===null?'9999':b.due_date);
  const timestamp=validInstant;
  const created=(timestamp(a.created_at)??Infinity)-(timestamp(b.created_at)??Infinity);
  let primary=sort==='smart'?smartScore(b,today,timezone)-smartScore(a,today,timezone):sort==='project'?compare(a.project||'\uffff',b.project||'\uffff'):['date','due'].includes(sort)?date:sort==='title'?compare(a.title,b.title):sort==='updated'?(timestamp(b.updated_at)??-Infinity)-(timestamp(a.updated_at)??-Infinity):sort==='created'?created:priority;
  // Legacy saved priority/date choices retain their unread-first behavior.
  if(['priority','date'].includes(sort))primary=Number(!!b.notification?.unread)-Number(!!a.notification?.unread)||primary;
  // Missing nullable primary keys stay last independently of direction.
  // Keep legacy saved date/priority behavior untouched.
  const missing=sort==='due'?Number(calendarDay(a.due_date)===null)-Number(calendarDay(b.due_date)===null):['created','updated'].includes(sort)?Number(timestamp(a[sort==='created'?'created_at':'updated_at'])===null)-Number(timestamp(b[sort==='created'?'created_at':'updated_at'])===null):0;
  return bucket(a)-bucket(b)||missing||(Number.isNaN(primary)?0:primary)*(direction==='desc'?-1:1)||date||priority||(Number.isNaN(created)?0:created)||compare(a.id,b.id);
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
 function closingMark(text,mark,start){if(mark==='`')return text.indexOf(mark,start);const stack=[];for(let p=start;p<text.length;){if(text[p]==='\\'){p+=2;continue;}if(text[p]==='`'){const end=text.indexOf('`',p+1);if(end!==-1){p=end+1;continue;}}if(text[p]!=='*'){p++;continue;}let token=text.startsWith('**',p)?'**':'*';if(stack.at(-1)==='*'&&text.startsWith('***',p))token='*';if(!stack.length&&token===mark)return p;if(stack.at(-1)===token)stack.pop();else stack.push(token);p+=token.length;}return -1;}
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
   if(mark){const end=closingMark(text,mark,i+mark.length);if(end>i+mark.length){flush();const content=text.slice(i+mark.length,end);out.push(el(mark==='`'?'code':mark==='**'?'strong':'em',mark==='`'?[content]:inline(content,depth+1)));i=end+mark.length;continue;}}
   plain+=text[i++];
  }
  flush();return out;
 }
 const lines=String(value??'').replace(/\r\n?/g,'\n').split('\n'),out=[];
 const listMatch=line=>line.match(/^(\s*)(?:([-+*])|(\d+)\.)\s+(.+)$/);
 function listAt(index,indent,depth=0){const first=listMatch(lines[index]),tag=first[2]?'ul':'ol',items=[];let i=index;
  while(i<lines.length){const m=listMatch(lines[i]);if(!m||m[1].length!==indent||(m[2]?'ul':'ol')!==tag)break;const children=inline(m[4]);i++;
   while(i<lines.length){const nested=listMatch(lines[i]);if(!nested||nested[1].length<=indent||depth>=8)break;const result=listAt(i,nested[1].length,depth+1);children.push(result.element);i=result.next;}
   items.push(el('li',children));
  }
  const element=el(tag,items);if(tag==='ol'){const start=Number(first[3]);if(Number.isSafeInteger(start)&&start>=0&&start<=1000000&&start!==1)element.start=start;}return {element,next:i};
 }
 for(let i=0;i<lines.length;){
  if(!lines[i].trim()){i++;continue;}
  if(lines[i].startsWith('```')){const end=lines.findIndex((line,j)=>j>i&&/^```\s*$/.test(line));if(end!==-1){out.push(el('pre',[el('code',[lines.slice(i+1,end).join('\n')])]));i=end+1;continue;}}
  const list=listMatch(lines[i]);
  if(list){const result=listAt(i,list[1].length);out.push(result.element);i=result.next;continue;}
  const paragraph=[lines[i++]];while(i<lines.length&&lines[i].trim()&&!/^\s*(?:[-+*]|\d+\.)\s+/.test(lines[i])&&!lines[i].startsWith('```'))paragraph.push(lines[i++]);out.push(el('p',inline(paragraph.join('\n'))));
 }
 return out;
}

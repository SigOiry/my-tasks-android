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
 return {id:task.id,token:task.notification.token,title:task.title,headline:task.notification.headline,summary:[...(task.notification.summary||[])]};
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

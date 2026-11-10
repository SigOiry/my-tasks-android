// Thin adapter: calls actual production exports, never computes a score/hash/order.
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
const [fixture, modulePath] = process.argv.slice(2);
const vectors = JSON.parse(readFileSync(fixture, 'utf8'));
const policy = await import(pathToFileURL(modulePath).href);
for (const name of ['projectPalette','smartScore','selectTasks']) {
  if (typeof policy[name] !== 'function') throw new Error(`Missing production export: ${name}`);
}
const out = {contexts:{},palette:{},scores:{},orders:{}};
for (const [name, context] of Object.entries(vectors.contexts)) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {timeZone:context.timezone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(context.now)).map(p=>[p.type,p.value]));
  out.contexts[name] = `${parts.year}-${parts.month}-${parts.day}`;
}
for (const item of vectors.palette) {
  const color = policy.projectPalette(structuredClone(item.task));
  out.palette[item.name] = {slot:color.index===null?-1:color.index,light:color.light.toUpperCase(),dark:color.dark.toUpperCase()};
}
for (const item of vectors.scores) {
  const context = vectors.contexts[item.context];
  process.env.TZ = context.timezone;
  out.scores[item.name] = policy.smartScore(structuredClone(item.task),out.contexts[item.context],context.timezone);
}
for (const item of vectors.orders) {
  const context = vectors.contexts[item.context];
  process.env.TZ = context.timezone;
  const tasks = structuredClone(item.tasks);
  const before = JSON.stringify(tasks);
  const options = {view:item.view,sort:'smart',direction:'asc',today:out.contexts[item.context],timezone:context.timezone};
  const ids = policy.selectTasks(tasks,options).map(t=>t.id);
  if (JSON.stringify(tasks)!==before) throw new Error(`Production selector mutated canonical input: ${item.name}`);
  // Exercise stable ties independently of arrival order; rank before truncating.
  const reversed = policy.selectTasks([...tasks].reverse(),options).map(t=>t.id);
  if (JSON.stringify(ids)!==JSON.stringify(reversed)) throw new Error(`Arrival-order dependent result: ${item.name}`);
  out.orders[item.name] = ids;
}
console.log(JSON.stringify(out));

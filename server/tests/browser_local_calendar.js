/* Run in Chromium with timezone override and the fixture preload below.
 * Synthetic transport only; production app modules and DOM are exercised. */
(async()=>{
 const f=window.__calendarFixture;
 if(!f)throw Error('calendar fixture preload required');
 const assert=(value,label)=>{if(!value)throw Error(label);};
 const rows=()=>[...document.querySelectorAll('.task-row')].map(e=>e.dataset.taskId);
 const wait=()=>new Promise(r=>setTimeout(r,700));
 await wait();
 assert(Intl.DateTimeFormat().resolvedOptions().timeZone===f.timezone,'browser timezone override');
 assert(document.getElementById('workspace-date').textContent===f.heading,'visible date uses local calendar, not server metadata');
 assert(JSON.stringify(rows())===JSON.stringify(['due','tomorrow','aged','rival']),'live Smart order includes local creation-calendar age');
 assert(document.querySelector('[data-task-id="due"] .due').textContent.startsWith('Today · '),'local Today due label');
 assert(!document.querySelector('[data-task-id="tomorrow"] .due').textContent.includes('Today'),'tomorrow is not labelled Today');
 document.querySelector('#views [data-view="today"]').click();await wait();
 assert(JSON.stringify(rows())===JSON.stringify(['due']),'Today scope uses local date');
 document.querySelector('#extra-views [data-view="week"]').click();await wait();
 assert(JSON.stringify(rows())===JSON.stringify(['due']),'Week ends on local Sunday, not server Sunday');
 return {timezone:f.timezone,instant:f.instant,passed:7};
})()

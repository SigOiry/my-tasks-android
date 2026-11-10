/* Synthetic loopback browser acceptance. No provider requests or production writes. */
(async()=>{
 if(location.hostname!=='127.0.0.1'||location.port!=='9138')throw Error('Scratch loopback service required');
 const $=id=>document.getElementById(id),results=[];
 const assert=(value,label)=>{if(!value)throw Error(label);results.push(label);};
 const wait=async()=>new Promise(resolve=>setTimeout(resolve,350));
 const filters=document.querySelector('.filters'),summary=filters.querySelector('summary');
 filters.open=true;await wait();
 assert(getComputedStyle(document.querySelector('.filterbar')).position==='absolute','filters are anchored rather than reflowing tasks');
 const rect=document.querySelector('.filterbar').getBoundingClientRect();
 assert(rect.left>=0&&rect.right<=innerWidth,'anchored filter fits compact viewport');
 for(const [id,value] of [['sort','smart'],['sort-direction','asc'],['color-mode','project']]){$(id).value=value;$(id).dispatchEvent(new Event('change'));}
 document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await wait();
 assert(!filters.open&&document.activeElement===summary,'Escape closes anchored filters and restores trigger focus');
 const policy=await import('/assets/policy.mjs');
 const data=await(await fetch('/api/tasks')).json();
 assert(JSON.stringify([...document.querySelectorAll('.task-row')].map(row=>row.dataset.taskId))===JSON.stringify(policy.selectTasks(data.tasks,{view:'active',today:policy.localCalendarDate(),sort:'smart'}).map(task=>task.id)),'rendered Smart order matches full eligible dataset');
 for(const [key,value] of [['sort','smart'],['direction','asc'],['color','project']])assert(localStorage.getItem('mytasks.web.'+key)===value,'persists '+key);
 const first=document.querySelector('.task-row');
 assert(first&&getComputedStyle(first).borderInlineStartWidth==='2px','project accents stay slim');
 assert([...document.querySelectorAll('.priority-label')].every(e=>['High','Medium','Low'].includes(e.textContent)),'priority has explicit readable labels');
 assert(document.documentElement.scrollWidth<=innerWidth,'no horizontal overflow');
 return {passed:results.length,results};
})()

/* CDP Page.addScriptToEvaluateOnNewDocument fixture; set __calendarFixture first.
 * No network authentication, credentials, provider calls or real task writes. */
{
 const f=window.__calendarFixture,NativeDate=Date;
 window.Date=class extends NativeDate {constructor(...args){super(...(args.length?args:[f.instant]));}static now(){return NativeDate.parse(f.instant);}};
 const task=(id,extra={})=>({id,title:id,status:'todo',priority:'low',notification:{unread:false},...extra});
 const data={today:f.serverToday,week_end:f.serverWeekEnd,tasks:[task('rival',{due_date:'2027-04-01'}),task('aged',{created_at:f.created}),task('due',{due_date:f.today}),task('tomorrow',{due_date:f.tomorrow})]};
 const original=fetch;
 window.fetch=async(path,opts)=>{
  if(String(path).startsWith('/auth/session'))return Response.json({authenticated:true,csrf:'synthetic-calendar-test'});
  if(String(path).startsWith('/api/tasks'))return Response.json(data);
  if(String(path).startsWith('/api/'))return Response.json({events:[],cursor:0,conversations:[]});
  return original(path,opts);
 };
 localStorage.clear();
}

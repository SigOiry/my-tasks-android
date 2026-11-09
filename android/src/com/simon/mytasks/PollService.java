package com.simon.mytasks;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;

public final class PollService extends JobService {
 static final String CHANNEL="task-updates";
 static final class Run {volatile boolean stopped;}
 Run currentRun;
 static void schedule(Context c){
  channels(c);
  JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
  j.schedule(new JobInfo.Builder(1701,new ComponentName(c,PollService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(Math.max(15*60*1000L,JobInfo.getMinPeriodMillis())).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
 }
 static void channels(Context c){
  NotificationChannel channel=new NotificationChannel(CHANNEL,"Task updates",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("Best-effort background checks, every 15 minutes or later");channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
  ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
 }
 interface Stop {boolean get();}
 static void poll(Context c,Store store,Store.Account account,Stop stop)throws Exception{
  Api api=new Api(account);channels(c);
  boolean baseline=store.bootstrapped();long cursor=store.cursor();long until=SystemClock.elapsedRealtime()+20000;
  for(int page=0;page<10&&!stop.get()&&store.current(account)&&SystemClock.elapsedRealtime()<until;page++){
   JSONObject data=api.call("GET","/api/events?after="+cursor+"&limit=100",null);JSONArray events=data.getJSONArray("events");
   for(int i=0;i<events.length();i++){
    if(stop.get()||!store.current(account)||SystemClock.elapsedRealtime()>=until)return;
    JSONObject event=events.getJSONObject(i);long id=event.getLong("id");if(id<=cursor)continue;
    if(baseline){
     String taskId=event.getString("task_id");JSONObject task;
     try{task=api.call("GET","/api/tasks/"+Api.segment(taskId),null);}catch(Api.Failure e){if(e.code==404){store.cursor(account,cursor=Core.advance(cursor,id,true),true);continue;}throw e;}
     JSONObject n=task.optJSONObject("notification");
     Boolean tracking=data.opt("notification_tracking") instanceof Boolean?(Boolean)data.opt("notification_tracking"):null;
     boolean unread=Core.eventUnread(tracking,n!=null&&n.optBoolean("unread"),task.optBoolean("deleted"),event.optString("kind"));
     if(n==null)n=new JSONObject();
     if(unread){
      NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
      boolean allowed=nm.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED);
      NotificationChannel channel=nm.getNotificationChannel(CHANNEL);allowed=allowed&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
      if(!Core.shouldNotify(true,allowed,true))return; // Retain pending event until permission is available.
      synchronized(Store.class){
       if(stop.get()||!store.current(account))return;
       Intent intent=new Intent(c,MainActivity.class).setAction("com.simon.mytasks.OPEN").setData(android.net.Uri.parse("mytasks://task/"+Api.segment(taskId))).putExtra("task_id",taskId).putExtra("account_epoch",account.epoch);
       PendingIntent pi=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
       Notification publicVersion=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("My Tasks").setContentText("A task has an update").build();
       String headline=n.optBoolean("unread")?n.optString("headline","Task updated"):"Task "+event.optString("kind","updated");
       CharSequence summary=MarkdownText.render(summary(n));summary=summary.length()==0?task.optString("title","Task updated"):summary;
       Notification.Builder builder=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(task.optString("title","Task updated")).setContentText(headline).setStyle(new Notification.BigTextStyle().bigText(summary)).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true);if(Build.VERSION.SDK_INT>=29)builder.setAllowSystemGeneratedContextualActions(false);Notification notification=builder.build();
       String tag=NotificationIdentity.tag(account.epoch,taskId,n.optString("token",""));
       // Only supersede this account/task's exact, versioned identities. Legacy
       // task-id tags have unknown epoch/token and are never cancelled by ACK.
       for(android.service.notification.StatusBarNotification active:nm.getActiveNotifications()){
        if(active.getId()==0&&NotificationIdentity.sameTask(active.getTag(),account.epoch,taskId)&&!tag.equals(active.getTag()))nm.cancel(active.getTag(),0);
       }
       nm.notify(tag,0,notification);
      }
     }
    }
    if(stop.get())return;
    cursor=Core.advance(cursor,id,true);store.cursor(account,cursor,baseline);
   }
   if(!data.optBoolean("has_more")){
    cursor=Math.max(cursor,data.optLong("cursor",cursor));store.cursor(account,cursor,true);return;
   }
  }
 }
 static String summary(JSONObject n){return NotificationProse.markdown(n);}
 @Override public boolean onStartJob(JobParameters p){final Run run=new Run();currentRun=run;Api.IO.execute(()->{boolean retry=false;try{Store s=new Store(this);Store.Account a=s.account();if(a!=null)poll(this,s,a,()->run.stopped);}catch(Exception e){retry=true;}final boolean again=retry;new Handler(Looper.getMainLooper()).post(()->{if(!run.stopped&&currentRun==run)jobFinished(p,again);});});return true;}
 @Override public boolean onStopJob(JobParameters p){if(currentRun!=null)currentRun.stopped=true;return true;}
}

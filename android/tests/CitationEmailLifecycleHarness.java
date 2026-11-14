package com.simon.mytasks;
import org.json.*;import java.util.*;
/** Synthetic transport and host UI seams; actual panel, detail and navigation methods. */
public class CitationEmailLifecycleHarness {
 static class Store {static class Account {}}
 static class Surfaces {long epoch=1;long generation(){return epoch;}boolean current(long g){return epoch==g;}}
 static class Queue {List<Runnable> tasks=new ArrayList<>();void execute(Runnable r){tasks.add(r);}void post(Runnable r){tasks.add(r);}void run(){tasks.remove(0).run();}}
 static class Api {static Queue IO=new Queue();static boolean fail;Api(Store.Account a){}JSONObject call(String m,String p,JSONObject b)throws Exception{if(fail)throw new Exception("synthetic failure");return new JSONObject().put("source",new JSONObject().put("subject",p));}}
 static class ConversationController {interface Listener {}int navigationVersion;String visible="thread-a";Listener listener;void cancelPoll(){}void save(){}
 // ACTUAL_HIDE
 }
 static class ConversationsUi implements ConversationController.Listener {String thread="thread-a";}
 static class MainActivity {
  Store.Account account=new Store.Account();Surfaces surfaces=new Surfaces();Queue main=new Queue();ConversationController conversations=new ConversationController();ConversationsUi conversationUi=new ConversationsUi();Object root=new Object();
  String selected="task-a",destination="Tasks",detailsMode="Hermès",pendingSummary="",notice="",shown="";boolean busy,editing,live=true;JSONObject draft;int detailY;Object listPane,detailPane;MainActivity ui=this;
  MainActivity(){conversations.listener=conversationUi;}boolean alive(Store.Account owner){return live&&account==owner;}void fullMessage(String s){shown=s;}void notice(String t,String b){notice=t;}Dialog dialogBuilder(){return new Dialog();}void startActivity(Object i){}Object task(String id){return new Object();}void captureDraft(){}boolean dirty(){return false;}void confirm(String t,String b,Runnable r){r.run();}void maybeSummary(){}void navigatePage(Object pane,boolean back){}
  // Render is a host seam for the actual lifecycle teardown below.
  void render(){conversations.hide();conversations.listener=null;conversationUi=null;root=new Object();}
  // ACTUAL_NAVIGATE
 }
 interface Choice {void run(Object d,int w);}
 static class Dialog {Dialog setTitle(String s){return this;}Dialog setMessage(String s){return this;}Dialog setNegativeButton(String s,Choice c){return this;}Dialog setPositiveButton(String s,Choice c){return this;}void show(){}}
 static class Intent {static final String ACTION_VIEW="view",CATEGORY_BROWSABLE="browser";Intent(String a,Object b){}Intent addCategory(String s){return this;}}
 static class android {static class net {static class Uri {static Object parse(String s){return s;}}}}
 static class CitationPolicy {static boolean safe(String s){return s.startsWith("https://");}}
 // ACTUAL_FIELDS
 // ACTUAL_METHODS
 static JSONObject source(String id)throws Exception{return new JSONObject().put("kind","saved_email").put("task_id","task-a").put("resource_id",id);}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  String scenario=args[0];MainActivity a=new MainActivity();panel(a,source("email-a"));check(Api.IO.tasks.size()==1,"validated email request was not dispatched");Api.fail=scenario.endsWith("error");Api.IO.run();check(a.main.tasks.size()==1,"completion was not queued");
  if(scenario.startsWith("task-leave"))a.navigate("");
  else if(scenario.startsWith("task-switch"))a.navigate("task-b");
  else if(scenario.startsWith("thread-switch")){a.conversations.visible="thread-b";a.conversationUi.thread="thread-b";}
  else if(scenario.startsWith("thread-leave"))a.conversations.hide();
  else if(scenario.startsWith("listener-replace"))a.conversations.listener=new ConversationsUi();
  else if(scenario.startsWith("leave-return")){a.navigate("task-b");a.navigate("task-a");a.detailsMode="Hermès";}
  else if(scenario.startsWith("account"))a.account=new Store.Account();
  else if(scenario.startsWith("private"))a.surfaces.epoch++;
  else if(scenario.startsWith("web-replaced"))panel(a,new JSONObject().put("kind","web").put("url","https://example.org"));
  else if(scenario.startsWith("superseded")){panel(a,source("email-b"));Api.fail=false;Api.IO.run();}
  else if(!scenario.startsWith("same-view"))throw new AssertionError("unknown scenario");
  a.main.run();
  if(scenario.equals("same-view-success"))check(a.shown.contains("/api/tasks/task-a/email-sources/email-a")&&a.notice.isEmpty(),"same-view success lost validated email detail");
  else if(scenario.equals("same-view-error"))check(a.shown.isEmpty()&&a.notice.equals("Email source unavailable"),"same-view failure lost notice");
  else {check(a.shown.isEmpty(),scenario+": delayed prior email opened atop current screen");check(a.notice.isEmpty(),scenario+": delayed prior error opened atop current screen");}
  if(scenario.startsWith("superseded")){a.main.run();check(a.shown.contains("/email-b"),"latest same-view source request did not complete");}
  System.out.println("CitationEmailLifecycleHarness passed: "+scenario);
 }
}

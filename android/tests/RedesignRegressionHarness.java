package com.simon.mytasks;
import java.util.*;import org.json.*;
/** Host-only synthetic transport/UI/OS seams. Methods are filled from current production. */
public class RedesignRegressionHarness extends Context {
 Store store=new Store();Store.Account account=new Store.Account("epoch-a");
 AuthPolicy.State authState=AuthPolicy.State.FULL;boolean foreground=true,quietInFlight,busy,online=true,editing,ackNeedsRefresh;
 UiReadGate quietGate=new UiReadGate();JSONArray tasks=new JSONArray();JSONObject draft,snapshot;String selected="synthetic",banner="",detailsMode="Details",destination="Tasks",pendingSummary="";
 TasksUi tasksUi=new TasksUi(this);ConversationUi conversationUi;TextView stateLabel;UpdateSheet summaryUi;final Queue main=new Queue();int confirmations;
 EditText title,description,notes,project,due;Spinner priority,status;String[] priorities={"high","medium","low"},statuses={"inbox","todo","in_progress","waiting","completed","cancelled"};ScrollView listScroll,detailScroll;int listY,detailY;Controller conversations=new Controller();
 void settings(){}void saveWorkspacePreferences(){}void buildListRows(){}boolean isDestroyed(){return false;}boolean alive(Store.Account a){return authState==AuthPolicy.State.FULL&&store.current(a);}
 void notice(String a,String b){}void confirm(String title,String message,Runnable action){confirmations++;action.run();}
 void render(){
  // HYDRATE_DRAFT
 }
 void renderPage(boolean returning){render();} // Host UI seam; motion exercised separately in NativeMotionMethodsHarness.
 void invalidateQuietRead(){quietGate.invalidate();quietInFlight=false;}
 // MAIN_METHODS
 static JSONObject task(String revision,String status,String token,boolean unread){return new JSONObject().put("id","synthetic").put("title","Synthetic").put("revision",revision).put("status",status).put("notification",new JSONObject().put("token",token).put("unread",unread));}
 static JSONObject data(JSONObject task){return new JSONObject().put("tasks",new JSONArray().put(task));}
 static RedesignRegressionHarness setup(){Api.IO.clear();Api.replies.clear();Api.failure=null;RedesignRegressionHarness h=new RedesignRegressionHarness();h.tasks.put(task("A","todo","A",true));h.draft=h.copy(h.task(h.selected));return h;}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 void finish(){Api.IO.run();main.run();}
 static void reload(){
  RedesignRegressionHarness h=setup();h.editing=true;h.draft.put("title","unsaved");Api.replies.add(data(task("B","todo","B",true)));h.confirmedReload();
  check(h.busy,"reload must enter actual intermediate render");h.finish();check("B".equals(h.draft.optString("revision")),"reload GET B must rebase editor revision, not retain A from intermediate render");check("Synthetic".equals(h.draft.optString("title")),"confirmed reload discards original unsaved fields on success");
  h=setup();h.editing=true;h.draft.put("title","unsaved");Api.failure=new RuntimeException("synthetic offline");h.confirmedReload();h.finish();check("unsaved".equals(h.draft.optString("title")),"failed reload must retain original input");
  h=setup();h.editing=true;h.conversationUi=new ConversationUi();h.draft.put("title","unsaved chat-side draft");Api.replies.add(data(task("B","todo","B",true)));h.confirmedReload();h.finish();check("B".equals(h.draft.optString("revision")),"chat-preserving render path must install canonical reload draft directly");
  h=setup();h.editing=true;h.draft.put("title","unsaved");Api.replies.add(data(task("B","todo","B",true)));h.refresh();h.finish();check("A".equals(h.draft.optString("revision"))&&"unsaved".equals(h.draft.optString("title")),"ordinary refresh preserves draft");
 }
 static void quick(){
  RedesignRegressionHarness h=setup();h.editing=true;Api.replies.add(task("B","completed","U",true));Api.replies.add(data(task("B","completed","U",true)));h.quickMutation("PATCH","/api/tasks/synthetic",h.task(h.selected),new JSONObject());h.finish();check("completed".equals(h.draft.optString("status"))&&"B".equals(h.draft.optString("revision")),"clean same-task editor must install canonical completed/B after quick mutation");
  h=setup();h.editing=true;h.conversationUi=new ConversationUi();Api.replies.add(task("B","completed","U",true));Api.replies.add(data(task("B","completed","U",true)));h.quickMutation("PATCH","/api/tasks/synthetic",h.task(h.selected),new JSONObject());h.finish();check("B".equals(h.draft.optString("revision"))&&"completed".equals(h.draft.optString("status")),"chat-preserving quick action must replace clean same-task draft directly");
  h=setup();h.editing=true;h.draft.put("title","unsaved");h.quickMutation("PATCH","/api/tasks/synthetic",h.task(h.selected),new JSONObject());check(Api.IO.size()==0&&"unsaved".equals(h.draft.optString("title")),"dirty same-task editor blocks quick action");
  h=setup();h.editing=true;h.draft.put("title","other draft");JSONObject other=task("Q","todo","Q",false).put("id","other");Api.replies.add(other);Api.replies.add(data(task("B","todo","H",true)).put("unused",true));h.quickMutation("PATCH","/api/tasks/other",other,new JSONObject());h.finish();check("other draft".equals(h.draft.optString("title"))&&"A".equals(h.draft.optString("revision")),"other task quick change preserves editor draft and revision");
 }
 static void post(RedesignRegressionHarness h,String epoch,String id,String token)throws Exception{
  Api.replies.add(new JSONObject().put("notification_tracking",true).put("events",new JSONArray().put(new JSONObject().put("id",1).put("task_id",id).put("kind","updated"))));
  Api.replies.add(task("R","todo",token,true).put("id",id));NativePoll.poll(h,h.store,new Store.Account(epoch),()->false);
 }
 static void ack()throws Exception{
  RedesignRegressionHarness h=setup();post(h,"epoch-a","synthetic","A");h.summaryUi=new UpdateSheet(UpdateReviewPolicy.capture(h.task(h.selected)));
  Api.replies.add(task("A","todo","A",false));h.markRead("synthetic","A");Api.IO.run();post(h,"epoch-a","synthetic","B");Notification newer=h.nm.active.values().iterator().next();h.main.run();
  check(h.nm.active.containsValue(newer),"ACK A callback must not cancel newer posted B while Activity still has A");
  check(h.nm.active.size()==1,"same account/task posting must supersede older token without growing notifications");
  post(h,"epoch-a","other","X");check(h.nm.active.size()==2,"posting another task preserves B");
  h.store.epoch="epoch-b";post(h,"epoch-b","synthetic","C");check(h.nm.active.size()==3,"different account identity does not supersede previous account task");
  h=setup();post(h,"epoch-a","synthetic","A");h.nm.notify("synthetic",0,new Notification());h.summaryUi=new UpdateSheet(UpdateReviewPolicy.capture(h.task(h.selected)));Api.replies.add(task("A","todo","A",false));h.markRead("synthetic","A");check(h.nm.active.size()==2,"no cancellation before server confirmation");h.finish();check(h.nm.active.size()==1&&h.nm.active.containsKey("synthetic"),"confirmed ACK cancels only exact displayed identity, not unknown legacy tag");
  h=setup();post(h,"epoch-a","synthetic","A");h.summaryUi=new UpdateSheet(UpdateReviewPolicy.capture(h.task(h.selected)));Api.failure=new RuntimeException("synthetic ack offline");h.markRead("synthetic","A");h.finish();check(h.nm.active.size()==1,"failed ACK leaves displayed notification");
  check(!NotificationIdentity.tag("a:b","c","d").equals(NotificationIdentity.tag("a","b:c","d")),"length-framed identities do not collide at delimiters");
  check(!NotificationIdentity.sameTask(NotificationIdentity.tag("a","bc","d"),"ab","c"),"account/task boundaries do not alias during supersession");
 }
 static void own(){
  for(boolean quick:new boolean[]{false,true}){
   RedesignRegressionHarness h=setup();Api.replies.add(task("U-rev","completed","U",true));Api.replies.add(data(task("H-rev","completed","H",true)));
   if(quick)h.quickMutation("PATCH","/api/tasks/synthetic",h.task(h.selected),new JSONObject());else h.mutation("PATCH","/api/tasks/synthetic",new JSONObject(),true);h.finish();
   check(h.tasksUi.emphasis.claim("synthetic","H",true,true),(quick?"quickMutation":"mutation")+" must not observe/suppress remote H from later GET");
   h.tasksUi.emphasis.pause();check(!h.tasksUi.emphasis.claim("synthetic","U",true,true),"exact own response U must be suppressed");check(h.task(h.selected).getJSONObject("notification").optBoolean("unread"),"static unread marker must remain server-owned");
  }
 }
 static void route(){
  for(boolean threadRoute:new boolean[]{false,true}){
   RedesignRegressionHarness h=setup();h.tasks.put(task("O","todo","O",false).put("id","other"));h.editing=true;h.draft.put("title","unsaved");
   if(threadRoute)new ThreadUi(h).taskThreadAction("other",false);else h.openTaskConversation("other");
   check("other".equals(h.selected)&&!h.editing,(threadRoute?"taskThreadAction":"openTaskConversation")+" must reset editing across task identity");check(h.confirmations==1,"cross-task dirty draft still requires confirmation");check("Hermès".equals(h.detailsMode),"route still opens task chat");
   h=setup();h.editing=true;h.draft.put("title","same task draft");if(threadRoute)new ThreadUi(h).taskThreadAction("synthetic",false);else h.openTaskConversation("synthetic");
   check(h.editing&&"same task draft".equals(h.draft.optString("title"))&&h.confirmations==0,"same-task chat preserves editor intent and draft without discard prompt");
  }
 }
 public static void main(String[] args)throws Exception{String test=args.length==0?"all":args[0];if(test.equals("all")||test.equals("route")){route();System.out.println("PASS route (Main + thread action cross-task reset, same-task editor/draft preserved)");}if(test.equals("all")||test.equals("own")){own();System.out.println("PASS own (mutation + quick exact U suppressed, later remote H eligible, unread preserved)");}if(test.equals("all")||test.equals("ack")){ack();System.out.println("PASS ack (delayed ACK versus newer OS post; bounded same-task supersession; task/account isolation)");}if(test.equals("all")||test.equals("reload")){reload();System.out.println("PASS reload (success rebase, failure input retention, ordinary refresh)");}if(test.equals("all")||test.equals("quick")){quick();System.out.println("PASS quick (clean same-task, dirty blocked, other-task preserved)");}}
}
class Queue {ArrayDeque<Runnable> work=new ArrayDeque<>();void post(Runnable r){work.add(r);}void execute(Runnable r){post(r);}void run(){while(!work.isEmpty())work.remove().run();}void clear(){work.clear();}int size(){return work.size();}}
class Store {String epoch="epoch-a";int cached;static class Account {String epoch;Account(String e){epoch=e;}}boolean current(Account a){return a!=null&&epoch.equals(a.epoch);}void cache(Account a,JSONObject o){if(current(a))cached++;}boolean bootstrapped(){return true;}long cursor(){return 0;}void cursor(Account a,long c,boolean b){}}
class Api {static final Queue IO=new Queue();static final ArrayDeque<JSONObject> replies=new ArrayDeque<>();static RuntimeException failure;Api(Store.Account a){}JSONObject call(String m,String p,JSONObject b){if(failure!=null)throw failure;if(replies.isEmpty())throw new AssertionError("Missing synthetic response for "+m+" "+p);return replies.remove();}static String segment(String s){return s;}static String friendly(Exception e){return e.toString();}static class Failure extends RuntimeException {int code;}}
class AuthPolicy {enum State{FULL}}
class PollService {interface Stop {boolean get();}static void poll(Object c,Store s,Store.Account a,Stop stop){}}
class NativePoll {static final String CHANNEL="task-updates";interface Stop{boolean get();}static void channels(Context c){}static String summary(JSONObject n){return "synthetic summary";}
 // POLL_METHOD
}
class TasksUi {final RedesignRegressionHarness a;final UpdateEmphasis emphasis=new UpdateEmphasis();ListView list;TasksUi(RedesignRegressionHarness a){this.a=a;}void refresh(){}void capture(){}
 // OWN_CHANGE_METHOD
}
class ThreadUi {final RedesignRegressionHarness a;final Controller c=new Controller();ThreadUi(RedesignRegressionHarness a){this.a=a;}
 // THREAD_ACTION_METHOD
}
class Controller {void create(String t,Runnable r){r.run();}void save(){}}
class ConversationUi {void taskPicker(String task){}void capture(){}void changed(){}}
class EditText {String value="";String getText(){return value;}}
class Spinner {int getSelectedItemPosition(){return 0;}}
class ScrollView {int getScrollY(){return 0;}}
class ListView {int getChildCount(){return 0;}View getChildAt(int i){return null;}}
class View {Object getTag(){return null;}static final int VISIBLE=0,GONE=8;}
class TaskRowView extends View {void stopEmphasis(){}}
class TextView {void setText(String s){}void setVisibility(int i){}}
class UpdateSheet {UpdateReviewPolicy.Snapshot shown;Dialog dialog=new Dialog();UpdateSheet(UpdateReviewPolicy.Snapshot s){shown=s;}void updateState(){}void waiting(boolean b){}void failure(){}}
class Dialog {void dismiss(){}}
class Context {static final String NOTIFICATION_SERVICE="notifications";final NotificationManager nm=new NotificationManager();Object getSystemService(String n){return nm;}int checkSelfPermission(String n){return 0;}}
class NotificationManager {static final int IMPORTANCE_NONE=0;Map<String,Notification> active=new HashMap<>();boolean areNotificationsEnabled(){return true;}NotificationChannel getNotificationChannel(String s){return null;}void notify(String tag,int id,Notification n){active.put(tag,n);}void cancel(String tag,int id){active.remove(tag);}android.service.notification.StatusBarNotification[] getActiveNotifications(){ArrayList<android.service.notification.StatusBarNotification> r=new ArrayList<>();for(Map.Entry<String,Notification> e:active.entrySet())r.add(new android.service.notification.StatusBarNotification(e.getKey(),e.getValue()));return r.toArray(new android.service.notification.StatusBarNotification[0]);}}
class StatusBarNotification {final String tag;final Notification n;StatusBarNotification(String t,Notification n){tag=t;this.n=n;}String getTag(){return tag;}int getId(){return 0;}Notification getNotification(){return n;}}
class NotificationChannel {int getImportance(){return 1;}}
class Bundle extends HashMap<String,String> {void putString(String k,String v){put(k,v);}String getString(String k){return get(k);}}
class Notification {static final int VISIBILITY_PRIVATE=0;Bundle extras=new Bundle();static class Builder {Notification n=new Notification();Builder(Context c,String s){}Builder setSmallIcon(int i){return this;}Builder setContentTitle(String s){return this;}Builder setContentText(String s){return this;}Builder setStyle(BigTextStyle s){return this;}Builder setVisibility(int i){return this;}Builder setPublicVersion(Notification p){return this;}Builder setContentIntent(PendingIntent p){return this;}Builder setAutoCancel(boolean b){return this;}Builder setOnlyAlertOnce(boolean b){return this;}Builder setAllowSystemGeneratedContextualActions(boolean b){return this;}Builder addExtras(Bundle b){n.extras=b;return this;}Notification build(){return n;}}static class BigTextStyle{BigTextStyle bigText(CharSequence c){return this;}}}
class Intent {Intent(Context c,Class<?> k){}Intent setAction(String s){return this;}Intent setData(Object o){return this;}Intent putExtra(String k,String v){return this;}}
class MainActivity {}
class PendingIntent {static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;static PendingIntent getActivity(Context c,int i,Intent t,int f){return new PendingIntent();}}
class Build {static class VERSION {static final int SDK_INT=35;}}
class PackageManager {static final int PERMISSION_GRANTED=0;}
class SystemClock {static long elapsedRealtime(){return 0;}}
class MarkdownText {static CharSequence render(String s){return s;}}
class R {static class drawable {static final int ic_notification=1;}}
class android {static class service {static class notification {static class StatusBarNotification {final String tag;StatusBarNotification(String tag,Notification n){this.tag=tag;}String getTag(){return tag;}int getId(){return 0;}}}}static class net {static class Uri {static Object parse(String s){return s;}}}}

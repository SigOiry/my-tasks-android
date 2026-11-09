package com.simon.mytasks;
import java.util.*;
import org.json.*;
public final class ConversationReviewHarness {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static void menu(){
  for(boolean archived:new boolean[]{false,true}){
   LinkedHashMap<String,String> free=ConversationActionPolicy.threadActions("",archived);
   LinkedHashMap<String,String> task=ConversationActionPolicy.threadActions("task-9",archived);
   check(!free.containsKey("saved")&&!free.containsKey("new"),"free hides task actions");
   check(task.containsKey("saved")&&task.containsKey("new"),"task exposes saved/new actions");
   for(String key:new String[]{"history","rename","archive","discussions","tasks","settings","mode","capabilities"})
    check(free.containsKey(key)&&free.get(key).equals(task.get(key)),"stable route: "+key);
   check(free.get("archive").equals(archived?"Reopen conversation":"Archive conversation"),"archive label");
  }
 }
 static class Store {static class Account{final String epoch;Account(String e){epoch=e;}}String epoch="owner";boolean current(Account a){return a!=null&&epoch.equals(a.epoch);}}
 static class AuthPolicy {enum State{FULL,LOGIN}}
 static class Surfaces {long value;long generation(){return value;}boolean current(long n){return n==value;}}
 static class Activity {
  boolean busy,editing;AuthPolicy.State authState=AuthPolicy.State.FULL;
  Store store=new Store();Store.Account account=new Store.Account("owner");Surfaces surfaces=new Surfaces();
  String selected="old",detailsMode="Hermès",destination="Discussions",pendingSummary="old",input="typed unsaved fields";int detailY=99,renders,captures,io;
  JSONObject draft=new JSONObject();Runnable pending;boolean hasDirty=true;
  Activity(){try{draft.put("title","previous captured title");}catch(Exception e){throw new RuntimeException(e);}}
  boolean alive(Store.Account a){return authState==AuthPolicy.State.FULL&&store.current(a);}
  void notice(String t,String m){}void render(){renders++;}
  // Write-boundary traps: an accidental automatic canonical save fails io==0.
  void save(boolean complete){io++;}
  void mutation(String method,String path,JSONObject body,boolean clearDraft){io++;}
  void captureDraft(){captures++;try{draft.put("title",input);}catch(Exception e){throw new RuntimeException(e);}}
  boolean dirty(){return hasDirty;}
  void confirm(String t,String m,Runnable yes){pending=yes;}
  // REVIEW_METHOD
  // EMPTY_METHOD
 }
 interface Selection{void select(Object d,int which);}
 static class Dialog {
  String[] labels;Selection action;
  Dialog setTitle(String t){return this;}Dialog setItems(String[] l,Selection a){labels=l;action=a;return this;}void show(){}
 }
 static class MessageActivity extends Activity {
  Dialog dialog=new Dialog();String reviewed="",viewed="";
  Dialog dialogBuilder(){return dialog;}
  @Override void reviewConversationAsTask(String text){reviewed=text;}
  void fullMessage(String text){viewed=text;}
 }
 static class Messages {
  MessageActivity a=new MessageActivity();String copied="";
  void copyOriginal(String text){copied=text;}
  // MESSAGE_METHOD
 }
 static void review(){
  String original="  # Proposal\nRaw **assistant** content; {\"title\":\"do not parse\"}  ";
  Activity a=new Activity();JSONObject before=a.draft;
  a.reviewConversationAsTask(original);
  check(a.pending!=null,"confirmation is genuinely deferred");
  check(a.draft==before&&a.selected.equals("old")&&a.draft.optString("title").equals(a.input)&&a.renders==0,"before confirm existing draft and route untouched");
  a.pending=null; // Cancel does not run the callback.
  check(a.draft==before&&a.selected.equals("old")&&a.io==0,"cancel preserves draft without IO");
  a.reviewConversationAsTask(original);a.pending.run();
  check(a.draft!=before&&a.selected.equals("NEW")&&a.editing,"confirm opens local NEW editor");
  check(a.draft.optString("title").isEmpty()&&a.draft.optString("description").equals(original),"blank required title and byte-exact editable description");
  check(a.draft.optString("project").isEmpty()&&a.draft.optString("notes").isEmpty()&&a.draft.optString("due_date").isEmpty(),"no invented structured fields");
  check(a.destination.equals("Tasks")&&a.detailsMode.equals("Details")&&a.pendingSummary.isEmpty()&&a.detailY==0&&a.renders==1&&a.io==0,"local editor only; no API IO");
  for(int scenario=0;scenario<4;scenario++){
   Activity stale=new Activity();JSONObject prior=stale.draft;stale.reviewConversationAsTask(original);Runnable yes=stale.pending;
   if(scenario==0)stale.surfaces.value++;if(scenario==1)stale.store.epoch="other";if(scenario==2)stale.authState=AuthPolicy.State.LOGIN;if(scenario==3)stale.busy=true;
   yes.run();check(stale.draft==prior&&stale.selected.equals("old")&&stale.renders==0,"reject stale/unauthenticated/busy deferred callback "+scenario);
  }
  Activity clean=new Activity();clean.hasDirty=false;clean.reviewConversationAsTask(original);check(clean.pending==null&&clean.selected.equals("NEW")&&clean.io==0,"clean editor opens locally");
  for(int scenario=0;scenario<3;scenario++){
   Activity blocked=new Activity();if(scenario==0)blocked.busy=true;if(scenario==1)blocked.authState=AuthPolicy.State.LOGIN;if(scenario==2)blocked.account=null;
   JSONObject prior=blocked.draft;blocked.reviewConversationAsTask(original);check(blocked.pending==null&&blocked.draft==prior&&blocked.captures==0,"entry guard "+scenario);
  }
  for(String role:new String[]{"assistant","user","system","tool"}){
   Messages m=new Messages();m.messageMenu(role,original);
   List<String> labels=Arrays.asList(m.a.dialog.labels);
   check(labels.contains("Copy original")&&labels.contains("View original text"),"original actions retained");
   check(labels.contains("Review as new task")==role.equals("assistant"),"only assistant offers manual review");
   m.a.dialog.action.select(null,labels.indexOf("Copy original"));check(m.copied.equals(original),"copy original intact");
   m.a.dialog.action.select(null,labels.indexOf("View original text"));check(m.a.viewed.equals(original),"view original intact");
   if(role.equals("assistant")){m.a.dialog.action.select(null,labels.indexOf("Review as new task"));check(m.a.reviewed.equals(original),"review forwards exact content");}
  }
 }
 public static void main(String[] args){menu();if(!args[0].equals("menu"))review();System.out.println("Conversation integration regressions: "+checks+" assertions passed");}
}

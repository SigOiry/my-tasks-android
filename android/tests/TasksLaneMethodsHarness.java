package com.simon.mytasks;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
/** Actual refresh + production comparator. Android views/dispatch only are seams. */
public class TasksLaneMethodsHarness {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static class android {static class view {static class ViewTreeObserver {
  interface OnPreDrawListener {boolean onPreDraw();}
  boolean isAlive(){return true;}void removeOnPreDrawListener(OnPreDrawListener l){}void addOnPreDrawListener(OnPreDrawListener l){}
 }}}
 static class ListView {void removeCallbacks(Runnable r){}void postDelayed(Runnable r,int ms){}android.view.ViewTreeObserver getViewTreeObserver(){return new android.view.ViewTreeObserver();}}
 static class Text {String text;void setText(String s){text=s;}void setEnabled(boolean b){}}
 static class Adapter {int notifications;void notifyDataSetChanged(){notifications++;}}
 static class Activity {
  String filter="All",projectFilter="",query="",sortOrder="Project",colorMode="Project";boolean sortReverse,online=true,busy;
  JSONArray tasks=new JSONArray();
  String human(String s){return s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
  String dueLabel(JSONObject t){return PresentationPolicy.dueLabel(PresentationPolicy.value(t,"due_date"),LocalDate.now(),Locale.US);}
 }
 static class TasksUi {
  Activity a=new Activity();ListView list=new ListView();Text count=new Text(),create=new Text();Adapter adapter=new Adapter();
  String viewFilter="All",projectFilter="",query="",sortOrder="Project",colorMode="Project";boolean reverse,pendingRefresh,paused,anchorReset;
  Runnable deferredRefresh=()->{};ArrayList<Entry> entries=new ArrayList<>();int captures,resets,restores;
  boolean sameFilters(){return true;}boolean deferRefresh(){return false;}
  Map<String,Float> snapshotRowPositions(){return Collections.emptyMap();}void applyRowPositions(Map<String,Float> m){}
  void capture(){captures++;}void resetAnchor(){resets++;}void restoreAnchor(){restores++;}
// PRODUCTION_METHODS
 }
 static JSONObject task(String id,String status,String project,boolean deleted)throws Exception{
  return new JSONObject().put("id",id).put("title",id).put("status",status).put("project",project).put("deleted",deleted)
   .put("priority",project.equals("Alpha")?"high":"low").put("due_date",LocalDate.now().toString())
   .put("created_at",project.equals("Alpha")?"2026-10-01T00:00:00Z":"2026-11-01T00:00:00Z")
   .put("updated_at",project.equals("Alpha")?"2026-11-01T00:00:00Z":"2026-11-09T00:00:00Z");
 }
 static List<String> tokens(TasksUi ui){List<String> out=new ArrayList<>();for(TasksUi.Entry e:ui.entries)out.add(e.task!=null?e.task.optString("id"):e.group!=null?"#"+e.group:"EMPTY");return out;}
 public static void main(String[] args)throws Exception{
  TasksUi ui=new TasksUi();
  // Reverse insertion deliberately: grouping must come from policy, never source order.
  for(boolean deleted:new boolean[]{true,false})for(String status:new String[]{"cancelled","completed","waiting","in_progress","todo"})for(String project:new String[]{"Zulu","Alpha"})
   ui.a.tasks.put(task((deleted?"d":"")+status+project,status,project,deleted));
  for(String mode:new String[]{"Smart","Project","Priority","Importance","Due date","Recently updated","Created date"})for(boolean reversed:new boolean[]{false,true})for(String scope:new String[]{"Active","Today","Week","All","Trash"}){
   ui.a.sortOrder=mode;ui.a.sortReverse=reversed;ui.a.filter=scope;ui.refresh();
   List<String> actual=tokens(ui);boolean trash=scope.equals("Trash");
   String active=trash?"Deleted":"Actionable",waiting=trash?"Deleted · Waiting":"Waiting",inactive=trash?"Deleted · Inactive":"Inactive";
   check(actual.get(0).equals("#"+active),mode+" "+scope+" must start with labelled actionable lane: "+actual);
   check(actual.indexOf("#"+waiting)>0,mode+" "+scope+" labelled lower waiting section: "+actual);
   check(actual.contains("#"+inactive)==(scope.equals("All")||trash),"terminal eligibility unchanged: "+actual);
   int lastLane=-1,tasks=0;Set<String> ids=new HashSet<>();
   for(TasksUi.Entry e:ui.entries)if(e.task!=null){
    tasks++;int lane=PresentationPolicy.smartLane(e.task);check(lane>=lastLane,"no lane can compete with earlier work "+mode+" "+actual);lastLane=lane;
    check(ids.add(e.task.getString("id")),"no duplicated eligible task");
   }
   check(tasks==((scope.equals("All")||trash)?10:6),"full eligible list retained");
   if(mode.equals("Project")){
    List<String> expected=new ArrayList<>();
    for(String section:(scope.equals("All")||trash)?new String[]{active,waiting,inactive}:new String[]{active,waiting}){
     expected.add("#"+section);
     for(String project:reversed?new String[]{"Zulu","Alpha"}:new String[]{"Alpha","Zulu"}){
      expected.add("#"+project);String prefix=trash?"d":"";
      if(section.equals(active)){expected.add(prefix+"in_progress"+project);expected.add(prefix+"todo"+project);}
      else if(section.equals(waiting))expected.add(prefix+"waiting"+project);
      else{expected.add(prefix+"cancelled"+project);expected.add(prefix+"completed"+project);}
     }
    }
    // Primary project direction reverses; deterministic ID ties do not.
    check(actual.equals(expected),"Project context and direction pinned inside every lane: "+actual+" expected "+expected);
   }
  }
  ui.a.filter="All";ui.a.projectFilter="Alpha";ui.a.query="waiting";ui.refresh();
  check(tokens(ui).get(0).equals("#Waiting")&&ui.count.text.startsWith("1 tasks"),"filtered waiting-only lane labelled, no fabricated actionable group");
  ui.a.query="no-such-task";ui.refresh();check(tokens(ui).equals(Arrays.asList("EMPTY")),"empty view unchanged");
  check(ui.resets==0&&ui.captures==ui.restores,"sort changes preserve capture/restore anchor path");
  System.out.println("Actual TasksUi.refresh lane/group checks passed: "+checks);
 }
}

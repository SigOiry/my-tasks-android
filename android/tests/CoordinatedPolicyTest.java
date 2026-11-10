package com.simon.mytasks;
import java.time.*;
import java.util.*;
import org.json.*;
/** Real canonical JSON and local calendar boundaries; no Android/mock policies. */
public final class CoordinatedPolicyTest {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Object call(String n,Class<?>[] types,Object...v)throws Exception{
  try{return PresentationPolicy.class.getMethod(n,types).invoke(null,v);}catch(NoSuchMethodException e){throw new AssertionError("Missing coordinated policy: "+n,e);}
 }
 static JSONObject task(String id,String priority,String due)throws Exception{return new JSONObject().put("id",id).put("status","todo").put("priority",priority).put("due_date",due);}
 public static void main(String[] args)throws Exception{
  JSONObject t=task("a","high","2026-11-09").put("project","Website");
  int slot=(Integer)call("projectSlot",new Class<?>[]{JSONObject.class},t);
  // Independently pinned FNV vectors; exact UTF-8 including whitespace and case.
  ok(slot==4,"Website vector is Amber");
  t.put("project","é");ok((Integer)call("projectSlot",new Class<?>[]{JSONObject.class},t)==3,"UTF-8 vector");
  t.put("project_id","hello");ok((Integer)call("projectSlot",new Class<?>[]{JSONObject.class},t)==3,"canonical ID wins");
  t.remove("project_id");t.remove("project");ok((Integer)call("projectSlot",new Class<?>[]{JSONObject.class},t)==-1,"missing is neutral");
  t.put("project","Website");ok((Integer)call("taskColor",new Class<?>[]{JSONObject.class,String.class,boolean.class,int.class},t,"Project",false,123)==0xffa57a35,"exact light Amber");
  ok((Integer)call("taskColor",new Class<?>[]{JSONObject.class,String.class,boolean.class,int.class},t,"Neutral",false,123)==123,"neutral has no categoric color");
  LocalDate today=LocalDate.parse("2026-11-10");java.time.ZoneId zone=java.time.ZoneId.of("America/Los_Angeles");
  JSONObject scored=task("score","high","2026-11-09").put("status","in_progress").put("created_at","2026-10-01T23:00:00-07:00").put("notification",new JSONObject().put("unread",true));
  ok(score(scored,today,zone)==96,"overdue50+high30+progress6+unread6+age4");
  String[] due={"2026-11-10","2026-11-11","2026-11-17","2026-11-18","bad",""};int[] expected={40,25,10,0,0,0};
  for(int i=0;i<due.length;i++)ok(score(task("d","low",due[i]),today,zone)==expected[i],"deadline bucket "+due[i]);
  ok(score(task("m","medium",""),today,zone)==15,"medium importance15");
  ok(score(task("u","low","").put("unread",true),today,zone)==0,"top-level invented unread ignored");
  ok(score(task("age","low","").put("created_at","2026-11-04T01:00:00Z"),today,zone)==1,"creation converted to local date before whole weeks");
  ok(score(task("age","low","").put("created_at","broken"),today,zone)==0,"invalid timestamps add no age");
  JSONObject urgent=task("urgent","low","2026-11-09").put("project","Z"),high=task("high","high","").put("project","A");
  ok(order(urgent,high,"Smart",false,today,zone)<0,"smart compares actionable across projects");
  JSONObject waiting=task("wait","high","2026-11-01").put("status","waiting");
  ok(order(waiting,high,"Smart",false,today,zone)>0,"waiting cannot outrank actionable");
  ok(order(waiting,high,"Smart",true,today,zone)>0,"reversing Smart keeps waiting lower");
  JSONObject done=task("done","high","2026-11-01").put("status","completed");ok(order(done,waiting,"Smart",false,today,zone)>0,"inactive never competes");
  ok(order(urgent,high,"Importance",false,today,zone)>0,"importance primary");ok(order(urgent,high,"Importance",true,today,zone)<0,"importance direction meaningful");
  JSONObject newer=task("new","low","").put("created_at","2026-11-09T02:00:00Z").put("updated_at","2026-11-09T02:00:00Z"),older=task("old","low","").put("created_at","2026-11-08T23:00:00-05:00").put("updated_at","2026-11-08T23:00:00-05:00");
  ok(order(newer,older,"Recently updated",false,today,zone)>0,"timestamp instants not lexical offset strings, newest default");
  ok(order(newer,older,"Created date",false,today,zone)<0,"created oldest default");
  ok(order(newer,task("missing","low",""),"Recently updated",true,today,zone)<0,"missing timestamps last both directions");
  ok(order(task("a","low",""),task("b","low",""),"Smart",false,today,zone)<0,"final canonical ID tie");
  ok("Smart".equals(call("savedSort",new Class<?>[]{String.class},new Object[]{null})),"Smart only absent choice default");
  ok("Priority".equals(call("savedSort",new Class<?>[]{String.class},"Priority")),"saved legacy priority preserved");
  ok(score(task("str","low","").put("notification",new JSONObject().put("unread","true")),today,zone)==0,"only canonical boolean unread adds6");
  JSONObject deletedAction=task("deleted_action","low","").put("deleted",true),deletedDone=task("deleted_done","high","2026-11-01").put("deleted",true).put("status","completed");
  ok(order(deletedAction,deletedDone,"Smart",false,today,zone)<0,"trash retains actionable before terminal, within its own scope");
  String[] modes={"Smart","Project","Importance","Due date","Recently updated","Created date"},forward={"Highest ranked","A–Z","High–Low","Soonest","Newest","Oldest"},backward={"Lowest ranked","Z–A","Low–High","Latest","Oldest","Newest"};
  for(int i=0;i<modes.length;i++)for(boolean reversed:new boolean[]{false,true})ok((reversed?backward:forward)[i].equals(call("directionLabel",new Class<?>[]{String.class,boolean.class},modes[i],reversed)),"meaningful direction "+modes[i]);
  for(boolean dark:new boolean[]{false,true})for(String importance:new String[]{"high","medium","low"}){
   int color=(Integer)call("importanceColor",new Class<?>[]{String.class,boolean.class},importance,dark);
   for(int bg:dark?new int[]{0xff1a1d24,0xff202e46}:new int[]{0xffffffff,0xffedf3ff})ok(contrast(color,bg)>=3,"importance meaningful indicator contrast");
  }
  for(String project:new String[]{"Website","website","é","hello"," ","研究"}){
   JSONObject colored=task("c","low","").put("project",project);int s=(Integer)call("projectSlot",new Class<?>[]{JSONObject.class},colored);
   int[] light={0xff527fc2,0xff3c8b83,0xff8972bb,0xffb26d87,0xffa57a35,0xff6c7e91},dark={0xff8fb2e8,0xff82c8ba,0xffbba7e5,0xffdfa2b9,0xffd4b26f,0xffa6b7c9};
   for(boolean night:new boolean[]{false,true}){int color=(Integer)call("taskColor",new Class<?>[]{JSONObject.class,String.class,boolean.class,int.class},colored,"Project",night,123);ok(color==(night?dark:light)[s],"exact project palette variant");ok(contrast(color,night?0xff1a1d24:0xffffffff)>=3,"project marker contrast");}
  }
  System.out.println("CoordinatedPolicyTest: "+checks+" checks passed");
 }
 static double luminance(int c){double v=0;double[] w={.2126,.7152,.0722};for(int i=0;i<3;i++){double x=((c>>(16-8*i))&255)/255.0;v+=w[i]*(x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4));}return v;}
 static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 static int score(JSONObject t,LocalDate d,java.time.ZoneId z)throws Exception{return (Integer)call("smartScore",new Class<?>[]{JSONObject.class,LocalDate.class,java.time.ZoneId.class},t,d,z);}
 static int order(JSONObject a,JSONObject b,String mode,boolean reverse,LocalDate d,java.time.ZoneId z)throws Exception{return (Integer)call("compare",new Class<?>[]{JSONObject.class,JSONObject.class,String.class,boolean.class,LocalDate.class,java.time.ZoneId.class},a,b,mode,reverse,d,z);}
}

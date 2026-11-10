package com.simon.mytasks;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
public final class PresentationPolicyTest {
 static int checks;
 static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static Object invoke(String name,Class<?>[] types,Object...args)throws Exception{
  try{return Class.forName("com.simon.mytasks.PresentationPolicy").getMethod(name,types).invoke(null,args);}
  catch(ClassNotFoundException|NoSuchMethodException e){throw new AssertionError("Missing presentation behavior: "+name,e);}
 }
 static String date(String date,String today)throws Exception{return (String)invoke("dueLabel",new Class<?>[]{String.class,LocalDate.class,Locale.class},date,LocalDate.parse(today),Locale.US);}
 public static void main(String[] args)throws Exception{
  ok(date("2026-11-09","2026-11-09").equals("Due today"),"today is explicit");
  ok(date("2026-11-08","2026-11-09").equals("Overdue · Nov 8, 2026"),"overdue readable with year");
  ok(date("2026-11-10","2026-11-09").equals("Due tomorrow"),"tomorrow relative locally");
  ok(date("2026-12-10","2026-11-09").equals("Dec 10, 2026"),"future readable local date");
  ok(date("","2026-11-09").equals("No due date"),"missing due is explicit");
  ok(date("2026-02-30","2026-11-09").equals("Date unavailable"),"impossible dates do not crash or normalize");
  JSONObject active=new JSONObject().put("status","todo").put("due_date","2026-11-08").put("title","Book review").put("project","Work");
  LocalDate today=LocalDate.parse("2026-11-09");
  for(String view:new String[]{"Active","Today","Week","All"})ok(matches(active,view,"Work","review",today),"overdue included in "+view);
  ok(!matches(active,"Trash","","",today),"nondeleted excluded from trash");
  ok(!matches(active,"Active","Home","",today),"exact project filter");
  ok(!matches(active,"Active","","absent",today),"search filters results");
  active.put("due_date","2026-11-16");ok(!matches(active,"Week","","",today),"next week excluded using local Sunday boundary");
  active.put("due_date",JSONObject.NULL);ok(!matches(active,"Today","","",today),"undated excluded from today");
  active.put("status","completed");ok(!matches(active,"Active","","",today),"completed hidden from active");ok(matches(active,"All","","",today),"completed visible in all");
  active.put("deleted",true);ok(matches(active,"Trash","","",today),"trash includes completed deleted");ok(!matches(active,"All","","",today),"all excludes trash");
  JSONObject a=new JSONObject().put("id","a").put("project","B").put("priority","high").put("due_date","2026-11-10");
  JSONObject b=new JSONObject().put("id","b").put("project","A").put("priority","low").put("due_date","2026-11-09");
  ok(compare(a,b,"Project")>0,"project-first sort");ok(compare(a,b,"Priority")<0,"priority-first sort crosses projects");ok(compare(a,b,"Due date")>0,"due-first sort crosses priority");
  a.remove("due_date");ok(compare(a,b,"Due date")>0,"missing dates last");
  String before=a.toString();compare(a,b,"Priority");ok(before.equals(a.toString()),"sorting does not rewrite task");
  JSONObject source=new JSONObject().put("source","Gmail").put("source_accounts",new JSONArray().put("Work"));
  source.put("source_references",new JSONArray().put("gmail:private:message:secret-id").put("https://mail.google.com/mail/u/0/#inbox/abc").put("javascript:alert(1)").put("https://user:secret@example.com/a").put("https://127.0.0.1/a"));
  String summary=(String)invoke("sourceSummary",new Class<?>[]{JSONObject.class},source);
  ok(summary.contains("Gmail")&&summary.contains("Work")&&!summary.contains("secret-id"),"human source without internal IDs");
  List<?> links=(List<?>)invoke("sourceLinks",new Class<?>[]{JSONObject.class},source);
  ok(links.size()==1&&links.get(0).equals("https://mail.google.com/mail/u/0/#inbox/abc"),"only provided safe HTTPS sources, exact fragment preserved");
  source.put("source","{\"raw\":\"provider\"}");summary=(String)invoke("sourceSummary",new Class<?>[]{JSONObject.class},source);ok(!summary.contains("raw"),"raw source JSON not rendered");
  for(String unsafe:new String[]{"javascript:alert(1)","file:///etc/passwd","intent://x","http://example.com/a","https://localhost/x","https://example.com/\nsecret","https://example.com:444/x"})ok(!(Boolean)invoke("safeSourceUrl",new Class<?>[]{String.class},unsafe),"unsafe source rejected");
  for(String encoded:new String[]{"https://example.com/%0d%0aInjected","https://example.com/%00","https://example.com/%5c@evil.test"})ok(!(Boolean)invoke("safeSourceUrl",new Class<?>[]{String.class},encoded),"encoded control/backslash is not a browser source action");
  source.put("source","  {\"raw\":\"provider\"}  ");summary=(String)invoke("sourceSummary",new Class<?>[]{JSONObject.class},source);ok(!summary.contains("raw"),"whitespace cannot expose source JSON");ok(summary.startsWith("No source details provided"),"unknown source never falsely described as manual");
  JSONObject sunday=new JSONObject().put("status","todo").put("due_date","2026-11-09");ok(!matches(sunday,"Week","","",LocalDate.parse("2026-11-08")),"Monday excluded on local Sunday");
  sunday.put("due_date","2026-11-08");ok(matches(sunday,"Week","","",LocalDate.parse("2026-11-08")),"Sunday is included through local week end");
  sunday.put("status","cancelled");ok(!matches(sunday,"Today","","",LocalDate.parse("2026-11-08")),"cancelled excluded from today");
  sunday.put("status","todo").put("title","Mixed CASE").put("project",JSONObject.NULL);ok(matches(sunday,"Active","Unsorted"," mixed case ",today),"case insensitive trimmed search, null project");
  String ack=(String)invoke("ackFailure",new Class<?>[]{int.class},409);ok(ack.contains("newer update")&&ack.contains("kept"),"ack conflict preserves displayed summary");ok(ack.contains("Refresh")&&!ack.contains("overwrite"),"ack conflict offers refresh, never force overwrite");
  ok(((String)invoke("ackFailure",new Class<?>[]{int.class},401)).contains("Settings"),"ack auth is actionable");
  JSONObject upper=new JSONObject().put("id","upper").put("title","Upper").put("status","todo").put("project","Work");
  JSONObject lower=new JSONObject().put("id","lower").put("title","Lower").put("status","todo").put("project","work");
  JSONArray caseTasks=new JSONArray().put(lower).put(upper).put(upper);
  List<?> caseOptions=(List<?>)invoke("projectOptions",new Class<?>[]{JSONArray.class,String.class,LocalDate.class},caseTasks,"Active",today);
  ok(caseOptions.equals(Arrays.asList("Work","work")),"exact project identities survive deduplication, casefold sort uses raw tie break");
  for(Object option:caseOptions){String name=(String)option;ok(matches(upper,"Active",name,"",today)==name.equals("Work"),"selecting "+name+" filters upper identity exactly");ok(matches(lower,"Active",name,"",today)==name.equals("work"),"selecting "+name+" filters lower identity exactly");}
  ok(matches(upper,"Active","","",today)&&matches(lower,"Active","","",today),"All projects resets exact filter without losing either case variant");
  JSONObject deletedOnly=new JSONObject().put("id","deleted").put("status","completed").put("deleted",true).put("project","deletedOnly");
  JSONObject completed=new JSONObject().put("status","completed").put("project","Finished");
  JSONObject nextWeek=new JSONObject().put("status","todo").put("due_date","2026-11-16").put("project","Later");
  JSONArray viewTasks=new JSONArray().put(upper).put(lower).put(deletedOnly).put(completed).put(nextWeek).put(JSONObject.NULL);
  List<?> trashOptions=(List<?>)invoke("projectOptions",new Class<?>[]{JSONArray.class,String.class,LocalDate.class},viewTasks,"Trash",today);
  ok(trashOptions.equals(Arrays.asList("deletedOnly")),"Trash-only project is selectable without ordinary projects");
  ok(matches(deletedOnly,"Trash",(String)trashOptions.get(0),"",today),"select Trash-only project reaches its deleted task");
  ok(matches(deletedOnly,"Trash","","",today),"All projects reset also reaches deleted task in Trash");
  for(String view:new String[]{"Active","Today","Week","All"}){
   List<?> names=(List<?>)invoke("projectOptions",new Class<?>[]{JSONArray.class,String.class,LocalDate.class},viewTasks,view,today);
   ok(!names.contains("deletedOnly"),"normal "+view+" never offers deleted-only project");
   ok(names.contains("Finished")==view.equals("All"),"options respect terminal inclusion in "+view);
   ok(names.contains("Later")==(view.equals("Active")||view.equals("All")),"options respect due-window inclusion in "+view);
  }
  ok(((List<?>)invoke("projectOptions",new Class<?>[]{JSONArray.class,String.class,LocalDate.class},new JSONArray(),"Trash",today)).isEmpty(),"empty view offers only UI reset, never a fabricated project");
  JSONArray reversed=new JSONArray().put(upper).put(lower);
  ok(invoke("projectOptions",new Class<?>[]{JSONArray.class,String.class,LocalDate.class},reversed,"Active",today).equals(caseOptions),"case tie order independent of source order");
  androidLaneOrdering();
  System.out.println("PASS "+checks+" presentation checks");
 }
 /** Android-only contract: independent pins, never shared with web fixtures. */
 static void androidLaneOrdering()throws Exception{
  LocalDate today=LocalDate.parse("2026-11-10");java.time.ZoneId zone=java.time.ZoneId.of("UTC");
  List<JSONObject> fixture=new ArrayList<>();
  String[] statuses={"todo","waiting","completed","todo","waiting","cancelled"};
  for(int lane=0;lane<6;lane++)for(String key:new String[]{"a","b"}){
   boolean first=key.equals("a");
   fixture.add(new JSONObject().put("id",lane+key).put("status",statuses[lane]).put("deleted",lane>=3)
    .put("project",first?"Alpha":"Zulu").put("priority",first?"high":"low")
    .put("due_date",first?"2026-11-09":"2026-11-11")
    .put("created_at",first?"2026-10-01T00:00:00Z":"2026-11-01T00:00:00Z")
    .put("updated_at",first?"2026-11-01T00:00:00Z":"2026-11-09T00:00:00Z"));
  }
  String canonical=fixture.toString();
  for(String mode:new String[]{"Smart","Project","Priority","Importance","Due date","Recently updated","Created date"})for(boolean reverse:new boolean[]{false,true}){
   // Explicit lane order is immutable; only the chosen key reverses within it.
   boolean bFirst=mode.equals("Recently updated")!=reverse;
   List<String> expected=new ArrayList<>();
   for(int lane=0;lane<6;lane++){expected.add(lane+(bFirst?"b":"a"));expected.add(lane+(bFirst?"a":"b"));}
   for(int seed=0;seed<8;seed++){
    List<JSONObject> sorted=new ArrayList<>(fixture);Collections.shuffle(sorted,new Random(seed));
    sorted.sort((x,y)->PresentationPolicy.compare(x,y,mode,reverse,today,zone));
    List<String> actual=new ArrayList<>();for(JSONObject t:sorted)actual.add(t.getString("id"));
    ok(actual.equals(expected),"Android fixed lanes + meaningful direction "+mode+" reverse="+reverse+": "+actual+" expected "+expected);
   }
  }
  ok(fixture.toString().equals(canonical),"Android lane sorting never mutates canonical tasks");
 }
 static int compare(JSONObject a,JSONObject b,String mode)throws Exception{return (Integer)invoke("compare",new Class<?>[]{JSONObject.class,JSONObject.class,String.class},a,b,mode);}
 static boolean matches(JSONObject task,String view,String project,String query,LocalDate today)throws Exception{
  return (Boolean)invoke("matches",new Class<?>[]{JSONObject.class,String.class,String.class,String.class,LocalDate.class},task,view,project,query,today);
 }
}

package com.simon.mytasks;
import java.util.*;
public final class CoreTest {
 static int checks;
 static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args) throws Exception {
  String mode=args.length==0?"all":args[0];
  if(mode.equals("url")||mode.equals("all")){
   ok(Core.server("https://tasks.example/").equals("https://tasks.example"),"normalize trailing slash");
   for(String s:new String[]{"http://192.168.1.2","https://u:p@host","https://host/x","https://host?x=1","https://host#f","ftp://host","https://host\\evil"}){boolean rejected=false;try{Core.server(s);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"reject unsafe origin: "+s);}
  }
  if(mode.equals("order")||mode.equals("all")){
   List<Core.SortKey> a=new ArrayList<>();a.add(new Core.SortKey("3","B","high","2025-01-01"));a.add(new Core.SortKey("2","A","low","2025-01-01"));a.add(new Core.SortKey("1","A","high",""));a.add(new Core.SortKey("4","A","high","2026-01-01"));Collections.sort(a,Core.ORDER);ok(a.get(0).id.equals("4")&&a.get(1).id.equals("1")&&a.get(2).id.equals("2")&&a.get(3).id.equals("3"),"project then priority then due; missing last");
  }
  if(mode.equals("path")||mode.equals("all")){
   ok(Core.path("task/a?x#y").equals("task%2Fa%3Fx%23y"),"task ID never changes URL path or query");
   ok(Core.path("é +").equals("%C3%A9%20%2B"),"unicode and spaces encoded exactly once");
  }
  if(mode.equals("event")||mode.equals("all")){
   ok(Core.advance(5,6,true)==6,"advance on handled event");ok(Core.advance(5,6,false)==5,"do not lose failed notification");ok(Core.advance(5,4,true)==5,"cursor never regresses");ok(!Core.shouldNotify(false,true,true),"bootstrap is quiet");ok(!Core.shouldNotify(true,false,true),"permission denied not handled");ok(Core.shouldNotify(true,true,true),"notify unread change");
  }
  if(mode.equals("notification")||mode.equals("all")){
   java.lang.reflect.Method decision;
   try{decision=Core.class.getMethod("eventUnread",Boolean.class,boolean.class,boolean.class,String.class);}
   catch(NoSuchMethodException e){throw new AssertionError("Missing explicit canonical tracking decision; unread=false currently drops untracked updates",e);}
   for(String kind:new String[]{"created","updated","restored"}){
    ok((Boolean)decision.invoke(null,Boolean.FALSE,false,false,kind),"untracked "+kind+" alerts despite false unread");
    ok(!(Boolean)decision.invoke(null,Boolean.TRUE,false,false,kind),"initialized read event never alerts");
    ok(!(Boolean)decision.invoke(null,null,false,false,kind),"missing tracking is not explicit false");
    ok(!(Boolean)decision.invoke(null,Boolean.FALSE,true,true,kind),"currently deleted task suppressed");
    ok((Boolean)decision.invoke(null,Boolean.TRUE,true,false,kind),"tracked unread task alerts");
   }
   for(String kind:new String[]{"deleted","unknown",""})ok(!(Boolean)decision.invoke(null,Boolean.FALSE,false,false,kind),"no fallback for "+kind);
  }
  if(mode.equals("chat-errors")||mode.equals("all")){
   java.lang.reflect.Method copy;
   try{copy=Core.class.getMethod("chatFailure",int.class);}
   catch(NoSuchMethodException e){throw new AssertionError("Missing chat-specific safe retry copy",e);}
   String ambiguous=(String)copy.invoke(null,0);
   ok(ambiguous.contains("original provider request may still be running"),"timeout warns about still-running original");
   ok(ambiguous.contains("not resent automatically"),"no silent replay");
   String large=(String)copy.invoke(null,413);
   ok(large.contains("too large")&&large.contains("shorten")&&!large.contains("retry"),"413 requires changed input, not identical retry");
  }
  if(mode.equals("chat-budget")||mode.equals("all")){
   java.lang.reflect.Method body;
   try{body=Core.class.getMethod("chatBody",String.class,String.class,String.class,List.class);}
   catch(NoSuchMethodException e){throw new AssertionError("Missing serialized UTF-8 chat budget; 12 x 8000 history exceeds middleware limit",e);}
   List<String[]> turns=new ArrayList<>();
   for(int i=0;i<12;i++)turns.add(new String[]{i%2==0?"user":"assistant","old"+i+repeat("漢😀\"\\\n",1300)});
   String message=repeat("😀",8000);
   String json=(String)body.invoke(null,"task/漢",message,"email_draft",turns);
   ok(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=60000,"actual UTF-8 wire budget includes non-ASCII, escapes and envelope");
   ok(json.contains("\"message\":\""+message+"\""),"entire 8000-codepoint current message preserved");
   ok(!json.contains("old0")&&json.contains("old11"),"oldest whole turns evicted, newest retained");
   ok(turns.size()==12&&turns.get(0)[1].startsWith("old0"),"input history unmodified");
   String exact=(String)body.invoke(null,"t"," \"\\\n\u0001漢😀 ","question",Collections.emptyList());
   ok(exact.equals("{\"task_id\":\"t\",\"message\":\" \\\"\\\\\\n\\u0001漢😀 \",\"mode\":\"question\",\"history\":[]}"),"JSON escaping preserves message, whitespace and unicode");
   for(String bad:new String[]{"",repeat("x",8001),repeat("😀",8001)}){
    boolean rejected=false;try{body.invoke(null,"t",bad,"question",turns);}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof IllegalArgumentException;}
    ok(rejected,"invalid current message rejected rather than trimmed");
   }
   List<String[]> many=new ArrayList<>();for(int i=0;i<15;i++)many.add(new String[]{"user","turn"+i});
   String limited=(String)body.invoke(null,"t","hi","question",many);
   ok(!limited.contains("turn2\"")&&limited.contains("turn3\"")&&limited.contains("turn14\""),"max 12 newest turns");
   // Construct independently an exactly-full body: seven full ASCII turns plus filler.
   List<String[]> edge=new ArrayList<>();StringBuilder expected=new StringBuilder("{\"task_id\":\"t\",\"message\":\"hi\",\"mode\":\"question\",\"history\":[");
   for(int i=0;i<7;i++){if(i>0)expected.append(',');expected.append("{\"role\":\"user\",\"content\":\"").append(repeat("x",8000)).append("\"}");edge.add(new String[]{"user",repeat("x",8000)});}
   expected.append(",{\"role\":\"user\",\"content\":\"");String end="\"}]}";
   int fill=60000-expected.toString().getBytes("UTF-8").length-end.length();edge.add(new String[]{"user",repeat("z",fill)});expected.append(repeat("z",fill)).append(end);
   String boundary=(String)body.invoke(null,"t","hi","question",edge);
   ok(boundary.equals(expected.toString())&&boundary.getBytes("UTF-8").length==60000,"exact 60000-byte payload retained");
   edge.get(7)[1]+="z";
   String over=(String)body.invoke(null,"t","hi","question",edge);
   ok(over.getBytes("UTF-8").length<=60000&&over.length()<boundary.length(),"one byte over evicts oldest turn");
   String heavy=(String)body.invoke(null,repeat("漢",256),repeat("\u0001",8000),"email_draft",turns);
   ok(heavy.getBytes("UTF-8").length<=60000,"escaped max message plus task_id/mode budgeted");
   if(System.getProperty("chat.fixture")!=null)java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("chat.fixture")),Arrays.asList(json,exact,boundary,over,heavy),java.nio.charset.StandardCharsets.UTF_8);
  }
  System.out.println("PASS "+checks+" checks ("+mode+")");
 }
}

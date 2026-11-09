package com.simon.mytasks;
import java.lang.reflect.*;import java.util.*;import org.json.*;
public final class TaskActionPolicyTest {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static JSONObject change(JSONObject task,String field,Object value)throws Exception{try{return (JSONObject)Class.forName("com.simon.mytasks.TaskActionPolicy").getMethod("change",JSONObject.class,String.class,Object.class).invoke(null,task,field,value);}catch(ClassNotFoundException|NoSuchMethodException e){throw new AssertionError("Missing revision-bound quick action",e);}catch(InvocationTargetException e){if(e.getCause()instanceof IllegalArgumentException)throw(IllegalArgumentException)e.getCause();throw e;}}
 public static void main(String[]args)throws Exception{
  String revision=String.join("",Collections.nCopies(64,"a"));JSONObject source=new JSONObject().put("id","synthetic-task").put("revision",revision).put("status","todo").put("notification",new JSONObject().put("token","unread-update").put("unread",true));String before=source.toString();
  JSONObject body=change(source,"status","completed");ok(body.length()==2,"quick action sends exactly revision and requested field");ok(body.getString("revision").equals(revision),"revision is exact, never normalized");ok(body.getString("status").equals("completed"),"completion is an explicit status change");ok(!body.has("notification")&&!body.has("token"),"completion does not acknowledge an update");ok(before.equals(source.toString()),"quick action never rewrites canonical input");
  for(String field:new String[]{"notification","token","id","revision","title","deleted"}){boolean rejected=false;try{change(source,field,"x");}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"unsupported quick field rejected: "+field);}
  for(String field:new String[]{"status","priority"}){boolean rejected=false;try{change(source,field,"unknown");}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"unsupported enum value rejected: "+field);}
  JSONObject move=change(source,"project","Work / Exact CASE");ok(move.getString("project").equals("Work / Exact CASE"),"project label is preserved exactly");ok(change(source,"project",JSONObject.NULL).isNull("project"),"clear project is explicit JSON null");
  for(JSONObject invalid:new JSONObject[]{new JSONObject(source.toString()).put("deleted",true),new JSONObject(source.toString()).put("revision",revision.toUpperCase(Locale.ROOT)),new JSONObject(source.toString()).put("revision"," "+revision),new JSONObject(source.toString()).put("id","")}){boolean rejected=false;try{change(invalid,"priority","high");}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"deleted/malformed source cannot dispatch a quick mutation");}
  System.out.println("TaskActionPolicyTest: "+checks+" assertions passed");
 }
}

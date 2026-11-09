package com.simon.mytasks;
import org.json.*;
import java.lang.reflect.*;
public final class UpdateReviewPolicyTest {
 static int checks;
 static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static Object call(String name,Class<?>[] types,Object...args)throws Exception{
  try{return Class.forName("com.simon.mytasks.UpdateReviewPolicy").getMethod(name,types).invoke(null,args);}
  catch(ClassNotFoundException|NoSuchMethodException e){throw new AssertionError("Missing update review behavior: "+name,e);}
 }
 static JSONObject task(String token,boolean unread)throws Exception{return new JSONObject().put("id","synthetic-task").put("title","A supplied title").put("revision","revision-original").put("notification",new JSONObject().put("token",token).put("unread",unread).put("sections",new JSONObject().put("context","Supplied background").put("update","Supplied change")));}
 public static void main(String[] args)throws Exception{
  JSONObject original=task("opaque-original",true);Object shown=call("capture",new Class<?>[]{JSONObject.class},original);
  original.put("title","Later title");original.getJSONObject("notification").put("token","opaque-new").getJSONObject("sections").put("update","Later change");
  Class<?> type=shown.getClass();
  ok(type.getMethod("taskId").invoke(shown).equals("synthetic-task"),"displayed task identity is retained");
  ok(type.getMethod("token").invoke(shown).equals("opaque-original"),"acknowledgement retains the displayed token after canonical change");
  ok(type.getMethod("title").invoke(shown).equals("A supplied title"),"displayed title is snapshot-bound");
  ok(type.getMethod("section",String.class).invoke(shown,"update").equals("Supplied change"),"displayed prose does not change behind the reader");
  ok(type.getMethod("section",String.class).invoke(shown,"next_steps").equals(""),"absent advice is never invented");
  JSONObject current=task("opaque-new",true),reply=task("opaque-original",false);String before=current.toString();
  JSONObject merged=(JSONObject)call("mergeAcknowledgement",new Class<?>[]{type,JSONObject.class,JSONObject.class},shown,current,reply);
  ok(merged.getJSONObject("notification").optBoolean("unread"),"acknowledging an old snapshot cannot clear an already observed new update");
  ok(merged.getJSONObject("notification").getString("token").equals("opaque-new"),"newer canonical identity survives a late old acknowledgement response");
  ok(current.toString().equals(before),"merge never mutates canonical input objects");
  ok((Boolean)call("confirmed",new Class<?>[]{type,JSONObject.class},shown,reply),"exact server-confirmed acknowledgement is recognized");
  ok(!(Boolean)call("shouldCancelNotification",new Class<?>[]{type,JSONObject.class,JSONObject.class},shown,reply,merged),"a newer OS notification is not cancelled by an older acknowledgement");
  JSONObject same=task("opaque-original",true);
  merged=(JSONObject)call("mergeAcknowledgement",new Class<?>[]{type,JSONObject.class,JSONObject.class},shown,same,reply);
  ok(!merged.getJSONObject("notification").optBoolean("unread"),"exact confirmed snapshot can clear its own marker");
  ok((Boolean)call("shouldCancelNotification",new Class<?>[]{type,JSONObject.class,JSONObject.class},shown,reply,merged),"exact confirmed snapshot can cancel its matching notification");
  JSONObject malformed=task("different-token",false);
  merged=(JSONObject)call("mergeAcknowledgement",new Class<?>[]{type,JSONObject.class,JSONObject.class},shown,same,malformed);
  ok(merged.getJSONObject("notification").optBoolean("unread"),"an unconfirmed different token cannot clear the displayed update");
  JSONObject foreign=task("opaque-original",false).put("id","another-task");
  ok(!(Boolean)call("confirmed",new Class<?>[]{type,JSONObject.class},shown,foreign),"another task is never confirmation");
  JSONObject fresh=task("opaque-new",true);
  merged=(JSONObject)call("mergeAcknowledgement",new Class<?>[]{type,JSONObject.class,JSONObject.class},shown,same,fresh);
  ok(merged.getJSONObject("notification").getString("token").equals("opaque-new"),"a canonical newer unread response becomes reviewable");
  ok((Boolean)call("newer",new Class<?>[]{type,JSONObject.class},shown,fresh),"new identity is distinguished without comparing opaque tokens lexically");
  System.out.println("UpdateReviewPolicyTest: "+checks+" assertions passed");
 }
}

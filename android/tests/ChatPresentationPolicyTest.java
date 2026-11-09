package com.simon.mytasks;

/** Host-only executable with explicit synthetic fixtures; no Android dependencies. */
public final class ChatPresentationPolicyTest {
 static int assertions;
 static void check(boolean condition,String reason){assertions++;if(!condition)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  Class<?> policy;
  try{policy=Class.forName("com.simon.mytasks.ChatPresentationPolicy");}
  catch(ClassNotFoundException e){throw new AssertionError("Missing viewport policy: polling must not pull readers to the bottom",e);}
  java.lang.reflect.Method follow=policy.getDeclaredMethod("shouldFollow",boolean.class,boolean.class,boolean.class);
  follow.setAccessible(true);
  check(!(Boolean)follow.invoke(null,false,false,false),"A reader above bottom stays put");
  check((Boolean)follow.invoke(null,false,true,false),"Own accepted send follows latest");
  check((Boolean)follow.invoke(null,true,false,false),"Near-bottom readers follow latest");
  check(!(Boolean)follow.invoke(null,true,true,true),"Prepending history preserves anchor even after send");
  grouping(policy);
  viewport(policy);
  restoration(policy);
  reconciliation(policy);
  System.out.println("ChatPresentationPolicyTest: "+assertions+" assertions passed");
 }
 static void restoration(Class<?> policy)throws Exception{
  java.lang.reflect.Method initial;
  try{initial=policy.getDeclaredMethod("initialFollow",boolean.class,boolean.class,boolean.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing saved-thread viewport restoration policy",e);}
  initial.setAccessible(true);
  check((Boolean)initial.invoke(null,false,false,false),"First visit starts at actual latest loaded message");
  check(!(Boolean)initial.invoke(null,true,false,false),"Returning or rotating preserves saved reading position even when messages arrived");
  check((Boolean)initial.invoke(null,true,true,false),"A viewport saved at bottom returns to bottom");
  check((Boolean)initial.invoke(null,true,false,true),"Own send admitted while away can follow originating thread");
 }
 static void reconciliation(Class<?> policy)throws Exception{
  java.lang.reflect.Method signature,count,own;
  try{signature=policy.getDeclaredMethod("messageSignature",org.json.JSONArray.class);count=policy.getDeclaredMethod("countAfter",org.json.JSONArray.class,long.class);own=policy.getDeclaredMethod("containsOwnSend",org.json.JSONArray.class,String.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing message-only reconciliation and actual arrival counting",e);}
  signature.setAccessible(true);count.setAccessible(true);own.setAccessible(true);
  org.json.JSONObject message=new org.json.JSONObject().put("id",100).put("role","assistant").put("content","Actual response").put("created_at","2026-11-09T10:00:00Z").put("request_id","request-A");
  org.json.JSONArray turns=new org.json.JSONArray().put(message);
  String initial=(String)signature.invoke(null,turns);
  message.put("irrelevant_poll_metadata",5);
  check(initial.equals(signature.invoke(null,turns)),"Poll metadata must not recreate selectable message views");
  message.put("content","Edited response");
  check(!initial.equals(signature.invoke(null,turns)),"A real content edit rebuilds affected history");
  check((Integer)count.invoke(null,turns,100L)==0,"Unchanged history has zero arrivals");
  turns.put(new org.json.JSONObject().put("id",101).put("role","user").put("request_id","request-B"));
  turns.put(new org.json.JSONObject().put("id",105).put("role","assistant"));
  check((Integer)count.invoke(null,turns,100L)==2,"Count observed messages, not numeric ID gaps");
  turns.put(new org.json.JSONObject().put("id",90).put("role","assistant"));
  check((Integer)count.invoke(null,turns,100L)==2,"Older history never increments arrivals");
  check((Boolean)own.invoke(null,turns,"request-B"),"Follow own admitted send by exact request identity");
  check(!(Boolean)own.invoke(null,turns,"request-A"),"Assistant response alone is not own send");
  check(!(Boolean)own.invoke(null,turns,""),"Empty identity cannot trigger follow");
 }
 static void viewport(Class<?> policy)throws Exception{
  java.lang.reflect.Method near,restore;
  try{near=policy.getDeclaredMethod("nearBottom",int.class,int.class,int.class,int.class);restore=policy.getDeclaredMethod("restoreY",int.class,int.class,int.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing stable viewport geometry",e);}
  near.setAccessible(true);restore.setAccessible(true);
  check(!(Boolean)near.invoke(null,2000,100,500,72),"Reader far from bottom does not follow polls");
  check((Boolean)near.invoke(null,2000,1428,500,72),"Bottom threshold includes boundary");
  check((Boolean)near.invoke(null,100,0,500,72),"Short content is at bottom");
  check((Integer)restore.invoke(null,900,-24,100)==924,"Partially visible message retains relative offset after prepend");
  check((Integer)restore.invoke(null,900,40,100)==860,"Whitespace before visible message retains offset");
  check((Integer)restore.invoke(null,-1,0,123)==123,"Missing anchor falls back to saved scroll, never latest");
  check((Integer)restore.invoke(null,10,40,123)==0,"Restored viewport clamps to zero");
 }
 static void grouping(Class<?> policy)throws Exception{
  java.lang.reflect.Method group;
  try{group=policy.getDeclaredMethod("grouped",org.json.JSONObject.class,org.json.JSONObject.class,java.time.ZoneId.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing grouping policy: consecutive roles need real timestamp boundaries",e);}
  group.setAccessible(true);
  org.json.JSONObject first=new org.json.JSONObject().put("role","assistant").put("created_at","2026-11-09T10:00:00Z");
  org.json.JSONObject next=new org.json.JSONObject(first.toString()).put("created_at","2026-11-09T10:05:00Z");
  java.time.ZoneId utc=java.time.ZoneId.of("UTC");
  check((Boolean)group.invoke(null,first,next,utc),"Five minute consecutive incoming messages group");
  next.put("created_at","2026-11-09T10:05:00.500Z");
  check(!(Boolean)group.invoke(null,first,next,utc),"Fractional seconds beyond the grouping window split");
  next.put("created_at","2026-11-09T10:05:01Z");
  check(!(Boolean)group.invoke(null,first,next,utc),"Long gaps start a new bubble group");
  next.put("created_at","2026-11-09T10:01:00Z").put("role","user");
  check(!(Boolean)group.invoke(null,first,next,utc),"Different speakers never group");
  next.put("role","assistant").put("created_at","not a timestamp");
  check(!(Boolean)group.invoke(null,first,next,utc),"Unknown timestamps never invent grouping");
  first.put("created_at","2026-11-09T23:59:00Z");next.put("created_at","2026-11-10T00:01:00Z");
  check(!(Boolean)group.invoke(null,first,next,utc),"Local date boundary separates groups");
  check((Boolean)group.invoke(null,first,next,java.time.ZoneId.of("America/New_York")),"Grouping uses display timezone rather than raw ISO date");
  next.put("created_at","2026-11-09T23:58:00Z");
  check(!(Boolean)group.invoke(null,first,next,utc),"Reversed clocks do not group");
 }
}

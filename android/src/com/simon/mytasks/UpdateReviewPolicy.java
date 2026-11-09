package com.simon.mytasks;
import org.json.*;
/** Presentation-only snapshot. Canonical unread state remains exclusively server-owned. */
public final class UpdateReviewPolicy {
 private UpdateReviewPolicy(){}
 public static final class Snapshot {
  private final String id,token,title,headline,context,update,next,updated;
  private Snapshot(JSONObject task){
   id=task.optString("id","");title=task.optString("title","");
   JSONObject n=task.optJSONObject("notification");if(n==null)n=new JSONObject();
   token=n.optString("token","");headline=n.optString("headline","");updated=n.optString("updated_at","");
   JSONObject sections=n.optJSONObject("sections");if(sections==null)sections=new JSONObject();
   context=sections.optString("context","");update=sections.optString("update","");next=sections.optString("next_steps","");
  }
  public String taskId(){return id;} public String token(){return token;} public String title(){return title;}
  public String headline(){return headline;} public String updatedAt(){return updated;}
  public String section(String name){return "context".equals(name)?context:"update".equals(name)?update:"next_steps".equals(name)?next:"";}
 }
 public static Snapshot capture(JSONObject task){if(task==null)throw new IllegalArgumentException("Task unavailable");return new Snapshot(task);}
 private static JSONObject notification(JSONObject task){return task==null?null:task.optJSONObject("notification");}
 public static boolean newer(Snapshot shown,JSONObject task){JSONObject n=notification(task);return task!=null&&shown.id.equals(task.optString("id"))&&n!=null&&n.optBoolean("unread")&&!n.optString("token","").isEmpty()&&!shown.token.equals(n.optString("token"));}
 public static boolean confirmed(Snapshot shown,JSONObject reply){JSONObject n=notification(reply);return !shown.token.isEmpty()&&reply!=null&&shown.id.equals(reply.optString("id"))&&n!=null&&!n.optBoolean("unread",true)&&shown.token.equals(n.optString("token"));}
 private static JSONObject copy(JSONObject task){try{return task==null?null:new JSONObject(task.toString());}catch(JSONException e){throw new IllegalArgumentException("Invalid task snapshot",e);}}
 public static JSONObject mergeAcknowledgement(Snapshot shown,JSONObject current,JSONObject reply){
  if(reply==null||!shown.id.equals(reply.optString("id"))||newer(shown,current))return copy(current);
  JSONObject n=notification(reply);return confirmed(shown,reply)||(n!=null&&n.optBoolean("unread"))?copy(reply):copy(current);
 }
 public static boolean shouldCancelNotification(Snapshot shown,JSONObject reply,JSONObject merged){JSONObject n=notification(merged);return confirmed(shown,reply)&&merged!=null&&shown.id.equals(merged.optString("id"))&&n!=null&&!n.optBoolean("unread",true);}
}

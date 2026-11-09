package com.simon.mytasks;
import org.json.*;
/** Builds only the requested quick change; never sends notification/acknowledgement state. */
public final class TaskActionPolicy {
 private TaskActionPolicy(){}
 public static JSONObject change(JSONObject task,String field,Object value){
  if(task==null||task.optString("id","").isEmpty()||task.optBoolean("deleted"))throw new IllegalArgumentException("Task unavailable for quick changes");
  String revision=task.optString("revision","");if(!revision.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Refresh the task before changing it");
  if("status".equals(field)){if(!(value instanceof String)||!java.util.Arrays.asList("inbox","todo","in_progress","waiting","completed","cancelled").contains(value))throw new IllegalArgumentException("Choose a supported status");}
  else if("priority".equals(field)){if(!(value instanceof String)||!java.util.Arrays.asList("high","medium","low").contains(value))throw new IllegalArgumentException("Choose a supported priority");}
  else if("project".equals(field)){if(value!=JSONObject.NULL&&!(value instanceof String))throw new IllegalArgumentException("Project must be a name or null");}
  else throw new IllegalArgumentException("Unsupported quick change");
  JSONObject body=new JSONObject();try{return body.put("revision",revision).put(field,value);}catch(JSONException e){throw new IllegalArgumentException("Task revision unavailable",e);}
 }
}

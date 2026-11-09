package com.simon.mytasks;
import org.json.*;
/** Snapshot-bound reviewed sections always supersede legacy technical diff summaries. */
final class NotificationProse {
 static String markdown(JSONObject notification){
  if(notification==null)return "";StringBuilder b=new StringBuilder();JSONObject s=notification.optJSONObject("sections");
  if(s!=null){section(b,"Context",s.optString("context",""));section(b,"New",s.optString("update",""));section(b,"Suggested next steps",s.optString("next_steps",""));return b.toString();}
  JSONArray old=notification.optJSONArray("summary");if(old!=null){for(int i=0;i<old.length();i++){String text=old.optString(i,"");if(!text.isEmpty()){if(b.length()>0)b.append('\n');b.append(text);}}return b.toString();}
  return notification.optString("summary","");
 }
 private static void section(StringBuilder b,String title,String text){if(text.trim().isEmpty())return;if(b.length()>0)b.append("\n\n");b.append("**").append(title).append("**\n\n").append(text);}
}

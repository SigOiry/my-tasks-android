package com.simon.mytasks;
import org.json.*;
/** Compact display hides redundant labels, never their accessibility context. */
final class CompactPolicy {
 static boolean expanded(int width){return width>=720;}
 static boolean secondaryChrome(boolean keyboard,int height){return !keyboard&&height>=320;}
 static int composerLines(int availableHeight){return availableHeight<280?1:3;}
 static String title(JSONObject t){JSONObject n=t.optJSONObject("notification");return (n!=null&&n.optBoolean("unread")?"● ":"")+(t.optString("priority").equals("high")?"! ":"")+t.optString("title");}
 static String detail(JSONObject t,boolean projectGrouped,String due){String project=!projectGrouped&&!t.isNull("project")?t.optString("project",""):"";return project+(project.isEmpty()||due.isEmpty()?"":" · ")+due;}
}

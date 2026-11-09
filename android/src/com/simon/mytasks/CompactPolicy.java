package com.simon.mytasks;
import org.json.*;
/** Compact display hides redundant labels, never their accessibility context. */
final class CompactPolicy {
 static boolean expanded(int width){return expanded(width,1f);}
 static boolean expanded(int width,float fontScale){return width>=720&&width/Math.max(1f,fontScale)>=600f;}
 static boolean globalNavigation(boolean expanded,String destination,String task,String thread){return expanded||(destination.equals("Tasks")?task.isEmpty():thread.isEmpty());}
 static int masterWidth(int availableWidth){return Math.max(280,Math.min(340,Math.round((availableWidth-88)*.46f)));}
 static boolean secondaryChrome(boolean keyboard,int height){return !keyboard&&height>=320;}
 static int composerLines(int availableHeight){return availableHeight<280?1:3;}
 static String title(JSONObject t){JSONObject n=t.optJSONObject("notification");return (n!=null&&n.optBoolean("unread")?"● ":"")+(t.optString("priority").equals("high")?"! ":"")+t.optString("title");}
 static String detail(JSONObject t,boolean projectGrouped,String due){String project=!projectGrouped&&!t.isNull("project")?t.optString("project",""):"";return project+(project.isEmpty()||due.isEmpty()?"":" · ")+due;}
}

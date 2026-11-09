package com.simon.mytasks;

/** Exact OS notification keys. Length framing avoids delimiter/hash collisions. */
final class NotificationIdentity {
 private NotificationIdentity(){}
 private static String part(String value){return value.length()+":"+value;}
 static String scope(String epoch,String taskId){return "mytasks.update.v1:"+part(epoch)+part(taskId);}
 static String tag(String epoch,String taskId,String updateToken){return scope(epoch,taskId)+part(updateToken);}
 /** Used only when posting a replacement, never to acknowledge a displayed update. */
 static boolean sameTask(String tag,String epoch,String taskId){return tag!=null&&tag.startsWith(scope(epoch,taskId));}
}

package com.simon.mytasks;

/** Pure row/scope presentation; no canonical task or acknowledgement mutations. */
final class TaskPresentation {
 static final String UPDATE="● New update";
 static final int FLAG_NONE=0,FLAG_OUTLINE=1,FLAG_FILLED=2;
 static String context(String detail,boolean unread){return unread?UPDATE+(detail.isEmpty()?"":" · "+detail):detail;}
 static boolean wrapContext(boolean unread){return unread;}
 static String[] visibleScopes(String current){return new String[]{"Active","Today","Week".equals(current)||"Trash".equals(current)?current:"All"};}
 static String[] menuScopes(){return new String[]{"Active","Today","Week","All","Trash"};}
 static int selectedScope(String current){String[] all=menuScopes();for(int i=0;i<all.length;i++)if(all[i].equals(current))return i;return -1;}
 static int flag(String priority){return "high".equals(priority)?FLAG_FILLED:"medium".equals(priority)?FLAG_OUTLINE:FLAG_NONE;}
 static int flagColor(String priority,boolean dark,int secondary){return "high".equals(priority)?(dark?0xffff9aa2:0xffce363f):"medium".equals(priority)?(dark?0xffe5b754:0xffa56b00):secondary;}
}

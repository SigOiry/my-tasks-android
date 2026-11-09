package com.simon.mytasks;
import java.util.LinkedHashMap;

/** Stable action identities: hiding task-only entries cannot shift other routes. */
final class ConversationActionPolicy {
 static LinkedHashMap<String,String> threadActions(String task,boolean archived){
  LinkedHashMap<String,String> actions=new LinkedHashMap<>();
  actions.put("history","Refresh / recover server history");
  actions.put("rename","Rename conversation");
  actions.put("archive",archived?"Reopen conversation":"Archive conversation");
  actions.put("discussions","All Discussions");actions.put("tasks","Tasks");actions.put("settings","Settings");
  if(task!=null&&!task.isEmpty()){actions.put("saved","Saved task conversations");actions.put("new","New task conversation");}
  actions.put("mode","Message mode and voice");actions.put("capabilities","Capabilities");
  return actions;
 }
}

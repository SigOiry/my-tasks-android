package com.simon.mytasks;
import org.json.*;
public final class ConversationStateTest {
 static int n; static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  ConversationState s=new ConversationState();s.select("a");s.draft("a","first");
  JSONObject request=s.prepare("a",7,"question");String id=request.getString("request_id");
  check(id.matches("[0-9a-f-]{36}"),"UUID identity before network");
  check(s.draft("a").equals(""),"submitted text is owned by envelope");
  s.draft("a","follow up");s.select("b");s.draft("b","different");
  ConversationState restored=new ConversationState(new JSONObject(s.json().toString()));
  check(restored.pending("a").getString("request_id").equals(id),"identity survives restart");
  check(restored.selected().equals("b"),"selection survives restart");
  restored.accept("a",new JSONObject().put("id",id).put("status","succeeded"));
  check(restored.draft("a").equals("follow up"),"completion preserves followup");
  check(restored.draft("b").equals("different"),"completion cannot touch another thread");
  boolean blocked=false;try{s.prepare("a",8,"question");}catch(IllegalStateException e){blocked=true;}check(blocked,"uncertain request cannot get new ID");
  JSONObject p=new JSONObject().put("conversation",new JSONObject().put("id","a")).put("messages",new JSONArray().put(msg(3)).put(msg(4))).put("has_more",true).put("before_cursor","old");
  s.page("a",p,false);s.page("a",new JSONObject(p.toString()).put("messages",new JSONArray().put(msg(1)).put(msg(2)).put(msg(3))).put("has_more",false).put("before_cursor",JSONObject.NULL),true);
  check(s.messages("a").length()==4,"overlapping pages deduped");check(s.messages("a").getJSONObject(0).getInt("id")==1,"older messages prepend");
  s.page("a",p,false);check(!s.page("a").optBoolean("has_more"),"refresh preserves known full history boundary");
  check(s.messages("a").length()==4,"refresh never truncates full history");
  check(ConversationState.pendingStatus("queued")&&ConversationState.pendingStatus("running")&&!ConversationState.pendingStatus("interrupted"),"restart interruption is terminal");
  check(ConversationState.canApplyVoice("epoch","a","epoch","a")&&!ConversationState.canApplyVoice("epoch","a","other","a")&&!ConversationState.canApplyVoice("epoch","a","epoch","b"),"voice account and thread guard");
  s.draft("unicode",new String(Character.toChars(0x1f600)));check(s.prepare("unicode",1,"question").getString("content").codePointCount(0,2)==1,"unicode codepoints");
  s.draft("large",String.join("",java.util.Collections.nCopies(8001,"x")));blocked=false;try{s.prepare("large",1,"question");}catch(IllegalArgumentException e){blocked=true;}check(blocked,"server message limit respected");
  ConversationState remote=new ConversationState();JSONObject empty=new JSONObject().put("conversation",new JSONObject().put("id","remote")).put("messages",new JSONArray()).put("has_more",false);remote.page("remote",empty,false);JSONObject distant=new JSONObject(p.toString()).put("messages",new JSONArray().put(msg(70)).put(msg(71)));remote.page("remote",distant,false);check(remote.page("remote").optBoolean("has_more"),"a previously empty cached thread can acquire remote older history");
  remote.page("remote",new JSONObject(empty.toString()).put("messages",new JSONArray().put(msg(1)).put(msg(2))),true);remote.page("remote",new JSONObject(p.toString()).put("messages",new JSONArray().put(msg(200)).put(msg(201))),false);check(remote.page("remote").optBoolean("has_more"),"disjoint remote latest page must reopen pagination rather than hiding a middle gap");
  ConversationState deltas=new ConversationState();deltas.page("delta",p,false);deltas.page("delta",new JSONObject().put("conversation",new JSONObject().put("id","delta")).put("messages",new JSONArray().put(msg(99))),false);check(deltas.page("delta").optBoolean("has_more"),"202 message delta cannot erase an older-history cursor");
  ConversationState coverage=new ConversationState();coverage.page("coverage",new JSONObject(empty.toString()).put("messages",new JSONArray().put(msg(1)).put(msg(2))).put("context_compacted",true),false);coverage.page("coverage",new JSONObject().put("conversation",new JSONObject().put("id","coverage")).put("messages",new JSONArray().put(msg(71))),false);check(coverage.page("coverage").optBoolean("context_compacted"),"message admission delta preserves truthful compaction hint");coverage.page("coverage",new JSONObject(p.toString()).put("messages",new JSONArray().put(msg(70)).put(msg(71))),false);check(coverage.page("coverage").optBoolean("has_more"),"a single admitted message cannot bridge an unknown remote history gap");
  System.out.println("ConversationStateTest: "+n+" assertions passed");
 }
 static JSONObject msg(int id)throws Exception{return new JSONObject().put("id",id).put("content","message "+id);}
}

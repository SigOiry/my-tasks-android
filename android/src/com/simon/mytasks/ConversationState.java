package com.simon.mytasks;

import org.json.*;
import java.util.*;

/** Pure persisted client state. Server messages, not a client-built model history. */
final class ConversationState {
 private final JSONObject data;
 ConversationState(){this(new JSONObject());}
 ConversationState(JSONObject saved){data=saved;for(String key:new String[]{"drafts","modes","pages","pending","task_selection"})if(data.optJSONObject(key)==null)put(data,key,new JSONObject());}
 static void put(JSONObject o,String key,Object value){try{o.put(key,value);}catch(JSONException e){throw new IllegalArgumentException("Invalid conversation state",e);}}
 JSONObject json(){return data;}
 void clear(){ArrayList<String> keys=new ArrayList<>();Iterator<String> iterator=data.keys();while(iterator.hasNext())keys.add(iterator.next());for(String key:keys)data.remove(key);for(String key:new String[]{"drafts","modes","pages","pending","task_selection"})put(data,key,new JSONObject());}
 String selected(){return data.optString("selected","");}
 void select(String id){put(data,"selected",id);}
 String taskSelection(String task){return data.optJSONObject("task_selection").optString(task,"");}
 void taskSelection(String task,String id){put(data.optJSONObject("task_selection"),task,id);}
 String draft(String id){return data.optJSONObject("drafts").optString(id,"");}
 void draft(String id,String value){put(data.optJSONObject("drafts"),id,value);}
 String mode(String id){return data.optJSONObject("modes").optString(id,"question");}
 void mode(String id,String value){put(data.optJSONObject("modes"),id,value);}
 JSONObject page(String id){return data.optJSONObject("pages").optJSONObject(id);}
 JSONObject conversation(String id){JSONObject p=page(id);return p==null?null:p.optJSONObject("conversation");}
 JSONArray messages(String id){JSONObject p=page(id);JSONArray messages=p==null?null:p.optJSONArray("messages");return messages==null?new JSONArray():messages;}
 JSONObject pending(String id){return data.optJSONObject("pending").optJSONObject(id);}
 static boolean pendingStatus(String status){return status.equals("queued")||status.equals("running");}
 boolean blocked(String id){JSONObject p=pending(id);return p!=null&&(p.optString("status").equals("uncertain")||p.optString("status").equals("prepared")||pendingStatus(p.optString("status")));}
 JSONObject prepare(String id,int revision,String mode){
  String content=draft(id);if(content.trim().isEmpty()||content.codePointCount(0,content.length())>8000)throw new IllegalArgumentException("Message must contain 1–8000 characters.");
  if(blocked(id))throw new IllegalStateException("Resolve the existing request before sending another message.");
  JSONObject p=new JSONObject();put(p,"request_id",UUID.randomUUID().toString());put(p,"content",content);put(p,"mode",mode);put(p,"expected_revision",revision);put(p,"status","prepared");put(data.optJSONObject("pending"),id,p);draft(id,"");return p;
 }
 JSONObject body(String id){JSONObject p=pending(id),b=new JSONObject();for(String k:new String[]{"request_id","content","mode","expected_revision"})put(b,k,p.opt(k));return b;}
 void uncertain(String id){JSONObject p=pending(id);if(p!=null)put(p,"status","uncertain");}
 void accept(String id,JSONObject request){
  JSONObject p=pending(id);String rid=request.optString("id");
  if(p==null||!p.optString("request_id").equals(rid)){p=new JSONObject();put(p,"request_id",rid);put(data.optJSONObject("pending"),id,p);}
  put(p,"status",request.optString("status"));put(p,"request",request);
 }
 void page(String id,JSONObject incoming,boolean older){
  JSONObject existing=page(id);TreeMap<Long,JSONObject> turns=new TreeMap<>();
  if(existing!=null)merge(turns,existing.optJSONArray("messages"));merge(turns,incoming.optJSONArray("messages"));
  JSONArray all=new JSONArray();for(JSONObject turn:turns.values())all.put(turn);
  JSONObject page=new JSONObject();put(page,"conversation",incoming.optJSONObject("conversation"));put(page,"messages",all);put(page,"context_compacted",incoming.has("context_compacted")?incoming.optBoolean("context_compacted"):existing!=null&&existing.optBoolean("context_compacted"));
  // Remote clients may have added more than a page since our last visit. An
  // old fully-loaded prefix is not proof that the newly observed middle exists.
  JSONArray fresh=incoming.optJSONArray("messages");JSONArray known=existing==null?new JSONArray():existing.optJSONArray("latest_page_ids");if(known==null)known=ids(existing.optJSONArray("messages"));
  boolean gap=existing!=null&&incoming.has("has_more")&&fresh!=null&&fresh.length()>0&&!overlaps(known,fresh);
  // Admission deltas are real messages, but never proof of complete coverage.
  put(page,"latest_page_ids",!older&&incoming.has("has_more")?ids(fresh):known);
  JSONObject boundary=older||existing==null||gap?incoming:existing;put(page,"has_more",boundary.optBoolean("has_more"));put(page,"before_cursor",boundary.opt("before_cursor"));put(data.optJSONObject("pages"),id,page);
 }
 private static JSONArray ids(JSONArray messages){JSONArray ids=new JSONArray();if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject message=messages.optJSONObject(i);if(message!=null)ids.put(message.optLong("id"));}return ids;}
 private static boolean overlaps(JSONArray known,JSONArray fresh){HashSet<Long> ids=new HashSet<>();for(int i=0;i<known.length();i++)ids.add(known.optLong(i));for(int i=0;i<fresh.length();i++){JSONObject message=fresh.optJSONObject(i);if(message!=null&&ids.contains(message.optLong("id")))return true;}return false;}
 private static void merge(TreeMap<Long,JSONObject> turns,JSONArray messages){if(messages==null)return;for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null)turns.put(m.optLong("id"),m);}}
 static boolean canApplyVoice(String epoch,String thread,String currentEpoch,String currentThread){return !thread.isEmpty()&&epoch.equals(currentEpoch)&&thread.equals(currentThread);}
}

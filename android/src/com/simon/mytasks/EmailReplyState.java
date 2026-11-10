package com.simon.mytasks;
import org.json.*;import java.util.Iterator;
/** Separate account-encrypted email workspace, never canonical task fields. */
final class EmailReplyState {
 final JSONObject data;EmailReplyState(JSONObject d){data=d;}
 static void put(JSONObject d,String k,Object v){ConversationState.put(d,k,v);}
 JSONObject json(){return data;} JSONObject draft(){return data.optJSONObject("draft");}
 String id(){return draft()==null?"":draft().optString("id");}int revision(){return draft()==null?0:draft().optInt("revision");}
 String body(){return data.optString("body",draft()==null?"":draft().optString("body"));}
 boolean dirty(){return draft()!=null&&!body().equals(draft().optString("body"));}
 boolean conflicted(){return data.optBoolean("conflict");}
 boolean editable(){return draft()!=null&&draft().optString("status").equals("draft")&&!data.optBoolean("send_pending");}
 void edit(String body){put(data,"body",body);data.remove("preview");}
 static final String[] LOCAL={"body","instruction","selection_start","selection_end","instruction_start","instruction_end","body_scroll","editor_scroll","assistant_scroll","focus","assistant","collapsed","intent","job","job_request","send_pending","send_request","conflict"};
 void parkDraft(){JSONObject old=draft();if(old==null)return;JSONObject saved=data.optJSONObject("local_drafts");if(saved==null){saved=new JSONObject();put(data,"local_drafts",saved);}JSONObject previous=new JSONObject();put(previous,"draft",old);for(String key:LOCAL)if(data.has(key))put(previous,key,data.opt(key));put(saved,old.optString("id"),previous);data.remove("draft");for(String key:LOCAL)data.remove(key);data.remove("preview");}
 void selectTask(String task){if(!task.equals(data.optString("task")))parkDraft();put(data,"task",task);}
 void accept(JSONObject d){JSONObject old=draft();if(old==null||!old.optString("id").equals(d.optString("id"))){parkDraft();JSONObject saved=data.optJSONObject("local_drafts");JSONObject restore=saved==null?null:saved.optJSONObject(d.optString("id"));if(restore!=null){Iterator<String> keys=restore.keys();while(keys.hasNext()){String k=keys.next();put(data,k,restore.opt(k));}}old=draft();}if(old!=null&&old.optString("id").equals(d.optString("id"))&&old.optInt("revision")>d.optInt("revision"))return;boolean keep=old!=null&&old.optString("id").equals(d.optString("id"))&&dirty();if(keep&&old.optInt("revision")!=d.optInt("revision")&&!body().equals(d.optString("body")))put(data,"conflict",true);put(data,"draft",d);if(!keep)put(data,"body",d.optString("body"));}
 JSONObject saveBody(){JSONObject b=new JSONObject();put(b,"expected_revision",revision());put(b,"body",body());return b;}
 void saved(JSONObject d,JSONObject sent){String local=body();accept(d);put(data,"body",local.equals(sent.optString("body"))?d.optString("body"):local);put(data,"conflict",false);}
 void conflict(JSONObject d){String local=body();accept(d);put(data,"body",local);put(data,"conflict",true);data.remove("preview");}
 boolean canApply(JSONObject p){return editable()&&!dirty()&&!conflicted()&&p!=null&&p.optInt("base_revision",-1)==revision();}
 void useRemote(){if(draft()!=null)put(data,"body",draft().optString("body"));put(data,"conflict",false);}
}

package com.simon.mytasks;

import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;

/** Activity-independent thread state, durable admission identity, bounded visible polling. */
final class ConversationController {
 interface Listener {void changed();}
 interface Work {JSONObject run(ConversationClient client)throws Exception;}
 interface Done {void accept(JSONObject result)throws Exception;}
 final Store store;final Store.Account account;final Handler main=new Handler(Looper.getMainLooper());
 final android.content.SharedPreferences prefs;
 final ConversationState state;
 final Set<String> operations=new HashSet<>();
 Listener listener;String visible="",notice="";boolean foreground=false,closed=false,connected=false;
 String scope="all",taskScope="",search="",next="";boolean archived=false;JSONArray threads=new JSONArray();
 private int listGeneration;int navigationVersion;private Runnable poll;private final Runnable saveDraft=()->save();
 private final Runnable metadataRefresh=new Runnable(){public void run(){if(!valid()||!foreground||listener==null)return;if(visible.isEmpty())list(false);else load(visible,false);watchMetadata();}};
 ConversationController(Store s,Store.Account a){
  store=s;account=a;prefs=s.context.getSharedPreferences("conversations",Context.MODE_PRIVATE);
  JSONObject saved=new JSONObject();if(a!=null&&a.epoch.equals(prefs.getString("epoch","")))try{saved=new JSONObject(prefs.getString("state","{}"));}catch(Exception ignored){}
  state=new ConversationState(saved);scope=saved.optString("scope","all");taskScope=saved.optString("task_scope","");search=saved.optString("search","");archived=saved.optBoolean("archived");threads=saved.optJSONArray("threads");if(threads==null)threads=new JSONArray();next=saved.optString("next","");
 }
 boolean valid(){return !closed&&store.current(account);}
 boolean save(){
  if(!valid())return false;JSONObject data=state.json();ConversationState.put(data,"threads",threads);ConversationState.put(data,"scope",scope);ConversationState.put(data,"task_scope",taskScope);ConversationState.put(data,"search",search);ConversationState.put(data,"archived",archived);ConversationState.put(data,"next",next);
  synchronized(Store.class){return store.current(account)&&prefs.edit().putString("epoch",account.epoch).putString("state",data.toString()).commit();}
 }
 String noticeFor(String id){return state.json().optString("notice:"+id,"");}
 void noticeFor(String id,String message){if(id.isEmpty())notice=message;else ConversationState.put(state.json(),"notice:"+id,message);}
 void changed(){if(listener!=null&&foreground)listener.changed();}
 void watchMetadata(){main.removeCallbacks(metadataRefresh);if(foreground&&listener!=null)main.postDelayed(metadataRefresh,15000);}
 void resume(){foreground=true;watchMetadata();if(!visible.isEmpty())load(visible,false);else if(listener!=null)list(false);}
 void pause(){foreground=false;cancelPoll();main.removeCallbacks(metadataRefresh);main.removeCallbacks(saveDraft);save();}
 void editDraft(String id,String text){state.draft(id,text);main.removeCallbacks(saveDraft);main.postDelayed(saveDraft,500);}
 void close(){pause();closed=true;listener=null;visible="";operations.clear();threads=new JSONArray();state.clear();notice="";}
 void show(String id){visible=id;cancelPoll();if(!id.isEmpty()){state.select(id);save();load(id,false);}changed();}
 void hide(){navigationVersion++;visible="";cancelPoll();save();}
 void filter(String scope,String task,boolean archived){this.scope=scope;taskScope=task;this.archived=archived;listGeneration++;next="";threads=new JSONArray();list(false);}
 void list(boolean more){
  final int gen=listGeneration;final String cursor=more?next:"";if(more&&cursor.isEmpty())return;
  final String requestedScope=scope,task=taskScope;final boolean arc=archived;
  execute("list:"+gen+":"+cursor,c->c.list(requestedScope,task,arc,cursor),r->{if(gen!=listGeneration)return;LinkedHashMap<String,JSONObject> known=new LinkedHashMap<>(),items=new LinkedHashMap<>();for(int i=0;i<threads.length();i++){JSONObject t=threads.optJSONObject(i);if(t!=null)known.put(t.optString("id"),t);}if(more)items.putAll(known);JSONArray rows=r.getJSONArray("conversations");for(int i=0;i<rows.length();i++){JSONObject t=rows.getJSONObject(i);items.put(t.getString("id"),ConversationState.metadata(known.get(t.getString("id")),t));if(state.conversation(t.getString("id"))!=null)seed(t);}threads=new JSONArray();for(JSONObject t:items.values())threads.put(t);next=r.isNull("next_cursor")?"":r.optString("next_cursor","");},null);
 }
 void create(String task,Runnable opened){final int navigation=navigationVersion;thread(visible,"create",c->c.create(task,null),r->{String id=r.getString("id");seed(r);notice="";if(navigation!=navigationVersion)return;state.select(id);if(task!=null)state.taskSelection(task,id);visible=id;if(opened!=null)opened.run();load(id,false);},"A creation may already have reached the server. Refresh Discussions before deciding to create another.");}
 void seed(JSONObject conversation){String id=conversation.optString("id");JSONObject p=new JSONObject();ConversationState.put(p,"conversation",conversation);ConversationState.put(p,"messages",new JSONArray());state.page(id,p,false);}
 void load(String id,boolean older){
  JSONObject page=state.page(id);String before=older&&page!=null?page.optString("before_cursor",""):"";if(older&&(page==null||!page.optBoolean("has_more")))return;
  thread(id,"history:"+id+":"+before,c->c.get(id,before),r->{
   state.page(id,r,older);JSONObject meta=r.getJSONObject("conversation"),latest=meta.optJSONObject("latest_request"),local=state.pending(id);
   // Resolve an unacknowledged local identity first, not an unrelated latest request.
   if(local!=null&&state.blocked(id)&& (latest==null||!local.optString("request_id").equals(latest.optString("id")))){check(id,local.optString("request_id"));}
   else if(latest!=null){if(local==null||!local.optString("status").equals("rejected")||ConversationState.pendingStatus(latest.optString("status"))||local.optString("request_id").equals(latest.optString("id")))state.accept(id,latest);schedule(id);}
  },null);
 }
 void patch(String id,String title,Boolean archive){JSONObject meta=state.conversation(id);if(meta==null)return;final int revision=meta.optInt("revision");thread(id,"edit:"+id,c->c.patch(id,revision,title,archive),r->{seed(r);if(title!=null)state.json().remove("rename:"+id);load(id,false);list(false);},"Conversation changed or connection failed. Your proposed title was not silently reapplied. Refresh and review the latest version.");}
 void send(String id,String mode){
  if(!connected){noticeFor(id,"Refresh this conversation with a connection before sending.");changed();return;}
  JSONObject meta=state.conversation(id);if(meta==null||meta.optBoolean("archived"))return;
  final String message=state.draft(id);final JSONObject previous=state.pending(id);
  try{state.prepare(id,meta.optInt("revision"),mode);if(!save()){state.json().optJSONObject("pending").remove(id);if(previous!=null)ConversationState.put(state.json().optJSONObject("pending"),id,previous);state.draft(id,message);throw new IllegalStateException("Could not persist request identity. Nothing was sent.");}post(id);}catch(Exception e){noticeFor(id,e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"Could not save request. Nothing was sent.");changed();}
 }
 void retryExact(String id){JSONObject p=state.pending(id);if(p==null||!p.has("content"))return;if(!save()){noticeFor(id,"Could not persist request. Nothing was sent.");changed();return;}post(id);}
 void post(String id){
  final JSONObject body=state.body(id);final String rid=body.optString("request_id");noticeFor(id,"");changed();
  executeTarget(id,"send:"+id,c->{try{return c.send(id,body);}catch(Api.Failure e){if(e.code>=400&&e.code<500)return new JSONObject().put("admission_rejected",e.code);throw e;}},r->{
   if(r.has("admission_rejected")){JSONObject p=state.pending(id);if(p!=null&&rid.equals(p.optString("request_id"))){ConversationState.put(p,"status","rejected");ConversationState.put(state.json(),"rejected:"+id,new JSONObject(p.toString()));}noticeFor(id,"Message not accepted (HTTP "+r.optInt("admission_rejected")+"). Refresh the latest thread and review before sending a new request.");load(id,false);return;}
   state.json().remove("rejected:"+id);state.accept(id,r.getJSONObject("request"));JSONObject page=new JSONObject().put("conversation",r.getJSONObject("conversation")).put("messages",new JSONArray().put(r.getJSONObject("user_message")));state.page(id,page,false);schedule(id);
  },"Delivery not confirmed. The saved request ID will not be automatically resent. Check status or explicitly retry the same request.",()->state.uncertain(id));
 }
 void check(String id,String request){
  if(request.isEmpty())return;thread(id,"status:"+id,c->c.request(id,request),r->{JSONObject p=state.pending(id);if(p!=null&&!p.optString("request_id").equals(request))return;state.accept(id,r);if(ConversationState.pendingStatus(r.optString("status")))schedule(id);else load(id,false);},"Request not confirmed. Refresh history before deciding what to do; no message was automatically resent.");
 }
 void reviewRetry(String id){
  if(!state.draft(id).isEmpty()){noticeFor(id,"Keep or clear your current draft before reviewing a retry.");changed();return;}
  JSONObject p=state.json().optJSONObject("rejected:"+id);if(p==null)p=state.pending(id);String content=p==null?"":p.optString("content","");String rid=p==null?"":p.optString("request_id","");JSONArray messages=state.messages(id);
  if(content.isEmpty())for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&m.optString("request_id").equals(rid)&&m.optString("role").equals("user"))content=m.optString("content");}
  state.draft(id,content);noticeFor(id,"Review the draft, then Send to start a new request. It was not sent automatically.");save();changed();
 }
 private void schedule(String id){if(!foreground||!id.equals(visible))return;cancelPoll();JSONObject p=state.pending(id);if(p==null||!ConversationState.pendingStatus(p.optString("status")))return;String rid=p.optString("request_id");poll=()->{poll=null;if(foreground&&id.equals(visible))check(id,rid);};main.postDelayed(poll,1800);}
 private void cancelPoll(){if(poll!=null)main.removeCallbacks(poll);poll=null;}
 void execute(String key,Work work,Done done,String error){execute(key,work,done,error,null);}
 void execute(String key,Work work,Done done,String error,Runnable failed){executeTarget("",key,work,done,error,failed);}
 void thread(String id,String key,Work work,Done done,String error){executeTarget(id,key,work,done,error,null);}
 void executeTarget(String target,String key,Work work,Done done,String error,Runnable failed){
  if(!valid()){noticeFor(target,"Connect your server in Settings.");changed();return;}if(!operations.add(key))return;changed();
  Api.IO.execute(()->{if(!valid())return;try{JSONObject result=work.run(new ConversationClient((method,path,body)->new Api(account).call(method,path,body)));main.post(()->{if(!valid())return;operations.remove(key);connected=true;noticeFor(target,"");try{done.accept(result);}catch(Exception e){noticeFor(target,"Conversation response could not be read. Refresh to recover server history.");}save();changed();});}catch(Exception e){main.post(()->{if(!valid())return;operations.remove(key);connected=false;noticeFor(target,error==null?Api.friendly(e):error);if(failed!=null)failed.run();save();changed();});}});
 }
}

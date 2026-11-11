package com.simon.mytasks;
import org.json.*;import java.util.UUID;
/** One workspace owner; all async callbacks fenced by account and route generation. */
final class EmailReplyController {
 interface Host{boolean valid();JSONObject read();boolean save(JSONObject d);void work(Runnable r);void ui(Runnable r);}
 interface Work{JSONObject run()throws Exception;}interface Done{void accept(JSONObject d)throws Exception;}
 final EmailReplyClient client;final Host host;final EmailReplyState state;Runnable listener;boolean busy,closed;String notice="";int generation;
 Runnable pendingAction;String pendingDraft;int pendingGeneration;boolean saving;
 boolean pendingFlush;String flushDraft;int flushGeneration;long editGeneration,reviewGeneration;
 void drainFlush(){
  if(!pendingFlush||busy)return;
  pendingFlush=false;
  if(valid()&&flushGeneration==generation&&flushDraft.equals(state.id()))flush();
 }
 void afterSave(Runnable action){
  if(!valid()||pendingAction!=null)return;
  if(state.data.optJSONObject("sources")!=null||state.conflicted()||!state.editable()){ready();return;}
  if(busy&&!saving)return;
  if(!persist()){changed();return;}
  pendingAction=action;pendingDraft=state.id();pendingGeneration=generation;
  if(state.dirty())flush();else resumeAction();
 }
 void resumeAction(){
  if(pendingAction==null||busy)return;
  if(!valid()||pendingGeneration!=generation||!pendingDraft.equals(state.id())){pendingAction=null;return;}
  if(state.conflicted()||!state.editable()){pendingAction=null;return;}
  if(!persist()){pendingAction=null;changed();return;}
  if(state.dirty()){flush();return;}
  Runnable action=pendingAction;pendingAction=null;action.run();
 }
 EmailReplyController(EmailReplyClient c,Host h){client=c;host=h;state=new EmailReplyState(h.read());}
 void changed(){if(listener!=null&&valid())listener.run();}
 boolean valid(){return !closed&&host.valid();}
 void invalidate(){closed=true;generation++;listener=null;}
 boolean persist(){boolean ok=valid()&&host.save(state.json());if(!ok)notice="Private recovery could not be saved. Nothing new will be submitted.";return ok;}
 void edit(String value){if(!value.equals(state.body()))editGeneration++;state.edit(value);persist();}
 void call(Work work,Done done){if(busy||!valid())return;busy=true;int ticket=generation;changed();host.work(()->{if(!valid()||ticket!=generation)return;try{JSONObject result=work.run();host.ui(()->{if(!valid()||ticket!=generation)return;busy=false;notice="";try{done.accept(result);}catch(Exception e){saving=false;pendingAction=null;pendingFlush=false;notice="Response unavailable. Local writing is retained. Refresh to recover.";}if(persist())drainFlush();else pendingFlush=false;changed();});}catch(Exception e){host.ui(()->{if(!valid()||ticket!=generation)return;busy=false;saving=false;pendingAction=null;pendingFlush=false;notice="Request not confirmed. Local writing is retained. Refresh before retrying.";persist();changed();});}});}
 void open(String task,String token){if(busy)return;EmailReplyState.put(state.data,"active",true);state.selectTask(task);EmailReplyState.put(state.data,"token",token==null?JSONObject.NULL:token);JSONObject loading=new JSONObject();EmailReplyState.put(loading,"sources",new JSONArray());EmailReplyState.put(loading,"reason","Resolving exact email context…");EmailReplyState.put(state.data,"sources",loading);persist();call(()->{try{return client.sources(task,token);}catch(Exception e){if(conflict(e))return new JSONObject().put("sources",new JSONArray()).put("reason","The displayed update is stale. Close Reply and review the latest update; no email was guessed or acknowledged.");throw e;}},r->EmailReplyState.put(state.data,"sources",r));}
 void choose(String source){if(busy||!valid())return;String task=state.data.optString("task"),token=state.data.isNull("token")?null:state.data.optString("token");String scope=new JSONArray().put(task).put(source).put(token==null?JSONObject.NULL:token).toString();String request=scope.equals(state.data.optString("create_scope"))?state.data.optString("create_request",UUID.randomUUID().toString()):UUID.randomUUID().toString();EmailReplyState.put(state.data,"create_request",request);EmailReplyState.put(state.data,"create_scope",scope);if(!persist())return;call(()->client.create(request,task,source,token),r->{state.data.remove("sources");state.data.remove("create_request");state.data.remove("create_scope");state.accept(r);});}
 void flush(){
  if(!valid()||!state.editable()||!state.dirty()||state.conflicted())return;
  if(busy){pendingFlush=true;flushDraft=state.id();flushGeneration=generation;return;}
  pendingFlush=false;
  JSONObject body=state.saveBody();String id=state.id();saving=true;
  call(()->{if(!id.equals(state.id()))return new JSONObject();try{return client.save(id,body);}catch(Exception e){if(conflict(e))return new JSONObject().put("conflict_draft",client.get(id));throw e;}},r->{
   saving=false;
   if(!id.equals(state.id())){pendingAction=null;return;}
   if(r.has("conflict_draft")){pendingAction=null;state.conflict(r.getJSONObject("conflict_draft"));notice="Draft changed elsewhere. Your writing is preserved. Compare before saving again.";}
   else{state.saved(r,body);if(state.dirty())flush();else resumeAction();}
  });
 }
 static boolean conflict(Exception e){try{return e.getClass().getDeclaredField("code").getInt(e)==409;}catch(Exception ignored){return false;}}
 void reload(){if(state.id().isEmpty())return;call(()->client.get(state.id()),r->{if(state.dirty()&&state.revision()!=r.optInt("revision"))state.conflict(r);else state.accept(r);if(!r.optString("status").equals("draft"))state.data.remove("send_pending");});}
 boolean ready(){if(state.data.optJSONObject("sources")!=null||state.dirty()||state.conflicted()||!state.editable()){notice="Save and resolve changes before this action.";changed();return false;}return !busy;}
 JSONObject revision(){JSONObject b=new JSONObject();EmailReplyState.put(b,"expected_revision",state.revision());return b;}
 void refresh(boolean all){if(!ready())return;JSONObject b=revision();EmailReplyState.put(b,"reply_all",all);call(()->client.operation(state.id(),"refresh-context",b),state::accept);}
 boolean assistantOutstanding(){JSONObject job=state.data.optJSONObject("job");if(!state.data.optString("job_request").isEmpty())return job==null||ConversationState.pendingStatus(job.optString("status"));job=state.draft()==null?null:state.draft().optJSONObject("latest_ai_request");return job!=null&&ConversationState.pendingStatus(job.optString("status"));}
 void assist(String intent){if(assistantOutstanding()){notice="Assistant request is still pending or unconfirmed. Check its saved result before asking again.";changed();return;}String instruction=state.data.optString("instruction");afterSave(()->assistSaved(intent,instruction));}
 void assistSaved(String intent,String instruction){
  if(!ready())return;
  JSONObject b=revision();String request=UUID.randomUUID().toString();
  EmailReplyState.put(b,"request_id",request);EmailReplyState.put(b,"intent",intent);EmailReplyState.put(b,"instruction",instruction);
  Object oldRequest=state.data.opt("job_request"),oldJob=state.data.opt("job");
  EmailReplyState.put(state.data,"job_request",request);state.data.remove("job");
  if(!persist()){if(oldRequest==null)state.data.remove("job_request");else EmailReplyState.put(state.data,"job_request",oldRequest);if(oldJob!=null)EmailReplyState.put(state.data,"job",oldJob);changed();return;}
  call(()->{JSONObject r=client.operation(state.id(),"assist",b);if(!ConversationState.pendingStatus(r.optString("status")))EmailReplyState.put(r,"draft_snapshot",client.get(state.id()));return r;},r->{EmailReplyState.put(state.data,"job",r);if(r.optJSONObject("draft_snapshot")!=null)state.accept(r.optJSONObject("draft_snapshot"));});
 }
 void poll(){if(busy)return;JSONObject d=state.draft(),job=state.data.optJSONObject("job");if(d==null)return;if(d.optString("status").equals("sending")||state.data.optBoolean("send_pending")){reload();return;}String request=state.data.optString("job_request",d.optJSONObject("latest_ai_request")==null?"":d.optJSONObject("latest_ai_request").optString("id"));if(request.isEmpty())return;if(job!=null&&!ConversationState.pendingStatus(job.optString("status")))return;call(()->{JSONObject r=client.job(state.id(),request);if(!ConversationState.pendingStatus(r.optString("status")))EmailReplyState.put(r,"draft_snapshot",client.get(state.id()));return r;},r->{EmailReplyState.put(state.data,"job",r);if(r.optJSONObject("draft_snapshot")!=null)state.accept(r.optJSONObject("draft_snapshot"));});}
 void proposal(JSONObject p,boolean apply){if(p==null||busy)return;if(apply&&!state.canApply(p)){notice="Proposal is stale. Your current writing wins; copy proposed text instead.";changed();return;}call(()->client.operation(state.id(),"proposals/"+EmailReplyClient.e(p.optString("id"))+"/"+(apply?"apply":"dismiss"),apply?revision():new JSONObject()),r->{if(apply)state.accept(r);state.data.remove("job");state.data.remove("job_request");reload();});}
 boolean currentReview(String id,int revision,String body,long edits,long review){return valid()&&review==reviewGeneration&&edits==editGeneration&&id.equals(state.id())&&revision==state.revision()&&body.equals(state.body())&&!state.dirty()&&!state.conflicted()&&state.editable();}
 void preview(){afterSave(()->{
  if(!ready()||!state.draft().optBoolean("can_send"))return;
  String id=state.id(),body=state.body();int revision=state.revision();long edits=editGeneration,review=reviewGeneration;JSONObject request=revision();
  call(()->currentReview(id,revision,body,edits,review)?client.operation(id,"preview",request):new JSONObject(),r->{if(currentReview(id,revision,body,edits,review)&&r.optInt("draft_revision",-1)==revision)EmailReplyState.put(state.data,"preview",r);});
 });}
 void send(){JSONObject preview=state.data.optJSONObject("preview");if(preview==null||state.dirty()||state.conflicted()||busy||state.data.optBoolean("send_pending")||preview.optInt("draft_revision",-1)!=state.revision())return;JSONObject b=revision();EmailReplyState.put(b,"confirmation_id",preview.optString("confirmation_id"));EmailReplyState.put(b,"confirmed",true);EmailReplyState.put(b,"request_id",UUID.randomUUID().toString());EmailReplyState.put(state.data,"send_pending",true);EmailReplyState.put(state.data,"send_request",b);state.data.remove("preview");if(!persist())return;call(()->{try{return client.operation(state.id(),"send",b);}catch(Exception e){if(conflict(e))return new JSONObject().put("send_rejected",true).put("draft_snapshot",client.get(state.id()));throw e;}},r->{if(r.optBoolean("send_rejected")){state.accept(r.getJSONObject("draft_snapshot"));state.data.remove("send_pending");notice="Send was rejected because the reviewed context changed. Refresh original context and review again; no reply was resent.";}else{state.accept(r);if(!r.optString("status").equals("sending"))state.data.remove("send_pending");}});}
 void reconcile(){call(()->{try{return client.operation(state.id(),"reconcile",new JSONObject());}catch(Exception e){if(conflict(e))return client.get(state.id());throw e;}},r->{state.accept(r);if(!r.optString("status").equals("sending"))state.data.remove("send_pending");if(r.optString("status").equals("draft"))notice="No submission is recorded in the saved draft. No reply was resent. Review and confirm again only if you still want to send.";});}
 void discard(){if(busy||!state.editable())return;call(()->client.discard(state.id(),state.revision()),r->{JSONObject locals=state.data.optJSONObject("local_drafts");if(locals!=null)locals.remove(state.id());state.data.remove("draft");for(String key:EmailReplyState.LOCAL)state.data.remove(key);state.data.remove("preview");EmailReplyState.put(state.data,"active",false);});}
 void close(){pendingAction=null;reviewGeneration++;state.data.remove("preview");EmailReplyState.put(state.data,"active",false);persist();listener=null;}
}

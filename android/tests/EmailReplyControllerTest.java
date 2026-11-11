package com.simon.mytasks;
import org.json.*;
public class EmailReplyControllerTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static JSONObject draft(int revision,String body)throws Exception{return new JSONObject().put("id","d").put("revision",revision).put("status","draft").put("body",body).put("can_send",true);}
 static class QueuedHost implements EmailReplyController.Host{
  java.util.ArrayDeque<Runnable> work=new java.util.ArrayDeque<>(),ui=new java.util.ArrayDeque<>();JSONObject data=new JSONObject();boolean valid=true,saveOk=true;
  public boolean valid(){return valid;}public JSONObject read(){return data;}public boolean save(JSONObject d){data=d;return saveOk;}public void work(Runnable r){work.add(r);}public void ui(Runnable r){ui.add(r);}
  void drain(){int guard=0;while(!work.isEmpty()||!ui.isEmpty()){if(++guard>100)throw new AssertionError("unbounded automatic retry");while(!work.isEmpty())work.remove().run();while(!ui.isEmpty())ui.remove().run();}}
 }
 static void reviewSavesThenExecutesOnce()throws Exception{
  QueuedHost h=new QueuedHost();java.util.List<String> operations=new java.util.ArrayList<>();
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{operations.add(m+" "+p);if(m.equals("PATCH")){check(b.getString("body").equals("local review"),"save local review body");return draft(2,b.getString("body"));}check(p.endsWith("/preview"),"only preview after save");check(b.getInt("expected_revision")==2,"review saved revision");return new JSONObject().put("confirmation_id","review").put("draft_revision",2);}),h);
  c.state.accept(draft(1,"old"));c.edit("local review");c.preview();c.preview();h.drain();
  check(operations.size()==2,"dirty Review must save then preview exactly once: "+operations);
  check(c.state.data.optJSONObject("preview")!=null&&!c.state.dirty(),"review automatically opens after save");
 }
 static void assistJoinsAutosaveAndExecutesOnce()throws Exception{
  QueuedHost h=new QueuedHost();java.util.List<String> operations=new java.util.ArrayList<>();int[] revision={1};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{operations.add(m+" "+p);if(m.equals("PATCH"))return draft(++revision[0],b.getString("body"));check(p.endsWith("/assist"),"only assist after save");check(b.getInt("expected_revision")==3,"assist uses latest saved typing");check(b.getString("intent").equals("discuss"),"first admitted intent wins");check(b.getString("instruction").equals("first question"),"tap captures instruction");return new JSONObject().put("id",b.getString("request_id")).put("status","running");}),h);
  c.state.accept(draft(1,"old"));c.edit("first typing");c.flush();EmailReplyState.put(c.state.data,"instruction","first question");c.assist("discuss");c.assist("rewrite");c.edit("latest typing");EmailReplyState.put(c.state.data,"instruction","next question");h.drain();
  check(operations.size()==3,"Assist must join existing save, save latest typing and execute once: "+operations);
  check(c.state.body().equals("latest typing")&&!c.state.dirty(),"assist does not replace human text");
 }
 static void failedSaveRetainsTextAndRequiresNewTap()throws Exception{
  QueuedHost h=new QueuedHost();int[] saves={0},reviews={0},notifications={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH")){if(++saves[0]==1)throw new java.io.IOException("offline");return draft(2,b.getString("body"));}if(p.endsWith("/preview")){reviews[0]++;return new JSONObject().put("confirmation_id","retry").put("draft_revision",2);}return draft(1,"old");}),h);
  c.listener=()->notifications[0]++;c.state.accept(draft(1,"old"));c.edit("retained after error");c.preview();h.drain();
  check(!c.busy&&c.state.body().equals("retained after error")&&c.state.dirty(),"save error retains editable local writing");
  check(!c.notice.isEmpty()&&notifications[0]>=2,"save error notifies UI visibly");
  c.reload();h.drain();check(reviews[0]==0&&saves[0]==1,"read recovery never replays pending review or save");
  c.preview();h.drain();check(reviews[0]==1&&saves[0]==2,"explicit retry tap saves and reviews after failure");
 }
 static void closeCancelsQueuedActionButFinishesSave()throws Exception{
  QueuedHost h=new QueuedHost();int[] saves={0},reviews={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH")){saves[0]++;return draft(2,b.getString("body"));}reviews[0]++;return new JSONObject();}),h);
  c.state.accept(draft(1,"old"));c.edit("close retains this");c.preview();c.close();h.drain();
  check(saves[0]==1&&reviews[0]==0,"Close cancels queued review without throwing away autosave");
  check(c.state.body().equals("close retains this")&&!c.state.dirty(),"Close permits admitted save to retain local writing");
 }
 static void assistantAdmissionCannotDuplicateUntilReadRecovery()throws Exception{
  for(boolean lost:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] admits={0},polls={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(p.endsWith("/assist")){admits[0]++;if(lost)throw new java.io.IOException("admission response lost");return new JSONObject().put("id",b.getString("request_id")).put("status","running");}if(p.contains("/assist/")){polls[0]++;return new JSONObject().put("status","succeeded");}return draft(1,"human writing");}),h);
   c.state.accept(draft(1,"human writing"));c.assist("discuss");h.drain();String request=c.state.data.getString("job_request");
   c.assist("rewrite");h.drain();check(admits[0]==1&&c.state.data.getString("job_request").equals(request),"pending or ambiguous assistant admission keeps exact identity; no second job");
   c.poll();h.drain();check(polls[0]==1&&admits[0]==1,"recovery only reads actual admitted job");
   c.assist("discuss");h.drain();check(admits[0]==2,"terminal read recovery permits explicit new assistant request");
  }
 }
 static void privateRecoveryFailureBlocksSubmissionAndAllowsExplicitRetry()throws Exception{
  for(boolean assist:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] writes={0},notifications={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{writes[0]++;if(m.equals("PATCH"))return draft(2,b.getString("body"));return new JSONObject().put("status","running").put("confirmation_id","review").put("draft_revision",2);}),h);
   c.listener=()->notifications[0]++;c.state.accept(draft(1,"old"));h.saveOk=false;c.edit("private text");if(assist)c.assist("discuss");else c.preview();h.drain();
   check(writes[0]==0&&!c.notice.isEmpty()&&notifications[0]>0,"unavailable private recovery blocks submission and notifies UI");
   check(c.state.body().equals("private text")&&!c.busy,"private recovery failure retains local text and releases action");
   h.saveOk=true;if(assist)c.assist("discuss");else c.preview();h.drain();check(writes[0]==2,"explicit retry after private recovery returns saves then acts");
  }
 }
 static void recoveryFailureAfterSaveCancelsQueuedAction()throws Exception{
  QueuedHost h=new QueuedHost();int[] reviews={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH"))return draft(2,b.getString("body"));reviews[0]++;return new JSONObject();}),h);
  c.state.accept(draft(1,"old"));c.edit("latest");c.preview();h.work.remove().run();h.saveOk=false;h.drain();
  check(reviews[0]==0&&!c.notice.isEmpty()&&c.state.body().equals("latest"),"failed private recovery after save must cancel queued review");
 }
 static void queuedActionIsFencedAgainstDraftReplacement()throws Exception{
  for(boolean saveAlreadyRan:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] saves={0},actions={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH")){saves[0]++;return draft(2,b.getString("body"));}actions[0]++;return new JSONObject();}),h);
   c.state.accept(draft(1,"old"));c.edit("old private text");c.preview();if(saveAlreadyRan)h.work.remove().run();
   c.state.accept(draft(1,"different draft").put("id","other"));h.drain();
   check(c.state.id().equals("other")&&c.state.body().equals("different draft"),"late save never replaces current workspace");
   check(actions[0]==0&&saves[0]==(saveAlreadyRan?1:0),"draft replacement fences queued network work and action");
  }
 }
 static final class MissingJob extends Exception{final int code=404;}
 static void missingAssistantLookupDoesNotResubmit()throws Exception{
  QueuedHost h=new QueuedHost();int[] admits={0},reads={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(p.endsWith("/assist")){admits[0]++;throw new java.io.IOException("offline before admission");}if(p.contains("/assist/")){reads[0]++;throw new MissingJob();}return draft(1,"retained");}),h);
  c.state.accept(draft(1,"retained"));c.assist("discuss");h.drain();c.poll();h.drain();
  check(reads[0]==1&&admits[0]==1&&!c.busy&&!c.notice.isEmpty(),"missing admitted identity is visibly recovered without resubmission");
  c.assist("discuss");h.drain();check(admits[0]==1,"missing lookup does not prove an in-flight admission absent; preserve identity rather than duplicate");
 }
 static void failedAssistantIdentityPersistenceReleasesUnsubmittedAction()throws Exception{
  QueuedHost h=new QueuedHost(){@Override public boolean save(JSONObject d){data=d;return !d.has("job_request");}};int[] admits={0},notifications={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{admits[0]++;return new JSONObject().put("status","running");}),h);
  c.listener=()->notifications[0]++;c.state.accept(draft(1,"private"));c.assist("discuss");h.drain();
  check(admits[0]==0&&!c.state.data.has("job_request")&&!c.assistantOutstanding(),"unsubmitted failed identity persistence must not become ambiguous admitted job");
  check(!c.notice.isEmpty()&&notifications[0]>0,"assistant persistence failure is visible");
 }
 static final class RevisionConflict extends Exception{final int code=409;}
 static void conflictCancelsQueuedActionWithoutLosingWriting()throws Exception{
  QueuedHost h=new QueuedHost();int[] actions={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH"))throw new RevisionConflict();if(m.equals("GET"))return draft(2,"remote changes");actions[0]++;return new JSONObject();}),h);
  c.state.accept(draft(1,"old"));c.edit("private changes");c.assist("discuss");h.drain();
  check(c.state.conflicted()&&c.state.body().equals("private changes")&&!c.notice.isEmpty(),"CAS conflict keeps human writing and visible resolution");
  c.state.useRemote();c.flush();h.drain();check(actions[0]==0,"resolving conflict does not replay old assistant action");
 }
 static void invalidatedRouteRejectsQueuedWorkAndLateSave()throws Exception{
  for(boolean saveAlreadyRan:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] requests={0};EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{requests[0]++;return draft(2,b.getString("body"));}),h);
   c.state.accept(draft(1,"old"));c.edit("private changes");c.preview();if(saveAlreadyRan)h.work.remove().run();c.invalidate();h.drain();
   check(requests[0]==(saveAlreadyRan?1:0)&&c.state.revision()==1&&c.state.body().equals("private changes"),"generation fence drops queued work and late callback after route invalidation");
  }
 }
 static void failedReviewRetainsTextAndRecoversWithoutSending()throws Exception{
  QueuedHost h=new QueuedHost();int[] saves={0},reviews={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH")){saves[0]++;return draft(2,b.getString("body"));}if(p.endsWith("/preview")){if(++reviews[0]==1)throw new java.io.IOException("review offline");return new JSONObject().put("confirmation_id","fresh").put("draft_revision",2);}check(m.equals("GET"),"read recovery must never send");return draft(2,"human changes");}),h);
  c.state.accept(draft(1,"old"));c.edit("human changes");c.preview();h.drain();
  check(!c.notice.isEmpty()&&!c.busy&&c.state.body().equals("human changes")&&!c.state.data.has("preview"),"review failure retains text and releases busy with visible error");
  c.reload();h.drain();check(reviews[0]==1,"refresh never automatically replays failed review");c.preview();h.drain();check(reviews[0]==2&&saves[0]==1,"explicit review retry uses already-saved body without send");
 }
 static void autosaveWaitsForUnrelatedRequest()throws Exception{
  for(int lifecycle=0;lifecycle<3;lifecycle++){
   QueuedHost h=new QueuedHost();int[] saves={0},gets={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH")){saves[0]++;check(b.getString("body").equals("typed during read"),"deferred save uses latest body");return draft(2,b.getString("body"));}gets[0]++;return draft(1,"old");}),h);
   c.state.accept(draft(1,"old"));c.reload();c.edit("typed during read");c.flush();c.flush();
   if(lifecycle==1)c.listener=null;if(lifecycle==2)c.close();h.drain();
   check(gets[0]==1&&saves[0]==1&&!c.state.dirty(),"debounced save during GET must drain exactly once, including detach/close");
   check(c.state.body().equals("typed during read"),"deferred save retains human writing");
  }
 }
 static void typingInvalidatesInFlightPreview()throws Exception{
  for(boolean editBack:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] reviews={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{check(p.endsWith("/preview"),"no automatic send or other mutation");reviews[0]++;return new JSONObject().put("draft_revision",1).put("confirmation_id","stale");}),h);
   c.state.accept(draft(1,"reviewed"));c.preview();h.work.remove().run();c.edit("newer writing");if(editBack)c.edit("reviewed");h.drain();
   check(!c.state.data.has("preview"),"late preview must not resurrect review after typing, even edit-back");
   check(c.state.body().equals(editBack?"reviewed":"newer writing"),"preview callback preserves latest writing");c.send();h.drain();check(reviews[0]==1,"stale preview never enables send");
  }
 }
 static void deferredAutosaveFailureDoesNotRetry()throws Exception{
  for(boolean failRead:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] saves={0},reads={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH")){saves[0]++;throw new java.io.IOException("save failed");}if(++reads[0]==1&&failRead)throw new java.io.IOException("read failed");return draft(1,"old");}),h);
   c.state.accept(draft(1,"old"));c.reload();c.edit("private writing");c.flush();h.drain();
   check(saves[0]==(failRead?0:1)&&!c.busy&&!c.notice.isEmpty()&&c.state.dirty(),"failed request stops deferred autosave without looping");
   c.reload();h.drain();check(saves[0]==(failRead?0:1),"later recovery read cannot replay failed deferred save");
   check(c.state.body().equals("private writing"),"failures preserve local writing");
  }
 }
 static void deferredAutosaveIsScoped()throws Exception{
  for(boolean invalidate:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] saves={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH"))saves[0]++;return draft(1,"old");}),h);
   c.state.accept(draft(1,"old"));c.call(()->draft(1,"other").put("id","other"),c.state::accept);c.edit("private writing");c.flush();if(invalidate)c.invalidate();h.drain();
   check(saves[0]==0,"deferred save cannot leak into replacement draft or invalidated route");
  }
 }
 static void previewScopeAndCloseFenceQueuedAndReturnedReviews()throws Exception{
  for(int change=0;change<6;change++)for(boolean responseReady:new boolean[]{false,true}){
   QueuedHost h=new QueuedHost();int[] reviews={0};
   EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{check(p.endsWith("/preview"),"review never sends automatically");reviews[0]++;return new JSONObject().put("draft_revision",1).put("confirmation_id","old");}),h);
   c.state.accept(draft(1,"reviewed"));c.preview();if(responseReady)h.work.remove().run();
   if(change==0)c.close();else if(change==1)c.invalidate();else if(change==2)c.state.accept(draft(1,"reviewed").put("id","other"));else if(change==3)c.state.accept(draft(2,"reviewed"));else if(change==4)c.state.edit("direct local change");else{c.edit("changed");c.edit("reviewed");}
   h.drain();check(!c.state.data.has("preview"),"close, route, draft, revision, body and edit-back fences reject late review");
   check(reviews[0]==(responseReady?1:0),"stale queued preview does not issue a request");
  }
 }
 static void queuedReviewUsesLatestSavedTypingAndRejectsLaterTyping()throws Exception{
  QueuedHost h=new QueuedHost();int[] saves={0},reviews={0};
  EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{if(m.equals("PATCH"))return draft(++saves[0]+1,b.getString("body"));check(p.endsWith("/preview")&&b.getInt("expected_revision")==3,"queued review uses latest saved revision");reviews[0]++;return new JSONObject().put("draft_revision",3).put("confirmation_id","latest");}),h);
  c.state.accept(draft(1,"old"));c.edit("first");c.preview();c.edit("second");
  h.work.remove().run();h.ui.remove().run();h.work.remove().run();h.ui.remove().run();
  h.work.remove().run();c.edit("third");c.flush();h.drain();
  check(reviews[0]==1&&saves[0]==3&&!c.state.dirty()&&c.state.body().equals("third"),"queued review saves latest writing; later debounce drains without another review");
  check(!c.state.data.has("preview"),"new typing during queued review response cannot resurrect old preview");
 }
 public static void main(String[] args)throws Exception{
  deferredAutosaveFailureDoesNotRetry();deferredAutosaveIsScoped();previewScopeAndCloseFenceQueuedAndReturnedReviews();queuedReviewUsesLatestSavedTypingAndRejectsLaterTyping();
  typingInvalidatesInFlightPreview();
  autosaveWaitsForUnrelatedRequest();
  reviewSavesThenExecutesOnce();assistJoinsAutosaveAndExecutesOnce();failedSaveRetainsTextAndRequiresNewTap();closeCancelsQueuedActionButFinishesSave();assistantAdmissionCannotDuplicateUntilReadRecovery();privateRecoveryFailureBlocksSubmissionAndAllowsExplicitRetry();recoveryFailureAfterSaveCancelsQueuedAction();queuedActionIsFencedAgainstDraftReplacement();missingAssistantLookupDoesNotResubmit();failedAssistantIdentityPersistenceReleasesUnsubmittedAction();conflictCancelsQueuedActionWithoutLosingWriting();invalidatedRouteRejectsQueuedWorkAndLateSave();failedReviewRetainsTextAndRecoversWithoutSending();
  final int[] calls={0};final JSONObject[] saved={new JSONObject()};EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{calls[0]++;return new JSONObject().put("id","d").put("revision",2).put("status","draft").put("body",b==null?"":b.optString("body"));}),new EmailReplyController.Host(){public boolean valid(){return true;}public JSONObject read(){return saved[0];}public boolean save(JSONObject d){saved[0]=d;return true;}public void work(Runnable r){r.run();}public void ui(Runnable r){r.run();}});
  c.state.accept(new JSONObject().put("id","d").put("revision",1).put("status","draft").put("body","old"));c.edit("new");c.flush();if(c.state.dirty()||!c.state.body().equals("new")||calls[0]!=1)throw new AssertionError("CAS autosave");
  c.state.edit("unsaved");c.send();if(calls[0]!=1)throw new AssertionError("send without reviewed revision");
  c.open("task-a",null);c.state.accept(new JSONObject().put("id","d").put("revision",3).put("status","sent").put("body","sent text"));c.open("task-b",null);if(c.state.draft()!=null)throw new AssertionError("prior task receipt leaked into new task chooser");
  c.state.accept(new JSONObject().put("id","b").put("revision",1).put("status","draft").put("body","server"));c.edit("private unsaved body");c.open("task-c",null);if(c.state.draft()!=null)throw new AssertionError("prior task draft visible");c.state.accept(new JSONObject().put("id","b").put("revision",1).put("status","draft").put("body","server"));if(!c.state.body().equals("private unsaved body"))throw new AssertionError("task switching lost cached local writing");
  System.out.println("EmailReplyControllerTest passed");
 }
}

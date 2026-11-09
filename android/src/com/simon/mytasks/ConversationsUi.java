package com.simon.mytasks;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.util.*;

/** Native discussion master/detail and a keyboard-reachable, thread-bound composer. */
final class ConversationsUi implements ConversationController.Listener {
 final MainActivity a;final ConversationController c;
 LinearLayout rows,messages,timeline,historyHeader,historyFooter;ScrollView scroll;TextView state,title;EditText input;ImageButton send;Button jump;
 String thread="",signature="",boundTask="",headerSignature="",footerSignature="",listSignature="",followRequest="";
 boolean syncing,restoring;long previousFirst=Long.MAX_VALUE;int previousCount,viewportGeneration;
 final LinkedHashMap<Long,View> messageRows=new LinkedHashMap<>();
 final java.time.ZoneId zone=java.time.ZoneId.systemDefault();
 JSONObject viewport(){String key="presentation:"+thread;JSONObject value=c.state.json().optJSONObject(key);if(value==null){value=new JSONObject();ConversationState.put(c.state.json(),key,value);}return value;}
 String timeLabel(String iso,boolean includeDate){java.time.Instant instant=ChatPresentationPolicy.instant(iso);if(instant==null)return "";return java.time.format.DateTimeFormatter.ofLocalizedDateTime(includeDate?java.time.format.FormatStyle.MEDIUM:java.time.format.FormatStyle.SHORT,java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone).format(instant);}
 String dateLabel(String iso){java.time.Instant instant=ChatPresentationPolicy.instant(iso);return instant==null?"":java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(zone).format(instant);}
 String clockLabel(String iso){java.time.Instant instant=ChatPresentationPolicy.instant(iso);return instant==null?"":java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone).format(instant);}
 ConversationsUi(MainActivity activity,ConversationController controller){a=activity;c=controller;}
 void attach(){c.listener=this;}
 void buildGlobal(LinearLayout parent){
  attach();LinearLayout body=a.horizontal();body.setGravity(Gravity.TOP);parent.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout master=a.vertical(),detail=a.vertical();
  if(a.wide){body.addView(master,new LinearLayout.LayoutParams(0,-1,.4f));View divider=new View(a);divider.setBackgroundColor(a.line);body.addView(divider,new LinearLayout.LayoutParams(a.dp(1),-1));body.addView(detail,new LinearLayout.LayoutParams(0,-1,.6f));buildList(master);if(!c.state.selected().isEmpty())buildThread(detail,c.state.selected(),false);else a.add(detail,a.label("Select a conversation or start a new one.",16,a.muted));}
  else if(c.state.selected().isEmpty())buildList(masterWith(body,master));else buildThread(masterWith(body,detail),c.state.selected(),false);
 }
 LinearLayout masterWith(LinearLayout body,LinearLayout pane){body.addView(pane,new LinearLayout.LayoutParams(-1,-1));return pane;}
 void buildList(LinearLayout pane){
  // Controls share the list's vertical scroll: short windows retain reachable rows.
  ScrollView sc=new ScrollView(a);sc.setFillViewport(true);LinearLayout content=a.vertical();sc.addView(content);pane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout toolbar=a.vertical();Button filter=a.button((c.archived?"Archived · ":"")+humanScope()+" ▾",()->filters());a.add(toolbar,filter);Button create=a.primary("New conversation",()->c.create(null,()->{c.state.select(c.visible);a.render();}));a.add(toolbar,create);a.add(content,toolbar);
  EditText search=new EditText(a);a.ui.input(search);search.setSingleLine(true);search.setText(c.search);search.setHint("Search loaded discussions");search.setContentDescription("Search loaded titles, task labels and excerpts; not message history");a.add(content,search);
  a.add(content,a.label("Search covers loaded titles, task labels and excerpts — not message history. Load more to include older discussions.",12,a.muted));
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int x,int y,int z){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int x,int y,int z){c.search=s.toString();updateRows();}});
  state=a.label("",13,a.muted);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(content,state);rows=a.vertical();a.add(content,rows);updateRows();c.hide();c.list(false);
 }
 String humanScope(){return c.scope.equals("free")?"Free":c.state.json().optBoolean("only_tasks")?"Task conversations":"All conversations";}
 void filters(){String[] labels={"All conversations","Free conversations","Task conversations","Archived conversations","Tasks","Settings"};a.dialogBuilder().setTitle("Discussions view").setItems(labels,(d,i)->{if(i==4){a.destination="Tasks";a.render();return;}if(i==5){a.settings();return;}c.state.select("");if(i==2){c.filter("all","",false);ConversationState.put(c.state.json(),"only_tasks",true);}else{ConversationState.put(c.state.json(),"only_tasks",false);c.filter(i==1?"free":"all","",i==3);}a.render();}).show();}
 void updateRows(){
  if(rows==null)return;JSONArray loaded=new JSONArray();
  for(int i=0;i<c.threads.length();i++){JSONObject t=c.threads.optJSONObject(i);if(t==null)continue;JSONObject current=c.state.conversation(t.optString("id"));loaded.put(current!=null&&current.optInt("revision")>=t.optInt("revision")?current:t);}
  String nextSignature=loaded.toString()+c.search+c.next+c.archived+c.scope+c.state.json().optBoolean("only_tasks");if(nextSignature.equals(listSignature))return;listSignature=nextSignature;
  rows.removeAllViews();boolean onlyTasks=c.state.json().optBoolean("only_tasks");String q=c.search.toLowerCase(Locale.ROOT);int shown=0;
  for(int i=0;i<loaded.length();i++){
   JSONObject t=loaded.optJSONObject(i);if(t==null||t.optBoolean("archived")!=c.archived||(onlyTasks&&t.isNull("task_id")))continue;
   String task=t.isNull("task_id")?"Free conversation":t.optString("task_title","Task conversation"),label=t.optString("title","Conversation"),preview=t.optString("last_preview","");
   if(!(label+" "+task+" "+preview).toLowerCase(Locale.ROOT).contains(q))continue;
   String id=t.optString("id");LinearLayout row=a.vertical();row.setPadding(a.dp(16),a.dp(12),a.dp(16),a.dp(12));row.setMinimumHeight(a.dp(80));row.setBackground(a.ui.touch(a.card,0,18));row.setFocusable(true);row.setClickable(true);
   row.setOnClickListener(v->{capture();c.state.select(id);c.save();a.render();});
   TextView h=a.heading(label,17);a.add(row,h);
   JSONObject req=t.optJSONObject("latest_request");String status=req==null?"":req.optString("status");String suffix=ConversationState.pendingStatus(status)?" · "+(status.equals("queued")?"Queued":"Reply in progress"):status.equals("failed")||status.equals("interrupted")?" · "+status:"";
   a.add(row,a.label(task+suffix,12,a.muted));
   if(!preview.isEmpty()){TextView p=a.label(preview,14,a.muted);p.setMaxLines(2);p.setEllipsize(TextUtils.TruncateAt.END);a.add(row,p);}
   String updated=timeLabel(t.optString("updated_at"),true);if(!updated.isEmpty())a.add(row,a.label("Updated "+updated,12,a.muted));
   row.setContentDescription(label+", "+task+suffix+", "+preview+(updated.isEmpty()?"":", updated "+updated));a.add(rows,row);a.space(rows,8);shown++;
  }
  if(shown==0)a.add(rows,a.label("No matching conversations loaded. Start a new conversation, refresh, or load more. Previously discarded chats cannot be recovered.",14,a.muted));
  if(!c.next.isEmpty())a.add(rows,a.button("Load more conversations",()->c.list(true)));a.add(rows,a.button("Refresh conversations",()->c.list(false)));
 }
 void buildTask(LinearLayout pane,String task){
  attach();boundTask=task;LinearLayout picker=a.horizontal();Button saved=a.button("Saved conversations",()->taskPicker(task));picker.addView(saved,new LinearLayout.LayoutParams(0,-2,1));picker.addView(a.button("New",()->c.create(task,()->{c.state.taskSelection(task,c.visible);a.render();})));a.add(pane,picker);
  String id=c.state.taskSelection(task);if(id.isEmpty()){state=a.label(c.notice,13,a.muted);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(pane,state);a.add(pane,a.label("Choose a saved thread or create a new conversation. Hermès uses the task's saved context; it cannot send mail or use tools.",15,a.muted));return;}a.chatUtilityChrome.add(picker);buildThread(pane,id,true);
 }
 void taskPicker(String task){
  // Paged task-specific picker does not disturb the global Discussions filter.
  showTaskPicker(task,"",new ArrayList<>());
 }
 void showTaskPicker(String task,String cursor,ArrayList<JSONObject> collected){final int navigation=c.navigationVersion;c.thread(thread,"picker:"+task+":"+cursor,client->client.list("task",task,false,cursor),r->{if(navigation!=c.navigationVersion||!a.selected.equals(task)||!a.destination.equals("Tasks"))return;JSONArray items=r.getJSONArray("conversations");for(int i=0;i<items.length();i++)collected.add(items.getJSONObject(i));ArrayList<String> names=new ArrayList<>();for(JSONObject t:collected)names.add(t.optString("title"));String next=r.isNull("next_cursor")?"":r.optString("next_cursor");if(!next.isEmpty())names.add("Load more…");if(names.isEmpty()){a.notice("No saved conversations","Use New to create a task-linked conversation.");return;}a.dialogBuilder().setTitle("Saved task conversations").setItems(names.toArray(new String[0]),(dialog,index)->{if(navigation!=c.navigationVersion)return;if(index==collected.size())showTaskPicker(task,next,collected);else{c.state.taskSelection(task,collected.get(index).optString("id"));c.save();a.render();}}).setNegativeButton("Cancel",null).show();},null);}
 void buildThread(LinearLayout pane,String id,boolean taskBound){
  thread=id;c.visible=id;c.state.select(id);signature="";headerSignature="";footerSignature="";messageRows.clear();previousCount=0;previousFirst=Long.MAX_VALUE;restoring=true;
  followRequest=viewport().optString("follow_request","");
  LinearLayout bar=a.horizontal();bar.setGravity(Gravity.CENTER_VERTICAL);if(taskBound)bar.addView(a.ui.icon(R.drawable.ic_back,"Back to task details",()->{a.detailsMode="Details";a.render();}),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));if(!taskBound&&!a.wide)bar.addView(a.ui.icon(R.drawable.ic_back,"Back to Discussions",()->{c.state.select("");c.hide();a.render();}),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
  title=a.heading("Conversation",17);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));bar.addView(a.ui.icon(R.drawable.ic_more,"Conversation actions",()->threadMenu()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(pane,bar);
  FrameLayout viewportFrame=new FrameLayout(a);pane.addView(viewportFrame,new LinearLayout.LayoutParams(-1,0,1));
  scroll=new ScrollView(a){
   @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
    boolean preserve=oldHeight>0&&timeline!=null&&!restoring&&c.listener==ConversationsUi.this;
    boolean wasBottom=preserve&&ChatPresentationPolicy.nearBottom(timeline.getHeight(),getScrollY(),oldHeight,a.dp(72));
    JSONObject anchor=null;if(preserve){captureViewport();try{anchor=new JSONObject(viewport().toString());}catch(JSONException ignored){preserve=false;}}
    super.onSizeChanged(width,height,oldWidth,oldHeight);if(preserve)restoreViewport(wasBottom,anchor);
   }
  };scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setSaveEnabled(false);
  timeline=a.vertical();timeline.setPadding(a.dp(8),0,a.dp(8),a.dp(12));historyHeader=a.vertical();messages=a.vertical();historyFooter=a.vertical();a.add(timeline,historyHeader);a.add(timeline,messages);a.add(timeline,historyFooter);scroll.addView(timeline);viewportFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  jump=a.button("Jump to latest",()->jumpToLatest());jump.setVisibility(View.GONE);jump.setElevation(a.dp(4));a.ui.primary(jump);FrameLayout.LayoutParams jumpLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);jumpLayout.setMargins(a.dp(12),0,a.dp(12),a.dp(8));viewportFrame.addView(jump,jumpLayout);
  scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(!restoring){captureViewport();updateJump();}});
  LinearLayout composer=a.horizontal();composer.setGravity(Gravity.BOTTOM);composer.setPadding(a.dp(4),a.dp(4),a.dp(4),a.dp(4));composer.setBackground(a.ui.glass());composer.setElevation(a.dp(3));
  input=new EditText(a);a.ui.input(input);input.setMinWidth(0);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_ACTION_NONE);input.setSingleLine(false);input.setMinLines(1);input.setMaxLines(3);input.setGravity(Gravity.TOP);input.setHint(c.state.mode(id).equals("email_draft")?"Request an email draft":"Message Hermès");input.setContentDescription("Message Hermès; Send submits this draft");input.setText(c.state.draft(id));input.setSaveEnabled(false);composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int x,int y,int z){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int x,int y,int z){if(!syncing)c.editDraft(id,s.toString());}});
  composer.addView(a.ui.icon(R.drawable.ic_more,"Message mode and voice input",()->composerMenu()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
  send=a.ui.icon(R.drawable.ic_send,"Send to Hermès",()->{JSONObject old=c.state.pending(id);String previous=old==null?"":old.optString("request_id");c.state.draft(id,input.getText().toString());c.send(id,c.state.mode(id));JSONObject current=c.state.pending(id);if(current!=null&&!previous.equals(current.optString("request_id"))){followRequest=current.optString("request_id");ConversationState.put(viewport(),"follow_request",followRequest);c.save();}syncDraft();});
  send.setImageTintList(android.content.res.ColorStateList.valueOf(a.ui.onPrimary));send.setBackground(a.ui.touch(a.ui.primary,0,24));composer.addView(send,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(pane,composer);
  updateThread();c.load(id,false);
 }
 void composerMenu(){String[] choices={"Question / analysis","Email draft (never sends mail)","Review voice input"};a.dialogBuilder().setTitle("Hermès · no tools").setItems(choices,(d,i)->{if(i==2)a.voice();else{c.state.mode(thread,i==1?"email_draft":"question");c.save();input.setHint(i==1?"Request an email draft":"Message Hermès");}}).show();}
 void threadMenu(){JSONObject meta=c.state.conversation(thread);if(meta==null)return;String[] options={"Refresh / recover server history","Rename conversation",meta.optBoolean("archived")?"Reopen conversation":"Archive conversation","All Discussions","Tasks","Settings","Saved task conversations","New task conversation"};a.dialogBuilder().setTitle(meta.optString("title","Conversation")).setItems(options,(d,i)->{if(i==3){a.destination="Discussions";c.state.select("");a.render();return;}if(i==4){a.destination="Tasks";a.render();return;}if(i==5){a.settings();return;}if(i==6||i==7){String task=boundTask.isEmpty()?(meta.isNull("task_id")?"":meta.optString("task_id")):boundTask;if(task.isEmpty()){a.notice("Free conversation","This thread has no task association. Start a free conversation from Discussions.");return;}taskThreadAction(task,i==7);return;}if(i==0)c.load(thread,false);else if(i==1){EditText name=new EditText(a);a.ui.input(name);name.setText(c.state.json().optString("rename:"+thread,meta.optString("title")));AlertDialog dialog=a.dialogBuilder().setTitle("Rename conversation").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Rename",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{String proposed=name.getText().toString().trim();if(proposed.isEmpty()){name.setError("A title is required");return;}ConversationState.put(c.state.json(),"rename:"+thread,proposed);c.save();c.patch(thread,proposed,null);dialog.dismiss();}));dialog.show();}else a.confirm(meta.optBoolean("archived")?"Reopen conversation?":"Archive conversation?","History remains saved on the server. Archived conversations can be reopened from Discussions.",()->c.patch(thread,null,!meta.optBoolean("archived")));}).show();}
 void taskThreadAction(String task,boolean create){a.captureDraft();Runnable open=()->{if(!task.equals(a.selected)){a.selected=task;a.draft=null;a.editing=false;}a.destination="Tasks";a.detailsMode="Hermès";a.render();if(create)c.create(task,()->a.render());else if(a.conversationUi!=null)a.conversationUi.taskPicker(task);};if(a.dirty()&&!task.equals(a.selected))a.confirm("Discard unsaved task fields?","Conversation drafts remain saved per thread.",open);else open.run();}
 void syncDraft(){if(input==null)return;String value=c.state.draft(thread);if(!input.getText().toString().equals(value)){syncing=true;input.setText(value);input.setSelection(value.length());syncing=false;}}
 void capture(){if(input!=null)c.state.draft(thread,input.getText().toString());captureViewport();}
 boolean nearBottom(){return scroll!=null&&ChatPresentationPolicy.nearBottom(timeline.getHeight(),scroll.getScrollY(),scroll.getHeight(),a.dp(72));}
 long latestId(){JSONArray turns=c.state.messages(thread);long latest=0;for(int i=0;i<turns.length();i++){JSONObject m=turns.optJSONObject(i);if(m!=null)latest=Math.max(latest,m.optLong("id"));}return latest;}
 void captureViewport(){
  if(scroll==null||thread.isEmpty()||restoring||scroll.getHeight()==0)return;
  JSONObject saved=viewport();int y=scroll.getScrollY();ConversationState.put(saved,"y",y);ConversationState.put(c.state.json(),"scroll:"+thread,y);
  saved.remove("anchor_id");saved.remove("anchor_offset");
  for(Map.Entry<Long,View> entry:messageRows.entrySet()){View row=entry.getValue();int top=messages.getTop()+row.getTop();if(top+row.getHeight()>y){ConversationState.put(saved,"anchor_id",entry.getKey());ConversationState.put(saved,"anchor_offset",top-y);break;}}
  boolean bottom=nearBottom();ConversationState.put(saved,"at_bottom",bottom);if(bottom)ConversationState.put(saved,"seen_latest",latestId());
 }
 void updateJump(){
  if(jump==null)return;boolean bottom=nearBottom();int count=ChatPresentationPolicy.countAfter(c.state.messages(thread),viewport().optLong("seen_latest",latestId()));
  jump.setText(count>0?count+" new · Latest":"Jump to latest");jump.setContentDescription((count>0?count+" new loaded messages. ":"")+"Jump to latest");jump.setVisibility(bottom?View.GONE:View.VISIBLE);
 }
 void jumpToLatest(){
  if(scroll==null)return;viewportGeneration++;restoring=true;scroll.scrollTo(0,Math.max(0,timeline.getHeight()-scroll.getHeight()));restoring=false;captureViewport();updateJump();c.save();
 }
 void restoreViewport(boolean follow,JSONObject anchor){
  final ScrollView target=scroll;final String targetThread=thread;final int generation=++viewportGeneration;restoring=true;
  target.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
   if(target.getViewTreeObserver().isAlive())target.getViewTreeObserver().removeOnPreDrawListener(this);
   if(scroll!=target||!thread.equals(targetThread)||c.listener!=ConversationsUi.this||!c.valid()||generation!=viewportGeneration)return true;
   View row=messageRows.get(anchor.optLong("anchor_id",-1));int top=row==null?-1:messages.getTop()+row.getTop();
   int y=follow?Math.max(0,timeline.getHeight()-target.getHeight()):ChatPresentationPolicy.restoreY(top,anchor.optInt("anchor_offset"),anchor.optInt("y",c.state.json().optInt("scroll:"+targetThread)));
   target.scrollTo(0,y);restoring=false;captureViewport();updateJump();return true;
  }});target.invalidate();
 }
 void updateThread(){
  if(messages==null||thread.isEmpty())return;
  JSONObject page=c.state.page(thread),meta=c.state.conversation(thread),pending=c.state.pending(thread);if(meta!=null)title.setText(meta.optString("title","Conversation"));
  send.setEnabled(c.connected&&meta!=null&&!meta.optBoolean("archived")&&!c.state.blocked(thread));input.setEnabled(meta==null||!meta.optBoolean("archived"));
  JSONArray turns=c.state.messages(thread);String sig=ChatPresentationPolicy.messageSignature(turns);
  String head=(meta==null?"":meta.optString("task_title")+meta.isNull("task_id")+meta.optBoolean("archived"))+(page!=null&&page.optBoolean("has_more"))+":"+(page!=null&&page.optBoolean("context_compacted"));
  String foot=(pending==null?"":pending.optString("status")+pending.optString("request_id")+pending.has("content"))+c.state.json().has("rejected:"+thread)+c.noticeFor(thread);
  boolean first=signature.isEmpty(),historyChanged=!sig.equals(signature),headerChanged=!head.equals(headerSignature),footerChanged=!foot.equals(footerSignature);
  if(!historyChanged&&!headerChanged&&!footerChanged)return;
  boolean bottom=restoring?viewport().optBoolean("at_bottom",true):nearBottom();captureViewport();
  JSONObject anchor;try{anchor=new JSONObject(viewport().toString());}catch(JSONException impossible){anchor=new JSONObject();}
  if(!anchor.has("y")&&c.state.json().has("scroll:"+thread)){ConversationState.put(anchor,"y",c.state.json().optInt("scroll:"+thread));ConversationState.put(anchor,"at_bottom",false);}
  boolean hasSaved=anchor.has("y");if(!viewport().has("seen_latest"))ConversationState.put(viewport(),"seen_latest",latestId());
  long firstId=turns.length()==0?Long.MAX_VALUE:turns.optJSONObject(0).optLong("id");boolean prepended=previousCount>0&&firstId<previousFirst;
  boolean own=ChatPresentationPolicy.containsOwnSend(turns,followRequest);if(own){followRequest="";viewport().remove("follow_request");}
  boolean follow=first?ChatPresentationPolicy.initialFollow(hasSaved,anchor.optBoolean("at_bottom",false),own):ChatPresentationPolicy.shouldFollow(bottom,own,prepended);
  signature=sig;headerSignature=head;footerSignature=foot;previousCount=turns.length();previousFirst=firstId;
  if(headerChanged)renderHeader(page,meta);
  if(historyChanged)renderMessages(turns);
  if(footerChanged)renderFooter(pending);
  restoreViewport(follow,anchor);
 }
 void renderHeader(JSONObject page,JSONObject meta){
  historyHeader.removeAllViews();
  if(meta!=null){String scope=meta.isNull("task_id")?"Free conversation · draft only":"Task-linked · "+meta.optString("task_title","Saved task");a.add(historyHeader,a.label(scope,12,a.muted));if(meta.isNull("task_id"))a.add(historyHeader,a.label("Hermès can discuss and draft. It cannot create tasks or projects, send mail, or access your calendar. No tools are enabled.",12,a.muted));if(meta.optBoolean("archived"))a.add(historyHeader,a.button("Archived · reopen to write",()->c.patch(thread,null,false)));}
  if(page!=null&&page.optBoolean("context_compacted"))a.add(historyHeader,a.label("Older context was summarized for the model. Full saved messages remain available here.",13,a.muted));
  if(page!=null&&page.optBoolean("has_more"))a.add(historyHeader,a.button("Load older messages",()->{captureViewport();c.load(thread,true);}));
 }
 void renderMessages(JSONArray turns){
  messages.removeAllViews();messageRows.clear();String previousDate="";
  for(int i=0;i<turns.length();i++){
   JSONObject turn=turns.optJSONObject(i);if(turn==null)continue;JSONObject prev=i>0?turns.optJSONObject(i-1):null,next=i+1<turns.length()?turns.optJSONObject(i+1):null;
   String date=dateLabel(turn.optString("created_at"));if(!date.isEmpty()&&!date.equals(previousDate)){TextView separator=a.label(date,12,a.muted);separator.setGravity(Gravity.CENTER);separator.setPadding(0,a.dp(16),0,a.dp(12));a.add(messages,separator);}previousDate=date;
   boolean outgoing=turn.optString("role").equals("user"),joinedBefore=ChatPresentationPolicy.grouped(prev,turn,zone),joinedAfter=ChatPresentationPolicy.grouped(turn,next,zone);
   LinearLayout row=a.vertical();row.setGravity(outgoing?Gravity.END:Gravity.START);row.setPadding(outgoing?a.dp(28):0,joinedBefore?a.dp(2):a.dp(10),outgoing?0:a.dp(28),joinedAfter?0:a.dp(8));
   LinearLayout bubble=a.vertical();bubble.setPadding(a.dp(14),a.dp(9),a.dp(14),a.dp(9));android.graphics.drawable.GradientDrawable shape=a.ui.shape(outgoing?a.ui.soft:a.card,0,20);
   float full=a.dp(20),small=a.dp(6);shape.setCornerRadii(outgoing?new float[]{full,full,joinedBefore?small:full,joinedBefore?small:full,joinedAfter?small:full,joinedAfter?small:full,full,full}:new float[]{joinedBefore?small:full,joinedBefore?small:full,full,full,full,full,joinedAfter?small:full,joinedAfter?small:full});bubble.setBackground(shape);
   if(!joinedBefore){TextView speaker=a.heading(outgoing?"You":"Hermès",12);speaker.setTextColor(a.muted);a.add(bubble,speaker);}
   final String original=turn.optString("content");TextView body=a.label("",16,a.ink);body.setText(MarkdownText.render(original));body.setAutoLinkMask(0);body.setTextIsSelectable(true);body.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
   body.setCustomSelectionActionModeCallback(new ActionMode.Callback(){public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add(0,7341,0,"Copy original").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);return true;}public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}public boolean onActionItemClicked(ActionMode mode,MenuItem item){if(item.getItemId()!=7341)return false;copyOriginal(original);mode.finish();return true;}public void onDestroyActionMode(ActionMode mode){}});
   a.add(bubble,body);if(original.length()>32000)a.add(bubble,a.button("View full original",()->a.fullMessage(original)));
   row.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
   if(!joinedAfter){String stamp=clockLabel(turn.optString("created_at"));if(!stamp.isEmpty()){TextView time=a.label(stamp,11,a.muted);time.setContentDescription("Sent "+timeLabel(turn.optString("created_at"),true));row.addView(time,new LinearLayout.LayoutParams(-2,-2));}}
   final long messageId=turn.optLong("id");messageRows.put(messageId,row);a.add(messages,row);
   bubble.setOnLongClickListener(v->{a.dialogBuilder().setTitle(outgoing?"Your message":"Hermès message").setItems(new String[]{"Copy original","View original text"},(dialog,which)->{if(which==0)copyOriginal(original);else a.fullMessage(original);}).show();return true;});
   // Selectable text keeps Android's standard rendered-text Copy as well.
  }
  if(turns.length()==0)a.add(messages,a.label("Start with a question, an idea, or a draft. Messages appear here after the server accepts them. Older discarded chats are not imported.",15,a.muted));
 }
 void copyOriginal(String original){((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Original message",original));}
 void renderFooter(JSONObject pending){
  historyFooter.removeAllViews();
  if(pending!=null){String status=pending.optString("status");if(ConversationState.pendingStatus(status))a.add(historyFooter,a.label(status.equals("queued")?"Hermès · queued. You can leave; your saved request continues on the server.":"Hermès · reply in progress. You can leave; your saved request continues on the server.",14,a.muted));else if(status.equals("prepared")||status.equals("uncertain")){a.add(historyFooter,a.label("Delivery uncertain. No automatic resend or new request will occur.",14,a.muted));a.add(historyFooter,a.button("Check saved request status",()->c.check(thread,pending.optString("request_id"))));if(pending.has("content"))a.add(historyFooter,a.button("Retry exact saved request",()->a.confirm("Retry the same request?","This keeps the original request identity and payload. The server will not rerun an already accepted request.",()->c.retryExact(thread))));}else if(status.equals("failed")||status.equals("interrupted")){a.add(historyFooter,a.label(status.equals("interrupted")?"Interrupted by server restart. It was not restarted automatically.":"Request failed. No message was automatically resent.",14,a.muted));a.add(historyFooter,a.button("Review retry as a new request",()->{c.reviewRetry(thread);syncDraft();}));}}
  if(c.state.json().has("rejected:"+thread)){a.add(historyFooter,a.label("A submitted message was not accepted. No provider work was restarted.",14,a.muted));a.add(historyFooter,a.button("Review rejected message",()->{c.reviewRetry(thread);syncDraft();}));}
  if(!c.noticeFor(thread).isEmpty()){TextView notice=a.label(c.noticeFor(thread),13,a.muted);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(historyFooter,notice);}
 }
 @Override public void changed(){updateRows();if(state!=null)state.setText(c.notice);updateThread();}
 String voiceThread(){return thread;}
 void applyVoice(String value){if(input==null)return;String draft=input.getText().toString();input.setText(draft+(draft.isEmpty()?"":" ")+value);input.setSelection(input.length());c.save();}
}

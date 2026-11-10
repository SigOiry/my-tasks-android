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
 LinearLayout rows,messages,timeline,historyHeader,historyFooter;ScrollView scroll;TextView state,title,context;EditText input;ImageButton send;Button jump;
 String thread="",signature="",boundTask="",headerSignature="",footerSignature="",listSignature="",followRequest="";
 boolean syncing,restoring;long previousFirst=Long.MAX_VALUE;int previousCount,viewportGeneration;
 final LinkedHashMap<Long,View> messageRows=new LinkedHashMap<>();
 final HashMap<Long,String> rowBindings=new HashMap<>();boolean animateLocal;
 final java.time.ZoneId zone=java.time.ZoneId.systemDefault();
 JSONObject viewport(){String key="presentation:"+thread;JSONObject value=c.state.json().optJSONObject(key);if(value==null){value=new JSONObject();ConversationState.put(c.state.json(),key,value);}return value;}
 String timeLabel(String iso,boolean includeDate){java.time.Instant instant=ChatPresentationPolicy.instant(iso);if(instant==null)return "";return java.time.format.DateTimeFormatter.ofLocalizedDateTime(includeDate?java.time.format.FormatStyle.MEDIUM:java.time.format.FormatStyle.SHORT,java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone).format(instant);}
 String dateLabel(String iso){java.time.Instant instant=ChatPresentationPolicy.instant(iso);return instant==null?"":java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(zone).format(instant);}
 String clockLabel(String iso){java.time.Instant instant=ChatPresentationPolicy.instant(iso);return instant==null?"":java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone).format(instant);}
 ConversationsUi(MainActivity activity,ConversationController controller){a=activity;c=controller;}
 void attach(){c.listener=this;}
 void buildGlobal(LinearLayout parent){
  boundTask="";attach();LinearLayout body=a.horizontal();body.setGravity(Gravity.TOP);parent.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout master=a.vertical(),detail=a.vertical();
  if(a.wide){body.addView(master,new LinearLayout.LayoutParams(a.dp(280),-1));View divider=new View(a);divider.setBackgroundColor(a.line);body.addView(divider,new LinearLayout.LayoutParams(a.dp(1),-1));body.addView(detail,new LinearLayout.LayoutParams(0,-1,1));body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=ChatRowPolicy.masterWidth(Math.round(body.getWidth()/a.getResources().getDisplayMetrics().density));if(master.getLayoutParams().width!=a.dp(width)){master.getLayoutParams().width=a.dp(width);master.requestLayout();}});buildList(master);if(!c.state.selected().isEmpty())buildThread(detail,c.state.selected(),false);else a.add(detail,a.label("Select a conversation or start a new one.",16,a.muted));}
  else if(c.state.selected().isEmpty())buildList(masterWith(body,master));else buildThread(masterWith(body,detail),c.state.selected(),false);
 }
 LinearLayout masterWith(LinearLayout body,LinearLayout pane){body.addView(pane,new LinearLayout.LayoutParams(-1,-1));return pane;}
 void buildList(LinearLayout pane){
  FrameLayout frame=new FrameLayout(a);pane.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
  ScrollView sc=new ScrollView(a);sc.setFillViewport(true);sc.setClipToPadding(false);sc.setPadding(0,0,0,a.dp(84));LinearLayout content=a.vertical();sc.addView(content);frame.addView(sc,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout heading=a.horizontal();heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(a.heading("Discussions",30),new LinearLayout.LayoutParams(0,-2,1));heading.addView(a.ui.icon(R.drawable.ic_more,"Discussion filters and search help",()->filters()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(content,heading);
  EditText search=new EditText(a);a.ui.input(search);search.setBackground(a.ui.shape(a.ui.surface,0,24));search.setSingleLine(true);search.setText(c.search);search.setHint("Search discussions");search.setContentDescription("Search loaded titles, task labels and excerpts; not message history. Load more for older discussions.");a.add(content,search);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int x,int y,int z){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int x,int y,int z){c.search=s.toString();updateRows();}});
  FrameLayout capsule=a.ui.glassSurface();LinearLayout segments=a.horizontal();String[] labels={"All","Tasks","Free"};for(int n=0;n<labels.length;n++){final int index=n;Button segment=a.button(labels[n],()->selectScope(index));a.ui.selected(segment,n==1?c.state.json().optBoolean("only_tasks"):n==2?c.scope.equals("free"):!c.state.json().optBoolean("only_tasks")&&!c.scope.equals("free"));segments.addView(segment,new LinearLayout.LayoutParams(0,-2,1));}capsule.addView(segments,new FrameLayout.LayoutParams(-1,-2));a.add(content,capsule);
  state=a.label("",13,a.muted);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(content,state);rows=a.vertical();a.add(content,rows);
  ImageButton create=a.ui.icon(R.drawable.ic_add,"New conversation",()->c.create(null,()->{c.state.select(c.visible);a.render();}));create.setImageTintList(android.content.res.ColorStateList.valueOf(a.ui.onPrimary));create.setBackground(a.ui.touch(a.ui.primary,0,28));FrameLayout.LayoutParams floating=new FrameLayout.LayoutParams(a.dp(56),a.dp(56),Gravity.BOTTOM|Gravity.END);floating.setMargins(a.dp(12),a.dp(12),a.dp(12),a.dp(12));frame.addView(create,floating);
  updateRows();c.hide();c.list(false);
 }
 void selectScope(int index){ConversationState.put(c.state.json(),"only_tasks",index==1);c.filter(index==2?"free":"all","",false);a.render();}
 String humanScope(){return c.scope.equals("free")?"Free":c.state.json().optBoolean("only_tasks")?"Task conversations":"All conversations";}
 void filters(){String[] labels={"All conversations","Free conversations","Task conversations","Archived conversations","Tasks","Settings","Refresh conversations"};a.dialogBuilder().setTitle("Discussions view").setItems(labels,(d,i)->{if(i==6){c.list(false);return;}if(i==4){a.destination="Tasks";a.render();return;}if(i==5){a.settings();return;}c.state.select("");if(i==2){c.filter("all","",false);ConversationState.put(c.state.json(),"only_tasks",true);}else{ConversationState.put(c.state.json(),"only_tasks",false);c.filter(i==1?"free":"all","",i==3);}a.render();}).show();}
 void updateRows(){
  if(rows==null)return;JSONArray loaded=new JSONArray();
  for(int i=0;i<c.threads.length();i++){JSONObject t=c.threads.optJSONObject(i);if(t==null)continue;JSONObject current=c.state.conversation(t.optString("id"));loaded.put(current!=null&&current.optInt("revision")>=t.optInt("revision")?current:t);}
  String nextSignature=loaded.toString()+c.search+c.next+c.archived+c.scope+c.state.json().optBoolean("only_tasks");if(nextSignature.equals(listSignature))return;listSignature=nextSignature;
  rows.removeAllViews();boolean onlyTasks=c.state.json().optBoolean("only_tasks");String q=c.search.toLowerCase(Locale.ROOT);int shown=0;
  for(int i=0;i<loaded.length();i++){
   JSONObject t=loaded.optJSONObject(i);if(t==null||t.optBoolean("archived")!=c.archived||(onlyTasks&&t.isNull("task_id")))continue;
   String task=t.isNull("task_id")?"Free conversation":t.optString("task_title","Task conversation"),label=t.optString("title","Conversation"),preview=t.optString("last_preview","");
   if(!(label+" "+task+" "+preview).toLowerCase(Locale.ROOT).contains(q))continue;
   String id=t.optString("id");LinearLayout row=a.horizontal();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));row.setMinimumHeight(a.dp(72));row.setBackground(a.ui.touch(id.equals(c.state.selected())?a.ui.soft:android.graphics.Color.TRANSPARENT,0,16));row.setFocusable(true);row.setClickable(true);
   row.setOnClickListener(v->{capture();c.state.select(id);c.save();a.renderPage(false);});
   ImageView glyph=new ImageView(a);glyph.setImageResource(t.isNull("task_id")?R.drawable.ic_chat:R.drawable.ic_tasks);glyph.setImageTintList(android.content.res.ColorStateList.valueOf(a.muted));glyph.setPadding(a.dp(10),a.dp(10),a.dp(10),a.dp(10));row.addView(glyph,new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));
   LinearLayout text=a.vertical();LinearLayout top=a.horizontal();top.setGravity(Gravity.CENTER_VERTICAL);TextView h=a.heading(label,16);h.setMaxLines(2);h.setEllipsize(TextUtils.TruncateAt.END);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));
   String updated=dateLabel(t.optString("updated_at"));TextView stamp=a.label(updated,11,a.muted);top.addView(stamp);a.add(text,top);
   JSONObject req=t.optJSONObject("latest_request");String status=req==null?"":req.optString("status");String suffix=ConversationState.pendingStatus(status)?" · "+(status.equals("queued")?"Queued":"Reply in progress"):status.equals("failed")||status.equals("interrupted")?" · "+status:"";
   TextView previewText=a.label((preview.isEmpty()?task:preview)+suffix,13,a.muted);previewText.setMaxLines(1);previewText.setEllipsize(TextUtils.TruncateAt.END);a.add(text,previewText);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
   row.setContentDescription(label+", "+task+suffix+", "+preview+(updated.isEmpty()?"":", updated "+updated));a.add(rows,row);View line=new View(a);line.setBackgroundColor(a.line);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,a.dp(1));lp.leftMargin=a.dp(52);rows.addView(line,lp);shown++;
  }
  if(shown==0)a.add(rows,a.label("No matching conversations loaded. Start a new conversation, refresh, or load more. Previously discarded chats cannot be recovered.",14,a.muted));
  if(!c.next.isEmpty())a.add(rows,a.button("Load more conversations",()->c.list(true)));
 }
 void buildTask(LinearLayout pane,String task){
  attach();boundTask=task;String id=c.state.taskSelection(task);if(!id.isEmpty()){buildThread(pane,id,true);return;}
  LinearLayout bar=a.horizontal();bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(a.ui.icon(R.drawable.ic_back,"Back to task details",()->{a.detailsMode="Details";a.renderPage(true);}),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));bar.addView(a.heading("Hermès",20),new LinearLayout.LayoutParams(0,-2,1));bar.addView(a.ui.icon(R.drawable.ic_more,"Task conversation actions",()->emptyTaskMenu(task)),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(pane,bar);
  LinearLayout empty=a.vertical();empty.setGravity(Gravity.CENTER);empty.setPadding(a.dp(24),a.dp(24),a.dp(24),a.dp(24));pane.addView(empty,new LinearLayout.LayoutParams(-1,0,1));a.add(empty,a.heading("A conversation for this task",22));a.add(empty,a.label("Choose saved history or start a new thread with the task’s saved context.",15,a.muted));a.add(empty,a.primary("New task conversation",()->c.create(task,()->{c.state.taskSelection(task,c.visible);c.save();a.render();})));a.add(empty,a.button("Choose saved conversation",()->taskPicker(task)));state=a.label(c.notice,13,a.muted);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(empty,state);c.hide();
 }
 void emptyTaskMenu(String task){a.dialogBuilder().setTitle("Task conversation").setItems(new String[]{"Saved conversations","New conversation"},(d,i)->{if(i==0)taskPicker(task);else c.create(task,()->{c.state.taskSelection(task,c.visible);c.save();a.render();});}).show();
 }
 void taskPicker(String task){
  // Paged task-specific picker does not disturb the global Discussions filter.
  showTaskPicker(task,"",new ArrayList<>());
 }
 void showTaskPicker(String task,String cursor,ArrayList<JSONObject> collected){final int navigation=c.navigationVersion;c.thread(thread,"picker:"+task+":"+cursor,client->client.list("task",task,false,cursor),r->{if(navigation!=c.navigationVersion||!a.selected.equals(task)||!a.destination.equals("Tasks"))return;JSONArray items=r.getJSONArray("conversations");for(int i=0;i<items.length();i++)collected.add(items.getJSONObject(i));ArrayList<String> names=new ArrayList<>();for(JSONObject t:collected)names.add(t.optString("title"));String next=r.isNull("next_cursor")?"":r.optString("next_cursor");if(!next.isEmpty())names.add("Load more…");if(names.isEmpty()){a.notice("No saved conversations","Use New to create a task-linked conversation.");return;}a.dialogBuilder().setTitle("Saved task conversations").setItems(names.toArray(new String[0]),(dialog,index)->{if(navigation!=c.navigationVersion)return;if(index==collected.size())showTaskPicker(task,next,collected);else{c.state.taskSelection(task,collected.get(index).optString("id"));c.save();a.render();}}).setNegativeButton("Cancel",null).show();},null);}
 void buildThread(LinearLayout pane,String id,boolean taskBound){
  thread=id;c.visible=id;c.state.select(id);signature="";headerSignature="";footerSignature="";messageRows.clear();rowBindings.clear();previousCount=0;previousFirst=Long.MAX_VALUE;restoring=true;
  followRequest=viewport().optString("follow_request","");
  LinearLayout bar=a.horizontal();bar.setGravity(Gravity.CENTER_VERTICAL);if(taskBound)bar.addView(a.ui.icon(R.drawable.ic_back,"Back to task details",()->{a.detailsMode="Details";a.renderPage(true);}),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));if(!taskBound&&!a.wide)bar.addView(a.ui.icon(R.drawable.ic_back,"Back to Discussions",()->{c.state.select("");c.hide();a.renderPage(true);}),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
  title=a.heading("Conversation",17);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);LinearLayout identity=a.vertical();a.add(identity,title);context=a.label(taskBound?"Task conversation":"Free discussion",12,a.muted);context.setMaxLines(1);context.setEllipsize(TextUtils.TruncateAt.END);a.add(identity,context);bar.addView(identity,new LinearLayout.LayoutParams(0,-2,1));bar.addView(a.ui.icon(R.drawable.ic_more,"Conversation actions",()->threadMenu()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(pane,bar);
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
  LinearLayout composer=a.horizontal();composer.setGravity(Gravity.BOTTOM);composer.setPadding(a.dp(4),a.dp(4),a.dp(4),a.dp(4));FrameLayout composerGlass=a.ui.glassSurface();composerGlass.setElevation(a.dp(1));composerGlass.addView(composer,new FrameLayout.LayoutParams(-1,-2));
  input=new EditText(a);a.ui.input(input);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setMinWidth(0);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_ACTION_NONE);input.setSingleLine(false);input.setMinLines(1);input.setMaxLines(3);input.setGravity(Gravity.TOP);input.setHint(c.state.mode(id).equals("email_draft")?"Request an email draft":"Message Hermès");input.setContentDescription("Message Hermès; Send submits this draft");input.setText(c.state.draft(id));input.setSaveEnabled(false);composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int x,int y,int z){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int x,int y,int z){if(!syncing)c.editDraft(id,s.toString());}});
  // Mode and reviewed voice input live in overflow, not beside every draft.
  send=a.ui.icon(R.drawable.ic_chat_send_up,"Send to Hermès",()->{JSONObject old=c.state.pending(id);String previous=old==null?"":old.optString("request_id");c.state.draft(id,input.getText().toString());c.send(id,c.state.mode(id));JSONObject current=c.state.pending(id);if(current!=null&&!previous.equals(current.optString("request_id"))){followRequest=current.optString("request_id");ConversationState.put(viewport(),"follow_request",followRequest);c.save();}syncDraft();});
  send.setImageTintList(android.content.res.ColorStateList.valueOf(a.ui.onPrimary));send.setBackground(a.ui.touch(a.ui.primary,0,24));composer.addView(send,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(pane,composerGlass);
  updateThread();c.load(id,false);
 }
 void composerMenu(){String[] choices={"Question / analysis","Email draft (never sends mail)","Review voice input"};a.dialogBuilder().setTitle("Hermès · no tools").setItems(choices,(d,i)->{if(i==2)a.voice();else{c.state.mode(thread,i==1?"email_draft":"question");c.save();input.setHint(i==1?"Request an email draft":"Message Hermès");}}).show();}
 void threadMenu(){JSONObject meta=c.state.conversation(thread);if(meta==null)return;final String task=boundTask.isEmpty()?(meta.isNull("task_id")?"":meta.optString("task_id")):boundTask;LinkedHashMap<String,String> actions=ConversationActionPolicy.threadActions(task,meta.optBoolean("archived"));String[] keys=actions.keySet().toArray(new String[0]),options=actions.values().toArray(new String[0]);a.dialogBuilder().setTitle(meta.optString("title","Conversation")).setItems(options,(d,i)->{String action=keys[i];if(action.equals("mode")){composerMenu();return;}if(action.equals("capabilities")){a.notice("Hermès capabilities","Discuss, plan and draft. The model cannot create tasks or projects, send mail or access your calendar. No model tools are enabled.");return;}if(action.equals("discussions")){a.destination="Discussions";c.state.select("");a.render();return;}if(action.equals("tasks")){a.destination="Tasks";a.render();return;}if(action.equals("settings")){a.settings();return;}if(action.equals("saved")||action.equals("new")){taskThreadAction(task,action.equals("new"));return;}if(action.equals("history"))c.load(thread,false);else if(action.equals("rename")){EditText name=new EditText(a);a.ui.input(name);name.setText(c.state.json().optString("rename:"+thread,meta.optString("title")));AlertDialog dialog=a.dialogBuilder().setTitle("Rename conversation").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Rename",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{String proposed=name.getText().toString().trim();if(proposed.isEmpty()){name.setError("A title is required");return;}ConversationState.put(c.state.json(),"rename:"+thread,proposed);c.save();c.patch(thread,proposed,null);dialog.dismiss();}));dialog.show();}else if(action.equals("archive"))a.confirm(meta.optBoolean("archived")?"Reopen conversation?":"Archive conversation?","History remains saved on the server. Archived conversations can be reopened from Discussions.",()->c.patch(thread,null,!meta.optBoolean("archived")));}).show();}
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
  JSONObject page=c.state.page(thread),meta=c.state.conversation(thread),pending=c.state.pending(thread);if(meta!=null){title.setText(meta.optString("title","Conversation"));context.setText(meta.isNull("task_id")?"Free discussion · planning & drafts":meta.optString("task_title","Task conversation"));}
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
  if(historyChanged){animateLocal=ChatRowPolicy.animate(own,bottom,first,prepended,a.ui.motionEnabled());renderMessages(turns);}
  if(footerChanged)renderFooter(pending);
  restoreViewport(follow,anchor);
 }
 void renderHeader(JSONObject page,JSONObject meta){
  historyHeader.removeAllViews();
  if(meta!=null&&meta.optBoolean("archived"))a.add(historyHeader,a.button("Archived · reopen to write",()->c.patch(thread,null,false)));
  if(page!=null&&page.optBoolean("context_compacted"))a.add(historyHeader,a.label("Older context was summarized for the model. Full saved messages remain available here.",13,a.muted));
  if(page!=null&&page.optBoolean("has_more"))a.add(historyHeader,a.button("Load older messages",()->{captureViewport();c.load(thread,true);}));
 }
 void renderMessages(JSONArray turns){
  LinkedHashMap<Long,View> retained=new LinkedHashMap<>();HashMap<Long,String> bindings=new HashMap<>();int position=0;String previousDate="";
  if(messageRows.isEmpty())messages.removeAllViews();
  for(int i=0;i<turns.length();i++){
   JSONObject turn=turns.optJSONObject(i);if(turn==null)continue;JSONObject prev=i>0?turns.optJSONObject(i-1):null,next=i+1<turns.length()?turns.optJSONObject(i+1):null;
   long id=turn.optLong("id");String binding=ChatRowPolicy.binding(prev,turn,next);bindings.put(id,binding);View existing=messageRows.get(id);String date=dateLabel(turn.optString("created_at"));boolean dateBreak=!date.isEmpty()&&!date.equals(previousDate);previousDate=date;
   if(existing!=null&&binding.equals(rowBindings.get(id))){retained.put(id,existing);if(messages.indexOfChild(existing)!=position){messages.removeView(existing);messages.addView(existing,position);}position++;continue;}
   boolean inserted=existing==null;if(existing!=null)messages.removeView(existing);
   LinearLayout wrapper=a.vertical();if(dateBreak){TextView separator=a.label(date,12,a.muted);separator.setGravity(Gravity.CENTER);separator.setPadding(0,a.dp(16),0,a.dp(12));a.add(wrapper,separator);}
   boolean outgoing=turn.optString("role").equals("user"),joinedBefore=ChatPresentationPolicy.grouped(prev,turn,zone),joinedAfter=ChatPresentationPolicy.grouped(turn,next,zone);
   LinearLayout row=a.vertical();row.setGravity(outgoing?Gravity.END:Gravity.START);row.setPadding(outgoing?a.dp(28):0,joinedBefore?a.dp(2):a.dp(10),outgoing?0:a.dp(28),joinedAfter?0:a.dp(8));
   LinearLayout bubble=a.vertical();bubble.setPadding(a.dp(14),a.dp(9),a.dp(14),a.dp(9));android.graphics.drawable.GradientDrawable shape=a.ui.shape(outgoing?android.graphics.Color.rgb(0,104,229):a.ui.surface,0,20);
   float full=a.dp(20),small=a.dp(6);shape.setCornerRadii(outgoing?new float[]{full,full,joinedBefore?small:full,joinedBefore?small:full,joinedAfter?small:full,joinedAfter?small:full,full,full}:new float[]{joinedBefore?small:full,joinedBefore?small:full,full,full,full,full,joinedAfter?small:full,joinedAfter?small:full});bubble.setBackground(shape);
   final String original=turn.optString("content");TextView body=a.label("",16,outgoing?android.graphics.Color.WHITE:a.ink);body.setText(MarkdownText.render(original));body.setAutoLinkMask(0);body.setTextIsSelectable(true);body.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
   body.setCustomSelectionActionModeCallback(new ActionMode.Callback(){public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add(0,7341,0,"Copy original").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);if(!outgoing)menu.add(0,7342,1,"Review as new task").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);return true;}public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}public boolean onActionItemClicked(ActionMode mode,MenuItem item){if(item.getItemId()==7342&&!outgoing){mode.finish();a.reviewConversationAsTask(original);return true;}if(item.getItemId()!=7341)return false;copyOriginal(original);mode.finish();return true;}public void onDestroyActionMode(ActionMode mode){}});
   if(!outgoing)body.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.action_review_as_task,"Review as new task"));}@Override public boolean performAccessibilityAction(View host,int action,android.os.Bundle arguments){if(action==R.id.action_review_as_task){a.reviewConversationAsTask(original);return true;}return super.performAccessibilityAction(host,action,arguments);}});
   a.add(bubble,body);if(original.length()>32000)a.add(bubble,a.button("View full original",()->a.fullMessage(original)));
   row.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
   if(!joinedAfter){String stamp=clockLabel(turn.optString("created_at"));if(!stamp.isEmpty()){TextView time=a.label(stamp,11,a.muted);time.setContentDescription("Sent "+timeLabel(turn.optString("created_at"),true));row.addView(time,new LinearLayout.LayoutParams(-2,-2));}}
   final long messageId=turn.optLong("id");a.add(wrapper,row);retained.put(messageId,wrapper);messages.addView(wrapper,position++);if(inserted&&outgoing&&animateLocal){row.setTranslationY(a.dp(8));row.animate().translationY(0).setDuration(NativeMotionPolicy.duration("insertion")).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
   bubble.setOnLongClickListener(v->{messageMenu(turn.optString("role"),original);return true;});
   // Selectable text keeps Android's standard rendered-text Copy as well.
  }
  for(Map.Entry<Long,View> entry:messageRows.entrySet())if(!retained.containsKey(entry.getKey()))messages.removeView(entry.getValue());messageRows.clear();messageRows.putAll(retained);rowBindings.clear();rowBindings.putAll(bindings);
  if(turns.length()==0)a.add(messages,a.label("Start with a question, a plan, or a draft. Messages appear after the server accepts them.",15,a.muted));
 }
 void messageMenu(String role,String original){
  LinkedHashMap<String,String> actions=new LinkedHashMap<>();actions.put("copy","Copy original");actions.put("view","View original text");
  if(role.equals("assistant"))actions.put("review","Review as new task");
  String[] keys=actions.keySet().toArray(new String[0]);
  a.dialogBuilder().setTitle(role.equals("user")?"Your message":"Hermès message").setItems(actions.values().toArray(new String[0]),(dialog,which)->{
   String action=keys[which];if(action.equals("copy"))copyOriginal(original);else if(action.equals("view"))a.fullMessage(original);else if(action.equals("review"))a.reviewConversationAsTask(original);
  }).show();
 }
 void copyOriginal(String original){((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Original message",original));}
 void renderFooter(JSONObject pending){
  historyFooter.removeAllViews();
  if(pending!=null){String status=pending.optString("status");if(ConversationState.pendingStatus(status))a.add(historyFooter,a.label(status.equals("queued")?"Hermès · queued. You can leave; your saved request continues on the server.":"Hermès · reply in progress. You can leave; your saved request continues on the server.",14,a.muted));else if(status.equals("prepared")||status.equals("uncertain")){a.add(historyFooter,a.label("Delivery uncertain. No automatic resend or new request will occur.",14,a.muted));a.add(historyFooter,a.button("Check saved request status",()->c.check(thread,pending.optString("request_id"))));if(pending.has("content"))a.add(historyFooter,a.button("Retry exact saved request",()->a.confirm("Retry the same request?","This keeps the original request identity and payload. The server will not rerun an already accepted request.",()->c.retryExact(thread))));}else if(status.equals("failed")||status.equals("interrupted")){a.add(historyFooter,a.label(status.equals("interrupted")?"Interrupted by server restart. It was not restarted automatically.":"Request failed. No message was automatically resent.",14,a.muted));a.add(historyFooter,a.button("Review retry as a new request",()->{c.reviewRetry(thread);syncDraft();}));}}
  if(pending!=null&&pending.optString("status").equals("running"))a.add(historyFooter,new PendingDots(a,pending.optString("request_id")));
  if(c.state.json().has("rejected:"+thread)){a.add(historyFooter,a.label("A submitted message was not accepted. No provider work was restarted.",14,a.muted));a.add(historyFooter,a.button("Review rejected message",()->{c.reviewRetry(thread);syncDraft();}));}
  if(!c.noticeFor(thread).isEmpty()){TextView notice=a.label(c.noticeFor(thread),13,a.muted);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);a.add(historyFooter,notice);}
 }
 @Override public void changed(){updateRows();if(state!=null)state.setText(c.notice);updateThread();}
 String voiceThread(){return thread;}
 void applyVoice(String value){if(input==null)return;String draft=input.getText().toString();input.setText(draft+(draft.isEmpty()?"":" ")+value);input.setSelection(input.length());c.save();}
}

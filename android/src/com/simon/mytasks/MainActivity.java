package com.simon.mytasks;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Native, dependency-free activity. Server is authoritative; cached tasks never accept edits. */
public final class MainActivity extends Activity {
 Store store;Store.Account account;
 JSONObject snapshot=new JSONObject();JSONArray tasks=new JSONArray();
 JSONObject draft;String selected="",filter="Active",query="",chatTask="",chatDraft="",chatModeValue="question";
 JSONArray history=new JSONArray();
 boolean online=false,busy=false,wide=false,restoring=false;
 String banner="Connect your server in Settings",chatStatus="",rejectedChatBody="";
 WorkspaceUi ui;String projectFilter="",sortOrder="Project",detailsMode="Details";boolean editing=false;
 final Map<String,Button> viewButtons=new LinkedHashMap<>();TextView listCount,notificationState;
 int bg,card,ink,muted,line,accent;int listY,detailY;
 LinearLayout root,listPane,detailPane,listItems,editor,chatMessages;TextView stateLabel;
 ScrollView listScroll,detailScroll;EditText title,description,notes,due,project,chatInput;Spinner priority,status,chatMode;
 String[] priorities={"high","medium","low"},statuses={"inbox","todo","in_progress","waiting","completed","cancelled"};
 final Handler main=new Handler(Looper.getMainLooper());
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 String text(JSONObject o,String key){return o==null||o.isNull(key)?"":o.optString(key,"");}
 @Override public void onCreate(Bundle saved){
  boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  setTheme(dark?R.style.MyTasks_Dark:R.style.MyTasks_Light);super.onCreate(saved);
  store=new Store(this);applyAppearance();
  if(saved!=null){projectFilter=saved.getString("projectFilter","");sortOrder=saved.getString("sortOrder","Project");detailsMode=saved.getString("detailsMode","Details");editing=saved.getBoolean("editing");}
  if(saved!=null){selected=saved.getString("selected","");filter=saved.getString("filter","Active");query=saved.getString("query","");chatTask=saved.getString("chatTask","");chatDraft=saved.getString("chatDraft","");chatStatus=saved.getString("chatStatus","");listY=saved.getInt("listY");detailY=saved.getInt("detailY");try{if(saved.containsKey("draft"))draft=new JSONObject(saved.getString("draft"));history=new JSONArray(saved.getString("history","[]"));}catch(Exception ignored){}}
  try{account=store.account();}catch(Exception e){banner="Credentials unavailable. Re-enter Settings.";}
  if(saved!=null){chatModeValue=saved.getString("chatMode","question");String epoch=saved.getString("epoch","");if(account==null||!account.epoch.equals(epoch)){selected="";draft=null;history=new JSONArray();chatDraft="";chatTask="";}if(chatStatus.contains("thinking"))chatStatus="Previous request was interrupted. The original provider request may still be running. Your message was not resent automatically; wait before deciding to send again.";}
  JSONObject cached=store.cached();if(cached!=null){snapshot=cached;tasks=cached.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();banner="Offline cache · read only — refresh to edit";}
  if(draft!=null){JSONObject base=task(selected);if(base!=null){JSONObject merged=copy(base);Iterator<String> keys=draft.keys();while(keys.hasNext()){String k=keys.next();try{merged.put(k,draft.get(k));}catch(Exception ignored){}}draft=merged;}}
  if(saved==null)acceptIntent(getIntent());render();PollService.schedule(this);if(account!=null)refresh();
 }
 @Override protected void onResume(){super.onResume();UpdateManager.get(this).attach(this);updateNotificationState();}
 @Override protected void onPause(){UpdateManager.get(this).detach(this);super.onPause();}
 void acceptIntent(Intent i){String id=i.getStringExtra("task_id");String epoch=i.getStringExtra("account_epoch");if(id!=null&&account!=null&&account.epoch.equals(epoch)){if(!id.equals(selected)){chatTask=id;history=new JSONArray();chatDraft="";chatStatus="";}selected=id;draft=null;detailsMode="Details";editing=false;detailY=0;}}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(busy){notice("Task update","Finish the current request, then select the updated task from the list.");return;}captureDraft();Runnable open=()->{acceptIntent(i);setIntent(i);render();refresh();};if(dirty()||!chatDraft.trim().isEmpty())confirm("Open updated task?","Unsaved task fields will be discarded. An unsent Hermes message is cleared if the selected task changes.",open);else open.run();}
 void applyAppearance(){
  String preference=store.context.getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","System");
  boolean dark=preference.equals("Dark")||(preference.equals("System")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
  getTheme().applyStyle(dark?R.style.MyTasks_Dark:R.style.MyTasks_Light,true);
  ui=new WorkspaceUi(this,dark);bg=ui.canvas;card=ui.surface;ink=ui.ink;muted=ui.secondary;line=ui.border;accent=ui.primary;
  if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);captureDraft();applyAppearance();render();}
 @Override protected void onSaveInstanceState(Bundle out){captureDraft();out.putString("projectFilter",projectFilter);out.putString("sortOrder",sortOrder);out.putString("detailsMode",detailsMode);out.putBoolean("editing",editing);out.putString("epoch",account==null?"":account.epoch);out.putString("chatMode",chatModeValue);out.putString("selected",selected);out.putString("filter",filter);out.putString("query",query);out.putString("chatTask",chatTask);out.putString("chatDraft",chatDraft);out.putString("chatStatus",chatStatus);out.putString("history",history.toString());if(draft!=null)out.putString("draft",stateDraft().toString());out.putInt("listY",listScroll==null?listY:listScroll.getScrollY());out.putInt("detailY",detailScroll==null?detailY:detailScroll.getScrollY());super.onSaveInstanceState(out);}
 JSONObject stateDraft(){JSONObject state=new JSONObject();for(String k:new String[]{"title","description","notes","project","due_date","status","priority","revision","id","deleted"})try{if(draft.has(k))state.put(k,draft.get(k));}catch(Exception ignored){}return state;}
 boolean alive(Store.Account a){return !isFinishing()&&!isDestroyed()&&store.current(a);}
 LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 GradientDrawable surface(int color,int stroke){return ui.shape(color,stroke,8);}
 TextView label(String s,int size,int color){return ui.text(s,size,color,false);}
 TextView heading(String s,int size){TextView t=ui.text(s,size,ink,true);if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);return t;}
 Button button(String name,Runnable action){return ui.button(name,action);}
 Button primary(String name,Runnable action){Button b=button(name,action);ui.primary(b);return b;}
 void add(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 void space(LinearLayout parent,int h){View v=new View(this);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);parent.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
 void rule(LinearLayout parent){View v=new View(this);v.setBackgroundColor(line);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
 void pair(LinearLayout parent,View a,View b){LinearLayout row=horizontal();row.addView(a,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMarginStart(dp(8));row.addView(b,p);add(parent,row);}
 void render(){
  wide=getResources().getConfiguration().screenWidthDp>=720;
  title=description=notes=due=project=chatInput=null;priority=status=chatMode=null;
  listScroll=detailScroll=null;listItems=null;listCount=null;viewButtons.clear();
  root=vertical();root.setBackgroundColor(bg);root.setPadding(dp(16),0,dp(16),0);
  root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom,left,right;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()|WindowInsets.Type.displayCutout());top=i.top;bottom=i.bottom;left=i.left;right=i.right;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}v.setPadding(dp(16)+left,top,dp(16)+right,bottom);return insets;});
  setContentView(root);root.requestApplyInsets();
  LinearLayout bar=horizontal();TextView logo=heading("My Tasks",24);bar.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton refresh=ui.icon(R.drawable.ic_refresh,"Refresh tasks",()->{captureDraft();refresh();});refresh.setEnabled(!busy);bar.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(48)));
  bar.addView(ui.icon(R.drawable.ic_settings,"Settings",this::settings),new LinearLayout.LayoutParams(dp(48),dp(48)));add(root,bar);
  stateLabel=label(banner,12,muted);stateLabel.setMaxLines(3);stateLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(root,stateLabel);space(root,8);rule(root);space(root,12);
  LinearLayout body=horizontal();body.setGravity(Gravity.TOP);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));listPane=vertical();detailPane=vertical();
  if(wide){body.addView(listPane,new LinearLayout.LayoutParams(0,-1,0.44f));View divider=new View(this);divider.setBackgroundColor(line);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(1),-1);dl.setMargins(dp(16),0,dp(16),0);body.addView(divider,dl);body.addView(detailPane,new LinearLayout.LayoutParams(0,-1,0.56f));}
  else body.addView(selected.isEmpty()?listPane:detailPane,new LinearLayout.LayoutParams(-1,-1));
  if(wide||selected.isEmpty())buildList();if(wide||!selected.isEmpty())buildDetails();
 }
 void buildList(){
  View layout=getLayoutInflater().inflate(R.layout.task_list,listPane,false);add(listPane,layout);layout.setLayoutParams(new LinearLayout.LayoutParams(-1,-1));
  LinearLayout controls=layout.findViewById(R.id.list_controls);listScroll=layout.findViewById(R.id.list_scroll);listItems=layout.findViewById(R.id.list_rows);
  // On compact list windows even the app bar/banner scroll: no fixed chrome can
  // consume the task viewport when resize or IME insets leave very little height.
  if(!wide){LinearLayout header=layout.findViewById(R.id.list_header);while(root.getChildCount()>1){View chrome=root.getChildAt(0);root.removeViewAt(0);header.addView(chrome);}}
  LinearLayout intro=horizontal();LinearLayout titles=vertical();TextView h=heading(filter.equals("Week")?"This week":filter+" tasks",22);titles.addView(h);listCount=label("",12,muted);titles.addView(listCount);intro.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
  Button create=primary("New",()->navigate("NEW"));create.setContentDescription("Create a new task");create.setEnabled(online&&!busy);ui.leadingIcon(create,R.drawable.ic_add);intro.addView(create);add(controls,intro);space(controls,12);
  HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);LinearLayout chips=horizontal();
  for(String f:new String[]{"Active","Today","Week","All","Trash"}){Button b=button(f,()->{captureDraft();filter=f;listY=0;render();});ui.selected(b,f.equals(filter));viewButtons.put(f,b);chips.addView(b,new LinearLayout.LayoutParams(-2,-2));}
  filters.addView(chips);add(controls,filters);filters.post(()->{Button chosen=viewButtons.get(filter);if(chosen!=null)filters.scrollTo(Math.max(0,chosen.getLeft()-dp(12)),0);});space(controls,12);
  EditText search=new EditText(this);ui.input(search);search.setSingleLine(true);search.setHint("Search tasks or projects");search.setText(query);search.setContentDescription("Search tasks or projects");search.setSaveEnabled(false);add(controls,search);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();listY=0;buildListRows();}public void afterTextChanged(Editable e){}});space(controls,8);
  Button projects=button(projectFilter.isEmpty()?"All projects":projectFilter,this::projectMenu);projects.setSingleLine(true);projects.setEllipsize(android.text.TextUtils.TruncateAt.END);projects.setContentDescription("Filter project: "+(projectFilter.isEmpty()?"All projects":projectFilter));
  Button sort=button("Sort: "+sortOrder,this::sortMenu);sort.setSingleLine(true);sort.setEllipsize(android.text.TextUtils.TruncateAt.END);pair(controls,projects,sort);space(controls,12);
  buildListRows();final ScrollView scroll=listScroll;final int savedY=listY;scroll.post(()->scroll.scrollTo(0,savedY));
 }
 void projectMenu(){
  ArrayList<String> options=new ArrayList<>();options.add("All projects");options.addAll(PresentationPolicy.projectOptions(tasks,filter,LocalDate.now()));String[] items=options.toArray(new String[0]);
  new AlertDialog.Builder(this).setTitle("Project").setSingleChoiceItems(items,projectFilter.isEmpty()?0:options.indexOf(projectFilter),(d,w)->{captureDraft();projectFilter=w==0?"":items[w];listY=0;d.dismiss();render();}).setNegativeButton("Cancel",null).show();
 }
 void sortMenu(){String[] items={"Project","Priority","Due date"};new AlertDialog.Builder(this).setTitle("Sort tasks").setSingleChoiceItems(items,Arrays.asList(items).indexOf(sortOrder),(d,w)->{captureDraft();sortOrder=items[w];listY=0;d.dismiss();render();}).setNegativeButton("Cancel",null).show();}
 boolean terminal(JSONObject t){return PresentationPolicy.terminal(t);}
 String human(String value){String s=value.replace('_',' ');return s.isEmpty()?"Not set":Character.toUpperCase(s.charAt(0))+s.substring(1);}
 String dueLabel(JSONObject t){return PresentationPolicy.dueLabel(text(t,"due_date"),LocalDate.now(),Locale.getDefault());}
 void buildListRows(){
  if(listItems==null)return;listItems.removeAllViews();ArrayList<JSONObject> shown=new ArrayList<>();LocalDate today=LocalDate.now();int unread=0;
  for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&PresentationPolicy.matches(t,filter,projectFilter,query,today)){shown.add(t);JSONObject n=t.optJSONObject("notification");if(n!=null&&n.optBoolean("unread"))unread++;}}
  Collections.sort(shown,(a,b)->PresentationPolicy.compare(a,b,sortOrder));if(listCount!=null)listCount.setText(shown.size()+" tasks"+(unread>0?" · "+unread+" unread":"")+(online?"":" · read only"));String group="";
  for(JSONObject t:shown){
   String g=sortOrder.equals("Priority")?human(text(t,"priority"))+" priority":sortOrder.equals("Due date")?dueLabel(t):PresentationPolicy.project(t);
   if(!g.equals(group)){space(listItems,12);TextView groupTitle=heading(g,14);add(listItems,groupTitle);space(listItems,4);group=g;}
   String id=text(t,"id");LinearLayout row=vertical();row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(ui.touch(selected.equals(id)?ui.soft:card,selected.equals(id)?accent:line,8));row.setMinimumHeight(dp(96));row.setClickable(true);row.setFocusable(true);row.setSelected(selected.equals(id));row.setOnClickListener(v->navigate(id));
   TextView taskTitle=heading(text(t,"title"),16);taskTitle.setMaxLines(3);taskTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);if(Build.VERSION.SDK_INT>=28)taskTitle.setAccessibilityHeading(false);add(row,taskTitle);space(row,4);
   LinearLayout badges=horizontal();boolean stackBadges=getResources().getConfiguration().fontScale>1.3f;if(stackBadges){badges.setOrientation(LinearLayout.VERTICAL);badges.setGravity(Gravity.START);}badges.addView(ui.badge(human(text(t,"priority"))+" priority",text(t,"priority").equals("high")));JSONObject n=t.optJSONObject("notification");boolean isUnread=n!=null&&n.optBoolean("unread");if(isUnread){TextView update=ui.badge("Unread",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);if(stackBadges)bp.topMargin=dp(4);else bp.setMarginStart(dp(8));badges.addView(update,bp);}add(row,badges);
   add(row,label(human(text(t,"status"))+" · "+dueLabel(t),13,muted));if(!sortOrder.equals("Project"))add(row,label(PresentationPolicy.project(t),12,muted));
   row.setContentDescription(text(t,"title")+", "+human(text(t,"priority"))+" priority, "+human(text(t,"status"))+", "+dueLabel(t)+(isUnread?", unread update":"")+(selected.equals(id)?", selected":""));row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);add(listItems,row);space(listItems,4);
  }
  if(shown.isEmpty()){space(listItems,32);add(listItems,heading(account==null?"Your workspace awaits":"No tasks in this view",22));add(listItems,label(account==null?"Open Settings to securely connect your task server. Your tasks will appear here.":"Try another view, clear your search, or choose a different project.",15,muted));if(!query.isEmpty()||!projectFilter.isEmpty())add(listItems,button("Clear filters",()->{captureDraft();query="";projectFilter="";render();}));}
  space(listItems,24);
 }
 JSONObject task(String id){for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
 JSONObject copy(JSONObject t){try{return new JSONObject(t.toString());}catch(Exception e){return new JSONObject();}}
 JSONObject empty(){JSONObject o=new JSONObject();try{o.put("title","").put("description","").put("notes","").put("project","").put("due_date","").put("priority","medium").put("status","todo");}catch(Exception ignored){}return o;}
 void navigate(String id){
  if(id.equals(selected))return;if(busy){notice("Please wait","A request is still in progress.");return;}captureDraft();
  Runnable go=()->{selected=id;draft=null;detailY=0;detailsMode="Details";editing=id.equals("NEW");chatTask=id;history=new JSONArray();chatDraft="";chatStatus="";render();};
  if(dirty()||!chatDraft.trim().isEmpty())confirm("Discard unsaved changes?","Unsaved task fields and your unsent Hermes message will be lost.",go);else go.run();
 }
 boolean dirty(){if(draft==null)return false;JSONObject old=selected.equals("NEW")?empty():task(selected);if(old==null)return true;for(String f:new String[]{"title","description","notes","project","due_date","priority","status"})if(!text(draft,f).equals(text(old,f)))return true;return false;}
 void captureDraft(){if(draft!=null&&title!=null){try{draft.put("title",title.getText().toString()).put("description",description.getText().toString()).put("notes",notes.getText().toString()).put("project",project.getText().toString()).put("due_date",due.getText().toString()).put("priority",priorities[priority.getSelectedItemPosition()]).put("status",statuses[status.getSelectedItemPosition()]);}catch(Exception ignored){}}if(chatInput!=null)chatDraft=chatInput.getText().toString();if(chatMode!=null&&chatMode.getSelectedItem()!=null)chatModeValue=chatMode.getSelectedItemPosition()==1?"email_draft":"question";if(listScroll!=null)listY=listScroll.getScrollY();if(detailScroll!=null)detailY=detailScroll.getScrollY();}
 EditText field(LinearLayout parent,String name,String value,boolean multi){
  TextView caption=label(name,13,muted);add(parent,caption);EditText e=new EditText(this);e.setId(View.generateViewId());caption.setLabelFor(e.getId());e.setText(value);ui.input(e);e.setSingleLine(!multi);e.setMinLines(multi?3:1);e.setGravity(Gravity.TOP);e.setContentDescription(name);e.setSelectAllOnFocus(false);e.setEnabled(online&&!busy);e.setSaveEnabled(false);add(parent,e);space(parent,12);return e;
 }
 Spinner choice(LinearLayout parent,String name,String[] options,String value){
  TextView caption=label(name,13,muted);add(parent,caption);Spinner s=new Spinner(this);s.setId(View.generateViewId());caption.setLabelFor(s.getId());
  String[] names=new String[options.length];for(int i=0;i<options.length;i++)names[i]=human(options[i]);
  ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names){@Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convert,parent);v.setTextColor(ink);v.setTextSize(16);return v;}@Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convert,parent);v.setTextColor(ink);v.setBackgroundColor(card);v.setMinHeight(dp(48));return v;}};
  s.setAdapter(a);for(int i=0;i<options.length;i++)if(options[i].equals(value))s.setSelection(i);s.setMinimumHeight(dp(48));s.setBackground(surface(card,line));s.setEnabled(online&&!busy);s.setContentDescription(name);add(parent,s);space(parent,12);return s;
 }
 void buildDetails(){
  if(selected.isEmpty()){space(detailPane,64);add(detailPane,heading("Select a task",24));add(detailPane,label("Keep your task list in view while you review details, make a plan, or work with Hermes.",16,muted));return;}
  LinearLayout top=horizontal();if(!wide)top.addView(ui.icon(R.drawable.ic_back,"Back to tasks",()->navigate("")),new LinearLayout.LayoutParams(dp(48),dp(48)));
  TextView h=heading(selected.equals("NEW")?"New task":"Task workspace",20);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton reload=ui.icon(R.drawable.ic_refresh,"Reload latest task",()->confirm("Reload latest task?","This discards unsaved fields and fetches the current server version. Your Hermes conversation is kept.",()->{draft=null;refresh();}));reload.setEnabled(account!=null&&!busy);top.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));add(detailPane,top);space(detailPane,8);
  JSONObject current=task(selected);if(draft==null)draft=selected.equals("NEW")?empty():current==null?null:copy(current);
  if(draft==null){add(detailPane,label("This task is not in your cache. Refresh to load it.",16,muted));return;}
  if(!selected.equals("NEW")&&!draft.optBoolean("deleted")){
   LinearLayout modes=horizontal();for(String mode:new String[]{"Details","Hermes"}){Button tab=button(mode,()->{captureDraft();detailsMode=mode;detailY=0;render();});ui.selected(tab,detailsMode.equals(mode));modes.addView(tab,new LinearLayout.LayoutParams(0,-2,1));}add(detailPane,modes);space(detailPane,12);
  }
  detailScroll=new ScrollView(this);detailScroll.setFillViewport(true);editor=vertical();editor.setPadding(0,0,0,dp(28));detailScroll.addView(editor);detailPane.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
  JSONObject n=draft.optJSONObject("notification");if(n!=null&&n.optBoolean("unread")){
   LinearLayout update=vertical();update.setPadding(dp(16),dp(12),dp(16),dp(16));update.setBackground(surface(ui.soft,line));add(update,heading("Unread update",14));add(update,heading(n.optString("headline","Task updated"),18));TextView summary=label(PollService.summary(n),15,ink);summary.setTextIsSelectable(true);add(update,summary);space(update,8);
   final String displayedToken=n.optString("token");Button read=button("Mark displayed update read",()->markRead(displayedToken));read.setEnabled(online&&!busy&&!displayedToken.isEmpty());add(update,read);add(update,label("Only this displayed update is acknowledged. Newer changes remain unread.",12,muted));add(editor,update);space(editor,16);
  }
  if(draft.optBoolean("deleted")){add(editor,heading("In trash",14));add(editor,heading(text(draft,"title"),24));add(editor,label(text(draft,"description"),16,muted));space(editor,16);Button restore=primary("Restore task",()->mutation("POST","/api/tasks/"+Api.segment(selected)+"/restore",revision(),false));restore.setEnabled(online&&!busy);add(editor,restore);return;}
  if(!selected.equals("NEW")&&detailsMode.equals("Hermes")){add(editor,heading(text(draft,"title"),20));add(editor,label(PresentationPolicy.project(draft)+" · "+human(text(draft,"status")),13,muted));buildChat();}
  else if(editing||selected.equals("NEW"))buildTaskEditor();else buildTaskReading();
  final ScrollView scroll=detailScroll;final int savedY=detailY;scroll.post(()->scroll.scrollTo(0,savedY));
 }
 void buildTaskReading(){
  add(editor,heading(text(draft,"title"),24));add(editor,label(PresentationPolicy.project(draft),14,muted));space(editor,8);
  add(editor,ui.badge(human(text(draft,"priority"))+" priority · "+human(text(draft,"status")),text(draft,"priority").equals("high")));space(editor,8);add(editor,label(dueLabel(draft),15,ink));space(editor,16);rule(editor);space(editor,16);
  readingSection("Description",text(draft,"description").isEmpty()?"No description yet.":text(draft,"description"));if(!text(draft,"notes").isEmpty())readingSection("Your notes",text(draft,"notes"));
  sourceContext();space(editor,16);Button edit=primary("Edit task",()->{captureDraft();editing=true;detailY=0;render();});edit.setEnabled(online&&!busy);add(editor,edit);if(!online)add(editor,label("Cached tasks are read only. Refresh with a connection to edit.",13,muted));
 }
 void readingSection(String name,String value){add(editor,heading(name,14));TextView t=label(value,16,ink);t.setTextIsSelectable(true);add(editor,t);space(editor,16);}
 void sourceContext(){
  add(editor,heading("Source context",14));add(editor,label(PresentationPolicy.sourceSummary(draft),14,muted));
  java.util.List<String> links=PresentationPolicy.sourceLinks(draft);
  for(String link:links){String host=android.net.Uri.parse(link).getHost();Button open=button("Open source · "+host,()->confirm("Open source?","This supplied link opens in your browser:\n"+host+"\nYour task-server token is never included.",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException|SecurityException e){notice("No browser available","Install or enable a browser to open this source.");}}));add(editor,open);space(editor,8);}
  if(links.isEmpty())add(editor,label("No external source link was provided. Internal source IDs are kept private.",12,muted));
 }
 void buildTaskEditor(){
  add(editor,heading(selected.equals("NEW")?"Capture a task":"Edit task",22));add(editor,label("Changes stay on this device until you save.",13,muted));space(editor,12);
  title=field(editor,"Task title",text(draft,"title"),false);description=field(editor,"Description",text(draft,"description"),true);
  priority=choice(editor,"Priority",priorities,text(draft,"priority"));status=choice(editor,"Status",statuses,text(draft,"status"));
  due=field(editor,"Due date · YYYY-MM-DD, optional",text(draft,"due_date"),false);due.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
  Button pick=button("Choose date",this::pickDate);pick.setEnabled(online&&!busy);ui.leadingIcon(pick,R.drawable.ic_calendar);Button clear=button("No due date",()->due.setText(""));clear.setEnabled(online&&!busy);pair(editor,pick,clear);space(editor,12);
  project=field(editor,"Project / category",text(draft,"project"),false);notes=field(editor,"Your notes",text(draft,"notes"),true);space(editor,8);
  Button save=primary(selected.equals("NEW")?"Create task":"Save changes",()->save(false));save.setEnabled(online&&!busy);add(editor,save);space(editor,8);
  if(!selected.equals("NEW")){Button complete=button("Mark complete",()->save(true));complete.setEnabled(online&&!busy&&!terminal(draft));ui.leadingIcon(complete,R.drawable.ic_check);add(editor,complete);space(editor,8);}
  Button cancel=button("Cancel editing",()->{captureDraft();Runnable discard=()->{draft=null;editing=false;detailY=0;if(selected.equals("NEW"))selected="";render();};if(dirty())confirm("Discard unsaved changes?","Your saved task is unchanged.",discard);else discard.run();});cancel.setEnabled(!busy);ui.quiet(cancel);add(editor,cancel);
  if(!selected.equals("NEW")){space(editor,24);rule(editor);space(editor,16);Button delete=button("Move to trash",()->{captureDraft();confirm("Move task to trash?","You can restore it from Trash. Unsaved fields will not be saved.",()->mutation("DELETE","/api/tasks/"+Api.segment(selected),revision(),false));});ui.danger(delete);delete.setEnabled(online&&!busy);add(editor,delete);}
 }
 void pickDate(){LocalDate date=PresentationPolicy.parseDate(due.getText().toString());if(date==null)date=LocalDate.now();new DatePickerDialog(this,(picker,y,m,d)->due.setText(LocalDate.of(y,m+1,d).toString()),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();}
 JSONObject revision(){JSONObject o=new JSONObject();try{o.put("revision",text(draft,"revision"));}catch(Exception ignored){}return o;}
 void save(boolean complete){captureDraft();try{
  if(text(draft,"title").trim().isEmpty())throw new IllegalArgumentException("Title is required");String date=text(draft,"due_date").trim();if(!date.isEmpty()){LocalDate.parse(date);if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("Use YYYY-MM-DD");}
  JSONObject body=new JSONObject();for(String f:new String[]{"title","description","notes","priority","status"})body.put(f,text(draft,f));body.put("project",text(draft,"project").trim().isEmpty()?JSONObject.NULL:text(draft,"project").trim());body.put("due_date",date.isEmpty()?JSONObject.NULL:date);if(complete)body.put("status","completed");boolean create=selected.equals("NEW");if(!create)body.put("revision",text(draft,"revision"));mutation(create?"POST":"PATCH",create?"/api/tasks":"/api/tasks/"+Api.segment(selected),body,true);
 }catch(Exception e){notice("Check task fields",e instanceof java.time.format.DateTimeParseException?"Use a valid date in YYYY-MM-DD format.":e.getMessage());}}
 void mutation(String method,String path,JSONObject body,boolean clearDraft){
  if(!online||busy||account==null)return;captureDraft();busy=true;banner="Saving…";Store.Account a=account;render();
  Api.IO.execute(()->{try{JSONObject result=new Api(a).call(method,path,body);String id=result.getString("id");JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);main.post(()->{if(!alive(a))return;busy=false;online=true;snapshot=latest;tasks=latest.optJSONArray("tasks");selected=id;draft=copy(result);editing=false;detailsMode="Details";banner="Saved · up to date";render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;if(e instanceof Api.Failure&&((Api.Failure)e).code==409){banner="Conflict · draft preserved — reload latest before saving";render();notice("Task changed", "Your draft is preserved. Copy any changes you need, then choose Reload latest. Nothing was overwritten.");}else{online=false;banner=Api.friendly(e)+" A write may already have reached the server; refresh and check before retrying.";render();}});}});
 }
 void markRead(String token){
  if(account==null||!online||busy)return;captureDraft();Store.Account a=account;String id=selected;busy=true;banner="Marking displayed update read…";render();
  Api.IO.execute(()->{try{
   JSONObject t=new Api(a).call("POST","/api/tasks/"+Api.segment(id)+"/updates/read",new JSONObject().put("token",token));
   main.post(()->{if(!alive(a))return;busy=false;try{if(draft!=null&&id.equals(selected))draft.put("notification",t.getJSONObject("notification"));for(int i=0;i<tasks.length();i++){JSONObject item=tasks.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))tasks.put(i,t);}}catch(Exception ignored){}((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(id,0);banner="Displayed update marked read";render();});
  }catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;banner="Update not acknowledged · refresh for current summary";render();int code=e instanceof Api.Failure?((Api.Failure)e).code:0;new AlertDialog.Builder(this).setTitle(code==409?"A newer update is waiting":"Update not acknowledged").setMessage(PresentationPolicy.ackFailure(code)).setNegativeButton("Keep summary",null).setPositiveButton("Refresh summary",(dialog,which)->{captureDraft();refresh();}).show();});}});
 }
 void refresh(){
  if(account==null){settings();return;}if(busy)return;captureDraft();final boolean keepDraft=dirty();Store.Account a=account;busy=true;banner="Refreshing…";render();
  Api.IO.execute(()->{try{JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);String notificationNote="";try{PollService.poll(this,store,a,()->isDestroyed());}catch(Exception e){notificationNote=" · update checks delayed";}final String suffix=notificationNote;main.post(()->{if(!alive(a))return;busy=false;online=true;snapshot=latest;tasks=latest.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();if(!keepDraft)draft=null;else if(draft!=null){JSONObject remote=task(selected);if(remote!=null)try{draft.put("notification",remote.optJSONObject("notification"));}catch(Exception ignored){}}banner="Connected · "+tasks.length()+" tasks"+suffix;render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;online=false;banner=Api.friendly(e);render();});}});
 }
 void buildChat(){
  space(editor,16);add(editor,heading("Task-linked conversation",18));add(editor,label("Ask a question or prepare an email draft. Hermes never sends email. Review its response before use.",14,muted));space(editor,16);
  if(!chatTask.equals(selected)){chatTask=selected;history=new JSONArray();chatDraft="";}
  chatMessages=vertical();for(int i=0;i<history.length();i++){
   JSONObject m=history.optJSONObject(i);if(m==null)continue;boolean assistant="assistant".equals(m.optString("role"));String content=m.optString("content");
   LinearLayout turn=vertical();turn.setPadding(dp(16),dp(12),dp(16),dp(12));turn.setBackground(surface(assistant?card:ui.soft,line));add(turn,heading(assistant?"Hermes":"You",14));TextView body=label(content,16,ink);body.setTextIsSelectable(true);add(turn,body);
   if(assistant){space(turn,8);Button copy=button("Copy response / draft",()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Hermes draft",content));Toast.makeText(this,"Copied to clipboard",Toast.LENGTH_SHORT).show();});add(turn,copy);}add(chatMessages,turn);space(chatMessages,12);
  }
  if(history.length()==0)add(chatMessages,label("Your conversation starts here. Hermes uses the selected task’s saved context; save task edits first if you want them included.",15,muted));add(editor,chatMessages);
  if(!chatStatus.isEmpty()){TextView state=label(chatStatus,14,muted);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(editor,state);space(editor,12);}
  chatMode=choice(editor,"Conversation mode",new String[]{"question","email_draft"},chatModeValue);chatInput=field(editor,"Message · voice text stays editable",chatDraft,true);
  Button send=primary("Send to Hermes",this::sendChat);send.setEnabled(online&&!busy);add(editor,send);space(editor,8);Button voice=button("Review voice input",this::voice);voice.setEnabled(online&&!busy);add(editor,voice);
  if(history.length()>0){space(editor,8);Button reset=button("New conversation",()->confirm("Start a new conversation?","This clears the conversation and unsent message on this device. Saved task data is unchanged.",()->{captureDraft();history=new JSONArray();chatDraft="";chatStatus="";rejectedChatBody="";detailY=0;render();}));reset.setEnabled(!busy);ui.quiet(reset);add(editor,reset);}
 }
 void sendChat(){
  captureDraft();if(!online||busy||account==null||chatDraft.trim().isEmpty())return;
  String message=chatDraft;Store.Account a=account;List<String[]> prior=new ArrayList<>();
  for(int i=0;i<history.length();i++){JSONObject t=history.optJSONObject(i);if(t!=null)prior.add(new String[]{t.optString("role"),t.optString("content")});}
  final String body;
  try{body=Core.chatBody(selected,message,chatModeValue,prior);}catch(IllegalArgumentException e){notice("Check chat message",e.getMessage());return;}
  if(body.equals(rejectedChatBody)){notice("Message still too large",Core.chatFailure(413));return;}
  busy=true;chatStatus="Hermes is thinking… (waiting up to 200 seconds; provider work may continue after a timeout)";render();
  Api.IO.execute(()->{try{JSONObject result=new Api(a).callChat(body);main.post(()->{if(!alive(a))return;busy=false;try{history.put(new JSONObject().put("role","user").put("content",message));history.put(new JSONObject().put("role","assistant").put("content",result.optString("reply")));history=copyHistory();}catch(Exception ignored){}chatDraft="";chatStatus="";render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;if(e instanceof Api.Failure&&((Api.Failure)e).code==413)rejectedChatBody=body;chatStatus=Core.chatFailure(e instanceof Api.Failure?((Api.Failure)e).code:0);render();});}});
 }
 JSONArray copyHistory(){JSONArray a=new JSONArray();for(int i=Math.max(0,history.length()-12);i<history.length();i++){JSONObject turn=history.optJSONObject(i);if(turn==null)continue;try{String content=turn.optString("content");a.put(new JSONObject().put("role",turn.optString("role")).put("content",content));}catch(Exception ignored){}}return a;}
 void voice(){
  noticeConfirm("Platform speech recognition","Your device's speech recognition app may send audio to its provider. My Tasks does not record audio. The recognized text will be placed in the message field for you to review; it is never sent automatically.",()->{
   Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Dictate a message for Hermes");
   try{startActivityForResult(i,108);}catch(ActivityNotFoundException e){notice("No speech recognizer","Install or enable a platform speech recognition app, or type your message instead.");}
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==UpdateManager.PERMISSION_REQUEST||request==UpdateManager.INSTALL_REQUEST){UpdateManager.get(this).result(request);return;}if(request==108&&result==RESULT_OK&&data!=null){ArrayList<String> values=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty()){chatDraft=(chatInput==null?chatDraft:chatInput.getText().toString())+(chatDraft.isEmpty()?"":" ")+values.get(0);if(chatInput!=null){chatInput.setText(chatDraft);chatInput.setSelection(chatDraft.length());}notice("Review voice input","The transcript is in your message field. Edit it, then tap Send when ready.");}}}
 void updateNotificationState(){
  if(notificationState==null)return;NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel c=manager.getNotificationChannel(PollService.CHANNEL);String state=Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED?"Notification permission required":!manager.areNotificationsEnabled()?"App notifications are disabled":c!=null&&c.getImportance()==NotificationManager.IMPORTANCE_NONE?"Task update channel is disabled":"Allowed by Android settings";notificationState.setText(state);
 }
 void settings(){
  captureDraft();LinearLayout form=vertical();form.setPadding(dp(20),dp(8),dp(20),dp(20));
  add(form,heading("Appearance",18));add(form,label("Choose the theme for this workspace.",13,muted));
  String currentTheme=getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","System");LinearLayout themes=horizontal();
  AlertDialog[] holder=new AlertDialog[1];for(String theme:new String[]{"System","Light","Dark"}){Button b=button(theme,()->{getSharedPreferences("appearance",MODE_PRIVATE).edit().putString("theme",theme).apply();holder[0].dismiss();applyAppearance();render();settings();});ui.selected(b,currentTheme.equals(theme));themes.addView(b,new LinearLayout.LayoutParams(0,-2,1));}add(form,themes);space(form,24);rule(form);space(form,16);
  add(form,heading("App & updates",18));add(form,label("My Tasks · Version "+UpdateManager.get(this).installedName(),14,ink));
  add(form,label("Public release checks are independent of your task server. Automatic checks run on the first foreground launch each local day. Manual checks need internet, but no task account.",13,muted));
  add(form,button("Check for updates",()->UpdateManager.get(this).check(true)));space(form,24);rule(form);space(form,16);
  add(form,heading("Task notifications",18));
  boolean permission=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
  notificationState=label("",14,ink);add(form,notificationState);updateNotificationState();
  add(form,label("Best-effort checks are requested at least 15 minutes apart, not instant push. Android may delay them much longer in Doze, battery saver, force-stop, or offline. Refresh checks now. First connection quietly establishes a baseline without old-event alerts.",13,muted));
  add(form,button(permission?"Manage notification settings":"Allow notifications",()->{if(Build.VERSION.SDK_INT>=33&&!permission){holder[0].dismiss();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);}else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));}));space(form,24);rule(form);space(form,16);
  add(form,heading("Connection & privacy",18));add(form,label("Use an HTTPS origin with a trusted certificate. Tokens are encrypted with Android Keystore. Saving a connection clears the local task cache and notification cursor.",13,muted));
  add(form,label("Task server",13,muted));EditText origin=new EditText(this);ui.input(origin);origin.setHint("https://task.oirysimon.com");origin.setSingleLine(true);origin.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);origin.setText(account==null?store.prefs.getString("server","https://task.oirysimon.com"):account.server);origin.setContentDescription("HTTPS backend origin");add(form,origin);space(form,12);
  add(form,label("Server token",13,muted));EditText token=new EditText(this);ui.input(token);token.setHint(account==null?"Enter server token":"New token to change connection");token.setSingleLine(true);token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSaveEnabled(false);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);token.setContentDescription("Server bearer token");add(form,token);
  space(form,12);add(form,label("Voice input uses your device’s chosen recognizer, which may send audio to its provider. Transcripts are always reviewed before sending. Task-linked Hermes drafts never send email.",13,muted));
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Settings").setView(scroll).setNegativeButton("Done",null).setPositiveButton("Save connection",null).create();holder[0]=dialog;
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(busy){notice("Please wait","A request is in progress. Try settings again when it finishes.");return;}String url=origin.getText().toString(),secret=token.getText().toString();try{Core.server(url);if(secret.trim().isEmpty())throw new IllegalArgumentException("Enter the server token to save settings.");}catch(Exception e){token.setError(e.getMessage());return;}
   Runnable apply=()->{busy=true;Api.IO.execute(()->{try{store.configure(url,secret);Store.Account next=store.account();main.post(()->{if(isDestroyed())return;account=next;snapshot=new JSONObject();tasks=new JSONArray();selected="";draft=null;editing=false;detailsMode="Details";projectFilter="";history=new JSONArray();chatDraft="";chatTask="";online=false;busy=false;banner="Connection saved";dialog.dismiss();render();PollService.schedule(this);refresh();});}catch(Exception e){main.post(()->{busy=false;notice("Settings not saved","Could not securely save credentials. Verify your HTTPS origin and token, then retry.");});}});};
   if(dirty()||!chatDraft.trim().isEmpty())confirm("Change connection?","Unsaved fields and your unsent Hermes message will be discarded; cached tasks will be cleared.",apply);else apply.run();}));dialog.show();
 }
 void notice(String title,String message){if(isDestroyed())return;new AlertDialog.Builder(this).setTitle(title).setMessage(message==null?"Please retry.":message).setPositiveButton("OK",null).show();}
 void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->yes.run()).show();}
 void noticeConfirm(String title,String message,Runnable yes){confirm(title,message,yes);}
 @Override public void onBackPressed(){if(!selected.isEmpty()){navigate("");}else super.onBackPressed();}
}

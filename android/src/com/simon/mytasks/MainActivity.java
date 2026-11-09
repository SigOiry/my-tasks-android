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
 final PrivateSurfaces surfaces=new PrivateSurfaces();final Map<Dialog,Runnable> dialogClosers=new IdentityHashMap<>();
 AuthPolicy.State authState=AuthPolicy.State.CHECKING;boolean authBusy=false;String authMessage="Verifying the saved session before loading private data.",authServer="";long sessionExpires;
 final Runnable expireSession=()->{if((authState==AuthPolicy.State.FULL||authState==AuthPolicy.State.CHANGE)&&sessionExpires>0&&System.currentTimeMillis()/1000>=sessionExpires)blockSession(401);};
 Api.AuthObserver authObserver;AlertDialog summaryDialog;String summaryTask="",summaryToken="",pendingSummary="";boolean ackNeedsRefresh=false;String summaryReviewed="";
 JSONObject snapshot=new JSONObject();JSONArray tasks=new JSONArray();
 JSONObject draft;String selected="",filter="Active",query="";
 boolean online=false,busy=false,wide=false,restoring=false;
 String banner="Connect your server in Settings";
 WorkspaceUi ui;String projectFilter="",sortOrder="Project",detailsMode="Details";boolean editing=false;
 ConversationController conversations;ConversationsUi conversationUi;String destination="Tasks",voiceEpoch="",voiceThread="";LinearLayout appBar,bottomTabs;boolean showSearch=false;
 final ArrayList<View> chatUtilityChrome=new ArrayList<>();String restoreComposerThread="";int restoreComposerPosition;
 final Map<String,Button> viewButtons=new LinkedHashMap<>();TextView listCount,notificationState;
 int bg,card,ink,muted,line,accent;int listY,detailY;
 LinearLayout root,listPane,detailPane,listItems,editor;TextView stateLabel;
 ScrollView listScroll,detailScroll;EditText title,description,notes,due,project;Spinner priority,status;
 String[] priorities={"high","medium","low"},statuses={"inbox","todo","in_progress","waiting","completed","cancelled"};
 final Handler main=new Handler(Looper.getMainLooper());
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 String text(JSONObject o,String key){return o==null||o.isNull(key)?"":o.optString(key,"");}
 @Override public void onCreate(Bundle saved){
  boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  setTheme(dark?R.style.MyTasks_Dark:R.style.MyTasks_Light);super.onCreate(saved);
  store=new Store(this);applyAppearance();
  if(saved!=null){projectFilter=saved.getString("projectFilter","");sortOrder=saved.getString("sortOrder","Project");detailsMode=saved.getString("detailsMode","Details");editing=saved.getBoolean("editing");}
  if(saved!=null){selected=saved.getString("selected","");filter=saved.getString("filter","Active");query=saved.getString("query","");listY=saved.getInt("listY");detailY=saved.getInt("detailY");try{if(saved.containsKey("draft"))draft=new JSONObject(saved.getString("draft"));}catch(Exception ignored){}}
  try{account=store.account();}catch(Exception e){banner="Credentials unavailable. Re-enter Settings.";}
  conversations=new ConversationController(store,null);if(saved!=null){destination=saved.getString("destination","Tasks");voiceEpoch=saved.getString("voiceEpoch","");voiceThread=saved.getString("voiceThread","");showSearch=saved.getBoolean("showSearch");}
  if(saved!=null&&(account==null||!account.epoch.equals(saved.getString("epoch","")))){selected="";draft=null;query="";projectFilter="";voiceEpoch="";voiceThread="";restoreComposerThread="";}
  authObserver=(a,code)->main.post(()->{if(isDestroyed())return;if(code==0){authBusy=false;checkSession();return;}if(!store.current(a))return;if(code==200){authBusy=false;account=a;checkSession();}else if(authState==AuthPolicy.State.FULL)blockSession(code);});Api.authObserver=authObserver;
  if(draft!=null){JSONObject base=task(selected);if(base!=null){JSONObject merged=copy(base);Iterator<String> keys=draft.keys();while(keys.hasNext()){String k=keys.next();try{merged.put(k,draft.get(k));}catch(Exception ignored){}}draft=merged;}}
  if(saved==null)acceptIntent(getIntent());render();PollService.schedule(this);checkSession();
 }
 @Override protected void onResume(){super.onResume();expireSession.run();UpdateManager.get(this).attach(this);updateNotificationState();if(conversations!=null)conversations.resume();}
 @Override protected void onPause(){captureDraft();if(conversations!=null)conversations.pause();UpdateManager.get(this).detach(this);super.onPause();}
 @Override protected void onDestroy(){main.removeCallbacks(expireSession);if(Api.authObserver==authObserver)Api.authObserver=null;dismissPrivateDialogs();if(conversations!=null)conversations.close();super.onDestroy();}
 void acceptIntent(Intent i){String id=i.getStringExtra("task_id");String epoch=i.getStringExtra("account_epoch");if(id!=null&&account!=null&&account.epoch.equals(epoch)){dismissPrivateDialogs();destination="Tasks";selected=id;pendingSummary=id;summaryReviewed="";draft=null;detailsMode="Details";editing=false;detailY=0;}}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(busy){notice("Task update","Finish the current request, then select the updated task from the list.");return;}captureDraft();Runnable open=()->{acceptIntent(i);setIntent(i);render();if(authState==AuthPolicy.State.FULL)refresh();else if(!authBusy)checkSession();};if(dirty())confirm("Open updated task?","Unsaved task fields will be discarded. Conversation drafts remain saved per thread.",open);else open.run();}
 void applyAppearance(){
  String preference=store.context.getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","System");
  boolean dark=preference.equals("Dark")||(preference.equals("System")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
  getTheme().applyStyle(dark?R.style.MyTasks_Dark:R.style.MyTasks_Light,true);
  ui=new WorkspaceUi(this,dark);bg=ui.canvas;card=ui.surface;ink=ui.ink;muted=ui.secondary;line=ui.border;accent=ui.primary;
  if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);captureDraft();applyAppearance();render();}
 @Override protected void onSaveInstanceState(Bundle out){captureDraft();conversations.save();out.putString("destination",destination);out.putString("voiceEpoch",voiceEpoch);out.putString("voiceThread",voiceThread);out.putBoolean("showSearch",showSearch);out.putString("projectFilter",projectFilter);out.putString("sortOrder",sortOrder);out.putString("detailsMode",detailsMode);out.putBoolean("editing",editing);out.putString("epoch",account==null?"":account.epoch);out.putString("selected",selected);out.putString("filter",filter);out.putString("query",query);if(draft!=null)out.putString("draft",stateDraft().toString());out.putInt("listY",listScroll==null?listY:listScroll.getScrollY());out.putInt("detailY",detailScroll==null?detailY:detailScroll.getScrollY());super.onSaveInstanceState(out);}
 JSONObject stateDraft(){JSONObject state=new JSONObject();for(String k:new String[]{"title","description","notes","project","due_date","status","priority","revision","id","deleted"})try{if(draft.has(k))state.put(k,draft.get(k));}catch(Exception ignored){}return state;}
 boolean alive(Store.Account a){return !isFinishing()&&!isDestroyed()&&authState==AuthPolicy.State.FULL&&store.current(a);}
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
  if(conversationUi!=null){conversationUi.capture();if(conversationUi.input!=null&&conversationUi.input.hasFocus()){restoreComposerThread=conversationUi.thread;restoreComposerPosition=conversationUi.input.getSelectionStart();}}
  if(conversations!=null){conversations.hide();conversations.listener=null;}conversationUi=null;
  chatUtilityChrome.clear();appBar=bottomTabs=null;
  wide=CompactPolicy.expanded(getResources().getConfiguration().screenWidthDp);
  title=description=notes=due=project=null;priority=status=null;
  listScroll=detailScroll=null;listItems=null;listCount=null;viewButtons.clear();
  root=vertical();root.setBackgroundColor(bg);root.setPadding(dp(16),0,dp(16),0);
  root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom,left,right;boolean keyboard;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()|WindowInsets.Type.displayCutout());top=i.top;bottom=i.bottom;left=i.left;right=i.right;keyboard=insets.isVisible(WindowInsets.Type.ime());}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();keyboard=bottom>dp(120);}v.setPadding(dp(12)+left,top,dp(12)+right,bottom);boolean utilities=CompactPolicy.secondaryChrome(keyboard,getResources().getConfiguration().screenHeightDp);if(appBar!=null)appBar.setVisibility(utilities?View.VISIBLE:View.GONE);if(bottomTabs!=null)bottomTabs.setVisibility(utilities?View.VISIBLE:View.GONE);if(conversationUi!=null&&conversationUi.input!=null){int available=root.getHeight()>0?Math.round((root.getHeight()-top-bottom)/getResources().getDisplayMetrics().density):getResources().getConfiguration().screenHeightDp;int maxLines=CompactPolicy.composerLines(available);if(conversationUi.input.getMaxLines()!=maxLines)conversationUi.input.setMaxLines(maxLines);}for(View chrome:chatUtilityChrome)chrome.setVisibility(utilities?View.VISIBLE:View.GONE);if(stateLabel!=null)stateLabel.setVisibility(utilities&&!destination.equals("Discussions")?View.VISIBLE:View.GONE);return insets;});
  setContentView(root);root.requestApplyInsets();
  if(authState!=AuthPolicy.State.FULL){new AuthUi(this).build(root);return;}
  LinearLayout bar=horizontal();appBar=bar;TextView logo=heading(destination,20);bar.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton refresh=ui.icon(R.drawable.ic_refresh,"Refresh current workspace",()->{captureDraft();if(destination.equals("Discussions")){if(conversations.visible.isEmpty())conversations.list(false);else conversations.load(conversations.visible,false);}else refresh();});refresh.setEnabled(!busy);bar.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(48)));
  bar.addView(ui.icon(R.drawable.ic_settings,"Settings",this::settings),new LinearLayout.LayoutParams(dp(48),dp(48)));add(root,bar);
  stateLabel=label(banner,12,muted);stateLabel.setMaxLines(1);stateLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(root,stateLabel);
  if(destination.equals("Discussions")){conversationUi=new ConversationsUi(this,conversations);conversationUi.buildGlobal(root);buildPrimaryTabs();return;}
  LinearLayout body=horizontal();body.setGravity(Gravity.TOP);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));listPane=vertical();detailPane=vertical();
  if(wide){body.addView(listPane,new LinearLayout.LayoutParams(0,-1,0.44f));View divider=new View(this);divider.setBackgroundColor(line);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(1),-1);dl.setMargins(dp(16),0,dp(16),0);body.addView(divider,dl);body.addView(detailPane,new LinearLayout.LayoutParams(0,-1,0.56f));}
  else body.addView(selected.isEmpty()?listPane:detailPane,new LinearLayout.LayoutParams(-1,-1));
  if(wide||selected.isEmpty())buildList();if(wide||!selected.isEmpty())buildDetails();
  buildPrimaryTabs();maybeSummary();
 }
 void buildPrimaryTabs(){bottomTabs=horizontal();bottomTabs.setPadding(dp(4),dp(4),dp(4),dp(4));bottomTabs.setBackground(ui.glass());bottomTabs.setElevation(dp(6));for(String tab:new String[]{"Tasks","Discussions"}){Button b=button(tab,()->{captureDraft();destination=tab;render();if(ui.motionEnabled()){root.setAlpha(.7f);root.animate().alpha(1).setDuration(180).start();}});ui.selected(b,destination.equals(tab));bottomTabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));}add(root,bottomTabs);if(conversationUi!=null&&conversationUi.input!=null&&conversationUi.thread.equals(restoreComposerThread)){EditText composer=conversationUi.input;int position=Math.max(0,Math.min(restoreComposerPosition,composer.length()));composer.post(()->{composer.requestFocus();composer.setSelection(position);});}restoreComposerThread="";root.requestApplyInsets();}
 void buildList(){
  View layout=getLayoutInflater().inflate(R.layout.task_list,listPane,false);FrameLayout dock=new FrameLayout(this);listPane.addView(dock,new LinearLayout.LayoutParams(-1,-1));dock.addView(layout,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout controls=layout.findViewById(R.id.list_controls);listScroll=layout.findViewById(R.id.list_scroll);listItems=layout.findViewById(R.id.list_rows);
  // On compact list windows even the app bar/banner scroll: no fixed chrome can
  // consume the task viewport when resize or IME insets leave very little height.
  if(!wide){LinearLayout header=layout.findViewById(R.id.list_header);while(root.getChildCount()>1){View chrome=root.getChildAt(0);root.removeViewAt(0);header.addView(chrome);}}
  LinearLayout tools=horizontal();Button view=button(filter+" ▾",()->{String[] views={"Active","Today","Week","All","Trash"};dialogBuilder().setTitle("Task view").setItems(views,(d,index)->{captureDraft();filter=views[index];listY=0;render();}).show();});tools.addView(view,new LinearLayout.LayoutParams(0,-2,1));
  tools.addView(ui.icon(R.drawable.ic_more,"Task list search, project and sort",()->{String[] options={"Search","Project filter","Sort tasks","Discussions","Settings"};dialogBuilder().setTitle("Task list").setItems(options,(d,index)->{captureDraft();if(index==0){showSearch=!showSearch;render();}else if(index==1)projectMenu();else if(index==2)sortMenu();else if(index==3){destination="Discussions";render();}else settings();}).show();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
  Button create=primary("+",()->navigate("NEW"));create.setTextSize(28);create.setContentDescription("Create a new task");create.setTooltipText("New task");create.setEnabled(online&&!busy);create.setPadding(0,0,0,0);create.setElevation(dp(6));FrameLayout.LayoutParams floating=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.END);floating.setMargins(dp(12),dp(12),dp(12),dp(12));dock.addView(create,floating);listItems.setPadding(0,0,0,dp(76));listItems.setBackgroundColor(card);add(controls,tools);
  if(showSearch||!query.isEmpty()){EditText search=new EditText(this);ui.input(search);search.setSingleLine(true);search.setHint("Search tasks or projects");search.setText(query);search.setContentDescription("Search tasks or projects");search.setSaveEnabled(false);add(controls,search);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();listY=0;buildListRows();}public void afterTextChanged(Editable e){}});}
  listCount=label("",12,muted);add(controls,listCount);
  buildListRows();final ScrollView scroll=listScroll;final int savedY=listY;scroll.post(()->scroll.scrollTo(0,savedY));
 }
 void projectMenu(){
  ArrayList<String> options=new ArrayList<>();options.add("All projects");options.addAll(PresentationPolicy.projectOptions(tasks,filter,LocalDate.now()));String[] items=options.toArray(new String[0]);
  dialogBuilder().setTitle("Project").setSingleChoiceItems(items,projectFilter.isEmpty()?0:options.indexOf(projectFilter),(d,w)->{captureDraft();projectFilter=w==0?"":items[w];listY=0;d.dismiss();render();}).setNegativeButton("Cancel",null).show();
 }
 void sortMenu(){String[] items={"Project","Priority","Due date"};dialogBuilder().setTitle("Sort tasks").setSingleChoiceItems(items,Arrays.asList(items).indexOf(sortOrder),(d,w)->{captureDraft();sortOrder=items[w];listY=0;d.dismiss();render();}).setNegativeButton("Cancel",null).show();}
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
   String id=text(t,"id");LinearLayout row=vertical();row.setPadding(dp(12),dp(5),dp(12),dp(5));row.setBackground(ui.touch(selected.equals(id)?ui.soft:card,selected.equals(id)?accent:0,10));row.setMinimumHeight(dp(68));row.setClickable(true);row.setFocusable(true);row.setSelected(selected.equals(id));row.setOnClickListener(v->navigate(id));
   TextView taskTitle=heading(CompactPolicy.title(t),16);taskTitle.setMaxLines(2);taskTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);if(Build.VERSION.SDK_INT>=28)taskTitle.setAccessibilityHeading(false);add(row,taskTitle);
   JSONObject n=t.optJSONObject("notification");boolean isUnread=n!=null&&n.optBoolean("unread");String dueValue=text(t,"due_date").isEmpty()?"":dueLabel(t);String secondary=CompactPolicy.detail(t,sortOrder.equals("Project"),dueValue);if(!secondary.isEmpty())add(row,label(secondary,12,muted));
   row.setContentDescription(text(t,"title")+", "+human(text(t,"priority"))+" priority, "+human(text(t,"status"))+", "+PresentationPolicy.project(t)+", "+dueLabel(t)+(isUnread?", unread update":"")+(selected.equals(id)?", selected":""));row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);add(listItems,row);space(listItems,4);
  }
  if(shown.isEmpty()){space(listItems,32);add(listItems,heading(account==null?"Your workspace awaits":"No tasks in this view",22));add(listItems,label(account==null?"Open Settings to securely connect your task server. Your tasks will appear here.":"Try another view, clear your search, or choose a different project.",15,muted));if(!query.isEmpty()||!projectFilter.isEmpty())add(listItems,button("Clear filters",()->{captureDraft();query="";projectFilter="";render();}));}
  space(listItems,24);
 }
 JSONObject task(String id){for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
 JSONObject copy(JSONObject t){try{return new JSONObject(t.toString());}catch(Exception e){return new JSONObject();}}
 JSONObject empty(){JSONObject o=new JSONObject();try{o.put("title","").put("description","").put("notes","").put("project","").put("due_date","").put("priority","medium").put("status","todo");}catch(Exception ignored){}return o;}
 void navigate(String id){
  if(id.equals(selected))return;if(busy){notice("Please wait","A request is still in progress.");return;}captureDraft();
  Runnable go=()->{selected=id;draft=null;detailY=0;detailsMode="Details";editing=id.equals("NEW");render();if(!id.isEmpty()&&ui.motionEnabled()){detailPane.setAlpha(.65f);detailPane.setTranslationY(dp(8));detailPane.animate().alpha(1).translationY(0).setDuration(200).start();}};
  if(dirty())confirm("Discard unsaved changes?","Unsaved task fields will be lost. Conversation drafts remain saved per thread.",go);else go.run();
 }
 boolean dirty(){if(draft==null)return false;JSONObject old=selected.equals("NEW")?empty():task(selected);if(old==null)return true;for(String f:new String[]{"title","description","notes","project","due_date","priority","status"})if(!text(draft,f).equals(text(old,f)))return true;return false;}
 void captureDraft(){if(draft!=null&&title!=null){try{draft.put("title",title.getText().toString()).put("description",description.getText().toString()).put("notes",notes.getText().toString()).put("project",project.getText().toString()).put("due_date",due.getText().toString()).put("priority",priorities[priority.getSelectedItemPosition()]).put("status",statuses[status.getSelectedItemPosition()]);}catch(Exception ignored){}}if(conversationUi!=null)conversationUi.capture();if(conversations!=null)conversations.save();if(listScroll!=null)listY=listScroll.getScrollY();if(detailScroll!=null)detailY=detailScroll.getScrollY();}
 EditText field(LinearLayout parent,String name,String value,boolean multi){
  TextView caption=label(name,13,muted);add(parent,caption);EditText e=new EditText(this);e.setId(View.generateViewId());caption.setLabelFor(e.getId());e.setText(value);ui.input(e);e.setSingleLine(!multi);e.setMinLines(multi?3:1);e.setGravity(Gravity.TOP);e.setContentDescription(name);e.setSelectAllOnFocus(false);e.setEnabled(online&&!busy);e.setSaveEnabled(false);add(parent,e);space(parent,12);return e;
 }
 Spinner choice(LinearLayout parent,String name,String[] options,String value){
  TextView caption=label(name,13,muted);add(parent,caption);Spinner s=new Spinner(this);s.setId(View.generateViewId());caption.setLabelFor(s.getId());
  String[] names=new String[options.length];for(int i=0;i<options.length;i++)names[i]=human(options[i]);
  ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names){@Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convert,parent);v.setTextColor(ink);v.setTypeface(ui.font);v.setTextSize(16);return v;}@Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convert,parent);v.setTextColor(ink);v.setBackgroundColor(card);v.setMinHeight(dp(48));return v;}};
  s.setAdapter(a);for(int i=0;i<options.length;i++)if(options[i].equals(value))s.setSelection(i);s.setMinimumHeight(dp(48));s.setBackground(surface(card,line));s.setEnabled(online&&!busy);s.setContentDescription(name);add(parent,s);space(parent,12);return s;
 }
 void buildDetails(){
  if(selected.isEmpty()){space(detailPane,64);add(detailPane,heading("Select a task",24));add(detailPane,label("Keep your task list in view while you review details, make a plan, or work with Hermes.",16,muted));return;}
  LinearLayout top=horizontal();if(!wide)top.addView(ui.icon(R.drawable.ic_back,"Back to tasks",()->navigate("")),new LinearLayout.LayoutParams(dp(48),dp(48)));
  TextView h=heading(selected.equals("NEW")?"New task":"Task workspace",20);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton reload=ui.icon(R.drawable.ic_refresh,"Reload latest task",()->confirm("Reload latest task?","This discards unsaved fields and fetches the current server version. Your Hermes conversation is kept.",()->{draft=null;refresh();}));reload.setEnabled(account!=null&&!busy);top.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));add(detailPane,top);space(detailPane,8);
  if(detailsMode.equals("Hermès")&&!conversations.state.taskSelection(selected).isEmpty())chatUtilityChrome.add(top);
  JSONObject current=task(selected);if(draft==null)draft=selected.equals("NEW")?empty():current==null?null:copy(current);
  if(draft==null){add(detailPane,label("This task is not in your cache. Refresh to load it.",16,muted));return;}
  if(!selected.equals("NEW")&&!draft.optBoolean("deleted")){
   LinearLayout modes=horizontal();for(String mode:new String[]{"Details","Hermès"}){Button tab=button(mode,()->{captureDraft();detailsMode=mode;detailY=0;render();});ui.selected(tab,detailsMode.equals(mode));modes.addView(tab,new LinearLayout.LayoutParams(0,-2,1));}add(detailPane,modes);if(detailsMode.equals("Hermès")&&!conversations.state.taskSelection(selected).isEmpty())chatUtilityChrome.add(modes);
  }
  if(!selected.equals("NEW")&&!draft.optBoolean("deleted")&&detailsMode.equals("Hermès")){conversationUi=new ConversationsUi(this,conversations);conversationUi.buildTask(detailPane,selected);return;}
  detailScroll=new ScrollView(this);detailScroll.setFillViewport(true);editor=vertical();editor.setPadding(0,0,0,dp(28));detailScroll.addView(editor);detailPane.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
  JSONObject n=draft.optJSONObject("notification");if(n!=null&&n.optBoolean("unread")){
   LinearLayout update=vertical();update.setPadding(dp(16),dp(12),dp(16),dp(16));update.setBackground(surface(ui.soft,line));add(update,heading("Unread update",14));add(update,heading(n.optString("headline","Task updated"),18));TextView summary=label("",15,ink);summary.setText(MarkdownText.render(NotificationProse.markdown(n)));summary.setTextIsSelectable(true);add(update,summary);space(update,8);
   Button review=button("Review update",()->{summaryReviewed="";pendingSummary=selected;maybeSummary();});add(update,review);add(editor,update);space(editor,16);
  }
  if(draft.optBoolean("deleted")){add(editor,heading("In trash",14));add(editor,heading(text(draft,"title"),24));add(editor,label(text(draft,"description"),16,muted));space(editor,16);Button restore=primary("Restore task",()->mutation("POST","/api/tasks/"+Api.segment(selected)+"/restore",revision(),false));restore.setEnabled(online&&!busy);add(editor,restore);return;}
  if(editing||selected.equals("NEW"))buildTaskEditor();else buildTaskReading();
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
 void pickDate(){LocalDate date=PresentationPolicy.parseDate(due.getText().toString());if(date==null)date=LocalDate.now();long generation=surfaces.generation();Dialog picker=new DatePickerDialog(this,(view,y,m,d)->{if(surfaces.current(generation)&&authState==AuthPolicy.State.FULL&&due!=null)due.setText(LocalDate.of(y,m+1,d).toString());},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());trackDialog(picker).show();}
 JSONObject revision(){JSONObject o=new JSONObject();try{o.put("revision",text(draft,"revision"));}catch(Exception ignored){}return o;}
 void save(boolean complete){captureDraft();try{
  if(text(draft,"title").trim().isEmpty())throw new IllegalArgumentException("Title is required");String date=text(draft,"due_date").trim();if(!date.isEmpty()){LocalDate.parse(date);if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("Use YYYY-MM-DD");}
  JSONObject body=new JSONObject();for(String f:new String[]{"title","description","notes","priority","status"})body.put(f,text(draft,f));body.put("project",text(draft,"project").trim().isEmpty()?JSONObject.NULL:text(draft,"project").trim());body.put("due_date",date.isEmpty()?JSONObject.NULL:date);if(complete)body.put("status","completed");boolean create=selected.equals("NEW");if(!create)body.put("revision",text(draft,"revision"));mutation(create?"POST":"PATCH",create?"/api/tasks":"/api/tasks/"+Api.segment(selected),body,true);
 }catch(Exception e){notice("Check task fields",e instanceof java.time.format.DateTimeParseException?"Use a valid date in YYYY-MM-DD format.":e.getMessage());}}
 void mutation(String method,String path,JSONObject body,boolean clearDraft){
  if(!online||busy||account==null)return;captureDraft();busy=true;banner="Saving…";Store.Account a=account;render();
  Api.IO.execute(()->{try{JSONObject result=new Api(a).call(method,path,body);String id=result.getString("id");JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);main.post(()->{if(!alive(a))return;busy=false;online=true;ackNeedsRefresh=false;snapshot=latest;tasks=latest.optJSONArray("tasks");selected=id;draft=copy(result);editing=false;detailsMode="Details";banner="Saved · up to date";render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;if(e instanceof Api.Failure&&((Api.Failure)e).code==409){banner="Conflict · draft preserved — reload latest before saving";render();notice("Task changed", "Your draft is preserved. Copy any changes you need, then choose Reload latest. Nothing was overwritten.");}else{online=false;banner=Api.friendly(e)+" A write may already have reached the server; refresh and check before retrying.";render();}});}});
 }
 void markRead(String id,String token){
  if(account==null||!online||busy||ackNeedsRefresh)return;captureDraft();Store.Account a=account;busy=true;banner="Marking displayed update read…";if(summaryDialog!=null)summaryDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);render();
  Api.IO.execute(()->{try{
   JSONObject t=new Api(a).call("POST","/api/tasks/"+Api.segment(id)+"/updates/read",new JSONObject().put("token",token));
   main.post(()->{if(!alive(a))return;busy=false;try{if(draft!=null&&id.equals(selected))draft.put("notification",t.getJSONObject("notification"));for(int i=0;i<tasks.length();i++){JSONObject item=tasks.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))tasks.put(i,t);}}catch(Exception ignored){}if(summaryDialog!=null){summaryDialog.dismiss();summaryDialog=null;}((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(id,0);banner="Displayed update marked read";render();});
  }catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;ackNeedsRefresh=true;banner="Update not acknowledged · refresh for current summary";if(summaryDialog!=null){Button action=summaryDialog.getButton(AlertDialog.BUTTON_POSITIVE);action.setText("Refresh summary before retry");action.setEnabled(true);}render();int code=e instanceof Api.Failure?((Api.Failure)e).code:0;notice(code==409?"A newer update is waiting":"Update not acknowledged",PresentationPolicy.ackFailure(code)+" Review a fresh summary before another acknowledgement.");});}});
 }
 void refresh(){
  if(authState!=AuthPolicy.State.FULL)return;if(account==null){settings();return;}if(busy)return;captureDraft();final boolean keepDraft=dirty();Store.Account a=account;busy=true;banner="Refreshing…";renderKeepingConversation();
  Api.IO.execute(()->{try{JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);String notificationNote="";try{PollService.poll(this,store,a,()->isDestroyed());}catch(Exception e){notificationNote=" · update checks delayed";}final String suffix=notificationNote;main.post(()->{if(!alive(a))return;busy=false;online=true;ackNeedsRefresh=false;snapshot=latest;tasks=latest.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();if(!keepDraft)draft=null;else if(draft!=null){JSONObject remote=task(selected);if(remote!=null)try{draft.put("notification",remote.optJSONObject("notification"));}catch(Exception ignored){}}banner="Connected · "+tasks.length()+" tasks"+suffix;renderKeepingConversation();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;online=false;banner=Api.friendly(e);renderKeepingConversation();});}});
 }
 void renderKeepingConversation(){if(conversationUi!=null){if(stateLabel!=null)stateLabel.setText(banner);buildListRows();conversationUi.changed();}else render();}
 void voice(){
  if(conversationUi==null||conversationUi.voiceThread().isEmpty()||account==null)return;
  final String target=conversationUi.voiceThread(),epoch=account.epoch;
  noticeConfirm("Platform speech recognition","Your device's selected recognition app may send audio to its provider. My Tasks does not record audio. Recognized text stays editable and is never sent automatically.",()->{
   if(conversationUi==null||!ConversationState.canApplyVoice(epoch,target,account==null?"":account.epoch,conversationUi.voiceThread()))return;
   voiceEpoch=epoch;voiceThread=target;
   Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Dictate a message for Hermès");
   try{startActivityForResult(intent,108);}catch(ActivityNotFoundException|SecurityException e){notice("No speech recognizer","Enable a platform speech recognition app, or type instead.");}
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==UpdateManager.PERMISSION_REQUEST||request==UpdateManager.INSTALL_REQUEST){UpdateManager.get(this).result(request);return;}
  if(request==108&&result==RESULT_OK&&data!=null){ArrayList<String> values=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty()){
   if(conversationUi!=null&&ConversationState.canApplyVoice(voiceEpoch,voiceThread,account==null?"":account.epoch,conversationUi.voiceThread())){conversationUi.applyVoice(values.get(0));notice("Review voice input","Edit the transcript before tapping Send. It has not been sent.");}
   else notice("Voice input not applied","The account or target conversation changed. Dictate again in the intended thread.");
  }}
  if(request==108){voiceEpoch="";voiceThread="";}
 }
 void checkSession(){
  if(account==null){authState=AuthPolicy.State.LOGIN;authMessage="Sign in with your username and password. Existing encrypted credentials are used automatically for owner activation.";render();return;}
  final Store.Account a=account;final long generation=surfaces.generation();authState=AuthPolicy.State.CHECKING;render();Api.IO.execute(()->{try{JSONObject session=Api.auth(a.server).session(a.token);main.post(()->{if(isDestroyed()||!surfaces.current(generation)||!store.current(a))return;acceptSession(a,session);});}catch(Exception e){main.post(()->{if(isDestroyed()||!surfaces.current(generation)||!store.current(a))return;authBusy=AuthUi.inFlight;authState=AuthPolicy.State.LOGIN;authMessage=Api.friendly(e);render();});}});
 }
 void acceptSession(Store.Account a,JSONObject session){
  account=a;authState=AuthPolicy.state(session,System.currentTimeMillis()/1000);online=false;busy=false;authBusy=AuthUi.inFlight;authServer=a.server;sessionExpires=session.optLong("expires_at",0);main.removeCallbacks(expireSession);if(sessionExpires>0&&(authState==AuthPolicy.State.FULL||authState==AuthPolicy.State.CHANGE))main.postDelayed(expireSession,Math.max(1,sessionExpires*1000-System.currentTimeMillis()+1000));
  if(authState!=AuthPolicy.State.FULL){authMessage=authState==AuthPolicy.State.CHANGE?"Owner verified. Replace the initial password before accessing private data.":"Your session is no longer active. Sign in again.";render();return;}
  if(conversations!=null)conversations.close();conversations=new ConversationController(store,a);conversations.foreground=true;
  JSONObject cached=store.cached();if(cached!=null){snapshot=cached;tasks=cached.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();banner="Offline cache · read only — refresh to edit";}
  if(draft!=null){JSONObject base=task(selected);if(base!=null){JSONObject merged=copy(base);Iterator<String> keys=draft.keys();while(keys.hasNext()){String k=keys.next();try{merged.put(k,draft.get(k));}catch(Exception ignored){}}draft=merged;}}
  render();refresh();
 }
 void blockSession(int status){
  dismissPrivateDialogs();try{account=store.invalidateWork(account);}catch(Exception e){account=null;}if(conversations!=null)conversations.close();conversationUi=null;conversations=new ConversationController(store,null);if(root!=null)root.removeAllViews();listPane=detailPane=editor=listItems=null;listScroll=detailScroll=null;summaryTask=summaryToken=pendingSummary=summaryReviewed="";((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
  snapshot=new JSONObject();tasks=new JSONArray();draft=null;selected="";voiceEpoch=voiceThread="";online=busy=false;destination="Tasks";authState=status==428?AuthPolicy.State.CHANGE:AuthPolicy.State.LOGIN;authMessage=status==428?"Your password must be changed before continuing.":"Your session expired or was revoked. Sign in again; no request is automatically resent.";render();
 }
 void beginPasswordChange(){if(authBusy||busy)return;captureDraft();dismissPrivateDialogs();if(conversations!=null)conversations.close();conversations=new ConversationController(store,null);conversationUi=null;authState=AuthPolicy.State.CHANGE;authMessage="Enter your current password and choose a new private password.";render();}
 void maybeSummary(){
  if(authState!=AuthPolicy.State.FULL||selected.isEmpty()||busy||summaryDialog!=null)return;JSONObject t=task(selected);JSONObject n=t==null?null:t.optJSONObject("notification");if(n==null||!n.optBoolean("unread")){if(t!=null)pendingSummary="";return;}
  String token=n.optString("token","");if(!pendingSummary.equals(selected)&&summaryReviewed.equals(selected+":"+token))return;
  final String id=selected;final boolean needsFresh=!online||ackNeedsRefresh;summaryReviewed=id+":"+token;pendingSummary="";summaryTask=id;summaryToken=token;
  LinearLayout panel=vertical();panel.setPadding(dp(20),dp(16),dp(20),dp(20));panel.setBackground(ui.glass());add(panel,heading("Task update",14));add(panel,heading(t.optString("title","Task updated"),22));add(panel,label(n.optString("headline",""),14,muted));TextView prose=label("",16,ink);prose.setText(MarkdownText.render(NotificationProse.markdown(n)));prose.setTextIsSelectable(true);prose.setAutoLinkMask(0);add(panel,prose);space(panel,16);add(panel,label("Only the exact displayed update can be marked read. Source links are kept separately in task details.",12,muted));
  if(!online)add(panel,label("Cached summary · refresh before marking read.",13,muted));ScrollView scroll=new ScrollView(this);scroll.addView(panel);AlertDialog dialog=dialogBuilder().setView(scroll).setNegativeButton("Keep unread / open task",(d,w)->{}).setPositiveButton(needsFresh?"Refresh summary before retry":"Mark displayed update read",null).create();summaryDialog=dialog;
  dialog.setOnDismissListener(d->{forgetDialog(dialog);if(summaryDialog==dialog)summaryDialog=null;});dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!token.isEmpty()&&!busy);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(needsFresh||ackNeedsRefresh){dialog.dismiss();summaryDialog=null;pendingSummary=id;draft=null;refresh();}else markRead(id,token);});if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(ui.shape(card,line,24));dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(620)),-2);}if(ui.motionEnabled()){panel.setAlpha(.7f);panel.setTranslationY(dp(12));panel.animate().alpha(1).translationY(0).setDuration(220).start();}});dialog.show();
 }
 void updateNotificationState(){
  if(notificationState==null)return;NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel c=manager.getNotificationChannel(PollService.CHANNEL);String state=Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED?"Notification permission required":!manager.areNotificationsEnabled()?"App notifications are disabled":c!=null&&c.getImportance()==NotificationManager.IMPORTANCE_NONE?"Task update channel is disabled":"Allowed by Android settings";notificationState.setText(state);
 }
 void settings(){
  captureDraft();LinearLayout form=vertical();form.setPadding(dp(20),dp(8),dp(20),dp(20));
  add(form,heading("Appearance",18));add(form,label("Choose the theme for this workspace.",13,muted));
  String currentTheme=getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","System");LinearLayout themes=horizontal();
  AlertDialog[] holder=new AlertDialog[1];for(String theme:new String[]{"System","Light","Dark"}){Button b=button(theme,()->{getSharedPreferences("appearance",MODE_PRIVATE).edit().putString("theme",theme).apply();holder[0].dismiss();applyAppearance();render();settings();});ui.selected(b,currentTheme.equals(theme));themes.addView(b,new LinearLayout.LayoutParams(0,-2,1));}add(form,themes);space(form,24);rule(form);space(form,16);
  for(String key:new String[]{"reduce_motion","reduce_transparency"}){Switch toggle=new Switch(this);toggle.setText(key.equals("reduce_motion")?"Reduce motion":"Reduce transparency");toggle.setTypeface(ui.font);toggle.setTextColor(ink);toggle.setMinHeight(dp(48));toggle.setChecked(getSharedPreferences("appearance",MODE_PRIVATE).getBoolean(key,false));toggle.setOnCheckedChangeListener((v,checked)->{getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean(key,checked).apply();holder[0].dismiss();render();settings();});add(form,toggle);}
  add(form,heading("App & updates",18));add(form,label("My Tasks · Version "+UpdateManager.get(this).installedName(),14,ink));
  add(form,label("Public release checks are independent of your task server. Automatic checks run on the first foreground launch each local day. Manual checks need internet, but no task account.",13,muted));
  add(form,button("Check for updates",()->UpdateManager.get(this).check(true)));space(form,24);rule(form);space(form,16);
  add(form,heading("Task notifications",18));
  boolean permission=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
  notificationState=label("",14,ink);add(form,notificationState);updateNotificationState();
  add(form,label("Best-effort checks are requested at least 15 minutes apart, not instant push. Android may delay them much longer in Doze, battery saver, force-stop, or offline. Refresh checks now. First connection quietly establishes a baseline without old-event alerts.",13,muted));
  add(form,button(permission?"Manage notification settings":"Allow notifications",()->{if(Build.VERSION.SDK_INT>=33&&!permission){holder[0].dismiss();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);}else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));}));space(form,24);rule(form);space(form,16);
  add(form,heading("Connection & privacy",18));add(form,label("Sign in with username and password. Only the issued session is encrypted with the existing Android Keystore key; passwords are never saved. Custom HTTPS servers are preserved.",13,muted));
  add(form,label("Task server · "+store.server(),14,ink));
  if(account!=null){add(form,button("Sign out / change server",()->{holder[0].dismiss();disconnectAccount();}));if(authState==AuthPolicy.State.FULL)add(form,button("Change password",()->{holder[0].dismiss();beginPasswordChange();}));}
  else add(form,button("Sign in",()->holder[0].dismiss()));
  space(form,12);add(form,label("Voice input uses your device’s chosen recognizer, which may send audio to its provider. Transcripts are always reviewed before sending. Task-linked Hermes drafts never send email.",13,muted));
  add(form,button("Inter font · OFL license",()->{try(java.io.InputStream in=getResources().openRawResource(R.raw.inter_license);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);notice("Inter · SIL Open Font License",out.toString("UTF-8"));}catch(Exception e){notice("Font license","Inter is distributed under the SIL Open Font License 1.1. License is bundled in this APK.");}}));
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  AlertDialog dialog=dialogBuilder().setTitle("Settings").setView(scroll).setNegativeButton("Done",null).create();holder[0]=dialog;dialog.show();
 }
 void disconnectAccount(){
  if(busy||authBusy){notice("Please wait","Finish the current request before signing out.");return;}
  confirm("Sign out of this account?","Revoke this session and clear local credentials, tasks and conversation drafts. Server history remains saved; accepted requests continue and are not resent.",()->{
   Store.Account previous=account;blockSession(401);try{store.beginLogout();}catch(Exception e){notice("Local account gate","Could not persist sign-out state. Keep the app open until sign-out finishes.");}busy=true;authBusy=true;render();
   Api.IO.execute(()->{String note="Signed out. Server history remains saved.";try{if(previous!=null){try{AuthClient auth=Api.auth(previous.server);auth.logout(previous.token);if(auth.session(previous.token).optBoolean("authenticated"))note="Local sign-out complete, but server revocation was not confirmed.";}catch(Exception e){note="Local sign-out complete. Server revocation could not be confirmed; the remote session may remain until expiry.";}}store.disconnect();final String message=note;main.post(()->{if(isDestroyed())return;if(summaryDialog!=null){summaryDialog.dismiss();summaryDialog=null;}conversationUi=null;account=null;conversations=new ConversationController(store,null);conversations.foreground=true;voiceEpoch=voiceThread="";snapshot=new JSONObject();tasks=new JSONArray();selected="";draft=null;editing=false;detailsMode="Details";projectFilter=query="";filter="Active";listY=detailY=0;restoreComposerThread="";online=busy=authBusy=false;destination="Tasks";authState=AuthPolicy.State.LOGIN;authMessage=message;summaryReviewed=pendingSummary="";render();});}catch(Exception e){main.post(()->{if(isDestroyed())return;busy=authBusy=false;notice("Local storage error","Could not finish clearing this account. Retry sign-out.");});}});
  });
 }
 Dialog trackDialog(Dialog dialog){Runnable close=dialog::dismiss;dialogClosers.put(dialog,close);surfaces.register(close);dialog.setOnDismissListener(d->forgetDialog(dialog));return dialog;}
 void forgetDialog(Dialog dialog){Runnable close=dialogClosers.remove(dialog);if(close!=null)surfaces.forget(close);}
 void dismissPrivateDialogs(){surfaces.invalidate();dialogClosers.clear();summaryDialog=null;}
 AlertDialog.Builder dialogBuilder(){return new AlertDialog.Builder(this){@Override public AlertDialog create(){AlertDialog dialog=super.create();trackDialog(dialog);if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(ui.shape(card,line,24));return dialog;}};}
 void fullMessage(String content){if(authState!=AuthPolicy.State.FULL)return;TextView text=label(content,16,ink);text.setTextIsSelectable(true);text.setAutoLinkMask(0);text.setPadding(dp(20),dp(16),dp(20),dp(16));ScrollView scroll=new ScrollView(this);scroll.addView(text);dialogBuilder().setTitle("Full saved message · plain text").setView(scroll).setPositiveButton("Done",null).show();}
 void notice(String title,String message){if(isDestroyed())return;dialogBuilder().setTitle(title).setMessage(message==null?"Please retry.":message).setPositiveButton("OK",null).show();}
 void confirm(String title,String message,Runnable yes){long generation=surfaces.generation();dialogBuilder().setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{if(surfaces.current(generation)&&!isDestroyed())yes.run();}).show();}
 void noticeConfirm(String title,String message,Runnable yes){confirm(title,message,yes);}
 @Override public void onBackPressed(){if(destination.equals("Discussions")&&!conversations.state.selected().isEmpty()){conversations.state.select("");conversations.hide();render();}else if(!destination.equals("Tasks")){destination="Tasks";render();}else if(!selected.isEmpty()){navigate("");}else super.onBackPressed();}
}

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
 Store store;Store.Account account;EmailReplyController email;EmailReplyUi emailUi;ComposerWindowObserver composerWindow;final java.util.List<android.graphics.Rect> composerOcclusions=new ArrayList<>();
 final PrivateSurfaces surfaces=new PrivateSurfaces();final Map<Dialog,Runnable> dialogClosers=new IdentityHashMap<>();
 AuthPolicy.State authState=AuthPolicy.State.CHECKING;boolean authBusy=false;String authMessage="Verifying the saved session before loading private data.",authServer="";long sessionExpires;
 final Runnable expireSession=()->{if((authState==AuthPolicy.State.FULL||authState==AuthPolicy.State.CHANGE)&&sessionExpires>0&&System.currentTimeMillis()/1000>=sessionExpires)blockSession(401);};
 Api.AuthObserver authObserver;Dialog summaryDialog;UpdateSheet summaryUi;String summaryTask="",summaryToken="",pendingSummary="";boolean ackNeedsRefresh=false;String summaryReviewed="";
 JSONObject snapshot=new JSONObject();JSONArray tasks=new JSONArray();
 JSONObject draft;String selected="",filter="Active",query="";
 boolean online=false,busy=false,wide=false,restoring=false;
 boolean railNavigation,shellRebuildQueued;int shellMasterWidth=300,shellRailWidth=184,shellInsetLeft,shellInsetTop,shellInsetRight,shellInsetBottom;ShellLayoutPolicy.Plan shellLayout;
 String banner="Connect your server in Settings";
 WorkspaceUi ui;TasksUi tasksUi;FrameLayout rootFrame,navigationSurface;float navigationOrigin=Float.NaN,discussionScopeOrigin=Float.NaN;String projectFilter="",sortOrder="Smart",colorMode="Project",detailsMode="Details";boolean sortReverse=false,editing=false;
 boolean foreground,quietInFlight;final UiReadGate quietGate=new UiReadGate();
 final Runnable foregroundRefresh=new Runnable(){public void run(){if(!foreground)return;quietRefresh();main.postDelayed(this,30000);}};
 ConversationController conversations;ConversationsUi conversationUi;String destination="Tasks",voiceEpoch="",voiceThread="";LinearLayout appBar,bottomTabs;boolean showSearch=false;
 final ArrayList<View> chatUtilityChrome=new ArrayList<>();String restoreComposerThread="";int restoreComposerPosition;
 final Map<String,Button> viewButtons=new LinkedHashMap<>();TextView listCount,notificationState;
 int bg,card,ink,muted,line,accent;int listY,detailY;
 LinearLayout root,listPane,detailPane,listItems,editor;TextView stateLabel;
 Runnable taskEditorNoticeUpdate;
 final TaskDescriptionText.State descriptionState=new TaskDescriptionText.State();String descriptionSelection="";
 ScrollView listScroll,detailScroll;EditText title,description,notes,due,project;Spinner priority,status;
 String[] priorities={"high","medium","low"},statuses={"inbox","todo","in_progress","waiting","completed","cancelled"};
 JSONObject taskPresentation=new JSONObject(),taskPresentationInitial=new JSONObject();String taskEditorEpoch="",taskEditorTask="";int taskPresentationGeneration;boolean taskPresentationPending;
 ScrollView pendingDetailScroll;Store.Account pendingDetailOwner;String pendingDetailTask="";int pendingDetailInitialY,pendingDetailSavedY,pendingDetailGeneration;
 final Handler main=new Handler(Looper.getMainLooper());
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 String text(JSONObject o,String key){return o==null||o.isNull(key)?"":o.optString(key,"");}
 @Override public void onCreate(Bundle saved){
  boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  setTheme(dark?R.style.MyTasks_Dark:R.style.MyTasks_Light);super.onCreate(saved);
  store=new Store(this);applyAppearance();
  if(saved!=null){projectFilter=saved.getString("projectFilter","");sortOrder=PresentationPolicy.savedSort(saved.getString("sortOrder",null));sortReverse=saved.getBoolean("sortReverse",false);colorMode=saved.getString("colorMode","Project");detailsMode=saved.getString("detailsMode","Details");editing=saved.getBoolean("editing");}
  if(saved!=null){selected=saved.getString("selected","");filter=saved.getString("filter","Active");query=saved.getString("query","");listY=saved.getInt("listY");detailY=saved.getInt("detailY");try{if(saved.containsKey("draft"))draft=new JSONObject(saved.getString("draft"));}catch(Exception ignored){}}
  try{account=store.account();}catch(Exception e){banner="Credentials unavailable. Re-enter Settings.";}
  conversations=new ConversationController(store,null);if(saved!=null){destination=saved.getString("destination","Tasks");voiceEpoch=saved.getString("voiceEpoch","");voiceThread=saved.getString("voiceThread","");showSearch=saved.getBoolean("showSearch");}
  if(saved!=null&&(account==null||!account.epoch.equals(saved.getString("epoch","")))){selected="";draft=null;query="";projectFilter="";voiceEpoch="";voiceThread="";restoreComposerThread="";}
  authObserver=(a,code)->main.post(()->{if(isDestroyed())return;if(code==0){authBusy=false;checkSession();return;}if(!store.current(a))return;if(code==200){authBusy=false;account=a;checkSession();}else if(authState==AuthPolicy.State.FULL)blockSession(code);});Api.authObserver=authObserver;
  if(draft!=null){JSONObject base=task(selected);if(base!=null){JSONObject merged=copy(base);Iterator<String> keys=draft.keys();while(keys.hasNext()){String k=keys.next();try{merged.put(k,draft.get(k));}catch(Exception ignored){}}draft=merged;}}
  tasksUi=new TasksUi(this);if(saved!=null&&account!=null&&account.epoch.equals(saved.getString("epoch","")))tasksUi.restore(saved);
  loadTaskPresentation(saved);
  if(saved==null&&account!=null&&account.epoch.equals(store.prefs.getString("ui_epoch",""))){filter=store.prefs.getString("ui_filter","Active");projectFilter=store.prefs.getString("ui_project","");sortOrder=PresentationPolicy.savedSort(store.prefs.getString("ui_sort",null));sortReverse=store.prefs.getBoolean("ui_sort_reverse",false);colorMode=store.prefs.getString("ui_color_mode","Project");}
  if(saved==null)acceptIntent(getIntent());render();PollService.schedule(this);checkSession();
 }
 @Override protected void onResume(){super.onResume();foreground=true;if(composerWindow==null)composerWindow=new ComposerWindowObserver(this,bounds->{composerOcclusions.clear();composerOcclusions.addAll(bounds);if(emailUi!=null)emailUi.fitPane();else if(root!=null)root.requestApplyInsets();});composerOcclusions.clear();composerWindow.start();if(root!=null)root.requestApplyInsets();if(emailUi!=null){emailUi.fitPane();emailUi.resume();}if(tasksUi!=null)tasksUi.resume();expireSession.run();UpdateManager.get(this).attach(this);updateNotificationState();if(conversations!=null)conversations.resume();main.removeCallbacks(foregroundRefresh);main.postDelayed(foregroundRefresh,30000);}
 @Override protected void onPause(){if(composerWindow!=null)composerWindow.stop();if(emailUi!=null){emailUi.capture();email.persist();}foreground=false;if(ui!=null){ui.finishPageMotion(root);ui.finishPageMotion(listPane);ui.finishPageMotion(detailPane);}main.removeCallbacks(foregroundRefresh);invalidateQuietRead();captureDraft();if(tasksUi!=null)tasksUi.pause();if(conversations!=null)conversations.pause();UpdateManager.get(this).detach(this);super.onPause();}
 @Override protected void onDestroy(){if(composerWindow!=null)composerWindow.stop();if(emailUi!=null)emailUi.detach();if(email!=null)email.invalidate();main.removeCallbacks(expireSession);main.removeCallbacks(foregroundRefresh);if(Api.authObserver==authObserver)Api.authObserver=null;dismissPrivateDialogs();if(tasksUi!=null)tasksUi.clear();if(conversations!=null)conversations.close();super.onDestroy();}
 void acceptIntent(Intent i){String id=i.getStringExtra("task_id");String epoch=i.getStringExtra("account_epoch");if(id!=null&&account!=null&&account.epoch.equals(epoch)){dismissPrivateDialogs();destination="Tasks";selected=id;pendingSummary=id;summaryReviewed="";draft=null;detailsMode="Details";editing=false;detailY=0;}}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(busy){notice("Task update","Finish the current request, then select the updated task from the list.");return;}captureDraft();Runnable open=()->{acceptIntent(i);setIntent(i);render();if(authState==AuthPolicy.State.FULL)refresh();else if(!authBusy)checkSession();};if(dirty())confirm("Open updated task?","Unsaved task fields will be discarded. Conversation drafts remain saved per thread.",open);else open.run();}
 void applyAppearance(){
  String preference=store.context.getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","System");
  boolean dark=preference.equals("Dark")||(preference.equals("System")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
  getTheme().applyStyle(dark?R.style.MyTasks_Dark:R.style.MyTasks_Light,true);
  ui=new WorkspaceUi(this,dark);bg=ui.canvas;card=ui.surface;ink=ui.ink;muted=ui.secondary;line=ui.border;accent=ui.primary;
  if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().setStatusBarContrastEnforced(false);}getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(emailUi!=null&&!emailUi.needsStyleRebuild(config)){emailUi.capture();wide=CompactPolicy.expanded(config.screenWidthDp,config.fontScale);emailUi.fitPane();root.requestApplyInsets();return;}captureDraft();UpdateSheet previous=summaryUi;if(previous!=null)previous.dialog.dismiss();applyAppearance();restoring=true;try{render();}finally{restoring=false;}if(previous!=null&&alive(previous.owner)){UpdateSheet replacement=new UpdateSheet(this,previous.shown);replacement.waiting=previous.waiting;replacement.failed=previous.failed;replacement.show();}}
 @Override protected void onSaveInstanceState(Bundle out){saveTaskPresentation(out);if(emailUi!=null){emailUi.capture();email.persist();}captureDraft();if(tasksUi!=null)tasksUi.save(out);conversations.save();out.putString("destination",destination);out.putString("voiceEpoch",voiceEpoch);out.putString("voiceThread",voiceThread);out.putBoolean("showSearch",showSearch);out.putString("projectFilter",projectFilter);out.putString("sortOrder",sortOrder);out.putBoolean("sortReverse",sortReverse);out.putString("colorMode",colorMode);out.putString("detailsMode",detailsMode);out.putBoolean("editing",editing);out.putString("epoch",account==null?"":account.epoch);out.putString("selected",selected);out.putString("filter",filter);out.putString("query",query);if(draft!=null)out.putString("draft",stateDraft().toString());out.putInt("listY",listScroll==null?listY:listScroll.getScrollY());out.putInt("detailY",capturedDetailY());super.onSaveInstanceState(out);}
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
  captureTaskPresentation();taskPresentationGeneration++;taskPresentationPending=false;taskEditorTask=taskEditorEpoch="";
  if((authState!=AuthPolicy.State.FULL&&authState!=AuthPolicy.State.CHECKING)||(!editing&&!selected.equals("NEW"))||(!taskPresentation.optString("task").isEmpty()&&!selected.equals(taskPresentation.optString("task"))))taskPresentation=new JSONObject();
  // A selected task may never render its description (empty, conversation, editor).
  if(!descriptionSelection.equals(selected)||selected.isEmpty()||authState!=AuthPolicy.State.FULL)descriptionState.bind("",null);
  descriptionSelection=selected;
  boolean restoreEmailKeyboard=emailUi!=null&&emailUi.keyboardVisible();
  if(emailUi!=null){emailUi.detach();emailUi=null;}
  if(tasksUi!=null)tasksUi.capture();
  if(conversationUi!=null)conversationUi.capture();
  if(conversations!=null){conversations.hide();conversations.listener=null;}conversationUi=null;
  chatUtilityChrome.clear();appBar=bottomTabs=null;
  applyShellPolicy(readShellPolicy(rootFrame));shellRebuildQueued=false;
  if(email!=null&&email.host.valid()&&email.state.data.optBoolean("active"))wide=CompactPolicy.expanded(getResources().getConfiguration().screenWidthDp,getResources().getConfiguration().fontScale);
  title=description=notes=due=project=null;priority=status=null;
  listScroll=detailScroll=null;listItems=null;listCount=null;viewButtons.clear();
  navigationSurface=null;root=vertical();root.setBackgroundColor(bg);root.setPadding(dp(16),0,dp(16),0);rootFrame=new FrameLayout(this);rootFrame.setBackgroundColor(bg);rootFrame.addView(root,new FrameLayout.LayoutParams(-1,-1));
  root.setOnApplyWindowInsetsListener((v,insets)->{if(v!=root)return insets;int top,bottom,left,right;boolean keyboard;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()|WindowInsets.Type.displayCutout());top=i.top;bottom=i.bottom;left=i.left;right=i.right;keyboard=insets.isVisible(WindowInsets.Type.ime());}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();keyboard=bottom>dp(120);}if(emailUi!=null)emailUi.keyboardChanged(keyboard);boolean utilities=CompactPolicy.secondaryChrome(keyboard,getResources().getConfiguration().screenHeightDp);shellInsetLeft=left;shellInsetTop=top;shellInsetRight=right;shellInsetBottom=bottom;if(emailUi!=null)v.setPadding(dp(wide?12:16)+left,top,dp(wide?12:16)+right,bottom);else updateShellGeometry(rootFrame);if(appBar!=null)appBar.setVisibility(utilities?View.VISIBLE:View.GONE);/* In-flow navigation receives the root insets once; it never covers rows. */if(conversationUi!=null&&conversationUi.input!=null){int available=root.getHeight()>0?Math.round((root.getHeight()-top-bottom)/getResources().getDisplayMetrics().density):getResources().getConfiguration().screenHeightDp;int maxLines=CompactPolicy.composerLines(available);if(conversationUi.input.getMaxLines()!=maxLines)conversationUi.input.setMaxLines(maxLines);}for(View chrome:chatUtilityChrome)chrome.setVisibility(utilities?View.VISIBLE:View.GONE);if(stateLabel!=null)stateLabel.setVisibility(utilities&&!destination.equals("Discussions")&&(!online||busy||banner.contains("preserved")||banner.contains("failed"))?View.VISIBLE:View.GONE);return insets;});
  rootFrame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(v==rootFrame&&((r-l)!=(or-ol)||(b-t)!=(ob-ot)))updateShellGeometry(v);});
  setContentView(rootFrame);root.requestApplyInsets();
  if(authState!=AuthPolicy.State.FULL){new AuthUi(this).build(root);return;}
  if(email!=null&&email.host.valid()&&email.state.data.optBoolean("active")){emailUi=new EmailReplyUi(this,email);emailUi.build(root);if(restoreEmailKeyboard)emailUi.restoreKeyboard();return;}
  stateLabel=label(banner,13,muted);stateLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);stateLabel.setVisibility(!online||busy||banner.contains("preserved")?View.VISIBLE:View.GONE);add(root,stateLabel);
  LinearLayout shell=horizontal();shell.setGravity(Gravity.TOP);root.addView(shell,new LinearLayout.LayoutParams(-1,0,1));if(railNavigation)buildRail(shell);LinearLayout workspace=vertical();shell.addView(workspace,new LinearLayout.LayoutParams(0,-1,1));
  if(destination.equals("Discussions")){conversationUi=new ConversationsUi(this,conversations);conversationUi.buildGlobal(workspace);buildPrimaryTabs();return;}
  LinearLayout body=horizontal();body.setGravity(Gravity.TOP);workspace.addView(body,new LinearLayout.LayoutParams(-1,0,1));listPane=vertical();detailPane=taskDetailPane();
  if(wide){body.addView(listPane,new LinearLayout.LayoutParams(dp(shellMasterWidth),-1));View divider=new View(this);divider.setBackgroundColor(line);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(1),-1);dl.setMargins(dp(12),0,dp(16),0);body.addView(divider,dl);body.addView(detailPane,new LinearLayout.LayoutParams(0,-1,1));}
  else body.addView(selected.isEmpty()?listPane:detailPane,new LinearLayout.LayoutParams(-1,-1));
  if(wide||selected.isEmpty())buildList();if(wide||!selected.isEmpty())buildDetails();
  buildPrimaryTabs();maybeSummary();
 }
 void applyShellPolicy(ShellLayoutPolicy.Plan plan){shellLayout=plan;railNavigation=plan.rail;wide=plan.dual;shellMasterWidth=plan.masterWidth;shellRailWidth=plan.railWidth;}
 ShellLayoutPolicy.Plan readShellPolicy(View host){
  int width=host==null?0:host.getWidth(),height=host==null?0:host.getHeight();int[] origin=new int[2];if(host!=null)host.getLocationInWindow(origin);
  if(width<=0||height<=0){if(Build.VERSION.SDK_INT>=30){android.graphics.Rect bounds=getWindowManager().getCurrentWindowMetrics().getBounds();width=bounds.width();height=bounds.height();}else{View decor=getWindow().getDecorView();width=decor.getWidth();height=decor.getHeight();}if(width<=0||height<=0){width=getResources().getDisplayMetrics().widthPixels;height=getResources().getDisplayMetrics().heightPixels;}}
  ArrayList<int[]> features=new ArrayList<>();for(android.graphics.Rect rect:composerOcclusions)features.add(new int[]{rect.left-origin[0],rect.top-origin[1],rect.right-origin[0],rect.bottom-origin[1]});
  return ShellLayoutPolicy.measure(width,height,getResources().getDisplayMetrics().density,getResources().getConfiguration().fontScale,shellInsetLeft,shellInsetTop,shellInsetRight,shellInsetBottom,features);
 }
 void updateShellGeometry(View host){
  // Email deliberately keeps the attached input and its own reported-hinge policy.
  if(host!=rootFrame||emailUi!=null||host.getWidth()<=0||host.getHeight()<=0)return;
  ShellLayoutPolicy.Plan next=readShellPolicy(host);boolean rebuild=!next.sameStructure(shellLayout);applyShellPolicy(next);
  root.setPadding(next.left+dp(16),next.top,Math.max(0,host.getWidth()-next.right)+dp(16),Math.max(0,host.getHeight()-next.bottom));
  if(rebuild&&authState==AuthPolicy.State.FULL&&!shellRebuildQueued){shellRebuildQueued=true;final FrameLayout target=rootFrame;final Store.Account owner=account;main.post(()->{if(rootFrame!=target)return;shellRebuildQueued=false;if(!alive(owner)||emailUi!=null)return;captureDraft();restoring=true;try{render();}finally{restoring=false;}});}
 }
 int navigationClearance(){return 0;}
 void buildPrimaryTabs(){
  if(!railNavigation&&CompactPolicy.globalNavigation(false,destination,selected,conversations.state.selected())){
   bottomTabs=horizontal();bottomTabs.setPadding(dp(4),dp(4),dp(4),dp(4));navigationSurface=new FrameLayout(this);navigationSurface.setBackgroundColor(ui.nav);
   String[] destinations={"Tasks","Discussions"};int[] icons={R.drawable.ic_tasks,R.drawable.ic_chat};
   for(int i=0;i<destinations.length;i++){final String tab=destinations[i];Button b=button(tab,()->{captureDraft();destination=tab;render();ui.enter(root,NativeMotionPolicy.duration("tab"));});ui.navigation(b,destination.equals(tab));ui.leadingIcon(b,icons[i]);bottomTabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
   navigationSurface.addView(bottomTabs,new FrameLayout.LayoutParams(-1,-2));root.addView(navigationSurface,new LinearLayout.LayoutParams(-1,-2));
  }
  root.requestApplyInsets();
 }
 void buildRail(LinearLayout shell){
  ScrollView material=new ScrollView(this);material.setFillViewport(true);material.setBackgroundColor(ui.nav);LinearLayout rail=vertical();rail.setPadding(dp(8),dp(8),dp(8),dp(8));material.addView(rail,new FrameLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(shellRailWidth),-1);rp.setMargins(0,0,dp(16),0);shell.addView(material,rp);
  String[] names={"Tasks","Discussions"};int[] icons={R.drawable.ic_tasks,R.drawable.ic_chat};for(int i=0;i<names.length;i++){final String name=names[i];Button b=button(name,()->{captureDraft();destination=name;render();});ui.navigation(b,name.equals(destination));ui.leadingIcon(b,icons[i]);rail.addView(b,new LinearLayout.LayoutParams(-1,-2));space(rail,8);}
  Button settings=button("Settings",this::settings);ui.navigation(settings,false);ui.leadingIcon(settings,R.drawable.ic_settings);rail.addView(settings,new LinearLayout.LayoutParams(-1,-2));
 }
 void buildList(){if(tasksUi==null)tasksUi=new TasksUi(this);tasksUi.build(listPane);}
 void saveWorkspacePreferences(){if(store==null||account==null||authState!=AuthPolicy.State.FULL)return;synchronized(Store.class){if(store.current(account))store.prefs.edit().putString("ui_epoch",account.epoch).putString("ui_filter",filter).putString("ui_project",projectFilter).putString("ui_sort",sortOrder).putBoolean("ui_sort_reverse",sortReverse).putString("ui_color_mode",colorMode).apply();}}
 void invalidateQuietRead(){synchronized(Store.class){quietGate.invalidate();}quietInFlight=false;}
 void quietRefresh(){
  if(!foreground||busy||quietInFlight||authState!=AuthPolicy.State.FULL||account==null)return;final Store.Account owner=account;final long ticket;synchronized(Store.class){ticket=quietGate.begin(owner.epoch);}quietInFlight=true;
  Api.IO.execute(()->{try{JSONObject fresh=new Api(owner).call("GET","/api/tasks",null);synchronized(Store.class){if(!quietGate.current(ticket,owner.epoch)||!store.current(owner))return;store.cache(owner,fresh);}main.post(()->{
   if(!quietGate.current(ticket,owner.epoch)||!alive(owner)||!foreground)return;quietInFlight=false;if(busy)return;JSONArray incoming=fresh.optJSONArray("tasks");if(incoming==null)return;boolean changed=!tasks.toString().equals(incoming.toString()),wasOffline=!online;if(!changed&&!wasOffline){if(summaryUi!=null)summaryUi.updateState();return;}captureDraft();snapshot=fresh;tasks=incoming;online=true;banner="Connected";
   if(editing&&draft!=null){JSONObject current=task(selected);if(current!=null){try{draft.put("notification",current.optJSONObject("notification"));}catch(Exception ignored){}if(!text(draft,"revision").equals(text(current,"revision")))banner="Task changed on server · your draft is preserved";}if(tasksUi!=null)tasksUi.refresh();if(stateLabel!=null){stateLabel.setText(banner);stateLabel.setVisibility(banner.contains("preserved")?View.VISIBLE:View.GONE);}updateTaskEditorNotice();}
   else if(conversationUi!=null){if(draft!=null){JSONObject current=task(selected);if(current!=null)try{draft.put("notification",current.optJSONObject("notification"));}catch(Exception ignored){}}buildListRows();}
   else if(selected.isEmpty()){buildListRows();if(stateLabel!=null)stateLabel.setVisibility(View.GONE);}else{draft=null;if(detailPane!=null){detailPane.removeAllViews();buildDetails();}buildListRows();}
   if(summaryUi!=null)summaryUi.updateState();
  });}catch(Exception error){main.post(()->{if(!quietGate.current(ticket,owner.epoch)||!alive(owner)||!foreground)return;quietInFlight=false;online=false;banner="Offline · saved content remains readable; refresh before editing";if(stateLabel!=null){stateLabel.setText(banner);stateLabel.setVisibility(View.VISIBLE);}updateTaskEditorNotice();buildListRows();if(summaryUi!=null)summaryUi.updateState();});}});
 }
 void showTaskActions(JSONObject clicked){
  if(clicked==null||busy||authState!=AuthPolicy.State.FULL)return;final JSONObject captured=copy(clicked);final String id=text(captured,"id");if(captured.optBoolean("deleted")){NativeSheets.menu(this,text(captured,"title"),new String[]{"Restore task","Open details"},index->{if(index==0)quickMutation("POST","/api/tasks/"+Api.segment(id)+"/restore",captured,revisionOf(captured));else navigate(id);});return;}
  String[] options={"Open details","Discuss with Hermès","Change status","Change priority","Move to project","Edit task","Move to trash"};NativeSheets.menu(this,text(captured,"title"),options,index->{if(index==0)navigate(id);else if(index==1)openTaskConversation(id);else if(index==2)quickChoice(captured,"status",statuses);else if(index==3)quickChoice(captured,"priority",priorities);else if(index==4)moveProject(captured);else if(index==5)editTask(captured);else confirm("Move task to trash?","This is recoverable from Trash. It does not acknowledge unread updates.",()->quickMutation("DELETE","/api/tasks/"+Api.segment(id),captured,revisionOf(captured)));});
 }
 JSONObject revisionOf(JSONObject task){JSONObject body=new JSONObject();try{body.put("revision",text(task,"revision"));}catch(Exception ignored){}return body;}
 void quickChoice(JSONObject task,String field,String[] values){String[] labels=new String[values.length];for(int i=0;i<values.length;i++)labels[i]=human(values[i])+(values[i].equals(text(task,field))?" · current":"");NativeSheets.menu(this,"Change "+field,labels,index->quickChange(task,field,values[index]));}
 void moveProject(JSONObject task){
  final Store.Account owner=account;final long generation=surfaces.generation();LinearLayout form=vertical();form.setPadding(dp(20),dp(12),dp(20),dp(12));TextView caption=label("Project / category",13,muted);add(form,caption);EditText name=new EditText(this);ui.input(name);name.setId(View.generateViewId());caption.setLabelFor(name.getId());name.setSingleLine(true);name.setText(text(task,"project"));name.setContentDescription("Project or category name");add(form,name);add(form,label("Use a saved name or enter a category. This changes the task’s project field, not a separate project object.",13,muted));
  dialogBuilder().setTitle("Move task").setView(form).setNegativeButton("Cancel",null).setNeutralButton("No project",(d,w)->{if(alive(owner)&&surfaces.current(generation))quickChange(task,"project",JSONObject.NULL);}).setPositiveButton("Move",(d,w)->{if(alive(owner)&&surfaces.current(generation))quickChange(task,"project",name.getText().toString().isEmpty()?JSONObject.NULL:name.getText().toString());}).show();
 }
 void quickChange(JSONObject task,String field,Object value){try{quickMutation("PATCH","/api/tasks/"+Api.segment(text(task,"id")),task,TaskActionPolicy.change(task,field,value));}catch(IllegalArgumentException error){notice("Cannot change task",error.getMessage());}}
 void quickMutation(String method,String path,JSONObject changed,JSONObject body){
  if(!online||busy||account==null){notice("Connect before changing tasks","Refresh with a connection before applying this action. No offline changes are queued.");return;}captureDraft();if(text(changed,"id").equals(selected)&&editing&&dirty()){notice("Keep your unsaved changes","Save or cancel editing this task before using quick actions. Your draft has not changed.");return;}
  final boolean keepDraft=!text(changed,"id").equals(selected)&&(editing||dirty());final Store.Account owner=account;final String id=text(changed,"id");invalidateQuietRead();busy=true;banner="Saving task…";renderKeepingConversation();Api.IO.execute(()->{try{JSONObject result=new Api(owner).call(method,path,body);final String ownToken=text(result.optJSONObject("notification"),"token");JSONObject fresh=new Api(owner).call("GET","/api/tasks",null);store.cache(owner,fresh);main.post(()->{if(!alive(owner))return;busy=false;online=true;snapshot=fresh;tasks=fresh.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();if(tasksUi!=null)tasksUi.acceptOwnChange(id,ownToken);if(!keepDraft)draft=selected.equals("NEW")?empty():task(selected)==null?null:copy(task(selected));else if(draft!=null){JSONObject latest=task(selected);if(latest!=null)try{draft.put("notification",latest.optJSONObject("notification"));}catch(Exception ignored){}}banner="Saved";renderKeepingConversation();});}catch(Exception error){main.post(()->{if(!alive(owner))return;busy=false;int code=error instanceof Api.Failure?((Api.Failure)error).code:0;banner=code==409?"Task changed · refresh before trying again; your draft is preserved":"Change not confirmed · refresh and check before retrying";if(code==0)online=false;renderKeepingConversation();notice(code==409?"Task changed":"Change not confirmed",code==409?"Nothing was overwritten. Refresh to review the current version before another action.":"Check the saved task after refreshing. The request may already have reached the server; it will not be automatically retried.");});}});
 }
 void editTask(JSONObject task){
  captureDraft();String id=text(task,"id");Runnable open=()->{if(!id.equals(selected))draft=null;selected=id;if(draft==null)draft=copy(task);editing=true;detailsMode="Details";destination="Tasks";detailY=0;pendingSummary="";render();};if(!id.equals(selected)&&dirty())confirm("Discard unsaved changes?","Your conversation drafts are retained. Unsaved task fields will be discarded.",open);else open.run();
 }
 void openTaskConversation(String id){captureDraft();Runnable open=()->{if(!id.equals(selected)){draft=null;editing=false;}selected=id;destination="Tasks";detailsMode="Hermès";pendingSummary="";renderPage(false);};if(!id.equals(selected)&&dirty())confirm("Leave unsaved task changes?","Unsaved fields for the previous task will be discarded. Conversation drafts remain saved.",open);else open.run();}
 void projectMenu(){
  ArrayList<String> options=new ArrayList<>();options.add("All projects");options.addAll(PresentationPolicy.projectOptions(tasks,filter,LocalDate.now()));String[] items=options.toArray(new String[0]);
  dialogBuilder().setTitle("Project").setSingleChoiceItems(items,projectFilter.isEmpty()?0:options.indexOf(projectFilter),(d,w)->{captureDraft();projectFilter=w==0?"":items[w];listY=0;if(tasksUi!=null)tasksUi.resetAnchor();saveWorkspacePreferences();d.dismiss();render();}).setNegativeButton("Cancel",null).show();
 }
 void sortMenu(){
  String[] items=PresentationPolicy.sortModes();String current="Priority".equals(sortOrder)?"Importance":sortOrder;
  LinearLayout options=vertical();
  add(options,button("Direction · "+PresentationPolicy.directionLabel(sortOrder,sortReverse),()->{captureDraft();sortReverse=!sortReverse;saveWorkspacePreferences();dismissPrivateDialogs();render();}));
  add(options,button("Why this order?",()->notice("Why this order",PresentationPolicy.whySmart())));
  dialogBuilder().setTitle("Sort tasks").setSingleChoiceItems(items,Arrays.asList(items).indexOf(current),(d,w)->{captureDraft();sortOrder=items[w];sortReverse=false;saveWorkspacePreferences();d.dismiss();render();}).setView(options).setNegativeButton("Cancel",null).show();
 }
 void colorMenu(){String[] modes={"Project","Importance","Neutral"};dialogBuilder().setTitle("Color by").setSingleChoiceItems(modes,Arrays.asList(modes).indexOf(colorMode),(d,w)->{captureDraft();colorMode=modes[w];saveWorkspacePreferences();d.dismiss();render();}).setNegativeButton("Cancel",null).show();}
 boolean terminal(JSONObject t){return PresentationPolicy.terminal(t);}
 String human(String value){String s=value.replace('_',' ');return s.isEmpty()?"Not set":Character.toUpperCase(s.charAt(0))+s.substring(1);}
 String dueLabel(JSONObject t){return PresentationPolicy.dueLabel(text(t,"due_date"),LocalDate.now(),Locale.getDefault());}
 void buildListRows(){if(tasksUi!=null)tasksUi.refresh();}
 JSONObject task(String id){for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
 JSONObject copy(JSONObject t){try{return new JSONObject(t.toString());}catch(Exception e){return new JSONObject();}}
 JSONObject empty(){JSONObject o=new JSONObject();try{o.put("title","").put("description","").put("notes","").put("project","").put("due_date","").put("priority","medium").put("status","todo");}catch(Exception ignored){}return o;}
 void renderPage(boolean returning){render();ui.navigatePage(destination.equals("Discussions")?root:selected.isEmpty()?listPane:detailPane,returning);}
 void navigate(String id){
  if(busy){notice("Please wait","A request is still in progress.");return;}if(id.equals(selected)){if(!id.isEmpty()){if(!destination.equals("Tasks")||!detailsMode.equals("Details")){destination="Tasks";detailsMode="Details";render();}pendingSummary=id;maybeSummary();}return;}captureDraft();final Store.Account owner=account;final long generation=surfaces.generation();
  Runnable go=()->{if(!alive(owner)||!surfaces.current(generation))return;destination="Tasks";selected=id;pendingSummary=id;draft=null;detailY=0;detailsMode="Details";editing=id.equals("NEW");render();ui.navigatePage(id.isEmpty()?listPane:detailPane,id.isEmpty());};
  if(dirty())confirm("Discard unsaved changes?","Unsaved task fields will be lost. Conversation drafts remain saved per thread.",go);else go.run();
 }
 void reviewConversationAsTask(String original){
  if(busy||authState!=AuthPolicy.State.FULL||account==null||!alive(account))return;
  final Store.Account owner=account;final long generation=surfaces.generation();final String proposal=original;
  captureDraft();
  Runnable review=()->{
   if(busy||authState!=AuthPolicy.State.FULL||!alive(owner)||!surfaces.current(generation))return;
   JSONObject proposed=empty();try{proposed.put("description",proposal);}catch(JSONException impossible){return;}
   selected="NEW";draft=proposed;editing=true;destination="Tasks";detailsMode="Details";pendingSummary="";detailY=0;render();
  };
  if(dirty())confirm("Review as new task?","Unsaved task fields will be discarded only if you continue. The assistant text becomes an editable description, not a saved task. Review all fields and enter a title before choosing Create task. Conversation drafts remain saved.",review);else review.run();
 }
 boolean dirty(){if(draft==null)return false;JSONObject old=selected.equals("NEW")?empty():task(selected);if(old==null)return true;for(String f:new String[]{"title","description","notes","project","due_date","priority","status"})if(!text(draft,f).equals(text(old,f)))return true;return false;}
 void captureDraft(){captureTaskPresentation();if(tasksUi!=null)tasksUi.capture();saveWorkspacePreferences();if(draft!=null&&title!=null){try{draft.put("title",title.getText().toString()).put("description",description.getText().toString()).put("notes",notes.getText().toString()).put("project",project.getText().toString()).put("due_date",due.getText().toString()).put("priority",priorities[priority.getSelectedItemPosition()]).put("status",statuses[status.getSelectedItemPosition()]);}catch(Exception ignored){}}if(conversationUi!=null)conversationUi.capture();if(conversations!=null)conversations.save();if(listScroll!=null)listY=listScroll.getScrollY();if(detailScroll!=null)detailY=capturedDetailY();}
 /** An undrained outer restore is presentation, even when no editable field exists. */
 int capturedDetailY(){
  if(detailScroll==null)return detailY;
  int current=detailScroll.getScrollY();
  if(pendingDetailScroll==detailScroll&&pendingDetailGeneration==taskPresentationGeneration&&selected.equals(pendingDetailTask)&&destination.equals("Tasks")&&alive(pendingDetailOwner)&&current==pendingDetailInitialY)return pendingDetailSavedY;
  if(taskPresentationPending&&account!=null&&taskEditorEpoch.equals(account.epoch)&&selected.equals(taskEditorTask)&&destination.equals("Tasks")&&alive(account)&&current==taskPresentationInitial.optInt("outer_y"))return taskPresentation.optInt("outer_y");
  return current;
 }
 void restoreDetailScroll(){
  final ScrollView scroll=detailScroll;final int savedY=detailY,initialY=scroll.getScrollY(),ticket=taskPresentationGeneration;final String targetTask=selected;final Store.Account owner=account;
  pendingDetailScroll=scroll;pendingDetailOwner=owner;pendingDetailTask=targetTask;pendingDetailInitialY=initialY;pendingDetailSavedY=savedY;pendingDetailGeneration=ticket;
  scroll.post(()->{if(pendingDetailScroll!=scroll||detailScroll!=scroll||ticket!=taskPresentationGeneration||!selected.equals(targetTask)||!destination.equals("Tasks")||!alive(owner))return;pendingDetailScroll=null;if(scroll.getScrollY()==initialY)scroll.scrollTo(0,savedY);});
 }
 /** Presentation only: never capture/replay task text from a render callback. */
 static JSONObject editorPresentation(EditText field){
  JSONObject state=new JSONObject();ConversationState.put(state,"start",field.getSelectionStart());ConversationState.put(state,"end",field.getSelectionEnd());ConversationState.put(state,"x",field.getScrollX());ConversationState.put(state,"y",field.getScrollY());ConversationState.put(state,"focus",field.hasFocus());return state;
 }
 static boolean untouchedEditor(EditText field,JSONObject initial){
  return field!=null&&initial!=null&&field.getText().toString().equals(initial.optString("text"))&&field.getSelectionStart()==initial.optInt("start")&&field.getSelectionEnd()==initial.optInt("end")&&field.getScrollX()==initial.optInt("x")&&field.getScrollY()==initial.optInt("y")&&field.hasFocus()==initial.optBoolean("focus");
 }
 void captureTaskPresentation(){
  if(title==null||account==null||!taskEditorEpoch.equals(account.epoch)||!taskEditorTask.equals(selected)||!destination.equals("Tasks")||authState!=AuthPolicy.State.FULL||!store.current(account))return;
  JSONObject saved=new JSONObject();try{
   saved.put("epoch",taskEditorEpoch).put("task",taskEditorTask).put("outer_y",capturedDetailY());
   EditText[] fields={title,description,notes,project,due};String[] names={"title","description","notes","project","due"};
   boolean focusChanged=false;for(int i=0;i<fields.length;i++){JSONObject initial=taskPresentationInitial.optJSONObject(names[i]);if(taskPresentationPending&&fields[i]!=null&&initial!=null&&fields[i].hasFocus()!=initial.optBoolean("focus"))focusChanged=true;}
   for(int i=0;i<fields.length;i++){EditText field=fields[i];if(field==null)continue;JSONObject state=editorPresentation(field),previous=taskPresentation.optJSONObject(names[i]);
    // Disabled fields are not new input; their independently usable outer viewport is.
    if(!field.isEnabled()){if(previous!=null)saved.put(names[i],previous);continue;}
    // Keep retained state for untouched initialization, but capture newer local interaction.
    if(taskPresentationPending&&previous!=null&&untouchedEditor(field,taskPresentationInitial.optJSONObject(names[i])))state=new JSONObject(previous.toString());
    if(focusChanged)state.put("focus",field.hasFocus());saved.put(names[i],state);
   }
   taskPresentation=saved;
  }catch(JSONException ignored){}
 }
 void restoreTaskPresentation(){
  taskEditorEpoch=account==null?"":account.epoch;taskEditorTask=selected;final int ticket=++taskPresentationGeneration;taskPresentationPending=false;taskPresentationInitial=new JSONObject();
  if(!taskEditorEpoch.equals(taskPresentation.optString("epoch"))||!selected.equals(taskPresentation.optString("task"))){taskPresentation=new JSONObject();return;}
  final JSONObject saved=taskPresentation,initial=taskPresentationInitial;final Store.Account owner=account;final String targetTask=selected;final EditText target=title;final ScrollView outer=detailScroll;final EditText[] fields={title,description,notes,project,due};final String[] names={"title","description","notes","project","due"};taskPresentationPending=true;
  ConversationState.put(initial,"outer_y",outer==null?detailY:outer.getScrollY());for(int i=0;i<fields.length;i++){if(fields[i]==null)continue;JSONObject state=editorPresentation(fields[i]);ConversationState.put(state,"text",fields[i].getText().toString());ConversationState.put(initial,names[i],state);}
  target.post(()->{
   if(ticket!=taskPresentationGeneration||title!=target||detailScroll!=outer||!selected.equals(targetTask)||!destination.equals("Tasks")||!alive(owner))return;
   taskPresentationPending=false;
   boolean focusChanged=false;boolean[] untouched=new boolean[fields.length];for(int i=0;i<fields.length;i++){JSONObject state=initial.optJSONObject(names[i]);untouched[i]=untouchedEditor(fields[i],state);if(fields[i]!=null&&state!=null&&fields[i].hasFocus()!=state.optBoolean("focus"))focusChanged=true;}
   boolean outerUntouched=outer!=null&&outer.getScrollY()==initial.optInt("outer_y");
   for(int i=0;i<fields.length;i++){EditText field=fields[i];JSONObject state=saved.optJSONObject(names[i]);if(!untouched[i]||state==null)continue;if(!focusChanged&&outerUntouched&&state.optBoolean("focus")&&field.isEnabled())field.requestFocus();int start=Math.max(0,Math.min(field.length(),state.optInt("start"))),end=Math.max(0,Math.min(field.length(),state.optInt("end")));field.setSelection(start,end);field.scrollTo(Math.max(0,state.optInt("x")),Math.max(0,state.optInt("y")));}
   if(outerUntouched&&!focusChanged)outer.scrollTo(0,Math.max(0,saved.optInt("outer_y")));
  });
 }
 void saveTaskPresentation(Bundle out){captureTaskPresentation();out.putString("task_presentation",taskPresentation.toString());}
 void loadTaskPresentation(Bundle saved){
  if(saved==null||account==null||!account.epoch.equals(saved.getString("epoch","")))return;
  try{JSONObject value=new JSONObject(saved.getString("task_presentation","{}"));if(account.epoch.equals(value.optString("epoch"))&&selected.equals(value.optString("task")))taskPresentation=value;}catch(JSONException ignored){}
 }
 EditText field(LinearLayout parent,String name,String value,boolean multi){
  TextView caption=label(name,13,muted);add(parent,caption);space(parent,4);EditText e=new EditText(this);e.setId(View.generateViewId());caption.setLabelFor(e.getId());e.setText(value);ui.input(e);e.setBackground(ui.shape(card,0,12));e.setSingleLine(!multi);e.setMinLines(multi?2:1);e.setMaxLines(multi?6:1);e.setGravity(Gravity.TOP|Gravity.START);e.setContentDescription(name);e.setSelectAllOnFocus(false);e.setEnabled(online&&!busy);e.setSaveEnabled(false);e.setImeOptions(multi?android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI:android.view.inputmethod.EditorInfo.IME_ACTION_NEXT|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);add(parent,e);space(parent,12);return e;
 }
 EditText valueField(LinearLayout parent,String name,String value){
  if(getResources().getConfiguration().fontScale>=1.5f)return field(parent,name,value,false);
  LinearLayout row=horizontal();row.setMinimumHeight(dp(52));TextView caption=label(name.startsWith("Due date")?"Due date":name,14,muted);row.addView(caption,new LinearLayout.LayoutParams(dp(96),-2));EditText e=new EditText(this);ui.input(e);e.setId(View.generateViewId());caption.setLabelFor(e.getId());e.setText(value);e.setContentDescription(name);e.setSingleLine(true);e.setMinWidth(0);e.setMinimumWidth(0);e.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);e.setPadding(dp(8),dp(8),dp(8),dp(8));e.setBackgroundColor(Color.TRANSPARENT);e.setEnabled(online&&!busy);e.setSaveEnabled(false);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);row.addView(e,new LinearLayout.LayoutParams(0,-2,1));add(parent,row);space(parent,4);return e;
 }
 Spinner choice(LinearLayout parent,String name,String[] options,String value){
  boolean large=getResources().getConfiguration().fontScale>=1.5f;LinearLayout row=large?vertical():horizontal();row.setMinimumHeight(dp(52));TextView caption=label(name,14,muted);if(large)add(row,caption);else row.addView(caption,new LinearLayout.LayoutParams(dp(96),-2));Spinner s=new Spinner(this,Spinner.MODE_DIALOG);s.setId(View.generateViewId());caption.setLabelFor(s.getId());s.setPrompt(name);
  String[] names=new String[options.length];for(int i=0;i<options.length;i++)names[i]=human(options[i]);
  ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,names){
   @Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView v=label(names[position]+"  ⌄",16,ink);v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);v.setPadding(dp(12),dp(8),dp(12),dp(8));v.setMinHeight(dp(48));return v;}
   @Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent){TextView v=label(names[position],16,ink);v.setBackgroundColor(card);v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setMinHeight(dp(48));return v;}
  };
  s.setAdapter(adapter);for(int i=0;i<options.length;i++)if(options[i].equals(value))s.setSelection(i);s.setMinimumHeight(dp(48));s.setBackground(ui.shape(card,0,12));s.setEnabled(online&&!busy);s.setContentDescription(name+", choose a value");if(large)add(row,s);else row.addView(s,new LinearLayout.LayoutParams(0,-2,1));add(parent,row);space(parent,4);return s;
 }
 void buildDetails(){
  if(selected.isEmpty()){space(detailPane,48);add(detailPane,heading("Select a task",24));add(detailPane,label("Choose a task to see its details.",16,muted));return;}
  JSONObject current=task(selected);if(draft==null)draft=selected.equals("NEW")?empty():current==null?null:copy(current);
  if(draft!=null&&!selected.equals("NEW")&&!draft.optBoolean("deleted")&&detailsMode.equals("Hermès")){conversationUi=new ConversationsUi(this,conversations);conversationUi.buildTask(detailPane,selected);return;}
  LinearLayout top=horizontal();top.addView(ui.icon(R.drawable.ic_back,"Back to tasks",()->navigate("")),new LinearLayout.LayoutParams(dp(48),dp(48)));
  TextView h=heading(selected.equals("NEW")?"New task":editing?"Edit task":"Tasks",18);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));
  if(draft!=null&&!selected.equals("NEW")&&!editing&&!draft.optBoolean("deleted")){Button edit=button("Edit",()->{captureDraft();editing=true;detailY=0;render();});ui.quiet(edit);edit.setEnabled(online&&!busy);top.addView(edit);}
  Runnable reloadLatest=()->confirm("Reload latest task?","This discards unsaved fields and fetches the current server version. Your Hermes conversation is kept.",()->{refresh(true);});
  ImageButton more=ui.icon(R.drawable.ic_more,"Task options",()->NativeSheets.menu(this,"Task options",new String[]{"Task actions","Reload latest task"},index->{if(index==1)reloadLatest.run();else if(task(selected)!=null)showTaskActions(task(selected));}));more.setEnabled(account!=null&&!busy);top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));add(detailPane,top);space(detailPane,8);
  if(draft==null){add(detailPane,label("This task is not in your cache. Reload latest task to load it.",16,muted));return;}
  detailScroll=new ScrollView(this){@Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(editing&&oldh>0&&h<oldh)post(()->{View focus=findFocus();if(!(focus instanceof EditText)||focus.getHeight()==0)return;EditText input=(EditText)focus;int budget=Math.max(dp(48),getHeight()-dp(8));android.graphics.Rect rect=new android.graphics.Rect(0,0,input.getWidth(),input.getHeight());if(rect.height()>budget&&input.getLayout()!=null){int offset=Math.max(0,Math.min(input.length(),input.getSelectionEnd()));rect.top=Math.max(0,input.getLayout().getLineTop(input.getLayout().getLineForOffset(offset))-dp(8));rect.bottom=Math.min(input.getHeight(),rect.top+budget);}input.requestRectangleOnScreen(rect,true);});}};detailScroll.setFillViewport(false);editor=vertical();editor.setPadding(dp(4),0,dp(4),dp(16));detailScroll.addView(editor);detailPane.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
  JSONObject n=draft.optJSONObject("notification");if(!editing&&n!=null&&n.optBoolean("unread")){
    LinearLayout update=horizontal();update.setPadding(dp(12),0,dp(4),0);update.setBackground(ui.shape(ui.soft,0,12));TextView updateLabel=heading("New update",14);updateLabel.setTextColor(accent);update.addView(updateLabel,new LinearLayout.LayoutParams(0,-2,1));Button review=button("Review",()->{pendingSummary=selected;maybeSummary();});ui.quiet(review);update.addView(review);add(editor,update);space(editor,12);
  }
  if(draft.optBoolean("deleted")){add(editor,heading("In trash",14));add(editor,heading(text(draft,"title"),24));readingDescription(text(draft,"description"),draft.opt("description_summary") instanceof String?text(draft,"description_summary"):"");space(editor,16);Button restore=primary("Restore task",()->mutation("POST","/api/tasks/"+Api.segment(selected)+"/restore",revision(),false));restore.setEnabled(online&&!busy);add(editor,restore);return;}
  if(editing||selected.equals("NEW")){buildTaskEditor();bindTaskEditorLayout(top,detailPane.getChildAt(1),detailPane.getChildAt(detailPane.getChildCount()-1));}else buildTaskReading();
  if(!taskPresentationPending)restoreDetailScroll();
 }
 // Keep fixed task chrome only when it leaves a usable editor viewport. Moving
 // chrome, never the inputs or their containing editor, keeps the active inputs attached.
 TaskDetailPane taskDetailPane(){return new TaskDetailPane();}
 class TaskDetailPane extends LinearLayout {
  java.util.function.BiPredicate<Integer,Integer> adapt;
  TaskDetailPane(){super(MainActivity.this);setOrientation(LinearLayout.VERTICAL);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){
   super.onMeasure(widthSpec,heightSpec);
   // Resolve THIS traversal before assigning any descendant frame. The previous
   // laid-out bounds can still describe the pre-IME window (or no layout at all).
   // Reparenting/line-cap changes require one bounded superclass remeasurement.
   if(adapt!=null&&adapt.test(getMeasuredWidth(),getMeasuredHeight()))super.onMeasure(widthSpec,heightSpec);
  }
 }
 void bindTaskEditorLayout(View header,View gap,View actions){
  final LinearLayout pane=detailPane,content=editor,boundRoot=root;final ScrollView scroll=detailScroll;final EditText input=title,body=description,memo=notes;
  final Store.Account owner=account;final String epoch=owner==null?"":owner.epoch,taskId=selected;final long generation=surfaces.generation();
  final LinearLayout.LayoutParams headerParams=(LinearLayout.LayoutParams)header.getLayoutParams(),gapParams=(LinearLayout.LayoutParams)gap.getLayoutParams(),actionParams=(LinearLayout.LayoutParams)actions.getLayoutParams();
  // Editable spans can change native line metrics without changing characters.
  // Memoize measured capacity inputs, not span identity or configured View style.
  final int[] measuredSize={-1,-1};final String[] measuredText={null,null},measuredMetrics={null,null};
  java.util.function.BiPredicate<Integer,Integer> adapt=(resolvedWidth,resolvedHeight)->{
   if(account!=owner||(owner!=null&&(!epoch.equals(owner.epoch)||!alive(owner)))||authState!=AuthPolicy.State.FULL||!surfaces.current(generation)||root!=boundRoot||detailPane!=pane||editor!=content||detailScroll!=scroll||title!=input||description!=body||notes!=memo||!taskId.equals(selected)||(!editing&&!selected.equals("NEW"))||!destination.equals("Tasks"))return false;
   if(boundRoot==null||!boundRoot.isAttachedToWindow()||content.getParent()!=scroll||scroll.getParent()!=pane||input.getParent()!=content||body.getParent()!=content||memo.getParent()!=content)return false;
   View attached=pane;while(attached!=null&&attached!=boundRoot)attached=attached.getParent() instanceof View?(View)attached.getParent():null;if(attached==null)return false;
   if((header.getParent()!=pane&&header.getParent()!=content)||gap.getParent()!=header.getParent()||actions.getParent()!=header.getParent())return false;
   int width=resolvedWidth-pane.getPaddingLeft()-pane.getPaddingRight(),height=resolvedHeight-pane.getPaddingTop()-pane.getPaddingBottom();if(width<=0||height<=0)return false;
   int fixed=taskEditorChromeHeight(header,width)+taskEditorChromeHeight(gap,width)+taskEditorChromeHeight(actions,width);
   boolean flowing=height-fixed<Math.max(dp(160),taskEditorChromeHeight(input,width)+dp(16));
   boolean changed=flowing!=(header.getParent()==content);
   if(flowing&&header.getParent()==pane){pane.removeView(header);pane.removeView(gap);pane.removeView(actions);content.addView(header,0,headerParams);content.addView(gap,1,gapParams);content.addView(actions,actionParams);}
   else if(!flowing&&header.getParent()==content){content.removeView(header);content.removeView(gap);content.removeView(actions);pane.addView(header,0,headerParams);pane.addView(gap,1,gapParams);pane.addView(actions,actionParams);}
   int budget=Math.max(1,height-(flowing?0:fixed)-dp(16)),fieldWidth=Math.max(1,width-content.getPaddingLeft()-content.getPaddingRight());
   String bodyText=body.getText().toString(),memoText=memo.getText().toString();
   String bodyMetrics=taskEditorMetricState(body,fieldWidth),memoMetrics=taskEditorMetricState(memo,fieldWidth);
   if(measuredSize[0]!=fieldWidth||measuredSize[1]!=budget||!bodyText.equals(measuredText[0])||!memoText.equals(measuredText[1])||!bodyMetrics.equals(measuredMetrics[0])||!memoMetrics.equals(measuredMetrics[1])){
    changed=true;
    sizeTaskEditorMultiline(body,fieldWidth,budget);sizeTaskEditorMultiline(memo,fieldWidth,budget);
    measuredSize[0]=fieldWidth;measuredSize[1]=budget;measuredText[0]=bodyText;measuredText[1]=memoText;
    measuredMetrics[0]=taskEditorMetricState(body,fieldWidth);measuredMetrics[1]=taskEditorMetricState(memo,fieldWidth);
   }
   return changed;
  };
  ((TaskDetailPane)pane).adapt=adapt;
  // Retain the owned post-layout metric observation; prevention runs in measure.
  pane.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->adapt.test(r-l,b-t));
 }
 String taskEditorMetricState(EditText field,int width){
  // Local measurement does not request a parent pass or change native line limits.
  // Include the first six line boundaries even when the current cap hides them:
  // a span removal below that cap may make a larger preferred capacity fit again.
  field.forceLayout();
  field.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  StringBuilder state=new StringBuilder().append(field.getMeasuredHeight()).append(':').append(field.getLineHeight());
  Layout layout=field.getLayout();if(layout!=null)for(int line=0;line<=Math.min(6,layout.getLineCount());line++)state.append(':').append(layout.getLineTop(line));
  return state.toString();
 }
 int taskEditorChromeHeight(View view,int width){
  LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)view.getLayoutParams();
  view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width-p.leftMargin-p.rightMargin),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  return (p.height>=0?p.height:view.getMeasuredHeight())+p.topMargin+p.bottomMargin;
 }
 void sizeTaskEditorMultiline(EditText field,int width,int budget){
  // Measure the preferred visible line capacity with native font metrics/padding;
  // nominal line-height arithmetic can miss fallback glyphs or line spacing.
  // Text, selection, input type, font and the native movement method stay untouched.
  for(int lines=6;lines>=1;lines--){
   field.setMinLines(lines);field.setMaxLines(lines);
   // A second layout pass can defer requestLayout before it marks this field
   // dirty. Invalidate its measurement cache explicitly for each trial capacity;
   // this is local measurement, not a parent request or a rebuilt editor.
   field.forceLayout();
   field.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
   if(field.getMeasuredHeight()<=budget||lines==1){field.setMinLines(Math.min(2,lines));return;}
  }
 }
 void buildTaskReading(){
  TextView taskTitle=heading(text(draft,"title"),25);if(Build.VERSION.SDK_INT>=28)taskTitle.setTypeface(android.graphics.Typeface.create(ui.font,600,false));taskTitle.setTextIsSelectable(true);add(editor,taskTitle);space(editor,12);
  LinearLayout facts=vertical();facts.setPadding(dp(14),0,dp(14),0);facts.setBackground(ui.shape(card,0,12));
  readingFact(facts,"Project",PresentationPolicy.project(draft));rule(facts);readingFact(facts,"Priority",human(text(draft,"priority")));rule(facts);readingFact(facts,"Status",human(text(draft,"status")));rule(facts);readingFact(facts,"Due date",dueLabel(draft));add(editor,facts);space(editor,16);
  if(!text(draft,"description").isEmpty())readingDescription(text(draft,"description"),draft.opt("description_summary") instanceof String?text(draft,"description_summary"):"");
  if(!text(draft,"notes").isEmpty())readingSection("Notes",text(draft,"notes"));sourceContext();
  StringBuilder activity=new StringBuilder();for(String[] field:new String[][]{{"created_at","Created"},{"updated_at","Last changed"}}){String raw=text(draft,field[0]);if(raw.isEmpty())continue;try{String formatted=java.time.OffsetDateTime.parse(raw).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM,java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault()));if(activity.length()>0)activity.append("\n");activity.append(field[1]).append(" · ").append(formatted);}catch(java.time.format.DateTimeParseException ignored){}}
  if(activity.length()>0)readingSection("Activity",activity.toString());
  if(!online)add(editor,label("Cached task · read only. Refresh to make changes.",13,muted));
  FrameLayout material=new FrameLayout(this);material.setBackground(ui.shape(card,0,12));LinearLayout action=vertical();action.setPadding(dp(6),dp(4),dp(6),dp(4));Button chat=button("Discuss with Hermes",()->openTaskConversation(selected));ui.quiet(chat);chat.setTextColor(accent);ui.leadingIcon(chat,R.drawable.ic_chat);add(action,chat);material.addView(action,new FrameLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(4),dp(4),dp(4),dp(8));detailPane.addView(material,p);
 }
 void readingFact(LinearLayout parent,String name,String value){
  boolean large=getResources().getConfiguration().fontScale>=1.5f;LinearLayout row=large?vertical():horizontal();row.setMinimumHeight(dp(52));row.setPadding(0,dp(8),0,dp(8));TextView caption=label(name,15,muted);TextView content=label(value,16,name.equals("Status")?accent:ink);content.setTextIsSelectable(true);
  if(!large){row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));content.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);row.addView(content,new LinearLayout.LayoutParams(0,-2,1));}else{add(row,caption);add(row,content);}add(parent,row);
 }
 void readingDescription(String value,String summary){
  descriptionState.bind(selected,value,summary);
  LinearLayout group=vertical();group.setPadding(dp(14),dp(12),dp(14),dp(12));group.setBackground(ui.shape(card,0,12));
  TextView caption=heading("Description",13);caption.setTextColor(muted);add(group,caption);space(group,4);
  TextView body=label("",16,ink);body.setTextIsSelectable(true);body.setAutoLinkMask(0);body.setLinksClickable(false);add(group,body);
  Button more=button("See more",()->{});ui.quiet(more);more.setMinHeight(dp(48));add(group,more);
  Button original=button("Original text",()->{});ui.quiet(original);original.setMinHeight(dp(48));add(group,original);
  Runnable update=()->{
   caption.setText(descriptionState.plain()?"Description · original text":!descriptionState.expanded&&descriptionState.hasSummary()?"Description · summary":!descriptionState.expanded&&descriptionState.shortened()?"Description · selected excerpts":"Description");
   body.setText(MarkdownText.render(descriptionState.document()));
   more.setText(descriptionState.expanded?"See less":"See more");more.setContentDescription(descriptionState.expanded?"Show less description":"Show full description");more.setVisibility(descriptionState.shortened()?View.VISIBLE:View.GONE);
   original.setText(descriptionState.original?"Formatted text":"Original text");original.setVisibility(descriptionState.expanded||!descriptionState.shortened()?View.VISIBLE:View.GONE);
  };
  more.setOnClickListener(v->{descriptionState.toggleExpanded();update.run();});
  original.setOnClickListener(v->{descriptionState.toggleOriginal();update.run();});
  update.run();add(editor,group);space(editor,12);
 }
 void readingSection(String name,String value){
  LinearLayout group=vertical();group.setPadding(dp(14),dp(12),dp(14),dp(12));group.setBackground(ui.shape(card,0,12));TextView caption=heading(name,13);caption.setTextColor(muted);add(group,caption);space(group,4);TextView t=label("",16,ink);t.setText(MarkdownText.render(value));t.setTextIsSelectable(true);t.setAutoLinkMask(0);add(group,t);add(editor,group);space(editor,12);
 }
 void sourceContext(){
  LinearLayout group=vertical();group.setPadding(dp(14),dp(12),dp(14),dp(12));group.setBackground(ui.shape(card,0,12));TextView caption=heading("Source",13);caption.setTextColor(muted);add(group,caption);space(group,4);add(group,label(PresentationPolicy.sourceSummary(draft),15,ink));
  java.util.List<String> links=PresentationPolicy.sourceLinks(draft);
  for(String link:links){String host=android.net.Uri.parse(link).getHost();Button open=button("Open source · "+host,()->confirm("Open source?","This supplied link opens in your browser:\n"+host+"\nYour task-server token is never included.",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException|SecurityException e){notice("No browser available","Install or enable a browser to open this source.");}}));ui.quiet(open);add(group,open);}
  add(group,button("Reply to email…",()->openEmailReply(selected,null)));
  add(editor,group);space(editor,12);
 }
 void buildTaskEditor(){
  LinearLayout notice=vertical();notice.setPadding(dp(14),dp(12),dp(14),dp(12));notice.setBackground(ui.shape(card,0,12));
  if(selected.equals("NEW"))add(notice,label("DRAFT · Not saved. Review all fields and enter a title before choosing Create task.",14,muted));
  else add(notice,label("Choose Save to save your changes. Changes are not saved automatically.",14,muted));
  TextView offline=label("Offline · saving is unavailable. Copy any changes you need before reloading the latest task.",14,muted);offline.setVisibility(!online?View.VISIBLE:View.GONE);add(notice,offline);
  TextView feedback=label(banner,14,ink);feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);feedback.setVisibility(!online||busy||banner.contains("preserved")?View.VISIBLE:View.GONE);add(notice,feedback);add(editor,notice);space(editor,12);bindTaskEditorNotice(feedback,offline);
  title=field(editor,"Title",text(draft,"title"),false);description=field(editor,"Description",text(draft,"description"),true);
  LinearLayout selections=vertical();selections.setPadding(dp(12),dp(8),dp(12),0);selections.setBackground(ui.shape(card,0,12));
  project=valueField(selections,"Project",text(draft,"project"));priority=choice(selections,"Priority",priorities,text(draft,"priority"));status=choice(selections,"Status",statuses,text(draft,"status"));
  due=valueField(selections,"Due date · YYYY-MM-DD",text(draft,"due_date"));due.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
  Button pick=button("Choose date",this::pickDate);pick.setEnabled(online&&!busy);ui.quiet(pick);ui.leadingIcon(pick,R.drawable.ic_calendar);Button clear=button("Clear date",()->due.setText(""));clear.setEnabled(online&&!busy);ui.quiet(clear);pair(selections,pick,clear);add(editor,selections);space(editor,12);
  notes=field(editor,"Notes",text(draft,"notes"),true);
  restoreTaskPresentation();
  FrameLayout material=new FrameLayout(this);material.setBackground(ui.shape(card,0,12));LinearLayout actions=horizontal();actions.setPadding(dp(6),dp(4),dp(6),dp(4));
  Button cancel=button("Cancel",()->{captureDraft();Runnable discard=()->{draft=null;editing=false;detailY=0;if(selected.equals("NEW"))selected="";render();};if(dirty())confirm("Discard unsaved changes?","Your saved task is unchanged.",discard);else discard.run();});cancel.setEnabled(!busy);ui.quiet(cancel);actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
  Button save=primary(selected.equals("NEW")?"Create task":"Save",()->save(false));save.setEnabled(online&&!busy);save.setContentDescription(selected.equals("NEW")?"Create task":"Save task changes");actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));material.addView(actions,new FrameLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(4),dp(4),dp(4),dp(8));detailPane.addView(material,p);
 }
 void bindTaskEditorNotice(TextView feedback,TextView offline){
  final Store.Account owner=account;final String epoch=owner==null?"":owner.epoch,taskId=selected;final LinearLayout boundRoot=root,boundEditor=editor;final long generation=surfaces.generation();
  taskEditorNoticeUpdate=()->{
   if(owner==null||account!=owner||!epoch.equals(owner.epoch)||!alive(owner)||!surfaces.current(generation)||root!=boundRoot||editor!=boundEditor||!taskId.equals(selected)||(!editing&&!selected.equals("NEW"))||!destination.equals("Tasks"))return;
   View attached=feedback;while(attached!=null&&attached!=boundRoot)attached=attached.getParent() instanceof View?(View)attached.getParent():null;if(attached==null||offline.getParent()!=feedback.getParent())return;
   feedback.setText(banner);feedback.setVisibility(!online||busy||banner.contains("preserved")?View.VISIBLE:View.GONE);offline.setVisibility(!online?View.VISIBLE:View.GONE);
  };
 }
 void updateTaskEditorNotice(){if(taskEditorNoticeUpdate!=null)taskEditorNoticeUpdate.run();}
 void pickDate(){LocalDate date=PresentationPolicy.parseDate(due.getText().toString());if(date==null)date=LocalDate.now();long generation=surfaces.generation();Dialog picker=new DatePickerDialog(this,(view,y,m,d)->{if(surfaces.current(generation)&&authState==AuthPolicy.State.FULL&&due!=null)due.setText(LocalDate.of(y,m+1,d).toString());},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());trackDialog(picker).show();}
 JSONObject revision(){JSONObject o=new JSONObject();try{o.put("revision",text(draft,"revision"));}catch(Exception ignored){}return o;}
 void save(boolean complete){captureDraft();try{
  if(text(draft,"title").trim().isEmpty())throw new IllegalArgumentException("Title is required");String date=text(draft,"due_date").trim();if(!date.isEmpty()){LocalDate.parse(date);if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("Use YYYY-MM-DD");}
  JSONObject body=new JSONObject();for(String f:new String[]{"title","description","notes","priority","status"})body.put(f,text(draft,f));body.put("project",text(draft,"project").trim().isEmpty()?JSONObject.NULL:text(draft,"project").trim());body.put("due_date",date.isEmpty()?JSONObject.NULL:date);if(complete)body.put("status","completed");boolean create=selected.equals("NEW");if(!create)body.put("revision",text(draft,"revision"));mutation(create?"POST":"PATCH",create?"/api/tasks":"/api/tasks/"+Api.segment(selected),body,true);
 }catch(Exception e){notice("Check task fields",e instanceof java.time.format.DateTimeParseException?"Use a valid date in YYYY-MM-DD format.":e.getMessage());}}
 void mutation(String method,String path,JSONObject body,boolean clearDraft){
  if(!online||busy||account==null)return;captureDraft();invalidateQuietRead();busy=true;banner="Saving…";Store.Account a=account;render();
  Api.IO.execute(()->{try{JSONObject result=new Api(a).call(method,path,body);String id=result.getString("id");final String ownToken=text(result.optJSONObject("notification"),"token");JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);main.post(()->{if(!alive(a))return;busy=false;online=true;ackNeedsRefresh=false;snapshot=latest;tasks=latest.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();if(tasksUi!=null)tasksUi.acceptOwnChange(id,ownToken);selected=id;draft=task(id)==null?copy(result):copy(task(id));editing=false;detailsMode="Details";banner="Saved · up to date";render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;if(e instanceof Api.Failure&&((Api.Failure)e).code==409){banner="Conflict · draft preserved — reload latest before saving";render();notice("Task changed", "Your draft is preserved. Copy any changes you need, then choose Reload latest. Nothing was overwritten.");}else{online=false;banner=Api.friendly(e)+" A write may already have reached the server; refresh and check before retrying.";render();}});}});
 }
 void markRead(String id,String token){
  if(account==null||!online||busy||ackNeedsRefresh||summaryUi==null)return;
  final UpdateReviewPolicy.Snapshot shown=summaryUi.shown;if(!id.equals(shown.taskId())||!token.equals(shown.token())||token.isEmpty())return;
  captureDraft();invalidateQuietRead();Store.Account a=account;busy=true;banner="Acknowledging displayed update…";summaryUi.waiting(true);renderKeepingConversation();
  Api.IO.execute(()->{try{
   JSONObject reply=new Api(a).call("POST","/api/tasks/"+Api.segment(id)+"/updates/read",new JSONObject().put("token",token));
   main.post(()->{if(!alive(a))return;busy=false;JSONObject merged=UpdateReviewPolicy.mergeAcknowledgement(shown,task(id),reply);
    if(merged!=null){for(int i=0;i<tasks.length();i++){JSONObject item=tasks.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))try{tasks.put(i,merged);}catch(Exception ignored){}}
     if(draft!=null&&id.equals(selected))try{draft.put("notification",merged.optJSONObject("notification"));}catch(Exception ignored){}}
    boolean cleared=UpdateReviewPolicy.shouldCancelNotification(shown,reply,merged);if(cleared)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NotificationIdentity.tag(a.epoch,id,token),0);
    banner=cleared?"Update acknowledged":UpdateReviewPolicy.newer(shown,merged)?"A newer update remains unread":"Acknowledgement not confirmed · review latest before retrying";
    if(summaryUi!=null&&id.equals(summaryUi.shown.taskId())&&token.equals(summaryUi.shown.token())){summaryUi.waiting(false);if(cleared)summaryUi.dialog.dismiss();else if(!UpdateReviewPolicy.newer(shown,merged)){ackNeedsRefresh=true;summaryUi.failure();}else summaryUi.updateState();}
    renderKeepingConversation();
   });
  }catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;ackNeedsRefresh=true;int code=e instanceof Api.Failure?((Api.Failure)e).code:0;if(code==0)online=false;banner=code==409?"A newer update is waiting · review latest":"Acknowledgement not confirmed · summary retained";if(summaryUi!=null&&id.equals(summaryUi.shown.taskId())&&token.equals(summaryUi.shown.token()))summaryUi.failure();renderKeepingConversation();});}});
 }
 void refresh(){refresh(false);}
 /** Confirmed reload intent is captured across intermediate renders; failure retains input. */
 void refresh(boolean discardDraft){
  if(authState!=AuthPolicy.State.FULL)return;if(account==null){settings();return;}if(busy)return;captureDraft();invalidateQuietRead();final boolean keepDraft=!discardDraft&&(editing||dirty());Store.Account a=account;busy=true;banner="Refreshing…";renderKeepingConversation();
  Api.IO.execute(()->{try{JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);String notificationNote="";try{PollService.poll(this,store,a,()->isDestroyed());}catch(Exception e){notificationNote=" · update checks delayed";}final String suffix=notificationNote;main.post(()->{if(!alive(a))return;busy=false;online=true;ackNeedsRefresh=false;snapshot=latest;tasks=latest.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();if(!keepDraft)draft=selected.equals("NEW")?empty():task(selected)==null?null:copy(task(selected));else if(draft!=null){JSONObject remote=task(selected);if(remote!=null)try{draft.put("notification",remote.optJSONObject("notification"));}catch(Exception ignored){}}banner="Connected · "+tasks.length()+" tasks"+suffix;renderKeepingConversation();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;online=false;banner=Api.friendly(e);renderKeepingConversation();});}});
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
  if(conversations!=null)conversations.close();conversations=new ConversationController(store,a);conversations.resume();initEmail(a);
  JSONObject cached=store.cached();if(cached!=null){snapshot=cached;tasks=cached.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();banner="Offline cache · read only — refresh to edit";}
  if(draft!=null){JSONObject base=task(selected);if(base!=null){JSONObject merged=copy(base);Iterator<String> keys=draft.keys();while(keys.hasNext()){String k=keys.next();try{merged.put(k,draft.get(k));}catch(Exception ignored){}}draft=merged;}}
  render();refresh();
 }
 void blockSession(int status){
  invalidateQuietRead();if(tasksUi!=null)tasksUi.clear();projectFilter=query="";filter="Active";sortOrder="Smart";sortReverse=false;colorMode="Project";editing=false;detailsMode="Details";dismissPrivateDialogs();try{account=store.invalidateWork(account);}catch(Exception e){account=null;}if(conversations!=null)conversations.close();conversationUi=null;conversations=new ConversationController(store,null);if(root!=null)root.removeAllViews();listPane=detailPane=editor=listItems=null;listScroll=detailScroll=null;summaryTask=summaryToken=pendingSummary=summaryReviewed="";((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
  snapshot=new JSONObject();tasks=new JSONArray();draft=null;selected="";voiceEpoch=voiceThread="";online=busy=false;destination="Tasks";authState=status==428?AuthPolicy.State.CHANGE:AuthPolicy.State.LOGIN;authMessage=status==428?"Your password must be changed before continuing.":"Your session expired or was revoked. Sign in again; no request is automatically resent.";render();
 }
 void beginPasswordChange(){if(authBusy||busy)return;captureDraft();dismissPrivateDialogs();if(conversations!=null)conversations.close();conversations=new ConversationController(store,null);conversationUi=null;authState=AuthPolicy.State.CHANGE;authMessage="Enter your current password and choose a new private password.";render();}
 void maybeSummary(){
  if(restoring||authState!=AuthPolicy.State.FULL||selected.isEmpty()||busy||summaryDialog!=null)return;JSONObject t=task(selected);JSONObject n=t==null?null:t.optJSONObject("notification");if(n==null||!n.optBoolean("unread")){if(t!=null)pendingSummary="";return;}
  String token=n.optString("token","");if(!pendingSummary.equals(selected))return;
  summaryReviewed=selected+":"+token;pendingSummary="";summaryTask=selected;summaryToken=token;new UpdateSheet(this,t).show();
 }
 void refreshSummary(String id){if(busy||!id.equals(selected))return;if(summaryDialog!=null)summaryDialog.dismiss();pendingSummary=id;summaryReviewed="";refresh();}
 void openReviewedTask(String id,boolean chat){if(!id.equals(selected))return;captureDraft();pendingSummary="";detailsMode=chat?"Hermès":"Details";detailY=0;render();}
 void updateNotificationState(){
  if(notificationState==null)return;NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel c=manager.getNotificationChannel(PollService.CHANNEL);String state=Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED?"Notification permission required":!manager.areNotificationsEnabled()?"App notifications are disabled":c!=null&&c.getImportance()==NotificationManager.IMPORTANCE_NONE?"Task update channel is disabled":"Allowed by Android settings";notificationState.setText(state);
 }
 void initEmail(Store.Account owner){
  if(emailUi!=null){emailUi.detach();emailUi=null;}if(email!=null)email.invalidate();email=new EmailReplyController(new EmailReplyClient((m,p,b)->new Api(owner).call(m,p,b)),new EmailReplyController.Host(){public boolean valid(){return alive(owner);}public JSONObject read(){return store.emailState(owner);}public boolean save(JSONObject data){return store.emailState(owner,data);}public void work(Runnable r){Api.IO.execute(r);}public void ui(Runnable r){main.post(r);}});
  if(email.state.data.optBoolean("active")&&!email.state.id().isEmpty())email.reload();
 }
 void openEmailReply(String task,String token){if(authState!=AuthPolicy.State.FULL||account==null)return;captureDraft();if(email==null||!email.host.valid())initEmail(account);email.open(task,token);render();}
 void settings(){new SettingsUi(this).show();}
 void disconnectAccount(){
  if(busy||authBusy){notice("Please wait","Finish the current request before signing out.");return;}
  confirm("Sign out of this account?","Revoke this session and clear local credentials, tasks and conversation drafts. Server history remains saved; accepted requests continue and are not resent.",()->{
   Store.Account previous=account;blockSession(401);try{store.beginLogout();}catch(Exception e){notice("Local account gate","Could not persist sign-out state. Keep the app open until sign-out finishes.");}busy=true;authBusy=true;render();
   Api.IO.execute(()->{String note="Signed out. Server history remains saved.";try{if(previous!=null){try{AuthClient auth=Api.auth(previous.server);auth.logout(previous.token);if(auth.session(previous.token).optBoolean("authenticated"))note="Local sign-out complete, but server revocation was not confirmed.";}catch(Exception e){note="Local sign-out complete. Server revocation could not be confirmed; the remote session may remain until expiry.";}}store.disconnect();final String message=note;main.post(()->{if(isDestroyed())return;if(summaryDialog!=null){summaryDialog.dismiss();summaryDialog=null;}conversationUi=null;account=null;conversations=new ConversationController(store,null);conversations.foreground=true;voiceEpoch=voiceThread="";snapshot=new JSONObject();tasks=new JSONArray();selected="";draft=null;editing=false;detailsMode="Details";projectFilter=query="";filter="Active";listY=detailY=0;restoreComposerThread="";online=busy=authBusy=false;destination="Tasks";authState=AuthPolicy.State.LOGIN;authMessage=message;summaryReviewed=pendingSummary="";render();});}catch(Exception e){main.post(()->{if(isDestroyed())return;busy=authBusy=false;notice("Local storage error","Could not finish clearing this account. Retry sign-out.");});}});
  });
 }
 Dialog trackDialog(Dialog dialog){Runnable close=dialog::dismiss;dialogClosers.put(dialog,close);surfaces.register(close);dialog.setOnDismissListener(d->forgetDialog(dialog));Window window=dialog.getWindow();if(window!=null)ui.dialogEnter(window);return dialog;}
 void forgetDialog(Dialog dialog){Runnable close=dialogClosers.remove(dialog);if(close!=null)surfaces.forget(close);if(foreground&&tasksUi!=null&&dialogClosers.isEmpty())main.postDelayed(()->{if(foreground&&tasksUi!=null)tasksUi.resume();},200);}
 void dismissPrivateDialogs(){surfaces.invalidate();dialogClosers.clear();summaryDialog=null;summaryUi=null;}
 AlertDialog.Builder dialogBuilder(){return new AlertDialog.Builder(this){@Override public AlertDialog create(){AlertDialog dialog=super.create();trackDialog(dialog);if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(ui.shape(card,line,24));return dialog;}};}
 void fullMessage(String content){if(authState!=AuthPolicy.State.FULL)return;TextView text=label(content,16,ink);text.setTextIsSelectable(true);text.setAutoLinkMask(0);text.setPadding(dp(20),dp(16),dp(20),dp(16));ScrollView scroll=new ScrollView(this);scroll.addView(text);dialogBuilder().setTitle("Full saved message · plain text").setView(scroll).setPositiveButton("Done",null).show();}
 void notice(String title,String message){if(isDestroyed())return;dialogBuilder().setTitle(title).setMessage(message==null?"Please retry.":message).setPositiveButton("OK",null).show();}
 void confirm(String title,String message,Runnable yes){long generation=surfaces.generation();dialogBuilder().setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{if(surfaces.current(generation)&&!isDestroyed())yes.run();}).show();}
 void noticeConfirm(String title,String message,Runnable yes){confirm(title,message,yes);}
 @Override public void onBackPressed(){if(emailUi!=null){emailUi.close();return;}if(destination.equals("Discussions")&&!conversations.state.selected().isEmpty()){conversations.state.select("");conversations.hide();renderPage(true);}else if(!destination.equals("Tasks")){destination="Tasks";renderPage(true);}else if(!selected.isEmpty()&&detailsMode.equals("Hermès")){captureDraft();detailsMode="Details";renderPage(true);}else if(!selected.isEmpty()){navigate("");}else super.onBackPressed();}
}

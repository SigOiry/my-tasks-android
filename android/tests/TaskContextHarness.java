package com.simon.mytasks;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
/** Host-only View/Activity seams. Executes current TasksUi build, refresh and Rows.
 * No Android pixels, font measurement, real accessibility service or transport. */
public class TaskContextHarness {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void equal(Object expected,Object actual,String message){check(Objects.equals(expected,actual),message+" expected="+expected+" actual="+actual);}
 static class android {
  static class graphics {static class Color {static final int TRANSPARENT=0;}}
  static class os {static class Build {static class VERSION {static final int SDK_INT=35;}}}
  static class view {
   static class MotionEvent {static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;int getActionMasked(){return 0;}}
   static class ViewTreeObserver {interface OnPreDrawListener {boolean onPreDraw();}boolean isAlive(){return true;}void removeOnPreDrawListener(OnPreDrawListener v){}void addOnPreDrawListener(OnPreDrawListener v){}}
  }
 }
 interface LayoutListener {void changed(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 interface TouchListener {boolean touch(View v,android.view.MotionEvent event);}
 static class View {
  static final int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2,ACCESSIBILITY_LIVE_REGION_POLITE=1;
  int visibility=VISIBLE,liveRegion;boolean enabled=true;ViewGroup parent;Object params;String description="";MainActivity owner;
  View(MainActivity a){owner=a;}
  void setVisibility(int v){visibility=v;}void setAccessibilityLiveRegion(int v){liveRegion=v;}
  void setContentDescription(String s){description=s;}void setImportantForAccessibility(int v){}void setBackground(Object o){}void setBackgroundColor(int c){}
  void setPadding(int l,int t,int r,int b){}void setEnabled(boolean b){enabled=b;}void addOnLayoutChangeListener(LayoutListener l){}
  int getWidth(){return 320;}float getTranslationX(){return 0;}void setOnTouchListener(TouchListener l){}
  final List<Runnable> callbacks=new ArrayList<>();
  void removeCallbacks(Runnable r){callbacks.removeIf(callback->callback==r);if(owner!=null)owner.events.add("removeCallback");}void postDelayed(Runnable r,int ms){callbacks.add(r);}void post(Runnable r){callbacks.add(r);}
  void drainOnce(){List<Runnable> ready=new ArrayList<>(callbacks);callbacks.clear();for(Runnable r:ready)r.run();}
  android.view.ViewTreeObserver getViewTreeObserver(){return new android.view.ViewTreeObserver();}
 }
 static class ViewGroup extends View {
  static final int FOCUS_AFTER_DESCENDANTS=1;List<View> children=new ArrayList<>();
  ViewGroup(MainActivity a){super(a);}void addView(View v){addView(v,null);}void addView(View v,Object p){if(v.parent!=null)throw new AssertionError("double attachment");v.parent=this;v.params=p;children.add(v);}
  int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
 }
 static class LinearLayout extends ViewGroup {
  static final int VERTICAL=1;LinearLayout(MainActivity a){super(a);}void setOrientation(int o){}
  static class LayoutParams {int width,height;float weight;LayoutParams(int w,int h){width=w;height=h;}LayoutParams(int w,int h,float f){this(w,h);weight=f;}}
 }
 static class FrameLayout extends ViewGroup {FrameLayout(MainActivity a){super(a);}static class LayoutParams extends LinearLayout.LayoutParams {LayoutParams(int w,int h){super(w,h);}}}
 static class TextView extends View {
  String text="";float size;int color,maxLines=Integer.MAX_VALUE;boolean singleLine;Object ellipsize;
  TextView(MainActivity a){super(a);}void setText(CharSequence s){text=s.toString();}void setMaxLines(int n){maxLines=n;}void setSingleLine(boolean b){singleLine=b;}void setEllipsize(Object o){ellipsize=o;}void setAccessibilityHeading(boolean b){}
 }
 static class Button extends TextView {Runnable action;Button(MainActivity a){super(a);}void setMinHeight(int n){}void click(){if(enabled&&action!=null)action.run();}}
 interface Editable extends CharSequence {}
 interface TextWatcher {void beforeTextChanged(CharSequence s,int start,int length,int after);void afterTextChanged(Editable e);void onTextChanged(CharSequence s,int start,int before,int length);}
 static class EditText extends TextView {TextWatcher watcher;EditText(MainActivity a){super(a);}void setHint(String s){}void setSaveEnabled(boolean b){}void addTextChangedListener(TextWatcher w){watcher=w;}void input(String s){setText(s);watcher.onTextChanged(s,0,0,s.length());}}
 static class AbsListView extends ViewGroup {
  AbsListView(MainActivity a){super(a);}interface OnScrollListener {int SCROLL_STATE_IDLE=0;void onScrollStateChanged(AbsListView v,int state);void onScroll(AbsListView v,int first,int visible,int total);}
 }
 static class ListView extends AbsListView {
  BaseAdapter adapter;List<View> headers=new ArrayList<>();ListView(MainActivity a){super(a);}
  void setDivider(Object d){}void setDividerHeight(int n){}void setClipToPadding(boolean b){}void setCacheColorHint(int n){}void setDescendantFocusability(int n){}
  void addHeaderView(View v,Object data,boolean selectable){headers.add(v);addView(v);}void setAdapter(BaseAdapter a){adapter=a;}
  void setRecyclerListener(java.util.function.Consumer<View> c){}void setOnScrollListener(OnScrollListener l){}
 }
 static abstract class BaseAdapter {
  int notifications;void notifyDataSetChanged(){notifications++;}public abstract int getCount();public abstract Object getItem(int p);public abstract long getItemId(int p);
  public boolean hasStableIds(){return false;}public int getViewTypeCount(){return 1;}public int getItemViewType(int p){return 0;}public boolean areAllItemsEnabled(){return true;}public boolean isEnabled(int p){return true;}public abstract View getView(int p,View recycled,ViewGroup parent);
 }
 static class TaskRowView extends View {JSONObject task;boolean grouped;TaskRowView(MainActivity a,Object e){super(a);}void stopEmphasis(){}void pause(){}void resume(){}void bind(JSONObject t,boolean g){task=t;grouped=g;}void groupCorners(boolean first,boolean last){}}
 static class R {static class drawable {static final int ic_search=1,ic_more=2,ic_add=3;}}
 static class WorkspaceUi {
  MainActivity a;int canvas=1,secondary=2,primary=3,soft=4,ink=5;WorkspaceUi(MainActivity a){this.a=a;}
  int dp(int n){return n;}TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(a);v.setText(s);v.size=size;v.color=color;return v;}
  Button button(String label,Runnable r){Button b=new Button(a);b.setText(label);b.action=r;return b;}Button icon(int id,String label,Runnable r){return button(label,r);}
  FrameLayout glassSurface(){return new FrameLayout(a);}Object shape(int a,int b,int c){return null;}void selected(Button b,boolean selected){}void tone(Button b,int x,int y,int z){}void primary(Button b){}void leadingIcon(Button b,int icon){}void input(EditText t){}void placeScopeCapsule(View v,LinearLayout l,int slot,float origin){}
 }
 static class MainActivity {
  String filter="Active",projectFilter="",query="",sortOrder="Project",colorMode="Project",draft="untouched draft";
  boolean sortReverse,online=true,busy,editing,showSearch,foreground=true;JSONArray tasks=new JSONArray();JSONObject snapshot=new JSONObject();
  Map<Object,Object> dialogClosers=new HashMap<>();List<String> events=new ArrayList<>();WorkspaceUi ui=new WorkspaceUi(this);TasksUi presenter;
  LinearLayout vertical(){return new LinearLayout(this);}LinearLayout horizontal(){return new LinearLayout(this);}TextView heading(String s,int size){return ui.text(s,size,ui.ink,true);}void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
  void captureDraft(){events.add("captureDraft:"+query+":"+projectFilter+":"+filter);}void render(){events.add("render");}void saveWorkspacePreferences(){events.add("save:"+query+":"+projectFilter+":"+filter+":"+presenter.anchorReset);}
  void settings(){events.add("settings");}void navigate(String route){events.add("navigate:"+route);}
  String human(String s){return s;}String dueLabel(JSONObject t){return PresentationPolicy.dueLabel(PresentationPolicy.value(t,"due_date"),LocalDate.now(),Locale.US);}
 }
 static class EmphasisSeam {int pauses;void pause(){pauses++;}}
 static class TasksUi {
  static final int NAVIGATION_CLEARANCE=0;MainActivity a;EmphasisSeam emphasis=new EmphasisSeam();ArrayList<Entry> entries=new ArrayList<>();ListView list;Rows adapter;TextView count;Button create;
  View scopeCapsule;LinearLayout scopeTrack;float scopeOrigin=Float.NaN;String viewFilter,projectFilter,query,sortOrder,colorMode;boolean reverse,paused,touching,scrolling,pendingRefresh,anchorReset;Runnable restore;
// PRODUCTION_DEFERRED_REFRESH
  final Anchor anchor=new Anchor();int captures,restores;
  TasksUi(MainActivity activity){a=activity;a.presenter=this;}
  void stopRowMotion(){}void pauseRowEmphasis(){}void resume(){}void selectScope(String scope){}void moreMenu(){}
  void capture(){captures++;}void restoreAnchor(){restores++;}Map<String,Float> snapshotRowPositions(){return Collections.emptyMap();}void applyRowPositions(Map<String,Float> positions){}void resetRowMotion(View row){}
// PRODUCTION_METHODS
 }
 static JSONObject task(String id,boolean unread)throws Exception{return new JSONObject().put("id",id).put("title",id).put("project","Alpha").put("status","todo").put("notification",new JSONObject().put("unread",unread));}
 static TasksUi build(MainActivity a){TasksUi ui=new TasksUi(a);ui.build(a.vertical());return ui;}
 static boolean descendant(View root,View target){if(root==target)return true;if(root instanceof ViewGroup)for(View child:((ViewGroup)root).children)if(descendant(child,target))return true;return false;}
 static void attachment()throws Exception{
  MainActivity a=new MainActivity();a.tasks.put(task("one",true)).put(task("two",false));TasksUi ui=build(a);
  check(ui.list.headers.size()==1&&descendant(ui.list.headers.get(0),ui.count),"context must be attached inside scrollable ListView header");
  equal(View.VISIBLE,ui.count.visibility,"attached context visible");equal(View.ACCESSIBILITY_LIVE_REGION_POLITE,ui.count.liveRegion,"polite live region");
  equal(14f,ui.count.size,"readable secondary context size");equal(a.ui.secondary,ui.count.color,"existing semantic secondary color");
  check(!ui.count.singleLine&&ui.count.maxLines==Integer.MAX_VALUE&&ui.count.ellipsize==null,"context wraps without arbitrary cap");
  check(ui.count.text.contains("2 tasks")&&ui.count.text.contains("1 unread"),"visible text has matched and unread counts");
  check(ui.create.enabled&&ui.create.parent!=ui.list,"Add task remains in flow");
 }
 static List<View> descendants(View root){List<View> views=new ArrayList<>();views.add(root);if(root instanceof ViewGroup)for(View child:((ViewGroup)root).children)views.addAll(descendants(child));return views;}
 static void context()throws Exception{
  MainActivity one=new MainActivity();one.tasks.put(task("one",true));TasksUi single=build(one);
  check(single.count.text.startsWith("1 task · 1 unread"),"singular task count, not 1 tasks");
  String[] modes={"Smart","Project","Priority","Importance","Due date","Recently updated","Created date"};
  String[][] directions={{"Highest ranked","Lowest ranked"},{"A–Z","Z–A"},{"High–Low","Low–High"},{"High–Low","Low–High"},{"Soonest","Latest"},{"Newest","Oldest"},{"Oldest","Newest"}};
  for(int m=0;m<modes.length;m++)for(boolean reversed:new boolean[]{false,true})for(String scope:new String[]{"Active","Today","Week","All","Trash"}){
   MainActivity a=new MainActivity();a.sortOrder=modes[m];a.sortReverse=reversed;a.filter=scope;
   a.tasks.put(task("b",false).put("due_date",LocalDate.now().toString())).put(task("a",true).put("due_date",LocalDate.now().toString())).put(task("done",true).put("status","completed")).put(task("trash",true).put("deleted",true)).put(JSONObject.NULL).put("malformed");
   String before=a.tasks.toString();TasksUi ui=build(a);
   int matched=scope.equals("Trash")?1:scope.equals("All")?3:2;int unread=scope.equals("All")?2:1;
   check(ui.count.text.startsWith(matched+(matched==1?" task":" tasks")+" · "+unread+" unread"),"matched/unread counts ignore null/scalar and ineligible tasks: "+ui.count.text);
   check(ui.count.text.contains(scope+" · All projects"),"scope/all-project context "+scope);
   check(ui.count.text.contains("Sorted by "+modes[m]+" · "+directions[m][reversed?1:0]),"actual sort direction "+ui.count.text);
   check(!ui.count.text.contains("Search applied"),"empty query is not applied search");
   List<JSONObject> expected=new ArrayList<>();for(int i=0;i<a.tasks.length();i++){JSONObject t=a.tasks.optJSONObject(i);if(t!=null&&PresentationPolicy.matches(t,scope,"","",LocalDate.now()))expected.add(t);}
   Collections.sort(expected,(l,r)->PresentationPolicy.compare(l,r,a.sortOrder,reversed,LocalDate.now(),java.time.ZoneId.systemDefault()));
   List<JSONObject> actual=new ArrayList<>();for(TasksUi.Entry e:ui.entries)if(e.task!=null)actual.add(e.task);
   equal(expected,actual,"original task references and order match frozen production policy");
   TaskRowView recycled=new TaskRowView(a,null);int rowCount=0;for(int i=0;i<ui.entries.size();i++)if(ui.entries.get(i).task!=null){
    View bound=ui.adapter.getView(i,recycled,ui.list);check(bound==recycled,"actual Rows binding reuses TaskRowView");check(recycled.task==actual.get(rowCount++),"actual Rows binds exact canonical task object");equal(modes[m].equals("Project"),recycled.grouped,"Project row binding flag preserved");
   }
   equal(before,a.tasks.toString(),"render never rewrites tasks");equal("untouched draft",a.draft,"render preserves draft");
   check(a.events.stream().allMatch(e->e.equals("removeCallback")),"render must not navigate, save or request");
   for(View v:descendants(ui.list.headers.get(0)))if(v instanceof TextView&&v!=ui.count)check(!((TextView)v).text.startsWith("Sorted by "),"no redundant standalone order label");
  }
  String longProject="  Project identité 東京 👩‍💻 "+String.join("",Collections.nCopies(30,"long-project-name "))+"END  ";
  MainActivity a=new MainActivity();a.showSearch=true;a.projectFilter=longProject;a.query=" \t\n ";a.tasks.put(task("needle",false).put("project",longProject));TasksUi ui=build(a);
  check(ui.count.text.contains(longProject),"exact long project identity retained without truncation");check(!ui.count.text.contains("Search applied"),"whitespace-only query follows trimmed matching semantics");equal(" \t\n ",a.query,"render preserves raw whitespace query");
  EditText search=null;for(View v:descendants(ui.list.headers.get(0)))if(v instanceof EditText)search=(EditText)v;
  check(search!=null,"actual search is attached");search.input("  needle  ");
  check(ui.count.text.contains("Search applied")&&ui.count.text.startsWith("1 task · 0 unread"),"bound search listener updates matched context");equal("  needle  ",a.query,"raw applied query retained");
  search.input("absent");check(ui.count.text.startsWith("0 tasks · 0 unread"),"zero matches update live context");
  for(boolean online:new boolean[]{false,true})for(boolean busy:new boolean[]{false,true}){
   MainActivity state=new MainActivity();state.online=online;state.busy=busy;TasksUi view=build(state);
   equal(online&&!busy,view.create.enabled,"exact Add task gate");equal(!online,view.count.text.contains("Read only"),"offline is labelled read only");
   check(!view.count.text.toLowerCase(Locale.ROOT).contains("refresh")&&!view.count.text.toLowerCase(Locale.ROOT).contains("loading"),"generic busy is not invented refresh/loading");
  }
 }
 static View emptyRow(TasksUi ui){equal(1,ui.adapter.getCount(),"empty adapter has only empty entry");View row=ui.adapter.getView(0,null,ui.list);ui.list.addView(row);return row;}
 static List<String> texts(View root){List<String> out=new ArrayList<>();for(View v:descendants(root))if(v instanceof TextView&&!(v instanceof Button))out.add(((TextView)v).text);return out;}
 static Button button(View root,String label){for(View v:descendants(root))if(v instanceof Button&&((Button)v).text.equals(label))return (Button)v;return null;}
 static void onlineEmpty()throws Exception{
  MainActivity a=new MainActivity();TasksUi ui=build(a);View row=emptyRow(ui);
  equal(Arrays.asList("No tasks yet","Add a task to get started."),texts(row),"online empty list has direct creation recovery");
  check(button(row,"Clear filters")==null&&button(row,"Connection settings")==null,"default online empty does not invent extra actions");
  a.events.clear();ui.create.click();equal(Arrays.asList("navigate:NEW"),a.events,"actual Add task callback");
  for(String scope:new String[]{"Active","Today","Week","All","Trash"})for(String query:new String[]{""," \t ","  missing  "})for(String project:new String[]{"","absent"}){
   MainActivity state=new MainActivity();state.filter=scope;state.query=query;state.projectFilter=project;state.sortOrder="Due date";state.sortReverse=true;
   // Hidden canonical data: terminal task is excluded from Active, wrong project/query from All.
   if(!scope.equals("All")||!query.trim().isEmpty()||!project.isEmpty())state.tasks.put(task("done",false).put("status","completed"));
   String before=state.tasks.toString();TasksUi view=build(state);View empty=emptyRow(view);
   boolean narrowed=!scope.equals("Active")||!query.trim().isEmpty()||!project.isEmpty()||state.tasks.length()>0;
   equal(narrowed?"No tasks in this view":"No tasks yet",texts(empty).get(0),"online scoped/filtered empty differs from empty list");
   boolean clearExpected=!query.isEmpty()||!project.isEmpty()||!scope.equals("Active");Button clear=button(empty,"Clear filters");equal(clearExpected,clear!=null,"exact existing raw-query/project/scope Clear predicate");
   check(button(empty,"Connection settings")==null,"online has no connection settings empty action");equal(before,state.tasks.toString(),"empty render preserves canonical JSON");
   check(state.events.stream().allMatch(e->e.equals("removeCallback")),"empty render triggers no actions");
   if(clear!=null){
    state.events.clear();view.anchor.remember("id",-30,7);view.restore=()->{};clear.click();
    equal(Arrays.asList("captureDraft:"+query+":"+project+":"+scope,"removeCallback","save:::Active:true","render"),state.events,"actual Clear callback order: capture before reset/save/render");
    equal("",state.query,"Clear query");equal("",state.projectFilter,"Clear project");equal("Active",state.filter,"Clear scope");equal("",view.anchor.anchorId,"Clear resets task anchor");equal(0,view.anchor.anchorTop,"Clear resets anchor offset");
    equal("Due date",state.sortOrder,"Clear keeps sort");check(state.sortReverse,"Clear keeps direction");equal("untouched draft",state.draft,"Clear preserves draft seam");equal(before,state.tasks.toString(),"Clear never mutates canonical tasks");
   }
  }
  MainActivity malformed=new MainActivity();malformed.tasks.put(JSONObject.NULL).put("not a task").put(7).put(new JSONArray());TasksUi invalid=build(malformed);
  equal("No tasks yet",texts(emptyRow(invalid)).get(0),"invalid/null task entries do not make empty list a filtered result");
 }
 static void offlineEmpty()throws Exception{
  MainActivity missing=new MainActivity();missing.online=false;TasksUi absent=build(missing);View unavailable=emptyRow(absent);
  equal(Arrays.asList("Task list unavailable","No saved task list is available. Check Connection settings to reconnect."),texts(unavailable),"offline without task-array evidence must not claim an empty saved list");
  for(Object raw:new Object[]{JSONObject.NULL,"invalid",new JSONObject(),Integer.valueOf(4),new JSONArray(),new JSONArray().put(JSONObject.NULL).put("invalid").put(7).put(new JSONArray()),new JSONArray().put(task("completed",true).put("status","completed"))})for(boolean busy:new boolean[]{false,true}){
   MainActivity a=new MainActivity();a.online=false;a.busy=busy;a.snapshot.put("tasks",raw);a.tasks=raw instanceof JSONArray?(JSONArray)raw:new JSONArray();
   String snapshot=a.snapshot.toString(),tasks=a.tasks.toString();TasksUi ui=build(a);View row=emptyRow(ui);
   String expected=!(raw instanceof JSONArray)?"Task list unavailable":a.tasks.optJSONObject(0)!=null?"No saved tasks in this view":"No saved tasks";
   equal(expected,texts(row).get(0),"snapshot shape and actual objects distinguish unavailable/empty/filtered saved data");
   if(expected.equals("No saved tasks"))equal("The saved task list is empty. Reconnect to check for current tasks.",texts(row).get(1),"empty cache does not claim live server is empty");
   if(expected.equals("No saved tasks in this view"))equal("Try another view or clear filters. Reconnect to check for current tasks.",texts(row).get(1),"cached filter result offers view and connection recovery");
   check(ui.count.text.contains("Read only"),"offline empty context read only");check(!ui.create.enabled,"offline Add disabled even if not busy");
   String copy=String.join(" ",texts(row))+ui.count.text;for(String claim:new String[]{"Refreshing","Loading","queued","sign in","network failed"})check(!copy.contains(claim),"no invented operational claim: "+claim);
   check(a.events.stream().allMatch(e->e.equals("removeCallback")),"offline rendering has no callbacks");
   Button settings=button(row,"Connection settings");check(settings!=null&&settings.enabled,"Connection settings available even offline/busy");a.events.clear();settings.click();equal(Arrays.asList("settings"),a.events,"actual Settings callback only");
   equal(snapshot,a.snapshot.toString(),"snapshot untouched");equal(tasks,a.tasks.toString(),"tasks untouched");equal("untouched draft",a.draft,"draft untouched");
  }
  for(String scope:new String[]{"Active","Today","Week","All","Trash"})for(String project:new String[]{"","unmatched"}){
   MainActivity a=new MainActivity();a.online=false;a.filter=scope;a.projectFilter=project;a.query="  absent  ";a.tasks.put(task("available",true));a.snapshot.put("tasks",a.tasks);TasksUi ui=build(a);View row=emptyRow(ui);
   equal("No saved tasks in this view",texts(row).get(0),"saved matching availability differs from filtered saved result");
   Button clear=button(row,"Clear filters");check(clear!=null,"offline filtered Clear available");a.events.clear();ui.restore=()->{};clear.click();
   equal(Arrays.asList("captureDraft:  absent  :"+project+":"+scope,"removeCallback","save:::Active:true","render"),a.events,"offline Clear preserves exact incumbent callback order");
  }
 }
 static void preservation()throws Exception{
  for(String reason:new String[]{"busy","editing","touching","scrolling","paused","dialog"}){
   MainActivity a=new MainActivity();a.tasks.put(task("kept",true));TasksUi ui=build(a);String old=ui.count.text;int notifications=ui.adapter.notifications;JSONObject identity=ui.entries.get(ui.entries.size()-1).task;
   a.tasks.put(task("deferred",false));
   if(reason.equals("busy"))a.busy=true;else if(reason.equals("editing"))a.editing=true;else if(reason.equals("touching"))ui.touching=true;else if(reason.equals("scrolling"))ui.scrolling=true;else if(reason.equals("paused"))ui.paused=true;else a.dialogClosers.put("dialog","open");
   ui.refresh();check(ui.pendingRefresh,"actual refresh stays deferred during "+reason);equal(old,ui.count.text,"context does not invent refresh status while deferred "+reason);equal(notifications,ui.adapter.notifications,"no adapter rebind during deferral");check(ui.entries.get(ui.entries.size()-1).task==identity,"deferred list preserves object identity");
   a.busy=a.editing=ui.touching=ui.scrolling=ui.paused=false;a.dialogClosers.clear();ui.refresh();check(!ui.pendingRefresh,"settled refresh clears pending flag");check(ui.count.text.startsWith("2 tasks · 1 unread"),"settled refresh reports actual matches");
  }
 }
 static void blocker(MainActivity a,String reason,boolean blocked){
  a.busy=blocked&&reason.equals("busy");a.editing=blocked&&reason.equals("editing");a.dialogClosers.clear();
  if(blocked&&reason.equals("dialog"))a.dialogClosers.put("dialog","open");
 }
 static void retainedRebuild()throws Exception{
  for(boolean online:new boolean[]{true,false})for(String reason:new String[]{"idle","busy","editing","dialog"}){
   MainActivity a=new MainActivity();a.online=online;a.sortOrder="Due date";a.sortReverse=true;
   a.projectFilter="Alpha";a.query="kept";a.tasks.put(task("kept",true));a.snapshot.put("tasks",a.tasks);
   TasksUi ui=build(a);String expected="1 task · 1 unread\nActive · Alpha · Search applied\nSorted by Due date · Latest"+(online?"":"\nRead only");
   equal(expected,ui.count.text,"initial retained context control");
   TextView oldCount=ui.count;ListView oldList=ui.list;List<TasksUi.Entry> retained=new ArrayList<>(ui.entries);
   ui.anchor.remember("kept",-37,4);ui.anchorReset=false;ui.restore=()->{throw new AssertionError("obsolete restore must be cancelled by actual pause");};oldList.post(ui.restore);
   blocker(a,reason,true);int captures=ui.captures,restores=ui.restores;
   ui.build(a.vertical());
   check(ui.count!=oldCount&&descendant(ui.list.headers.get(0),ui.count),"new context attached to replacement header "+reason);
   equal(expected,ui.count.text,"retained rebuild keeps exact context "+online+"/"+reason);
   equal(1,ui.emphasis.pauses,"actual pause executed");check(ui.restore==null&&oldList.callbacks.isEmpty(),"actual pause cancels old restore/deferred work");
   check(!ui.paused,"replacement build resumes presenter");equal("kept",ui.anchor.anchorId,"retained task anchor");equal(-37,ui.anchor.anchorTop,"retained anchor top");equal(4,ui.anchor.anchorPosition,"retained anchor position");
   boolean blocked=!reason.equals("idle");equal(blocked,ui.pendingRefresh,"unchanged filters retain deferral "+reason);
   if(blocked){
    equal(retained,ui.entries,"retained entry identities, not data rebind");equal(0,ui.adapter.notifications,"new adapter not notified while blocked");equal(captures+1,ui.captures,"only existing build capture during deferral");equal(restores,ui.restores,"no anchor restore added during deferral");
    a.tasks.put(task("kept-new",false));ui.list.drainOnce();
    equal(expected,ui.count.text,"actual scheduled retry still describes retained rows");equal(retained,ui.entries,"retry retains exact entries");equal(1,ui.list.callbacks.size(),"one replacement retry scheduled");
    blocker(a,reason,false);ui.list.drainOnce();
    equal("2 tasks · 1 unread\nActive · Alpha · Search applied\nSorted by Due date · Latest"+(online?"":"\nRead only"),ui.count.text,"actual deferred callback settles to new rows");
    equal(1,ui.adapter.notifications,"settling rebind happens once");equal(0,ui.list.callbacks.size(),"settled retry removed");
   }
   check(!ui.pendingRefresh,"settled presenter no pending refresh");equal(online,ui.create.enabled,"settled Add gate");
  }
 }
 static void deferredConnectionTransitions()throws Exception{
  for(String reason:new String[]{"busy","editing","dialog"})for(boolean startOnline:new boolean[]{true,false})for(int size:new int[]{0,1,2}){
   MainActivity a=new MainActivity();a.online=startOnline;a.filter="All";a.projectFilter="  Alpha 東京  ";a.query="  kept  ";a.sortOrder="Project";a.sortReverse=true;
   for(int i=0;i<size;i++)a.tasks.put(task("kept-"+i,i==0).put("project",a.projectFilter));a.snapshot.put("tasks",a.tasks);
   TasksUi ui=build(a);List<TasksUi.Entry> retained=new ArrayList<>(ui.entries);JSONArray prior=a.tasks;String before=prior.toString();
   String expected=size+(size==1?" task":" tasks")+" · "+(size==0?0:1)+" unread\nAll ·   Alpha 東京   · Search applied\nSorted by Project · Z–A";
   equal(expected+(startOnline?"":"\nRead only"),ui.count.text,"connection transition initial control");
   // Pending snapshot disagrees in both task count and unread count with retained rows.
   a.tasks=new JSONArray().put(task("kept-next",false).put("project",a.projectFilter));a.snapshot.put("tasks",a.tasks);
   blocker(a,reason,true);a.online=!startOnline;ui.build(a.vertical());
   equal(expected+(a.online?"":"\nRead only"),ui.count.text,"replacement header uses retained rows and CURRENT online state "+reason);
   equal(a.online&&!a.busy,ui.create.enabled,"rebuild Add gate follows current online/busy state");
   for(boolean online:new boolean[]{startOnline,!startOnline,startOnline}){
    a.online=online;ui.list.drainOnce();
    equal(expected+(online?"":"\nRead only"),ui.count.text,"scheduled deferred retry updates only connection disclosure "+reason);
    equal(online&&!a.busy,ui.create.enabled,"deferred Add gate cannot advertise stale online state "+reason);
    equal(retained,ui.entries,"connection state must not force data rebind");equal(0,ui.adapter.notifications,"no notifications before deferral settles");
    equal(before,prior.toString(),"retained snapshot never mutated");
   }
   blocker(a,reason,false);ui.list.drainOnce();
   equal("1 task · 0 unread\nAll ·   Alpha 東京   · Search applied\nSorted by Project · Z–A"+(a.online?"":"\nRead only"),ui.count.text,"settled header adopts pending snapshot");
   check(!ui.pendingRefresh&&ui.adapter.notifications==1,"single settled data rebind");equal(a.online,ui.create.enabled,"settled Add gate");
  }
 }
 static void changedFiltersAndFirstBuildControls()throws Exception{
  for(boolean online:new boolean[]{true,false})for(String reason:new String[]{"busy","editing","dialog"}){
   MainActivity fresh=new MainActivity();fresh.online=online;blocker(fresh,reason,true);fresh.tasks.put(task("first",false));TasksUi first=build(fresh);
   equal("1 task · 0 unread\nActive · All projects\nSorted by Project · A–Z"+(online?"":"\nRead only"),first.count.text,"blocked first build still binds uninitialized filters");
   check(!first.pendingRefresh&&first.adapter.notifications==1,"first build binds exactly once");equal(online&&!fresh.busy,first.create.enabled,"first build Add gate");
   for(String change:new String[]{"scope","project","query","sort","direction","color"}){
    MainActivity a=new MainActivity();a.online=online;a.sortOrder="Due date";a.sortReverse=true;
    a.tasks.put(task("kept",true)).put(task("done",true).put("status","completed").put("project","Beta"));TasksUi ui=build(a);
    a.tasks.put(task("kept-new",false));blocker(a,reason,true);ui.anchor.remember("kept",-29,3);ui.anchorReset=false;
    int shown=2,unread=1;String scope="Active",project="All projects",search="",sort="Due date",direction="Latest";
    if(change.equals("scope")){a.filter=scope="All";shown=3;unread=2;}
    if(change.equals("project")){a.projectFilter=project="Beta";shown=unread=0;}
    if(change.equals("query")){a.query="kept-new";search=" · Search applied";shown=1;unread=0;}
    if(change.equals("sort")){a.sortOrder=sort="Project";direction="Z–A";}
    if(change.equals("direction")){a.sortReverse=false;direction="Soonest";}
    if(change.equals("color"))a.colorMode="Priority";
    ui.build(a.vertical());
    equal(shown+(shown==1?" task":" tasks")+" · "+unread+" unread\n"+scope+" · "+project+search+"\nSorted by "+sort+" · "+direction+(online?"":"\nRead only"),ui.count.text,"changed "+change+" preserves existing immediate refresh contract under "+reason);
    check(!ui.pendingRefresh&&ui.adapter.notifications==1,"changed filters bypass deferral as before");
    List<JSONObject> actual=new ArrayList<>();for(int i=0;i<ui.entries.size();i++)if(ui.entries.get(i).task!=null){TaskRowView row=(TaskRowView)ui.adapter.getView(i,null,ui.list);check(row.task==ui.entries.get(i).task,"actual Rows binds current entry");actual.add(row.task);}
    equal(shown,actual.size(),"context count equals actually bound rows");
    if(change.equals("scope")||change.equals("project")||change.equals("query")){equal("",ui.anchor.anchorId,"scope/project/query resets anchor as before");equal(0,ui.anchor.anchorTop,"reset top");}
    else{equal("kept",ui.anchor.anchorId,"sort/color keeps retained anchor");equal(-29,ui.anchor.anchorTop,"sort/color keeps offset");}
    equal(online&&!a.busy,ui.create.enabled,"changed-filter Add gate");
   }
  }
 }
 public static void main(String[] args)throws Exception{
  if(args[0].equals("attachment"))attachment();else if(args[0].equals("context"))context();else if(args[0].equals("online-empty"))onlineEmpty();else if(args[0].equals("offline-empty"))offlineEmpty();else if(args[0].equals("preservation"))preservation();else if(args[0].equals("retained-rebuild"))retainedRebuild();else if(args[0].equals("connection-transitions"))deferredConnectionTransitions();else if(args[0].equals("changed-filter-controls"))changedFiltersAndFirstBuildControls();else throw new AssertionError("Unknown case "+args[0]);
  System.out.println("Task context "+args[0]+": "+checks+" host checks passed (actual methods; UI seams, not native proof)");
 }
}

package com.simon.mytasks;
import java.util.*;
import java.time.LocalDate;
import org.json.*;
/** Host View attachment/property seams, queued synthetic transport, explicit dialog consent.
 * Whole production builders, field bindings, capture/save/refresh and bound callbacks execute.
 * Render resets Views and calls buildDetails; lifecycle/geometry are covered independently by M5.
 * No Android pixels, real browser, storage, service, network or accessibility runtime. */
public class TaskReaderEditorHarness {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void equal(Object want,Object got,String why){check(Objects.equals(want,got),why+" expected="+want+" actual="+got);}
 static class Shape {int fill,stroke,radius;Shape(int f,int s,int r){fill=f;stroke=s;radius=r;}}
 interface Click {void click(View v);}
 static class View {
  static final int VISIBLE=0,GONE=8,ACCESSIBILITY_LIVE_REGION_POLITE=1;
  static int nextId;int id,visibility=VISIBLE,liveRegion,minHeight;boolean enabled=true,saveEnabled=true;String contentDescription="";Object background,params;ViewGroup parent;Click click;MainActivity a;
  View(MainActivity a){this.a=a;}static int generateViewId(){return ++nextId;}
  void setId(int n){id=n;}int getId(){return id;}void setEnabled(boolean b){enabled=b;}boolean isEnabled(){return enabled;}
  void setSaveEnabled(boolean b){saveEnabled=b;}void setVisibility(int n){visibility=n;}void setAccessibilityLiveRegion(int n){liveRegion=n;}
  void setBackground(Object o){background=o;}void setBackgroundColor(int c){background=new Shape(c,0,0);}void setPadding(int l,int t,int r,int b){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b;}
  void setContentDescription(String s){contentDescription=s;}void setMinimumHeight(int n){minHeight=n;}void setMinimumWidth(int n){}
  void setOnClickListener(Click c){click=c;}void performClick(){if(enabled&&visibility==VISIBLE&&click!=null)click.click(this);}
  // Deterministic geometry seam: natural sizes are fixture inputs, not Android pixels.
  interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
  static class MeasureSpec {static final int EXACTLY=0x40000000,AT_MOST=0x80000000,UNSPECIFIED=0;static int makeMeasureSpec(int size,int mode){return size|mode;}static int getMode(int spec){return spec&0xc0000000;}static int getSize(int spec){return spec&0x3fffffff;}}
  List<OnLayoutChangeListener> layoutListeners=new ArrayList<>();int width=320,height=80,naturalHeight=80,measuredHeight=80,paddingLeft,paddingTop,paddingRight,paddingBottom;boolean attached=true;
  // AOSP API35 View: LAYOUT_REQUIRED dispatches even at unchanged bounds; force clears AFTER listeners.
  boolean forceLayout=true,layoutRequired=true;int lastW=Integer.MIN_VALUE,lastH=Integer.MIN_VALUE;
  // Ordered host seam: size callback and gated descendant clear precede child layout/listeners.
  // This models the observed modern/layout-valid path, not exact API37 framework execution.
  boolean focused,modernFocusClear=true,layoutValidForSizeChange=true;int measuredWidth;
  void addOnLayoutChangeListener(OnLayoutChangeListener l){layoutListeners.add(l);}void layout(int w,int h){int ow=width,oh=height;width=w;height=h;if(ow!=w||oh!=h){onSizeChanged(w,h,ow,oh);if(a.orderedTraversal&&modernFocusClear&&layoutValidForSizeChange&&(w==0||h==0)&&findFocus()!=null){a.order.add("zero-frame-clear");clearHostFocus();a.focusClears++;}}if(ow!=w||oh!=h||layoutRequired){layoutRequired=false;onLayout();for(OnLayoutChangeListener l:new ArrayList<>(layoutListeners)){a.listenerCalls++;if(a.orderedTraversal&&this==a.detailPane)a.order.add("pane-listener");l.onLayoutChange(this,0,0,w,h,0,0,ow,oh);}}forceLayout=false;}
  protected void onLayout(){}protected void onSizeChanged(int w,int h,int ow,int oh){}
  void clearHostFocus(){focused=false;if(this instanceof ViewGroup)for(View v:((ViewGroup)this).children)v.clearHostFocus();}
  boolean needsMeasure(int w,int h){return forceLayout||w!=lastW||h!=lastH;}void measured(int w,int h){lastW=w;lastH=h;layoutRequired=true;}
  void measure(int w,int h){if(needsMeasure(w,h)){onMeasure(w,h);measured(w,h);}}protected void onMeasure(int w,int h){measuredWidth=MeasureSpec.getSize(w);measuredHeight=MeasureSpec.getMode(h)==MeasureSpec.EXACTLY?MeasureSpec.getSize(h):MeasureSpec.getMode(h)==MeasureSpec.AT_MOST?Math.min(naturalHeight,MeasureSpec.getSize(h)):naturalHeight;}int getMeasuredWidth(){return measuredWidth;}int getMeasuredHeight(){return measuredHeight;}int getPaddingLeft(){return paddingLeft;}int getPaddingTop(){return paddingTop;}int getPaddingRight(){return paddingRight;}int getPaddingBottom(){return paddingBottom;}
  Object getLayoutParams(){return params;}void setLayoutParams(Object p){params=p;}boolean isAttachedToWindow(){return attached;}
  // ViewRootImpl admits first-pass requests; second-pass requests defer without setting FORCE_LAYOUT.
  void requestLayout(){a.frameRequests++;if(a.inLayout){if(!a.requesters.contains(this))a.requesters.add(this);if(a.secondPass)return;}forceLayout=true;for(View p=parent;p!=null&&!p.forceLayout;p=p.parent)p.forceLayout=true;}
  void forceLayout(){forceLayout=true;}void invalidate(){}
  void post(Runnable r){a.main.post(r);}ViewGroup getParent(){return parent;}int getWidth(){return width;}int getHeight(){return height;}View findFocus(){if(focused)return this;if(this instanceof ViewGroup)for(View v:((ViewGroup)this).children){View f=v.findFocus();if(f!=null)return f;}return null;}
 }
 static class ViewGroup extends View {
  List<View> children=new ArrayList<>();ViewGroup(MainActivity a){super(a);}int getChildCount(){return children.size();}View getChildAt(int n){return children.get(n);}void removeView(View v){check(children.remove(v),"remove attached child");v.parent=null;requestLayout();}void addView(View v,int i,Object p){check(v.parent==null,"single parent");v.parent=this;v.params=p;children.add(i,v);requestLayout();}void removeAllViews(){for(View v:children)v.parent=null;children.clear();}void addView(View v){addView(v,null);}void addView(View v,Object p){check(v.parent==null,"single parent");v.parent=this;v.params=p;children.add(v);requestLayout();}
 }
 static class LinearLayout extends ViewGroup {
  static final int VERTICAL=1;int orientation;void setOrientation(int n){orientation=n;}
  protected void onMeasure(int w,int h){super.onMeasure(w,h);if(a.orderedTraversal&&this==a.detailPane){a.order.add("super-measure");a.superMeasures++;a.measureSpecs.add(w+":"+h);int fixed=paddingTop+paddingBottom;for(View v:new ArrayList<>(children))if(v!=a.detailScroll){v.measure(w,0);fixed+=occupied(v);}if(a.detailScroll!=null){a.detailScroll.measuredWidth=measuredWidth;a.detailScroll.measuredHeight=Math.max(0,measuredHeight-fixed);}}}
  protected void onLayout(){if(a.orderedTraversal&&this==a.detailPane){a.order.add("child-frames");for(View v:new ArrayList<>(children))v.layout(measuredWidth,v==a.detailScroll?v.measuredHeight:occupied(v));}}
  LinearLayout(MainActivity a){super(a);}static class LayoutParams {int width,height,topMargin,bottomMargin,leftMargin,rightMargin;float weight;LayoutParams(int w,int h){width=w;height=h;}LayoutParams(int w,int h,float n){this(w,h);weight=n;}void setMargins(int l,int t,int r,int b){leftMargin=l;topMargin=t;rightMargin=r;bottomMargin=b;}}
 }
 static class FrameLayout extends ViewGroup {FrameLayout(MainActivity a){super(a);}static class LayoutParams extends LinearLayout.LayoutParams {LayoutParams(int w,int h){super(w,h);}}}
 static class GlassLayout extends FrameLayout {GlassLayout(MainActivity a){super(a);}}
 static class ScrollView extends FrameLayout {int y;ScrollView(MainActivity a){super(a);}void setFillViewport(boolean b){}protected void onSizeChanged(int w,int h,int ow,int oh){}int getScrollY(){return y;}}
 static class TextView extends View {
  String text="";int color,labelFor,maxLines=Integer.MAX_VALUE,minLines,gravity,autoLink;float size;boolean selectable,singleLine,links=true;Object ellipsize;
  TextView(MainActivity a){super(a);}void setText(CharSequence c){text=c.toString();requestLayout();invalidate();}CharSequence getText(){return text;}void setTextColor(int n){color=n;}void setTypeface(Object o){}
  void setTextIsSelectable(boolean b){selectable=b;}void setAutoLinkMask(int n){autoLink=n;}void setLinksClickable(boolean b){links=b;}
  void setGravity(int n){gravity=n;}void setLabelFor(int n){labelFor=n;}void setSingleLine(boolean b){singleLine=b;}void setMinLines(int n){minLines=n;requestLayout();invalidate();}void setMaxLines(int n){maxLines=n;requestLayout();invalidate();}void setEllipsize(Object o){ellipsize=o;}void setMinHeight(int n){minHeight=n;}
 }
 static class EditText extends TextView {
  int ime,inputType,selectionStart,selectionEnd,lineHeight=88,extraLineHeight,oneLineHeight=169,textLines=6;boolean selectAll;EditText(MainActivity a){super(a);}void measure(int w,int h){if(needsMeasure(w,h)){measuredHeight=singleLine?naturalHeight:oneLineHeight+(Math.min(maxLines,Math.max(minLines,textLines))-1)*(lineHeight+extraLineHeight);measured(w,h);}}int getLineHeight(){return lineHeight;}void setSelection(int start,int end){selectionStart=start;selectionEnd=end;}int getSelectionStart(){return selectionStart;}void setSelectAllOnFocus(boolean b){selectAll=b;}void setImeOptions(int n){ime=n;}void setInputType(int n){inputType=n;}void setMinWidth(int n){}int length(){return text.length();}int getSelectionEnd(){return selectionEnd;}Layout getLayout(){return new Layout(this);}void requestRectangleOnScreen(android.graphics.Rect r,boolean b){}
 }
 static class Layout {final EditText field;Layout(EditText f){field=f;}int getLineCount(){return field.textLines;}int getLineForOffset(int n){return 0;}int getLineTop(int n){return n==0?0:field.oneLineHeight+(n-1)*(field.lineHeight+field.extraLineHeight);}}
 static class Button extends TextView {String role="secondary";Button(MainActivity a){super(a);}}
 static class ImageButton extends Button {ImageButton(MainActivity a){super(a);}}
 static class Spinner extends View {static final int MODE_DIALOG=0;int selected;String prompt;ArrayAdapter<String> adapter;Spinner(MainActivity a,int mode){super(a);}void setPrompt(String s){prompt=s;}void setAdapter(ArrayAdapter<String> v){adapter=v;}void setSelection(int n){selected=n;}int getSelectedItemPosition(){return selected;}}
 static class ArrayAdapter<T> {ArrayAdapter(MainActivity a,int resource,String[] values){}public View getView(int p,View c,android.view.ViewGroup v){return null;}public View getDropDownView(int p,View c,android.view.ViewGroup v){return null;}}
 static class Gravity {static final int TOP=1,START=2,END=4,CENTER_VERTICAL=8;}
 static class Color {static final int TRANSPARENT=0;}
 static class Build {static class VERSION {static final int SDK_INT=35;}}
 static class android {
  static class R {static class layout {static final int simple_spinner_item=1;}}
  static class graphics {static class Typeface {static Object create(Object f,int w,boolean i){return f;}}static class Rect {int top,bottom;Rect(int l,int t,int r,int b){top=t;bottom=b;}int height(){return bottom-top;}}}
  static class view {static class ViewGroup extends TaskReaderEditorHarness.ViewGroup {ViewGroup(MainActivity a){super(a);}}static class inputmethod {static class EditorInfo {static final int IME_ACTION_NEXT=5,IME_FLAG_NO_EXTRACT_UI=268435456;}}}
  static class text {static class InputType {static final int TYPE_CLASS_DATETIME=4,TYPE_DATETIME_VARIATION_DATE=16;}}
  static class net {static class Uri {String raw;Uri(String s){raw=s;}static Uri parse(String s){return new Uri(s);}String getHost(){return java.net.URI.create(raw).getHost();}}}
 }
 static class R {static class drawable {static final int ic_chat=1,ic_calendar=2,ic_more=3,ic_back=4;}}
 static class Configuration {float fontScale=1;}
 static class Resources {Configuration config=new Configuration();Configuration getConfiguration(){return config;}}
 static class Ui {
  int surface=0xffffffff,soft=0xffe8effb,border=0xffced7e4;Object font=new Object();MainActivity a;Ui(MainActivity a){this.a=a;}
  Object shape(int f,int s,int r){return new Shape(f,s,r);}FrameLayout glassSurface(){return new GlassLayout(a);}void quiet(Button b){b.role="quiet";}void leadingIcon(Button b,int n){}void input(EditText e){}
  ImageButton icon(int icon,String label,Runnable action){ImageButton b=new ImageButton(a);b.contentDescription=label;b.setOnClickListener(v->action.run());return b;}
 }
 static class MarkdownText {static CharSequence render(SafeMarkdown.Document d){return d.text;}static CharSequence render(String s){return SafeMarkdown.parse(s).text;}}
 static class Dialog {void show(){}}
 interface DateListener {void changed(Object view,int y,int m,int d);}
 static class DatePickerDialog extends Dialog {DateListener listener;DatePickerDialog(MainActivity a,DateListener l,int y,int m,int d){listener=l;a.picker=this;}}
 static class Surfaces {long version=1;long generation(){return version;}boolean current(long n){return n==version;}}
 static class Intent {static final String ACTION_VIEW="VIEW",CATEGORY_BROWSABLE="BROWSABLE";String action,category;android.net.Uri uri;Intent(String s,android.net.Uri u){action=s;uri=u;}Intent addCategory(String s){category=s;return this;}}
 static class ActivityNotFoundException extends RuntimeException {}
 static class NativeSheets {static void menu(MainActivity a,String title,String[] labels,java.util.function.IntConsumer c){a.menu=c;a.menuLabels=labels;}}
 static class Queue {ArrayDeque<Runnable> queue=new ArrayDeque<>();void post(Runnable r){queue.add(r);}void execute(Runnable r){post(r);}void run(){while(!queue.isEmpty())queue.remove().run();}}
 static class Store {static class Account {String epoch="epoch";}boolean valid=true;boolean current(Account a){return valid;}int writes;void cache(Account a,JSONObject o){writes++;}}
 static class Api {
  static Queue IO=new Queue();static ArrayDeque<JSONObject> replies=new ArrayDeque<>();static List<String> calls=new ArrayList<>();static List<JSONObject> payloads=new ArrayList<>();static JSONObject body;static RuntimeException failure;
  Api(Store.Account a){}JSONObject call(String m,String p,JSONObject b){calls.add(m+" "+p);body=b;payloads.add(b==null?null:new JSONObject(b.toString()));if(failure!=null)throw failure;if(replies.isEmpty())throw new AssertionError("Missing synthetic reply");return replies.remove();}
  static String segment(String s){return s;}static String friendly(Exception e){return e.getMessage();}static class Failure extends RuntimeException {int code;Failure(int c){super("Synthetic failure");code=c;}}
 }
 static class AuthPolicy {enum State {FULL,LOGIN}}
 static class PollService {static void poll(MainActivity a,Store s,Store.Account o,java.util.function.BooleanSupplier stop){}}
 static class TasksUi {void refresh(){}void capture(){}void acceptOwnChange(String id,String token){}}
 static class ConversationsUi {MainActivity a;ConversationsUi(MainActivity a,Object c){this.a=a;}void buildTask(ViewGroup p,String id){a.events.add("conversation:"+id);}void capture(){}}
 static class Controller {void save(){}}
 static class Summary {void updateState(){}}
 static class MainActivity {
  boolean foreground=true,quietInFlight;final UiReadGate quietGate=new UiReadGate();Summary summaryUi;String destination="Tasks";LinearLayout root;int renders;
  Runnable taskEditorNoticeUpdate;boolean inLayout,secondPass;int frameRequests,listenerCalls;List<View> requesters=new ArrayList<>();
  boolean orderedTraversal;int focusClears,superMeasures;List<String> order=new ArrayList<>(),measureSpecs=new ArrayList<>();
  String selected="task-a",detailsMode="Details",banner="Connected",pendingSummary="";boolean online=true,busy,editing,ackNeedsRefresh,taskPresentationPending;int detailY,listY;
  int card=0xffffffff,ink=0xff17243b,muted=0xff536176,accent=0xff245cc6;Ui ui=new Ui(this);Resources resources=new Resources();Resources getResources(){return resources;}
  JSONObject draft,snapshot=new JSONObject();JSONArray tasks=new JSONArray();Store store=new Store();Store.Account account=new Store.Account();AuthPolicy.State authState=AuthPolicy.State.FULL;
  LinearLayout editor,detailPane;ScrollView detailScroll,listScroll;TextView stateLabel;EditText title,description,notes,project,due;Spinner priority,status;
  String[] priorities={"high","medium","low"},statuses={"inbox","todo","in_progress","waiting","completed","cancelled"};
  TaskDescriptionText.State descriptionState=new TaskDescriptionText.State();TasksUi tasksUi;Controller conversations=new Controller();ConversationsUi conversationUi;Queue main=new Queue();Surfaces surfaces=new Surfaces();
  List<String> events=new ArrayList<>();Runnable confirmation;String confirmTitle,confirmMessage;java.util.function.IntConsumer menu;String[] menuLabels;DatePickerDialog picker;Intent opened;
  float density=1;int dp(int n){return Math.round(n*density);}LinearLayout vertical(){return new LinearLayout(this);}LinearLayout horizontal(){return new LinearLayout(this);}
  TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.size=size;v.color=color;return v;}TextView heading(String s,int size){return label(s,size,ink);}
  Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b;}Button primary(String s,Runnable r){Button b=button(s,r);b.role="primary";return b;}
  void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}void rule(LinearLayout p){p.addView(new View(this));}void pair(LinearLayout p,Button l,Button r){LinearLayout row=horizontal();row.addView(l);row.addView(r);add(p,row);}
  void render(){renders++;root=vertical();// GLOBAL_STATUS_BINDING
   title=description=notes=project=due=null;priority=status=null;// TASK_PANE_BINDING
   root.addView(detailPane);detailScroll=null;editor=null;buildDetails();}
  void renderKeepingConversation(){render();}void restoreTaskPresentation(){events.add("restoreTaskPresentation");}void restoreDetailScroll(){events.add("restoreDetailScroll");}void captureTaskPresentation(){events.add("captureTaskPresentation");}int capturedDetailY(){return detailScroll.getScrollY();}void saveWorkspacePreferences(){}
  void confirm(String title,String message,Runnable action){confirmTitle=title;confirmMessage=message;confirmation=action;}void notice(String title,String message){events.add("notice:"+title+":"+message);}void navigate(String s){events.add("navigate:"+s);}void showTaskActions(JSONObject t){events.add("actions:"+t.optString("id"));}
  void openTaskConversation(String s){events.add("discuss:"+s);}void openEmailReply(String s,Object o){events.add("reply:"+s);}void maybeSummary(){events.add("review:"+pendingSummary);}void startActivity(Intent i){opened=i;}
  Dialog trackDialog(Dialog d){return d;}void invalidateQuietRead(){synchronized(Store.class){quietGate.invalidate();}quietInFlight=false;}boolean alive(Store.Account a){return a==account&&store.current(a)&&authState==AuthPolicy.State.FULL;}boolean isDestroyed(){return false;}void settings(){events.add("settings");}
// PRODUCTION_METHODS
 }
 static List<View> all(View root){List<View> list=new ArrayList<>();if(root==null)return list;list.add(root);if(root instanceof ViewGroup)for(View v:((ViewGroup)root).children)list.addAll(all(v));return list;}
 static TextView text(View root,String s){for(View v:all(root))if(v instanceof TextView&&((TextView)v).text.equals(s))return (TextView)v;throw new AssertionError("Missing attached text: "+s);}
 static Button button(View root,String s){return (Button)text(root,s);}
 static View icon(View root,String name){for(View v:all(root))if(v.contentDescription.equals(name))return v;throw new AssertionError("Missing icon "+name);}
 static boolean under(View child,View parent){for(View v=child;v!=null;v=v.parent)if(v==parent)return true;return false;}
 static String original(){return "**Exact canonical text**\n\n"+String.join(" ",Collections.nCopies(100,"Original detail."))+" FINAL CONDITION 東京";}
 static JSONObject task(){return new JSONObject().put("id","task-a").put("revision","revision-A").put("title","An exact task").put("description",original()).put("description_summary","**Action:** review this task.").put("notes","Exact notes").put("project","Project Alpha").put("priority","high").put("status","waiting").put("due_date","2030-06-07").put("created_at","2026-01-02T10:30:00Z").put("updated_at","2026-01-03T10:30:00Z").put("source",new JSONObject().put("type","email")).put("notification",new JSONObject().put("unread",true).put("token","token-A"));}
 static MainActivity setup(boolean edit){Api.IO=new Queue();Api.replies.clear();Api.calls.clear();Api.payloads.clear();Api.failure=null;Api.body=null;MainActivity a=new MainActivity();a.tasks.put(task());a.draft=a.copy(a.task(a.selected));a.editing=edit;return a;}
 static void order(){for(float scale:new float[]{1,1.6f}){MainActivity a=setup(false);a.resources.config.fontScale=scale;a.render();List<View> nodes=all(a.editor);check(nodes.indexOf(text(a.editor,"Due date"))<nodes.indexOf(text(a.editor,"Description · summary")),"Project/Priority/Status/Due date facts must precede long description");check(text(a.editor,"An exact task").selectable,"selectable title");}}
 static void groups(){
  for(boolean edit:new boolean[]{false,true})for(float scale:new float[]{1,1.6f}){
   MainActivity a=setup(edit);a.resources.config.fontScale=scale;a.render();int surfaces=0;
   for(View v:all(a.editor))if(v.background instanceof Shape){Shape s=(Shape)v.background;if(s.fill==0)continue;surfaces++;equal(12,s.radius,"reader/editor group and input corners use standard 12dp");equal(255,s.fill>>>24,"solid group surface");check(s.fill==a.card||s.fill==a.ui.soft,"existing Correspondence surface roles only");}
   check(surfaces>=5,"attached solid sections remain present");
  }
 }
 static void notice(){
  String[] banners={"Connected","Saving…","Task changed on server · your draft is preserved","Conflict · draft preserved — reload latest before saving","Synthetic failure A write may already have reached the server; refresh and check before retrying.","A long opaque status\n"+String.join(" ",Collections.nCopies(100,"Keep all details."))+" STATUS TAIL"};
  for(boolean fresh:new boolean[]{false,true})for(float scale:new float[]{1,1.6f})for(boolean online:new boolean[]{false,true})for(boolean busy:new boolean[]{false,true})for(String banner:banners){
   MainActivity a=setup(true);if(fresh){a.selected="NEW";a.draft=a.empty();}a.resources.config.fontScale=scale;a.online=online;a.busy=busy;a.banner=banner;String before=a.draft.toString();a.render();
   String guidance=fresh?"DRAFT · Not saved. Review all fields and enter a title before choosing Create task.":"Choose Save to save your changes. Changes are not saved automatically.";
   TextView guide=text(a.editor,guidance);check(under(guide,a.detailScroll),"explicit-save guidance belongs beside scrollable fields");check(all(a.editor).indexOf(guide)<all(a.editor).indexOf(a.title),"guidance before fields");
   TextView status=text(a.editor,banner);equal(!online||busy||banner.contains("preserved")?View.VISIBLE:View.GONE,status.visibility,"reuse exact existing visibility predicate, no error parsing");equal(View.ACCESSIBILITY_LIVE_REGION_POLITE,status.liveRegion,"local status is polite live region");
   for(TextView t:new TextView[]{guide,status}){check(!t.singleLine&&t.maxLines==Integer.MAX_VALUE&&t.ellipsize==null,"notice wraps without truncation");check(under(t,a.detailScroll),"notice survives hidden global chrome in local scroll content");}
   if(!online)text(a.editor,"Offline · saving is unavailable. Copy any changes you need before reloading the latest task.");
   equal(before,a.draft.toString(),"guidance does not mutate or capture draft");equal(0,Api.calls.size(),"notice never starts retry or reload");
  }
 }
 static void actions(){
  for(boolean edit:new boolean[]{false,true})for(boolean online:new boolean[]{false,true})for(boolean busy:new boolean[]{false,true})for(boolean dark:new boolean[]{false,true}){
   MainActivity a=setup(edit);a.online=online;a.busy=busy;if(dark){a.card=0xff1a2432;a.ink=0xffedf2f9;a.muted=0xffadbcd0;a.accent=0xffa9c7ff;a.ui.surface=a.card;}a.render();
   Button action=button(a.detailPane,edit?"Save":"Discuss with Hermes");ViewGroup wrapper=action.parent.parent;
   check(wrapper.getClass()==FrameLayout.class,"reader/editor actions use native FrameLayout, not GlassLayout");check(wrapper.parent==a.detailPane&&!under(wrapper,a.detailScroll),"opaque action wrapper stays outside scrolling fields");
   check(wrapper.background instanceof Shape,"opaque action background attached");Shape surface=(Shape)wrapper.background;equal(a.card,surface.fill,"existing solid surface role");equal(255,surface.fill>>>24,"opaque action fill");equal(12,surface.radius,"standard action group corners");
   LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)wrapper.params;equal(-1,p.width,"full-width action band");equal(-2,p.height,"wrapping action height");check(a.detailPane.children.indexOf(wrapper)>a.detailPane.children.indexOf(a.detailScroll),"action band follows scroll content");
   if(edit){Button cancel=button(a.detailPane,"Cancel");check(cancel.parent==action.parent,"Save/Cancel share action row");check(cancel.parent.children.indexOf(cancel)<cancel.parent.children.indexOf(action),"Cancel then Save");}else{a.events.clear();action.performClick();equal(Arrays.asList("discuss:task-a"),a.events,"actual Discuss callback retained even offline");}
   equal(0,Api.calls.size(),"action construction performs no transport");
  }
 }
 static void saveReload(){
  for(boolean fresh:new boolean[]{false,true})for(boolean blank:new boolean[]{false,true}){
   MainActivity a=setup(true);if(fresh){a.selected="NEW";a.draft=a.empty();}a.render();a.title.setText("  Exact entered title  ");a.description.setText(original());a.notes.setText(" raw notes \n");a.project.setText(blank?"   ":"  Trim project  ");a.due.setText(blank?"":"2032-03-04");a.priority.setSelection(2);a.status.setSelection(1);
   JSONObject saved=task().put("revision","revision-B").put("title","Server canonical title");Api.replies.add(saved);Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(saved)));
   button(a.detailPane,fresh?"Create task":"Save").performClick();check(a.busy,"actual save begins busy state");equal(View.VISIBLE,text(a.editor,"Saving…").visibility,"actual busy banner local");check(!a.title.enabled&&!button(a.detailPane,"Cancel").enabled,"busy rebuild disables fields and Cancel");Api.IO.run();
   equal(Arrays.asList((fresh?"POST /api/tasks":"PATCH /api/tasks/task-a"),"GET /api/tasks"),Api.calls,"only original mutation and authoritative refresh");JSONObject payload=Api.payloads.get(0);
   equal("  Exact entered title  ",payload.optString("title"),"title serialization unchanged");equal(original(),payload.optString("description"),"save consumes exact canonical full description");equal(" raw notes \n",payload.optString("notes"),"notes unchanged");equal("low",payload.optString("priority"),"selected priority");equal("todo",payload.optString("status"),"Save does not complete");
   if(blank){check(payload.isNull("project")&&payload.isNull("due_date"),"blank project/date are JSON null");}else{equal("Trim project",payload.optString("project"),"trim project only");equal("2032-03-04",payload.optString("due_date"),"date unchanged");}
   if(fresh)check(!payload.has("revision"),"create has no revision");else equal("revision-A",payload.optString("revision"),"exact optimistic revision");
   a.main.run();check(!a.busy&&!a.editing&&a.online,"successful save returns reader");equal("revision-B",a.draft.optString("revision"),"authoritative revision installed");text(a.editor,"Server canonical title");
  }
  for(boolean conflict:new boolean[]{false,true}){
   MainActivity a=setup(true);a.render();a.title.setText("Preserve local title");a.description.setText(original()+" LOCAL TAIL");Api.failure=conflict?new Api.Failure(409):new RuntimeException("Synthetic offline");button(a.detailPane,"Save").performClick();Api.IO.run();a.main.run();
   String expected=conflict?"Conflict · draft preserved — reload latest before saving":"Synthetic offline A write may already have reached the server; refresh and check before retrying.";
   equal(expected,a.banner,"original failure banner preserved verbatim");TextView status=text(a.editor,expected);equal(View.VISIBLE,status.visibility,"actual failed write notice attached and visible");equal("revision-A",a.draft.optString("revision"),"failure never advances revision");equal("Preserve local title",a.title.text,"failure retains entered title");equal(original()+" LOCAL TAIL",a.description.text,"failure retains complete description");equal(conflict,a.online,"original conflict versus uncertain network state");equal(1,Api.calls.size(),"no automatic write retry");
  }
  for(boolean failure:new boolean[]{false,true}){
   MainActivity a=setup(true);a.render();a.title.setText("Keep unless reload succeeds");icon(a.detailPane,"Task options").performClick();equal(Arrays.asList("Task actions","Reload latest task"),Arrays.asList(a.menuLabels),"existing menu route");a.menu.accept(1);equal("Reload latest task?",a.confirmTitle,"reload requires discard confirmation");equal(0,Api.IO.queue.size(),"no fetch before consent");equal("Keep unless reload succeeds",a.title.text,"confirmation retains input");
   if(failure)Api.failure=new RuntimeException("Synthetic reload offline");else Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision","revision-Z").put("description","Fresh authoritative description"))));
   a.confirmation.run();check(a.busy,"confirmed reload uses original refresh");Api.IO.run();a.main.run();
   equal(Arrays.asList("GET /api/tasks"),Api.calls,"reload GET only");equal(failure?"revision-A":"revision-Z",a.draft.optString("revision"),"reload installs revision only on success");equal(failure?"Keep unless reload succeeds":"An exact task",a.title.text,"failed reload retains input, success discards it");equal(failure?original():"Fresh authoritative description",a.description.text,"reload canonical text");
  }
  MainActivity invalid=setup(true);invalid.render();invalid.title.setText(" ");button(invalid.detailPane,"Save").performClick();equal(0,Api.IO.queue.size(),"invalid title never submits");check(invalid.events.contains("notice:Check task fields:Title is required"),"validation route unchanged");
 }
 static void routes(){
  MainActivity a=setup(false);a.draft.put("source","Synthetic email").put("source_url","https://example.org/source?record=synthetic");a.render();text(a.editor,"Synthetic email");button(a.editor,"Open source · example.org").performClick();check(a.opened==null,"browser never opens before confirmation");equal("Open source?",a.confirmTitle,"source confirmation retained");check(a.confirmMessage.contains("Your task-server token is never included."),"source token disclosure retained");a.confirmation.run();equal("https://example.org/source?record=synthetic",a.opened.uri.raw,"exact supplied URL");equal(Intent.CATEGORY_BROWSABLE,a.opened.category,"browser category");equal(0,Api.calls.size(),"source open is not a task API request");
  a=setup(false);a.online=false;a.render();text(a.editor,"Cached task · read only. Refresh to make changes.");check(!button(a.detailPane,"Edit").enabled,"offline reader Edit disabled");
  a=setup(false);a.draft.put("deleted",true);a.render();text(a.editor,"In trash");button(a.editor,"See more").performClick();button(a.editor,"Original text").performClick();text(a.editor,original());Api.replies.add(task().put("revision","restored"));Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision","restored"))));button(a.editor,"Restore task").performClick();Api.IO.run();equal("POST /api/tasks/task-a/restore",Api.calls.get(0),"trash Restore callback unchanged");equal("revision-A",Api.payloads.get(0).optString("revision"),"restore exact revision");a.main.run();
  a=setup(false);a.draft=null;a.tasks=new JSONArray();a.render();text(a.detailPane,"This task is not in your cache. Reload latest task to load it.");check(a.detailScroll==null,"missing cache does not invent editor");icon(a.detailPane,"Task options").performClick();a.menu.accept(1);equal("Reload latest task?",a.confirmTitle,"missing cache recovery reachable");
  a=setup(false);a.detailsMode="Hermès";a.render();check(a.events.contains("conversation:task-a")&&a.editor==null,"conversation-only route bypasses details");
  a=setup(false);a.draft.put("description","");a.render();text(a.editor,"Due date");for(View v:all(a.editor))if(v instanceof Button)check(!((Button)v).text.equals("See more"),"description-less route no stale controls");
  a=setup(true);a.render();button(a.detailPane,"Cancel").performClick();check(a.confirmation==null&&!a.editing,"clean cancel needs no discard confirmation");
  a=setup(true);a.selected="NEW";a.draft=a.empty();a.render();a.title.setText("unsaved new");button(a.detailPane,"Cancel").performClick();check(a.confirmation!=null,"new dirty Cancel confirms");a.confirmation.run();equal("",a.selected,"new draft Cancel returns list selection");check(a.draft==null,"new draft discarded after explicit consent");
 }
 static void preservation(){
  MainActivity reader=setup(false);String before=reader.draft.toString();reader.render();equal(Collections.singletonList("restoreDetailScroll"),reader.events,"reading triggers no action");equal(0,Api.calls.size(),"render never requests");
  text(reader.editor,"Project Alpha");text(reader.editor,"High");text(reader.editor,"Waiting");text(reader.editor,"Notes");text(reader.editor,"Source");text(reader.editor,"Activity");
  button(reader.editor,"See more").performClick();button(reader.editor,"Original text").performClick();TextView raw=text(reader.editor,original());check(raw.selectable&&!raw.links&&raw.autoLink==0,"canonical selectable inert original");equal(before,reader.draft.toString(),"reading leaves canonical data/revision/unread untouched");
  button(reader.detailPane,"Discuss with Hermes").performClick();check(reader.events.contains("discuss:task-a"),"bound Discuss identity");button(reader.editor,"Reply to email…").performClick();check(reader.events.contains("reply:task-a"),"bound Reply identity");button(reader.editor,"Review").performClick();check(reader.events.contains("review:task-a"),"explicit Review route");
  for(float scale:new float[]{1,1.6f})for(boolean online:new boolean[]{true,false})for(boolean busy:new boolean[]{true,false}){
   MainActivity a=setup(true);a.resources.config.fontScale=scale;a.online=online;a.busy=busy;a.render();boolean enabled=online&&!busy;
   equal("An exact task",a.title.text,"exact title");equal(original(),a.description.text,"canonical editor description, never summary");equal("Exact notes",a.notes.text,"notes");equal("Project Alpha",a.project.text,"project");equal("2030-06-07",a.due.text,"date");equal(0,a.priority.selected,"priority reference");equal(3,a.status.selected,"status reference");
   for(View v:new View[]{a.title,a.description,a.notes,a.project,a.due,a.priority,a.status}){check(under(v,a.detailScroll),"all seven fields attached to scroll");equal(enabled,v.enabled,"field enablement");TextView caption=null;for(View c:all(a.editor))if(c instanceof TextView&&((TextView)c).labelFor==v.id)caption=(TextView)c;check(caption!=null,"accessible field label");}
   for(EditText e:new EditText[]{a.title,a.description,a.notes,a.project,a.due}){check(!e.saveEnabled,"no framework text autosave");check((e.ime&android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI)!=0,"retain no-extract IME");}
   check(a.description.maxLines==6&&a.notes.maxLines==6&&!a.description.singleLine&&a.title.singleLine,"field line contracts");equal(20,a.due.inputType,"native date input type");
   Button save=button(a.detailPane,"Save"),cancel=button(a.detailPane,"Cancel");equal(enabled,save.enabled,"save gate");equal(!busy,cancel.enabled,"cancel gate");check(!under(save,a.detailScroll)&&!under(cancel,a.detailScroll),"actions outside scrolling fields");equal("primary",save.role,"primary save");
   equal(Arrays.asList("restoreTaskPresentation","restoreDetailScroll"),a.events,"presentation restores retained");equal(0,Api.calls.size(),"editor render no network");
  }
  MainActivity a=setup(true);a.render();button(a.editor,"Choose date").performClick();check(a.picker!=null,"bound native picker");a.picker.listener.changed(null,2031,1,3);equal("2031-02-03",a.due.text,"picker callback");button(a.editor,"Clear date").performClick();equal("",a.due.text,"clear callback");a.surfaces.version++;a.picker.listener.changed(null,2040,0,1);equal("",a.due.text,"stale date picker revoked");
  a.title.setText("Unsaved local title");button(a.detailPane,"Cancel").performClick();equal("Discard unsaved changes?",a.confirmTitle,"dirty cancel confirmation");check(a.editing&&a.draft!=null,"cancel does not discard before consent");a.confirmation.run();check(!a.editing&&a.draft!=null,"cancel renders saved reader");equal("An exact task",a.draft.optString("title"),"cancel original saved title");
  MainActivity fresh=setup(true);fresh.selected="NEW";fresh.draft=fresh.empty();fresh.render();button(fresh.detailPane,"Create task");fresh.title.setText("New task");fresh.description.setText(original());fresh.captureDraft();equal(original(),fresh.draft.optString("description"),"create capture canonical text");
 }
 static TextView retainedNotice(MainActivity a){for(View v:all(a.editor))if(v instanceof TextView&&v.liveRegion==View.ACCESSIBILITY_LIVE_REGION_POLITE)return (TextView)v;throw new AssertionError("Missing attached feedback");}
 static void noticeOwnership(String mode){
  MainActivity a=setup(true);a.render();TextView old=retainedNotice(a);TextView offline=text(a.editor,"Offline · saving is unavailable. Copy any changes you need before reloading the latest task.");Runnable bound=a.taskEditorNoticeUpdate;
  EditText title=a.title;String draft=a.draft.toString();int renders=a.renders;
  switch(mode){
   case "root":a.root=a.vertical();break;
   case "editor":a.editor=a.vertical();break;
   case "task":a.selected="task-b";break;
   case "owner":a.account=new Store.Account();break;
   case "epoch":a.account.epoch="replacement-epoch";break;
   case "session":a.surfaces.version++;break;
   case "auth":a.authState=AuthPolicy.State.LOGIN;break;
   case "store":a.store.valid=false;break;
   case "detached":a.root.removeAllViews();break;
   case "notice-detached":old.parent.removeAllViews();break;
   case "reader":a.editing=false;break;
   case "destination":a.destination="Discussions";break;
   case "rebind":a.render();renders=a.renders;break;
   default:throw new AssertionError(mode);
  }
  a.banner="Conflict · draft preserved — reload latest before saving";a.online=false;bound.run();
  equal("Connected",old.text,"stale "+mode+" binding must not update previous notice");equal(View.GONE,old.visibility,"stale warning remains untouched");equal(View.GONE,offline.visibility,"stale binding must not change offline guidance");
  if(mode.equals("rebind")){equal("Connected",retainedNotice(a).text,"old binding cannot write replacement notice");a.updateTaskEditorNotice();equal(a.banner,retainedNotice(a).text,"replacement binding is live");}
  equal(draft,a.draft.toString(),"notice publication does not mutate draft");equal("An exact task",title.text,"notice does not write fields");equal(renders,a.renders,"notice cannot render");equal(0,Api.calls.size(),"notice cannot perform transport");equal(0,a.store.writes,"notice cannot write cache");equal(0,a.main.queue.size(),"notice cannot schedule work");
 }
 static void retainedTransition(String mode){
  MainActivity a=setup(true);
  if(mode.equals("recovery")){a.online=false;a.banner="Offline · saved content remains readable; refresh before editing";}
  if(mode.equals("clear")||mode.equals("render-control"))a.banner="Task changed on server · your draft is preserved";
  a.render();a.title.setText("Unsaved exact text  東京");a.description.setText(original()+" LOCAL TAIL");
  TextView notice=retainedNotice(a);EditText field=a.title;LinearLayout editor=a.editor;int renders=a.renders;boolean enabled=a.title.enabled;
  if(mode.equals("render-control")){a.applyStatusInsets(true);equal(View.GONE,a.stateLabel.visibility,"global chrome hidden");equal(a.banner,notice.text,"fresh render mirrors existing warning");equal(View.VISIBLE,notice.visibility,"fresh warning is local");equal(0,Api.calls.size(),"render does not fetch");return;}
  if(mode.startsWith("failure"))Api.failure=new RuntimeException("Synthetic quiet failure");
  else Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision",mode.equals("clear")?"revision-A":"revision-B").put("notes","remote changed"))));
  a.quietRefresh();equal(1,Api.IO.queue.size(),"actual quiet read scheduled");
  if(mode.equals("stale-control"))a.invalidateQuietRead();
  Api.IO.run();a.main.run();
  equal(Arrays.asList("GET /api/tasks"),Api.calls,"one synthetic GET, no mutation or retry");equal(mode.startsWith("failure")||mode.equals("stale-control")?0:1,a.store.writes,"unchanged quiet cache publication");
  equal(renders,a.renders,"quiet publication never forces render");check(field==a.title&&editor==a.editor&&notice==retainedNotice(a),"retain exact field/editor/notice instances");
  equal("Unsaved exact text  東京",a.title.text,"quiet read preserves exact local input");equal(original()+" LOCAL TAIL",a.description.text,"quiet read preserves canonical description");equal("revision-A",a.draft.optString("revision"),"optimistic revision unchanged");equal(enabled,a.title.enabled,"no enablement change even on recovery");
  a.applyStatusInsets(true);equal(View.GONE,a.stateLabel.visibility,"actual production IME visibility statement hides global chrome");
  String expected=mode.startsWith("failure")?"Offline · saved content remains readable; refresh before editing":mode.equals("clear")||mode.equals("stale-control")?"Connected":"Task changed on server · your draft is preserved";
  equal(expected,a.banner,"actual publication expected literal");equal(expected,notice.text,"retained notice mirrors changed quiet banner");equal(mode.equals("clear")||mode.equals("stale-control")?View.GONE:View.VISIBLE,notice.visibility,"current visibility with IME hidden global status");check(under(notice,a.detailScroll),"notice still attached beside fields");
  TextView offline=text(a.editor,"Offline · saving is unavailable. Copy any changes you need before reloading the latest task.");equal(mode.startsWith("failure")?View.VISIBLE:View.GONE,offline.visibility,"offline guidance clears on recovery without rebind");
  if(mode.equals("failure-guidance")){
   TextView guidance=text(a.editor,"Offline · saving is unavailable. Copy any changes you need before reloading the latest task.");equal(View.VISIBLE,guidance.visibility,"accurate offline guidance appears without rebuilding enabled fields");check(under(guidance,a.detailScroll),"offline guidance remains local");
   for(View v:all(a.editor))if(v instanceof TextView&&v.visibility==View.VISIBLE)check(!((TextView)v).text.contains("fields are read only"),"do not falsely claim retained fields disabled");
  }
 }
 static void retainedQueuedReplacement(String mode,boolean failure){
  MainActivity a=setup(true);a.render();a.title.setText("Queued local title");TextView old=retainedNotice(a);EditText field=a.title;
  if(failure)Api.failure=new RuntimeException("Synthetic queued failure");else Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision","revision-B"))));
  a.quietRefresh();Api.IO.run();equal(1,a.main.queue.size(),"actual response waiting on UI queue");
  switch(mode){
   case "ticket":a.invalidateQuietRead();break;
   case "owner":a.account=new Store.Account();a.store.valid=false;break;
   case "auth":a.authState=AuthPolicy.State.LOGIN;break;
   case "root":a.root=a.vertical();break;
   case "editor":a.editor=a.vertical();break;
   case "task":a.selected="task-b";break;
   case "session":a.surfaces.version++;break;
   case "detached":a.root.removeAllViews();break;
   case "rebind":a.captureDraft();a.render();break;
   default:throw new AssertionError(mode);
  }
  int renders=a.renders;a.main.run();a.applyStatusInsets(true);
  equal("Connected",old.text,"queued "+mode+" result cannot write obsolete notice");equal(View.GONE,old.visibility,"obsolete notice visibility unchanged");equal(View.GONE,a.stateLabel.visibility,"global IME-hidden control");
  if(mode.equals("rebind")){TextView current=retainedNotice(a);check(current!=old,"builder replaced notice");equal(failure?"Offline · saved content remains readable; refresh before editing":"Task changed on server · your draft is preserved",current.text,"current replacement editor receives admitted result");equal(View.VISIBLE,current.visibility,"new notice visible locally");}
  equal(renders,a.renders,"callback does not force render");equal("Queued local title",field.text,"no writing into old field");equal("Queued local title",a.title.text,"no writing into current field");equal("revision-A",a.draft.optString("revision"),"no optimistic revision advance");
  equal(Arrays.asList("GET /api/tasks"),Api.calls,"only original quiet GET");equal(failure?0:1,a.store.writes,"original pre-UI cache count");equal(0,Api.IO.queue.size(),"no follow-up scheduled");equal(0,a.main.queue.size(),"UI drained without retry");
 }
 static void retainedRoundTrip(){
  MainActivity a=setup(true);a.render();a.title.setText("Round trip exact input");TextView notice=retainedNotice(a),offline=text(a.editor,"Offline · saving is unavailable. Copy any changes you need before reloading the latest task.");
  EditText field=a.title;int renders=a.renders;
  for(String mode:new String[]{"error","recovery","clear","unchanged"}){
   Api.failure=mode.equals("error")?new RuntimeException("Synthetic loss"):null;
   if(!mode.equals("error"))Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision",mode.equals("recovery")?"revision-B":"revision-A").put("notes","remote"))));
   a.quietRefresh();Api.IO.run();a.main.run();a.applyStatusInsets(true);
   String expected=mode.equals("error")?"Offline · saved content remains readable; refresh before editing":mode.equals("recovery")?"Task changed on server · your draft is preserved":"Connected";
   equal(expected,notice.text,"same notice tracks round trip "+mode);equal(mode.equals("error")||mode.equals("recovery")?View.VISIBLE:View.GONE,notice.visibility,"round trip warning visibility");equal(mode.equals("error")?View.VISIBLE:View.GONE,offline.visibility,"round trip offline guidance");equal(View.GONE,a.stateLabel.visibility,"IME stays hidden");
   check(field==a.title&&notice==retainedNotice(a),"round trip keeps bindings");equal("Round trip exact input",a.title.text,"typing survives every transition");check(a.title.enabled,"inherited retained enablement unchanged");equal("revision-A",a.draft.optString("revision"),"revision retained every transition");equal(renders,a.renders,"no forced render in round trip");
  }
  equal(Arrays.asList("GET /api/tasks","GET /api/tasks","GET /api/tasks","GET /api/tasks"),Api.calls,"round trip reads only");equal(3,a.store.writes,"unchanged successful read cache behavior");
 }
 static void retainedCurrentPredicate(){
  MainActivity a=setup(true);a.render();TextView notice=retainedNotice(a);String draft=a.draft.toString();EditText title=a.title;int renders=a.renders;
  for(boolean online:new boolean[]{true,false})for(boolean busy:new boolean[]{false,true})for(String banner:new String[]{"Connected","Saving…","Conflict · draft preserved — reload latest before saving","opaque failed status without preservation", "A write may already have reached the server; refresh and check before retrying.\nFULL TAIL"}){
   a.online=online;a.busy=busy;a.banner=banner;a.updateTaskEditorNotice();a.applyStatusInsets(true);
   equal(banner,notice.text,"verbatim live banner without parsing or truncation");equal(!online||busy||banner.contains("preserved")?View.VISIBLE:View.GONE,notice.visibility,"incumbent local predicate only");equal(View.GONE,a.stateLabel.visibility,"global hidden for all live state permutations");check(!notice.singleLine&&notice.maxLines==Integer.MAX_VALUE&&notice.ellipsize==null,"live warning still wraps");
   equal(draft,a.draft.toString(),"presentation helper never captures or mutates draft");check(title==a.title&&title.enabled,"no rebind/enablement change");equal(renders,a.renders,"no rendering");
  }
  equal(0,Api.calls.size(),"no transport");equal(0,a.store.writes,"no cache");equal(0,a.main.queue.size(),"no scheduling");
 }
 // Allocation oracle models vertical fixed siblings + weighted scroll, not the adaptive decision.
 static int occupied(View v){LinearLayout.LayoutParams p=v.params instanceof LinearLayout.LayoutParams?(LinearLayout.LayoutParams)v.params:null;return (p!=null&&p.height>=0?p.height:v.naturalHeight)+(p==null?0:p.topMargin+p.bottomMargin);}
 static void allocate(MainActivity a){int fixed=a.detailPane.paddingTop+a.detailPane.paddingBottom;for(View v:a.detailPane.children)if(v!=a.detailScroll)fixed+=occupied(v);a.detailScroll.height=Math.max(0,a.detailPane.height-fixed);}
 static View[] geometry(MainActivity a){View top=a.detailPane.getChildAt(0),gap=a.detailPane.getChildAt(1),footer=button(a.detailPane,a.selected.equals("NEW")?"Create task":"Save").parent.parent;top.naturalHeight=139;gap.naturalHeight=8;footer.naturalHeight=154;a.title.naturalHeight=170;a.description.naturalHeight=609;a.notes.naturalHeight=609;return new View[]{top,gap,footer};}
 static void resize(MainActivity a,int height){a.detailPane.layout(640,height);allocate(a);}
 static void adaptiveViewport(){
  for(boolean online:new boolean[]{true,false})for(boolean busy:new boolean[]{false,true}){
   MainActivity a=setup(true);a.online=online;a.busy=busy;a.resources.config.fontScale=2;a.render();View[] chrome=geometry(a);EditText input=a.title;String draft=a.draft.toString();List<String> events=new ArrayList<>(a.events);int renders=a.renders;
   resize(a,1000);equal(687,a.detailScroll.height,"ample height keeps original fixed header/actions allocation");check(chrome[0].parent==a.detailPane&&chrome[2].parent==a.detailPane,"ample chrome stays fixed");
   // Retained diagnosis: pane281, fixed header147, title170. Footer-only relocation leaves134.
   a.title.setText("Exact local title 東京");resize(a,281);check(a.detailScroll.height>=170,"short pane must fit the entire 170px title rather than zero viewport");check(under(chrome[0],a.detailScroll)&&under(chrome[2],a.detailScroll),"both header and actions scroll only when fixed chrome cannot fit");
   equal(281,a.detailScroll.height,"adaptive viewport receives the whole available pane");check(a.editor.getChildAt(0)==chrome[0]&&a.editor.getChildAt(1)==chrome[1]&&a.editor.getChildAt(a.editor.getChildCount()-1)==chrome[2],"adaptive header/gap precede guidance; actions follow all fields");
   for(View field:new View[]{a.title,a.description,a.notes,a.project,a.due,a.priority,a.status})check(under(field,a.detailScroll),"all seven original fields stay in scroll");check(input==a.title,"never replace the active input");equal("Exact local title 東京",input.text,"resize never replaces writing");equal(online&&!busy,input.enabled,"resize preserves natural enablement");
   resize(a,1000);equal(687,a.detailScroll.height,"growing restores original allocation");check(chrome[0].parent==a.detailPane&&chrome[1].parent==a.detailPane&&chrome[2].parent==a.detailPane,"fixed arrangement restored");equal(draft,a.draft.toString(),"no draft capture/mutation on layout");equal(events,a.events,"no presentation restore, lifecycle or business callbacks on resize");equal(renders,a.renders,"no forced render");equal(0,Api.calls.size(),"no transport");
  }
  MainActivity reader=setup(false);reader.render();View header=reader.detailPane.getChildAt(0);reader.detailPane.layout(640,281);check(header.parent==reader.detailPane,"reader arrangement unchanged");
 }
 static void adaptiveMultiline(){
  for(boolean enabled:new boolean[]{true,false})for(int viewport:new int[]{528,440,281}){
   MainActivity a=setup(true);a.online=enabled;a.resources.config.fontScale=2;a.render();geometry(a);a.description.setText(original()+"\nLOCAL TAIL");a.notes.setText(original()+"\nNOTES TAIL");a.description.setSelection(2,a.description.length());a.notes.setSelection(3,a.notes.length());
   EditText description=a.description,notes=a.notes;String draft=a.draft.toString();int ime=description.ime,inputType=description.inputType;float size=description.size;
   resize(a,viewport==281?281:viewport+313);equal(viewport,a.detailScroll.height,"independent retained viewport fixture");
   for(EditText field:new EditText[]{description,notes}){field.measure(640,0);check(field.getMeasuredHeight()<=viewport,"entire multiline widget must fit viewport "+viewport+" instead of609");check(field.getMeasuredHeight()>=field.oneLineHeight,"at least a full font-scaled line plus padding");check(field.maxLines>=1&&field.maxLines<=6&&field.minLines==Math.min(2,field.maxLines),"retain preferred min2/max6 where feasible");equal(enabled,field.enabled,"sizing does not enable disabled inputs");check(field.getSelectionEnd()==field.length(),"selection still reaches exact canonical tail");}
   equal(original()+"\nLOCAL TAIL",description.text,"complete description text retained");equal(original()+"\nNOTES TAIL",notes.text,"complete notes retained");equal(2,description.getSelectionStart(),"description range starts unchanged");equal(3,notes.getSelectionStart(),"notes range starts unchanged");equal(ime,description.ime,"IME options unchanged");equal(inputType,description.inputType,"input type unchanged");equal(size,description.size,"no font shrinking");check(description==a.description&&notes==a.notes,"same multiline references");equal(draft,a.draft.toString(),"revision/draft untouched");
   resize(a,1200);for(EditText field:new EditText[]{description,notes}){equal(6,field.maxLines,"restore six-line preference when space returns");equal(2,field.minLines,"restore minimum two lines");field.measure(640,0);equal(609,field.getMeasuredHeight(),"natural full six-line measured height restored");}
  }
 }
 static void adaptiveOwnership(String mode){
  MainActivity a=setup(true);a.render();View[] chrome=geometry(a);LinearLayout pane=a.detailPane,content=a.editor;EditText title=a.title,body=a.description,memo=a.notes;View.OnLayoutChangeListener listener=pane.layoutListeners.get(0);resize(a,1000);String draft=a.draft.toString();int renders=a.renders;
  switch(mode){
   case "root":a.root=a.vertical();break;case "pane":a.detailPane=a.vertical();break;case "editor":a.editor=a.vertical();break;case "scroll":a.detailScroll=new ScrollView(a);break;
   case "title":a.title=new EditText(a);break;case "description":a.description=new EditText(a);break;case "notes":a.notes=new EditText(a);break;
   case "owner":a.account=new Store.Account();break;case "epoch":a.account.epoch="new-epoch";break;case "session":a.surfaces.version++;break;case "auth":a.authState=AuthPolicy.State.LOGIN;break;case "store":a.store.valid=false;break;case "task":a.selected="task-b";break;case "reader":a.editing=false;break;case "destination":a.destination="Discussions";break;
   case "detached":a.root.removeAllViews();break;case "content-detached":content.parent.removeView(content);break;case "window-detached":a.root.attached=false;break;
   case "header-detached":pane.removeView(chrome[0]);break;case "gap-detached":pane.removeView(chrome[1]);break;case "actions-detached":pane.removeView(chrome[2]);break;case "field-detached":content.removeView(body);break;
   case "rebind":a.render();renders=a.renders;break;default:throw new AssertionError(mode);
  }
  List<View> oldPane=new ArrayList<>(pane.children),oldContent=new ArrayList<>(content.children);pane.height=281;
  listener.onLayoutChange(pane,0,0,640,281,0,0,640,1000);
  equal(oldPane,pane.children,"stale "+mode+" callback cannot rearrange pane");equal(oldContent,content.children,"stale callback cannot rearrange old editor");equal(6,body.maxLines,"stale callback cannot resize previous description");equal(6,memo.maxLines,"stale callback cannot resize previous notes");equal(draft,a.draft.toString(),"stale callback cannot mutate draft");equal(renders,a.renders,"stale callback cannot render");equal(0,Api.calls.size(),"stale callback no transport");
 }
 static void adaptiveNullOwner(){MainActivity a=setup(true);a.account=null;a.render();geometry(a);resize(a,281);equal(281,a.detailScroll.height,"null-account synthetic presentation remains adaptive without weakening notice owners");check(a.taskEditorNoticeUpdate!=null,"notice binding retained");equal(2,a.description.maxLines,"null-owner presentation uses real geometry too");}
 static void adaptiveMeasuredLines(){MainActivity a=setup(true);a.render();geometry(a);a.description.extraLineHeight=50;a.notes.extraLineHeight=50;resize(a,841);for(EditText field:new EditText[]{a.description,a.notes}){field.measure(640,0);check(field.getMeasuredHeight()<=528,"font fallback/line spacing must use measured widget height, not nominal line-height arithmetic");equal(3,field.maxLines,"three actual445px lines fit; four583px lines do not");}}
 static void adaptiveControls(){
  for(boolean fresh:new boolean[]{true,false}){
   MainActivity a=setup(true);if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}a.render();View[] chrome=geometry(a);View[] fields={a.title,a.description,a.notes,a.project,a.due,a.priority,a.status};a.title.setText("Exact unsaved title");a.title.setSelection(2,8);a.description.textLines=1;
   resize(a,0);check(chrome[0].parent==a.detailPane,"unmeasured pane deferred");resize(a,281);equal(281,a.detailScroll.height,"first short layout adapts, including NEW without editing flag");
   // Start short, then type a six-line value without resizing the pane: capacity must already fit.
   a.description.setText(original());a.description.textLines=6;a.description.measure(640,0);check(a.description.getMeasuredHeight()<=281,"typing into previously short text stays within the budget");
   for(int height:new int[]{1000,281,753,281,1000}){resize(a,height);check(a.title==fields[0]&&a.description==fields[1]&&a.notes==fields[2]&&a.project==fields[3]&&a.due==fields[4]&&a.priority==fields[5]&&a.status==fields[6],"all seven binding identities survive round trips");equal(2,a.title.getSelectionStart(),"title selection start unchanged");equal(8,a.title.getSelectionEnd(),"title selection end unchanged");}
   a.detailPane.setPadding(5,11,5,17);resize(a,309);equal(281,a.detailScroll.height,"budget uses content height after pane padding");
   button(a.editor,"Choose date").performClick();a.picker.listener.changed(null,2033,2,4);equal("2033-03-04",a.due.text,"date picker after adaptive relocation");button(a.editor,"Clear date").performClick();equal("",a.due.text,"Clear after adaptive relocation");
   Button save=button(a.detailPane,fresh?"Create task":"Save"),cancel=button(a.detailPane,"Cancel");check(under(save,a.detailScroll)&&under(cancel,a.detailScroll),"original callbacks accessible in adaptive action band");
   Api.replies.add(task().put("revision","revision-B"));Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision","revision-B"))));save.performClick();Api.IO.run();JSONObject payload=Api.payloads.get(0);equal("Exact unsaved title",payload.optString("title"),"adaptive Save consumes exact writing");equal(original(),payload.optString("description"),"adaptive Save consumes entire description");check(payload.isNull("due_date"),"adaptive date null semantics unchanged");equal(fresh?"POST /api/tasks":"PATCH /api/tasks/task-a",Api.calls.get(0),"original Save route");if(!fresh)equal("revision-A",payload.optString("revision"),"adaptive optimistic revision");a.main.run();
  }
  MainActivity a=setup(true);a.render();geometry(a);resize(a,281);a.title.setText("Dirty short-window writing");button(a.detailPane,"Cancel").performClick();equal("Discard unsaved changes?",a.confirmTitle,"adaptive Cancel still confirms");check(a.editing,"no discard before consent");a.confirmation.run();check(!a.editing,"confirmed Cancel still returns reader");
 }
 static void adaptiveFractionalDensity(){for(float density:new float[]{1,1.25f,2.625f}){MainActivity a=setup(true);a.density=density;a.render();View[] chrome=geometry(a);resize(a,2000);check(chrome[0].parent==a.detailPane&&chrome[2].parent==a.detailPane,"ample fractional-density layout stays fixed");resize(a,281);equal(281,a.detailScroll.height,"short viewport budgets actual pixels at density "+density);for(EditText field:new EditText[]{a.description,a.notes}){field.measure(640,0);check(field.getMeasuredHeight()<=281,"fractional dp rounding cannot overrun viewport");}resize(a,2000);equal(6,a.description.maxLines,"large viewport restores preference at every density");}}
 // Independent review probes: Android scheduling/measurement are HOST SEAMS.
 static void settledStable(int paneHeight, boolean enabled, boolean fresh){
  MainActivity a=setup(true);a.online=enabled;if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}
  a.render();geometry(a);a.detailPane.width=640;a.detailPane.height=paneHeight;
  String before=a.draft.toString();EditText body=a.description;String writing=body.text;
  int frames=0,totalCallbacks=0;boolean pending=true;
  for(;frames<12&&pending;frames++){
   a.frameRequests=0;a.listenerCalls=0;
   settledMeasure(a.root,640);a.inLayout=true;a.secondPass=false;settledLayout(a.root);a.inLayout=false;
   List<View> valid=new ArrayList<>();for(View v:a.requesters)if(v.forceLayout)valid.add(v);a.requesters.clear();
   for(View v:valid)for(View n=v;n!=null&&n.forceLayout;n=n.parent)n.forceLayout=false;
   if(!valid.isEmpty()){
    for(View v:valid)v.requestLayout();settledMeasure(a.root,640);a.inLayout=true;a.secondPass=true;settledLayout(a.root);a.inLayout=false;a.secondPass=false;
   }
   pending=!a.requesters.isEmpty();totalCallbacks+=a.listenerCalls;
   System.out.println("frame="+frames+" pane="+paneHeight+" enabled="+enabled+" new="+fresh+" callbacks="+a.listenerCalls+" requests="+a.frameRequests+" nextFrame="+pending+" max="+body.maxLines+" min="+body.minLines+" measured="+body.measuredHeight+" viewport="+a.detailScroll.height);
   List<View> next=new ArrayList<>(a.requesters);a.requesters.clear();for(View v:next)v.requestLayout();
  }
  equal(before,a.draft.toString(),"draft/revision preserved even during scheduling probe");equal(writing,body.text,"writing unchanged");equal(enabled,body.enabled,"enablement unchanged");equal(0,Api.calls.size(),"no transport");
  check(!pending,"stable editor must settle; still requests next-frame layout after "+frames+" frames / "+totalCallbacks+" callbacks");
 }
 static void settledMeasure(View v,int width){
  int useWidth=width;
  if(v.parent!=null)useWidth=Math.max(1,width-v.parent.paddingLeft-v.parent.paddingRight);
  if(v==v.a.detailPane)useWidth=v.a.detailPane.width;
  // The pane is MATCH_PARENT in render: measure against the fixture's current
  // allocated height, not an UNSPECIFIED natural-height80 followed by layout281.
  int heightSpec=v==v.a.detailPane?View.MeasureSpec.makeMeasureSpec(v.height,View.MeasureSpec.EXACTLY):0;
  v.measure(View.MeasureSpec.makeMeasureSpec(useWidth,View.MeasureSpec.EXACTLY),heightSpec);
  if(v instanceof ViewGroup)for(View child:new ArrayList<>(((ViewGroup)v).children))settledMeasure(child,useWidth);
 }
 static void settledLayout(View v){
  if(v==v.a.detailPane)allocate(v.a);
  if(v instanceof ViewGroup)for(View child:new ArrayList<>(((ViewGroup)v).children))settledLayout(child);
  v.layout(v.width,v.height);
 }

 static void settledStaleMeasurement(boolean changedSpec){
  MainActivity a=setup(true);a.render();geometry(a);EditText body=a.description;
  body.setMinLines(2);body.setMaxLines(2);int spec=View.MeasureSpec.makeMeasureSpec(changedSpec?631:632,View.MeasureSpec.EXACTLY);body.measure(spec,0);body.layout(632,257);
  a.inLayout=true;a.secondPass=true;a.frameRequests=0;a.sizeTaskEditorMultiline(body,632,265);a.inLayout=false;a.secondPass=false;
  System.out.println("secondPass max="+body.maxLines+" min="+body.minLines+" measured="+body.measuredHeight+" requests="+a.frameRequests);
  equal(2,body.maxLines,"largest feasible capacity for same/changed specs");
  check(body.maxLines<=2,"capacity may not rise to six from stale 257px measurement under 265px budget");
 }

 // Request-aware controls and transition family; allocation/font sizes remain host inputs.
 static boolean settledFrame(MainActivity a){
  a.frameRequests=0;a.listenerCalls=0;
  settledMeasure(a.root,a.detailPane.width);a.inLayout=true;a.secondPass=false;settledLayout(a.root);a.inLayout=false;
  List<View> valid=new ArrayList<>();for(View v:a.requesters)if(v.forceLayout)valid.add(v);a.requesters.clear();
  for(View v:valid)for(View n=v;n!=null&&n.forceLayout;n=n.parent)n.forceLayout=false;
  if(!valid.isEmpty()){
   for(View v:valid)v.requestLayout();settledMeasure(a.root,a.detailPane.width);a.inLayout=true;a.secondPass=true;settledLayout(a.root);a.inLayout=false;a.secondPass=false;
  }
  boolean pending=!a.requesters.isEmpty();List<View> next=new ArrayList<>(a.requesters);a.requesters.clear();for(View v:next)v.requestLayout();return pending;
 }
 static void settledDrain(MainActivity a){
  boolean pending=true;int frames=0;while(pending&&frames++<12)pending=settledFrame(a);
  check(!pending,"transition must settle within bounded frames");
  a.detailPane.requestLayout();check(!settledFrame(a),"unchanged-bounds relayout must not start a feedback loop");
  check(a.listenerCalls>0,"control must deliver same-bounds LAYOUT_REQUIRED callback");equal(0,a.frameRequests,"settled callback must issue zero new requests");
 }
 static void settledTransitions(boolean fresh,boolean enabled,float density){
  MainActivity a=setup(true);a.online=enabled;a.density=density;if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}
  a.render();View[] chrome=geometry(a);View[] fields={a.title,a.description,a.notes,a.project,a.due,a.priority,a.status};
  String draft=a.draft.toString();int renders=a.renders;int ime=a.description.ime,type=a.description.inputType;float font=a.description.size;
  a.title.setText("Exact title 東京");a.title.setSelection(2,8);a.description.textLines=1;a.description.setText("Short body");a.notes.textLines=1;a.notes.setText("Short notes");
  a.detailPane.width=640;a.detailPane.height=281;settledDrain(a);
  equal(281,a.detailScroll.height,"first-short pane cannot allocate zero viewport");check(chrome[0].parent==a.editor&&chrome[2].parent==a.editor,"header and actions are scroll reachable");
  // Content growth at unchanged bounds must never grow beyond its pre-budgeted capacity.
  a.description.textLines=6;a.description.setText(original());a.notes.textLines=6;a.notes.setText(original()+" NOTES");settledDrain(a);
  check(a.description.measuredHeight<=281&&a.notes.measuredHeight<=281,"short-text growth fits the unchanged viewport");
  // Fallback geometry can change with text even though nominal line height stays constant.
  a.description.extraLineHeight=50;a.description.setText(original()+" 新");settledDrain(a);
  equal(1,a.description.maxLines,"same geometry with new fallback text must re-evaluate measured capacity");
  check(a.description.measuredHeight<=a.detailScroll.height,"changed text uses measured fallback capacity");
  a.detailPane.height=841;a.detailPane.requestLayout();settledDrain(a);
  for(int h:new int[]{2000,281,841,753,281,2000}){
   a.detailPane.height=h;a.detailPane.requestLayout();settledDrain(a);
   for(EditText f:new EditText[]{a.description,a.notes}){check(f.measuredHeight<=a.detailScroll.height,"settled field fits retained viewport");equal(Math.min(2,f.maxLines),f.minLines,"preferred minimum restored when feasible");}
   if(h==2000){equal(6,a.description.maxLines,"growth restores six measured lines");equal(6,a.notes.maxLines,"growth restores notes capacity");check(chrome[0].parent==a.detailPane&&chrome[2].parent==a.detailPane,"growth restores fixed chrome");}
  }
  a.detailPane.width=511;a.detailPane.requestLayout();check(!settledFrame(a),"changed specs also settle");check(a.frameRequests>0,"changed field width must re-evaluate capacity");settledDrain(a);
  for(int i=0;i<fields.length;i++)check(fields[i]==new View[]{a.title,a.description,a.notes,a.project,a.due,a.priority,a.status}[i],"all field identities retained");
  equal("Exact title 東京",a.title.text,"title unchanged");equal(2,a.title.selectionStart,"range start unchanged");equal(8,a.title.selectionEnd,"range end unchanged");
  equal(original()+" 新",a.description.text,"exact body including fallback glyphs");equal(original()+" NOTES",a.notes.text,"exact notes");equal(enabled,a.description.enabled,"natural disabled/enabled retained");
  equal(ime,a.description.ime,"IME configuration unchanged");equal(type,a.description.inputType,"input type unchanged");equal(font,a.description.size,"font unchanged");equal(draft,a.draft.toString(),"no draft/revision mutation");equal(renders,a.renders,"no forced render");equal(0,Api.calls.size(),"no transport");
  System.out.println("PASS settled transitions NEW="+fresh+" enabled="+enabled+" density="+density+" checks="+checks);
 }
 static void settledInactive(String mode){
  MainActivity a=setup(true);a.render();geometry(a);a.detailPane.width=640;a.detailPane.height=281;
  if(mode.equals("owner"))a.account=new Store.Account();else if(mode.equals("detached"))a.root.attached=false;else if(mode.equals("destination"))a.destination="Email";else if(mode.equals("generation"))a.surfaces.version++;
  a.frameRequests=0;a.requesters.clear();a.inLayout=true;for(View.OnLayoutChangeListener l:a.detailPane.layoutListeners)l.onLayoutChange(a.detailPane,0,0,640,281,0,0,640,281);a.inLayout=false;
  equal(0,a.frameRequests,"obsolete listener must not schedule layout");equal(6,a.description.maxLines,"obsolete listener must not resize");
  System.out.println("PASS inactive "+mode+" requests="+a.frameRequests);
 }
 static void settledReader(){MainActivity a=setup(false);a.render();equal(0,a.detailPane.layoutListeners.size(),"reader has no adaptive listener");System.out.println("PASS reader no adaptive listener");}

 // Clipboard/span storage and metrics below are host seams, not native paste proof.
 // The preserved independent challenge separately executes the AOSP paste/receiver.
 static void metricPasteCase(boolean fresh,String target,String mode){
  MainActivity a=setup(true);if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}
  a.render();geometry(a);a.detailPane.width=640;a.detailPane.height=281;
  View[] fields={a.title,a.description,a.notes,a.project,a.due,a.priority,a.status};
  EditText field=target.equals("description")?a.description:a.notes;
  String value="Same characters\nsecond\nthird\nfourth\nfifth\nsixth";field.setText(value);field.textLines=6;
  settledDrain(a);equal(2,field.maxLines,"initial two native-height lines fit");
  String draft=a.draft.toString();float font=field.size;int type=field.inputType,renders=a.renders;
  field.setSelection(0,field.length());String replacement=value+(mode.equals("changed")?" X":"");
  field.setText(replacement); // Complete-selection replacement, unchanged or changed characters.
  if(!mode.equals("plain")){field.extraLineHeight=44;field.oneLineHeight=213;}field.requestLayout();
  settledDrain(a);
  equal(replacement,field.text,"exact pasted writing");equal(draft,a.draft.toString(),"no capture/revision change");
  equal(font,field.size,"configured font unchanged");equal(type,field.inputType,"native input type unchanged");
  for(int i=0;i<fields.length;i++)check(fields[i]==new View[]{a.title,a.description,a.notes,a.project,a.due,a.priority,a.status}[i],"all seven field identities");
  equal(renders,a.renders,"no render");check(field.enabled,"native enablement retained");equal(0,Api.calls.size(),"no transport");
  System.out.println("metric paste NEW="+fresh+" field="+target+" mode="+mode+" max="+field.maxLines+" widget="+field.measuredHeight+" viewport="+a.detailScroll.height);
  check(field.measuredHeight<=a.detailScroll.height,"same-character styled replacement must recalibrate full widget");
  equal(mode.equals("plain")?2:1,field.maxLines,"largest feasible styled/plain capacity");
 }
 // Native span add/remove/replace/range/mutable-metric events are represented by
 // their measured output, deliberately without a RelativeSizeSpan type switch.
 static void metricChangeCase(boolean fresh,String target,boolean enabled,String mode){
  MainActivity a=setup(true);a.online=enabled;if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}
  a.render();geometry(a);a.detailPane.width=640;a.detailPane.height=281;
  EditText f=target.equals("description")?a.description:a.notes;
  String writing="Unchanged styled characters\nsecond\nthird\nfourth\nfifth\nsixth";f.setText(writing);f.setSelection(2,10);f.textLines=6;
  if(mode.equals("remove")||mode.equals("replace")||mode.equals("mutate")){f.oneLineHeight=213;f.extraLineHeight=44;}
  if(mode.equals("hidden-contraction")){f.extraLineHeight=100;}
  if(mode.equals("reflow")){f.textLines=1;}
  settledDrain(a);String draft=a.draft.toString();int renders=a.renders,ime=f.ime,type=f.inputType;float font=f.size;
  int oldHeight=f.measuredHeight;View[] fields={a.title,a.description,a.notes,a.project,a.due,a.priority,a.status};
  int want;
  switch(mode){
   case "remove":case "replace":case "mutate":f.oneLineHeight=169;f.extraLineHeight=0;want=2;break;
   case "hidden-contraction":equal(1,f.maxLines,"hidden second-line metric initially forbids two lines");f.extraLineHeight=0;want=2;break;
   case "reflow":f.textLines=6;f.extraLineHeight=100;want=1;break;
   case "nonmetric":want=2;break;
   case "range":f.extraLineHeight=100;want=1;break;
   case "add":case "second-pass":f.oneLineHeight=213;f.extraLineHeight=44;want=1;break;
   default:throw new AssertionError(mode);
  }
  if(mode.equals("hidden-contraction")){f.forceLayout();f.measure(View.MeasureSpec.makeMeasureSpec(632,View.MeasureSpec.EXACTLY),0);equal(oldHeight,f.measuredHeight,"current capped height unchanged; hidden line geometry must invalidate");}
  if(mode.equals("second-pass")){
   a.inLayout=true;a.secondPass=true;f.requestLayout();for(View.OnLayoutChangeListener l:a.detailPane.layoutListeners)l.onLayoutChange(a.detailPane,0,0,640,281,0,0,640,281);a.inLayout=false;a.secondPass=false;
  }else f.requestLayout();
  if(mode.equals("nonmetric")){check(!settledFrame(a),"nonmetric update settles");equal(0,a.frameRequests,"same metrics do not repeat line setters");}
  settledDrain(a);equal(want,f.maxLines,"changed native metrics choose feasible capacity: "+mode);
  check(f.measuredHeight<=a.detailScroll.height,"complete widget remains feasible: "+mode);
  equal(writing,f.text,"exact unchanged characters");equal(2,f.selectionStart,"range start");equal(10,f.selectionEnd,"range end");equal(enabled,f.enabled,"natural enablement");
  equal(draft,a.draft.toString(),"draft/revision untouched");equal(renders,a.renders,"no render");equal(font,f.size,"configured font");equal(type,f.inputType,"input type");equal(ime,f.ime,"IME options");equal(0,Api.calls.size(),"no transport");
  for(int i=0;i<fields.length;i++)check(fields[i]==new View[]{a.title,a.description,a.notes,a.project,a.due,a.priority,a.status}[i],"seven retained references");
  System.out.println("PASS metric change "+mode+" NEW="+fresh+" target="+target+" enabled="+enabled+" max="+f.maxLines+" measured="+f.measuredHeight);
 }
 static void metricOwnerCase(String mode){
  MainActivity a=setup(true);a.render();View[] chrome=geometry(a);LinearLayout pane=a.detailPane,content=a.editor;EditText body=a.description,memo=a.notes;View.OnLayoutChangeListener listener=pane.layoutListeners.get(0);resize(a,1000);
  switch(mode){
   case "root":a.root=a.vertical();break;case "pane":a.detailPane=a.vertical();break;case "editor":a.editor=a.vertical();break;case "scroll":a.detailScroll=new ScrollView(a);break;
   case "title":a.title=new EditText(a);break;case "description":a.description=new EditText(a);break;case "notes":a.notes=new EditText(a);break;
   case "owner":a.account=new Store.Account();break;case "epoch":a.account.epoch="new-epoch";break;case "session":a.surfaces.version++;break;case "auth":a.authState=AuthPolicy.State.LOGIN;break;case "store":a.store.valid=false;break;case "task":a.selected="task-b";break;case "reader":a.editing=false;break;case "destination":a.destination="Discussions";break;
   case "detached":a.root.removeAllViews();break;case "content-detached":content.parent.removeView(content);break;case "window-detached":a.root.attached=false;break;
   case "header-detached":pane.removeView(chrome[0]);break;case "gap-detached":pane.removeView(chrome[1]);break;case "actions-detached":pane.removeView(chrome[2]);break;case "field-detached":content.removeView(body);break;
   case "rebind":a.render();break;default:throw new AssertionError(mode);
  }
  body.extraLineHeight=100;memo.extraLineHeight=100;body.forceLayout=memo.forceLayout=false;a.frameRequests=0;int height=body.measuredHeight;
  listener.onLayoutChange(pane,0,0,640,1000,0,0,640,1000);
  equal(0,a.frameRequests,"obsolete metric change cannot request work");check(!body.forceLayout&&!memo.forceLayout,"ownership rejects before local forceLayout");equal(height,body.measuredHeight,"obsolete metrics are not measured");equal(6,body.maxLines,"obsolete field capacity untouched");
  System.out.println("PASS metric ownership "+mode);
 }
 // One ordered traversal uses independent weighted allocation, then real child size callbacks,
 // then the pane listener. No allocation or listener is injected before measurement.
 static void orderedFrame(MainActivity a,int width,int height){
  a.orderedTraversal=true;a.order.clear();a.measureSpecs.clear();a.superMeasures=0;
  a.detailPane.requestLayout();a.detailPane.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
  a.inLayout=true;a.detailPane.layout(width,height);a.inLayout=false;
  System.out.println("ORDER height="+height+" viewport="+a.detailScroll.height+" focus="+(a.root.findFocus()==a.title)+" clears="+a.focusClears+" measures="+a.superMeasures+" events="+a.order+" specs="+a.measureSpecs);
 }
 static void orderedShrink(){
  for(String mode:new String[]{"positive-height","layout-invalid","legacy","modern-zero"}){
   MainActivity c=setup(true);c.render();geometry(c);orderedFrame(c,640,1000);c.title.focused=true;
   c.detailScroll.layoutValidForSizeChange=!mode.equals("layout-invalid");c.detailScroll.modernFocusClear=!mode.equals("legacy");
   c.detailScroll.layout(640,mode.equals("positive-height")?170:0);
   equal(mode.equals("modern-zero")?1:0,c.focusClears,"host size-change gate "+mode);
   equal(!mode.equals("modern-zero"),c.root.findFocus()==c.title,"descendant focus gate "+mode);
  }
  MainActivity a=setup(true);a.render();geometry(a);orderedFrame(a,640,1000);a.title.focused=true;a.title.setSelection(2,8);
  orderedFrame(a,640,281);
  check(a.order.indexOf("child-frames")>a.order.indexOf("super-measure"),"super measurement precedes child frames");
  check(a.order.indexOf("pane-listener")>a.order.indexOf("child-frames"),"pane listener follows child frame assignment");
  check(a.root.findFocus()==a.title,"ordered shrink must preserve title focus before post-layout recovery; zero frame cleared descendant");
  equal(0,a.focusClears,"no destructive intermediate zero frame");equal(281,a.detailScroll.height,"first assigned short viewport already adapted");
  equal(2,a.title.selectionStart,"range start");equal(8,a.title.selectionEnd,"range end");
  check(a.superMeasures>=1&&a.superMeasures<=2,"bounded superclass measurement");for(String spec:a.measureSpecs)equal(a.measureSpecs.get(0),spec,"remeasure preserves incoming specs");
  System.out.println("PASS ordered shrink checks="+checks+" HOST SEAM, not native focus acceptance");
 }
 static void orderedMatrix(){
  int cases=0;
  for(boolean fresh:new boolean[]{true,false})for(boolean online:new boolean[]{true,false})for(boolean busy:new boolean[]{true,false})for(float density:new float[]{1,1.25f,2.625f})for(float scale:new float[]{1,2})for(int width:new int[]{320,511,640}){
   MainActivity a=setup(true);a.online=online;a.busy=busy;a.density=density;a.resources.config.fontScale=scale;if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}
   a.render();View[] chrome=geometry(a);View[] fields={a.title,a.description,a.notes,a.project,a.due,a.priority,a.status};
   a.title.setText("Exact title 東京");a.description.setText(original());a.notes.setText("Exact notes\nTAIL");a.project.setText("Exact project");a.due.setText("2033-02-01");a.priority.setSelection(2);a.status.setSelection(4);a.title.setSelection(2,8);
   String draft=a.draft.toString();int renders=a.renders,ime=a.description.ime,type=a.description.inputType;float font=a.description.size;
   a.title.focused=online&&!busy;a.description.textLines=1;
   // Stale large bounds on first measurement are deliberate; only incoming specs are current.
   a.detailPane.width=2000;a.detailPane.height=2000;
   for(int height:new int[]{281,1000,281,841,2000,281}){
    orderedFrame(a,width,height);
    check(a.detailScroll.height>=170,"every assigned viewport fits complete title, first-short and shrink/grow");
    equal(0,a.focusClears,"never assign a destructive zero frame");equal(online&&!busy,a.root.findFocus()==a.title,"retain focus when enabled; do not invent it on disabled fields");
    check(a.superMeasures>=1&&a.superMeasures<=2,"bounded measurement per traversal");for(String spec:a.measureSpecs)equal(a.measureSpecs.get(0),spec,"unchanged incoming specs");
    for(int i=0;i<fields.length;i++){check(fields[i]==new View[]{a.title,a.description,a.notes,a.project,a.due,a.priority,a.status}[i],"all seven original field identities");check(under(fields[i],a.detailScroll),"all fields remain attached to original scroll");equal(online&&!busy,fields[i].enabled,"unchanged natural enablement");}
    if(height==281){equal(281,a.detailScroll.height,"first assigned short viewport");check(chrome[0].parent==a.editor&&chrome[2].parent==a.editor,"same chrome scrolls when needed");}
    if(height==2000){check(chrome[0].parent==a.detailPane&&chrome[2].parent==a.detailPane,"grow restores fixed chrome");equal(6,a.description.maxLines,"restore preferred six lines");}
    a.description.textLines=6;a.description.requestLayout();
    a.description.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),0);
    check(a.description.measuredHeight<=a.detailScroll.height,"short-text growth remains within assigned viewport");
    orderedFrame(a,width,height); // Admit changed text-line metrics before the idle control.
    // Same-bounds requested layout must not repeat setters or schedule another pass.
    a.frameRequests=0;orderedFrame(a,width,height);equal(1,a.frameRequests,"only explicit test request, policy is settled");equal(1,a.superMeasures,"unchanged capacity needs no second super measure");check(a.requesters.isEmpty(),"post-layout callback schedules no further frame");
   }
   equal("Exact title 東京",a.title.text,"exact title");equal(original(),a.description.text,"complete description");equal("Exact notes\nTAIL",a.notes.text,"exact notes");equal("Exact project",a.project.text,"exact project");equal("2033-02-01",a.due.text,"exact date");equal(2,a.priority.selected,"exact priority");equal(4,a.status.selected,"exact status");equal(draft,a.draft.toString(),"revision/canonical draft unchanged");equal(2,a.title.selectionStart,"range start");equal(8,a.title.selectionEnd,"range end");equal(ime,a.description.ime,"IME untouched");equal(type,a.description.inputType,"input type untouched");equal(font,a.description.size,"font untouched");equal(renders,a.renders,"no render");equal(0,Api.calls.size(),"no transport");
   String guidance=fresh?"DRAFT · Not saved. Review all fields and enter a title before choosing Create task.":"Choose Save to save your changes. Changes are not saved automatically.";TextView g=text(a.editor,guidance);check(under(g,a.detailScroll)&&g.maxLines==Integer.MAX_VALUE&&g.ellipsize==null,"complete original guidance retained");
   cases++;
  }
  System.out.println("PASS ordered matrix cases="+cases+" checks="+checks);
 }
 static void orderedResolvedSpecs(){
  for(int mode:new int[]{View.MeasureSpec.EXACTLY,View.MeasureSpec.AT_MOST,View.MeasureSpec.UNSPECIFIED}){
   MainActivity a=setup(true);a.render();geometry(a);a.orderedTraversal=true;a.title.focused=true;a.detailPane.width=2000;a.detailPane.height=2000;a.detailPane.naturalHeight=309;a.detailPane.setPadding(5,11,5,17);
   int w=View.MeasureSpec.makeMeasureSpec(511,View.MeasureSpec.EXACTLY),h=View.MeasureSpec.makeMeasureSpec(mode==View.MeasureSpec.EXACTLY?309:2000,mode);
   a.detailPane.measure(w,h);equal(309,a.detailPane.getMeasuredHeight(),"resolved height, not stale bounds or AT_MOST maximum");a.inLayout=true;a.detailPane.layout(511,309);a.inLayout=false;
   equal(281,a.detailScroll.height,"current resolved content height accounts for padding before frame");equal(0,a.focusClears,"resolved first short frame never clears focus");equal(a.title,a.root.findFocus(),"same descendant focus");equal(2,a.superMeasures,"changed allocation remeasured once");for(String spec:a.measureSpecs)equal(w+":"+h,spec,"incoming mode and size unchanged for both super calls");
  }
  MainActivity a=setup(true);a.render();geometry(a);a.orderedTraversal=true;a.frameRequests=0;View[] chrome={a.detailPane.getChildAt(0),a.detailPane.getChildAt(1)};
  a.detailPane.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY));equal(0,a.frameRequests,"zero unresolved dimensions do no adaptive work");equal(6,a.description.maxLines,"zero dimensions defer capacity");check(chrome[0].parent==a.detailPane&&chrome[1].parent==a.detailPane,"zero dimensions defer reparenting");
  System.out.println("PASS ordered resolved specs checks="+checks);
 }
 static void orderedMetrics(){
  for(String target:new String[]{"description","notes"})for(boolean enabled:new boolean[]{true,false}){
   MainActivity a=setup(true);a.online=enabled;a.render();geometry(a);EditText f=target.equals("description")?a.description:a.notes;
   f.setText(original());f.setSelection(2,8);a.title.focused=enabled;orderedFrame(a,640,281);equal(2,f.maxLines,"initial native metrics");String before=a.draft.toString();
   for(int extra:new int[]{100,0,44,0}){
    f.extraLineHeight=extra;f.requestLayout();orderedFrame(a,640,281);
    equal(extra==0?2:1,f.maxLines,"same-character metric/spans change recalibrates before frame");check(f.measuredHeight<=a.detailScroll.height,"widget fits after metric change");
    equal(original(),f.text,"spanned characters remain exact");equal(2,f.selectionStart,"metric range start");equal(8,f.selectionEnd,"metric range end");equal(before,a.draft.toString(),"revision unchanged");equal(0,a.focusClears,"metric change cannot collapse focus viewport");
    a.frameRequests=0;orderedFrame(a,640,281);equal(1,a.frameRequests,"metric cache settles after change");equal(1,a.superMeasures,"metric cache no repeated allocation");
   }
  }
  System.out.println("PASS ordered metrics checks="+checks);
 }
 static void orderedInvalidation(){
  for(String mode:new String[]{"root","pane","editor","scroll","title","description","notes","owner","epoch","session","auth","store","task","reader","destination","detached","content-detached","window-detached","header-detached","gap-detached","actions-detached","field-detached","rebind"}){
   MainActivity a=setup(true);a.render();View[] chrome=geometry(a);orderedFrame(a,640,1000);LinearLayout pane=a.detailPane,content=a.editor;EditText body=a.description,memo=a.notes;View.OnLayoutChangeListener listener=pane.layoutListeners.get(0);
   switch(mode){
    case "root":a.root=a.vertical();break;case "pane":a.detailPane=a.vertical();break;case "editor":a.editor=a.vertical();break;case "scroll":a.detailScroll=new ScrollView(a);break;
    case "title":a.title=new EditText(a);break;case "description":a.description=new EditText(a);break;case "notes":a.notes=new EditText(a);break;
    case "owner":a.account=new Store.Account();break;case "epoch":a.account.epoch="new-epoch";break;case "session":a.surfaces.version++;break;case "auth":a.authState=AuthPolicy.State.LOGIN;break;case "store":a.store.valid=false;break;case "task":a.selected="task-b";break;case "reader":a.editing=false;break;case "destination":a.destination="Discussions";break;
    case "detached":a.root.removeAllViews();break;case "content-detached":content.parent.removeView(content);break;case "window-detached":a.root.attached=false;break;
    case "header-detached":pane.removeView(chrome[0]);break;case "gap-detached":pane.removeView(chrome[1]);break;case "actions-detached":pane.removeView(chrome[2]);break;case "field-detached":content.removeView(body);break;
    case "rebind":a.render();break;default:throw new AssertionError(mode);
   }
   List<View> oldPane=new ArrayList<>(pane.children),oldContent=new ArrayList<>(content.children);String draft=a.draft.toString();int renders=a.renders;
   body.extraLineHeight=100;memo.extraLineHeight=100;a.frameRequests=0;body.forceLayout=memo.forceLayout=false;
   pane.forceLayout();pane.measure(View.MeasureSpec.makeMeasureSpec(511,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(281,View.MeasureSpec.EXACTLY));listener.onLayoutChange(pane,0,0,511,281,0,0,640,1000);
   equal(oldPane,pane.children,"invalidated "+mode+" measure/listener leaves pane alone");equal(oldContent,content.children,"invalidated editor unchanged");equal(6,body.maxLines,"invalidated body untouched");equal(6,memo.maxLines,"invalidated notes untouched");check(!body.forceLayout&&!memo.forceLayout,"reject before metric observation");equal(0,a.frameRequests,"invalidated binding requests no work");equal(draft,a.draft.toString(),"draft unchanged");equal(renders,a.renders,"no render");equal(0,Api.calls.size(),"no transport");
  }
  for(String route:new String[]{"reader","missing","conversation","unselected","trash"}){
   MainActivity a=setup(false);if(route.equals("missing")){a.draft=null;a.tasks=new JSONArray();}if(route.equals("conversation"))a.detailsMode="Hermès";if(route.equals("unselected"))a.selected="";if(route.equals("trash"))a.draft.put("deleted",true);
   a.render();equal(0,a.detailPane.layoutListeners.size(),"inactive route has no policy observer");a.orderedTraversal=true;a.frameRequests=0;a.superMeasures=0;a.detailPane.measure(View.MeasureSpec.makeMeasureSpec(511,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(281,View.MeasureSpec.EXACTLY));equal(1,a.superMeasures,"inactive route executes superclass once only");equal(0,a.frameRequests,"inactive route no policy sizing");
  }
  System.out.println("PASS ordered invalidation checks="+checks);
 }
 static void orderedActions(){
  for(boolean fresh:new boolean[]{true,false}){
   MainActivity a=setup(true);if(fresh){a.selected="NEW";a.draft=a.empty();a.editing=false;}a.render();geometry(a);a.title.setText("Exact ordered Save");a.description.setText(original());orderedFrame(a,640,281);
   button(a.editor,"Choose date").performClick();a.picker.listener.changed(null,2033,2,4);equal("2033-03-04",a.due.text,"original date callback");button(a.editor,"Clear date").performClick();equal("",a.due.text,"original clear date");
   Api.replies.add(task().put("revision","revision-B"));Api.replies.add(new JSONObject().put("tasks",new JSONArray().put(task().put("revision","revision-B"))));button(a.detailPane,fresh?"Create task":"Save").performClick();Api.IO.run();JSONObject payload=Api.payloads.get(0);equal("Exact ordered Save",payload.optString("title"),"original Save writing");equal(original(),payload.optString("description"),"full original Save body");equal(fresh?"POST /api/tasks":"PATCH /api/tasks/task-a",Api.calls.get(0),"original Save route");if(!fresh)equal("revision-A",payload.optString("revision"),"optimistic revision");a.main.run();
  }
  MainActivity a=setup(true);a.render();geometry(a);a.title.setText("Dirty ordered Cancel");orderedFrame(a,640,281);button(a.detailPane,"Cancel").performClick();equal("Discard unsaved changes?",a.confirmTitle,"original Cancel consent");check(a.editing,"not discarded before consent");a.confirmation.run();check(!a.editing,"original Cancel returns reader");
  System.out.println("PASS ordered actions checks="+checks);
 }
 public static void main(String[] args){if(args[0].equals("ordered-resolved")){orderedResolvedSpecs();return;}if(args[0].equals("ordered-matrix")){orderedMatrix();return;}if(args[0].equals("ordered-metrics")){orderedMetrics();return;}if(args[0].equals("ordered-invalidation")){orderedInvalidation();return;}if(args[0].equals("ordered-actions")){orderedActions();return;}if(args[0].equals("ordered-shrink")){orderedShrink();return;}if(args[0].equals("metric-change")){metricChangeCase(Boolean.parseBoolean(args[1]),args[2],Boolean.parseBoolean(args[3]),args[4]);return;}if(args[0].equals("metric-owner")){metricOwnerCase(args[1]);return;}if(args[0].equals("metric-paste")){metricPasteCase(Boolean.parseBoolean(args[1]),args[2],args[3]);return;}if(args[0].equals("settled-transitions")){settledTransitions(Boolean.parseBoolean(args[1]),Boolean.parseBoolean(args[2]),Float.parseFloat(args[3]));return;}if(args[0].equals("settled-inactive")){settledInactive(args[1]);return;}if(args[0].equals("settled-reader")){settledReader();return;}if(args[0].equals("settled-stale")){settledStaleMeasurement(Boolean.parseBoolean(args[1]));return;}if(args[0].equals("settled-stable")){settledStable(Integer.parseInt(args[1]),Boolean.parseBoolean(args[2]),Boolean.parseBoolean(args[3]));return;}if(args[0].equals("adaptive-density")){adaptiveFractionalDensity();System.out.println("PASS adaptive density "+checks);return;}if(args[0].equals("adaptive-controls")){adaptiveControls();System.out.println("PASS adaptive controls "+checks);return;}if(args[0].equals("adaptive-measured-lines")){adaptiveMeasuredLines();System.out.println("PASS measured lines "+checks);return;}if(args[0].startsWith("adaptive-owner-")){adaptiveOwnership(args[0].substring(15));System.out.println("PASS "+args[0]+" "+checks);return;}if(args[0].equals("adaptive-null-owner")){adaptiveNullOwner();System.out.println("PASS null owner "+checks);return;}if(args[0].equals("adaptive-multiline")){adaptiveMultiline();System.out.println("PASS adaptive multiline "+checks);return;}if(args[0].equals("adaptive-viewport")){adaptiveViewport();System.out.println("PASS adaptive viewport "+checks);return;}if(args[0].equals("notice-roundtrip")){retainedRoundTrip();System.out.println("PASS roundtrip "+checks);return;}if(args[0].equals("notice-predicate")){retainedCurrentPredicate();System.out.println("PASS predicate "+checks);return;}if(args[0].startsWith("notice-queued-")){retainedQueuedReplacement(args[0].substring(14),Boolean.parseBoolean(args[1]));System.out.println("PASS queued "+args[0]+" failure="+args[1]+" "+checks);return;}if(args[0].startsWith("notice-owner-")){noticeOwnership(args[0].substring(13));System.out.println("PASS "+args[0]+" "+checks+" checks");return;}if(args[0].startsWith("retained-")){retainedTransition(args[0].substring(9));System.out.println("PASS "+args[0]+" "+checks+" checks");return;}switch(args[0]){case "order":order();break;case "groups":groups();break;case "notice":notice();break;case "actions":actions();break;case "save-reload":saveReload();break;case "routes":routes();break;case "preservation":preservation();break;default:throw new AssertionError("Unknown case");}System.out.println("PASS "+args[0]+" "+checks+" checks (host seams, not native runtime)");}
}

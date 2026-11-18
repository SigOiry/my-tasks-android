package com.simon.mytasks;
import java.util.*;
import org.json.*;
/** Actual Activity lifecycle/reader methods; inert Android layout/storage shells only. */
class DescriptionLifecycleHarness extends DescriptionLifecycleBase {
 String selected="",descriptionSelection="",destination="Tasks",detailsMode="Details",pendingSummary="",banner="",restoreComposerThread="";
 boolean busy,editing,wide,online=true,restoring;int detailY,restoreComposerPosition,bg,card,ink,muted,line;
 AuthPolicy.State authState=AuthPolicy.State.FULL;
 final TaskDescriptionText.State descriptionState=new TaskDescriptionText.State();
 JSONObject draft;Map<String,JSONObject> tasks=new HashMap<>();
 Store.Account account=new Store.Account();Surfaces surfaces=new Surfaces();
 Ui ui=new Ui();TasksUi tasksUi;Conversations conversations;ConversationsUi conversationUi;Email email;EmailUi emailUi;UpdateSheet summaryUi;
 LinearLayout root,listPane,detailPane,listItems,editor,appBar,bottomTabs;FrameLayout rootFrame,navigationSurface;
 TextView stateLabel,listCount;View title,description,notes,due,project,priority,status;ScrollView listScroll,detailScroll;
 List<View> chatUtilityChrome=new ArrayList<>();Map<String,Button> viewButtons=new HashMap<>();
 JSONObject taskPresentation=new JSONObject();int taskPresentationGeneration;boolean taskPresentationPending,shellRebuildQueued,railNavigation;String taskEditorTask="",taskEditorEpoch="";int shellMasterWidth=300,shellInsetLeft,shellInsetTop,shellInsetRight,shellInsetBottom;
 void captureTaskPresentation(){}Object readShellPolicy(View v){return null;}void applyShellPolicy(Object p){wide=false;}void updateShellGeometry(View v){}
 // METHODS
 // DETAIL_ENTRY
 void buildTaskReading(){
 // DESCRIPTION_GATE
 }
 String text(JSONObject o,String k){return o==null?"":o.optString(k,"");}
 JSONObject task(String id){return tasks.get(id);}JSONObject copy(JSONObject o){return new JSONObject(o.toString());}JSONObject empty(){return new JSONObject();}
 boolean alive(Store.Account a){return true;}boolean dirty(){return false;}void captureDraft(){}void maybeSummary(){}void notice(String a,String b){}void confirm(String a,String b,Runnable r){r.run();}
 void applyAppearance(){ui=new Ui();}void buildList(){}void buildRail(LinearLayout p){}void buildPrimaryTabs(){}void setContentView(View v){}
 Resources resources=new Resources();Resources getResources(){return resources;}
 int dp(int n){return n;}LinearLayout vertical(){return new LinearLayout();}LinearLayout horizontal(){return vertical();}
 // Whole render is extracted here; task editor layout is deliberately excluded.
 LinearLayout taskDetailPane(){return vertical();}
 TextView heading(String s,int n){return new TextView(s);}TextView label(String s,int n,int c){return new TextView(s);}Button button(String s,Runnable r){return new Button(s,r);}
 void add(LinearLayout p,View v){p.addView(v);}void space(LinearLayout p,int n){}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 Button control(String text){for(View v:editor.children)if(v instanceof LinearLayout)for(View c:((LinearLayout)v).children)if(c instanceof Button&&((Button)c).text.toString().equals(text))return (Button)c;throw new AssertionError("Missing control "+text);}
 void expand(boolean original){control("See more").click();if(original)control("Original text").click();}
 void assertExpanded(boolean original,String reason){check(descriptionState.expanded&&descriptionState.original==original,reason);check(control("See less")!=null,reason+" bound control");}
 public static void main(String[] args){
  String mode=args.length==0?"empty":args[0];DescriptionLifecycleHarness h=new DescriptionLifecycleHarness();
  String source="**Opening fact** "+String.join(" ",Collections.nCopies(100,"Original detail."))+" FINAL CONDITION";
  h.tasks.put("a",new JSONObject().put("description",source).put("description_summary","**Action:** wait for approval."));
  h.tasks.put("b",new JSONObject().put("description",mode.equals("conversation")?"Conversation description":""));
  for(boolean original:new boolean[]{false,true}){
   h.navigate("a");h.expand(original);
   h.render();h.assertExpanded(original,"Same-task refresh preserves reader mode");
   h.navigate("a");h.assertExpanded(original,"Same-task navigation preserves reader mode");
   h.onConfigurationChanged(new Configuration());h.assertExpanded(original,"Handled rotation preserves reader mode");check(!h.restoring,"Rotation restoration flag balanced");
   if(mode.equals("conversation"))h.openTaskConversation("b");else h.navigate("b");
   check(mode.equals("conversation")?h.conversationUi!=null:h.editor.children.isEmpty(),"B bypasses readingDescription");
   h.navigate("a");
   check(!h.descriptionState.expanded&&!h.descriptionState.original,"A -> "+mode+" B -> A must reopen compact, not retain "+(original?"original":"expanded")+" mode");
   check(h.control("See more")!=null,"Compact control rebound after task switch");
   h.expand(original);h.navigate("");h.navigate("a");check(!h.descriptionState.expanded&&!h.descriptionState.original,"List exit clears reader");
   h.expand(original);h.authState=AuthPolicy.State.CHECKING;h.render();h.authState=AuthPolicy.State.FULL;h.render();check(!h.descriptionState.expanded&&!h.descriptionState.original,"Auth exit clears reader");
  }
  System.out.println("PASS actual Activity render/navigation/handled rotation: A -> "+mode+" B -> A; expanded/original + same-task controls");
 }
 static class View {
  static final int VISIBLE=0,GONE=8,ACCESSIBILITY_LIVE_REGION_POLITE=1;int visibility;Object params;View(){}View(Object o){}
  interface InsetsListener{WindowInsets apply(View v,WindowInsets i);}void setOnApplyWindowInsetsListener(InsetsListener l){}
  void setVisibility(int n){visibility=n;}void setPadding(int a,int b,int c,int d){}void setBackgroundColor(int n){}void setBackground(Object o){}void requestApplyInsets(){}
  interface Layout{void change(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}void addOnLayoutChangeListener(Layout l){}
  int getHeight(){return 100;}Object getLayoutParams(){return params;}void setLayoutParams(Object p){params=p;}
 }
 static class TextView extends View {CharSequence text;TextView(){this("");}TextView(String s){text=s;}void setText(CharSequence s){text=s;}void setTextColor(int c){}void setTextIsSelectable(boolean b){}void setAutoLinkMask(int n){}void setLinksClickable(boolean b){}void setContentDescription(String s){}void setAccessibilityLiveRegion(int n){}boolean hasFocus(){return false;}int getSelectionStart(){return 0;}int getMaxLines(){return 1;}void setMaxLines(int n){} }
 static class Button extends TextView {interface Listener{void click(View v);}Runnable action;Button(String s,Runnable r){super(s);action=r;}void setMinHeight(int n){}void setOnClickListener(Listener l){action=()->l.click(this);}void click(){action.run();}}
 static class LinearLayout extends View {List<View> children=new ArrayList<>();void setGravity(int n){}void addView(View v){children.add(v);}void addView(View v,Object p){v.params=p;addView(v);}static class LayoutParams{LayoutParams(int a,int b){}LayoutParams(int a,int b,int c){}void setMargins(int a,int b,int c,int d){}}}
 static class FrameLayout extends LinearLayout {FrameLayout(Object o){}static class LayoutParams{int bottomMargin,leftMargin,rightMargin;LayoutParams(int a,int b){}}}
 static class ScrollView extends View{}
 static class Configuration {int screenWidthDp=400,screenHeightDp=800;float fontScale=1;}
 static class Resources {Configuration getConfiguration(){return new Configuration();}Metrics getDisplayMetrics(){return new Metrics();}}
 static class Metrics {float density=1;}
 static class Build {static class VERSION{static int SDK_INT=0;}}
 static class Gravity {static final int TOP=0;}
 static class WindowInsets {static class Type{static int systemBars(){return 1;}static int ime(){return 2;}static int displayCutout(){return 4;}}android.graphics.Insets getInsets(int n){return new android.graphics.Insets();}boolean isVisible(int n){return false;}int getSystemWindowInsetTop(){return 0;}int getSystemWindowInsetBottom(){return 0;}int getSystemWindowInsetLeft(){return 0;}int getSystemWindowInsetRight(){return 0;}}
 static class CompactPolicy {static boolean expanded(int w,float s){return false;}static boolean secondaryChrome(boolean k,int h){return true;}static int composerLines(int a){return 3;}static int masterWidth(int w){return 300;}}
 static class AuthPolicy {enum State{FULL,CHECKING}}
 static class AuthUi {AuthUi(Object o){}void build(View v){}}
 static class Store {static class Account{}}
 static class Surfaces {long generation(){return 1;}boolean current(long n){return true;}}
 static class Ui {Object shape(int c,int n,int r){return null;}void quiet(Button b){}void navigatePage(View v,boolean r){}}
 static class MarkdownText {static CharSequence render(SafeMarkdown.Document d){return d.text;}}
 static class TasksUi {void capture(){}}
 static class Conversations {Object listener;void hide(){}}
 static class ConversationsUi {TextView input;String thread="";ConversationsUi(Object a,Object c){}void capture(){}void buildGlobal(View v){}void buildTask(View v,String id){}}
 static class Email {Host host;EmailState state;}static class Host{boolean valid(){return true;}}static class EmailState{JSONObject data;}
 static class EmailUi {EmailUi(Object a,Object e){}boolean keyboardVisible(){return false;}void detach(){}void build(View v){}void restoreKeyboard(){}void keyboardChanged(boolean b){}boolean needsStyleRebuild(Configuration c){return true;}void capture(){}void fitPane(){}}
 static class EmailReplyUi extends EmailUi {EmailReplyUi(Object a,Object e){super(a,e);}}
 static class UpdateSheet {Dialog dialog;Store.Account owner;Object shown;boolean waiting,failed;UpdateSheet(Object a,Object s){}void show(){}}
 static class Dialog {void dismiss(){}}
}
class DescriptionLifecycleBase {public void onConfigurationChanged(DescriptionLifecycleHarness.Configuration c){}}

package com.simon.mytasks;
import java.util.*;import org.json.*;
/** Synthetic geometry only. Actual production capture/restore methods are inserted at run time. */
public class ReadingContinuityHarness {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static JSONObject copy(JSONObject value){return new JSONObject(value.toString());}
 static ConversationsUi ui(){ConversationsUi u=new ConversationsUi();u.c.listener=u;u.thread="A";u.scroll.height=300;u.scroll.width=400;u.timeline.height=3000;return u;}
 static TextView row(ConversationsUi u,long id,int top,int height,int bodyTop,String text,int... starts){
  View wrapper=new View();wrapper.top=top;wrapper.height=height;wrapper.width=u.scroll.width;wrapper.parent=u.messages;
  View bubble=new View();bubble.top=bodyTop;bubble.parent=wrapper;
  TextView body=new TextView();body.parent=bubble;body.text=text;body.layout=new Layout(text,starts,20);body.height=body.layout.getHeight();body.width=u.scroll.width-40;
  // BODY_BINDING
  u.messageRows.put(id,wrapper);return body;
 }
 static void blankTail(){
  ConversationsUi u=ui();row(u,5,400,100,10,"previous answer",0);row(u,6,500,180,40,"Question six has readable text",0,13);u.scroll.y=494;u.captureViewport();JSONObject mark=copy(u.viewport());
  // Old first wrapper has only six blank pixels; the next body starts at 540 (offset +46).
  u.messageRows.clear();u.scroll.width=700;row(u,5,300,70,10,"previous answer",0);row(u,6,370,100,25,"Question six has readable text",0);
  u.restoreViewport(false,mark);Queue.run();
  System.out.println("blank-tail requested placement: body char0 offset46; expectedY349 actualY"+u.scroll.y);
  check(u.scroll.y==349,"reflow must keep question six readable, not anchor the previous blank tail/date/timestamp");
 }
 static void boundedTerminal(){
  ConversationsUi u=ui();row(u,9,500,300,10,"terminal message with lines to retain",0,10,20,30);u.scroll.y=535;u.captureViewport();JSONObject mark=copy(u.viewport());
  u.messageRows.clear();row(u,9,200,250,10,"terminal message with lines to retain",0,20);u.timeline.height=480;u.scroll.max=180;u.restoreViewport(false,mark);Queue.run();
  JSONObject proof=u.viewport().optJSONObject("reading_restore");System.out.println("terminal expected requested215/max180/actual180; actualY="+u.scroll.y+" proof="+proof);
  check(u.scroll.y==180,"terminal nearest attainable bound");check(proof!=null,"bounds fallback must expose requested/max/actual evidence, not silently clamp");
  check(proof.optInt("requested_y")==215&&proof.optInt("max_y")==180&&proof.optInt("actual_y")==180&&proof.optBoolean("bounded"),"independent terminal bounds evidence");
  check(!proof.optBoolean("follow")&&!u.viewport().optBoolean("at_bottom"),"bounds clamp must not masquerade as follow-bottom intent");
  u.captureViewport();check(!u.viewport().optBoolean("at_bottom"),"passive capture/recreation after a clamp must not invent user follow intent");
 }
 static void userWins(){
  for(String action:new String[]{"touch","wheel","key","accessibility"}){
   ConversationsUi u=ui();u.bindScroll();u.scroll.height=300;row(u,1,500,300,10,"a long readable message",0,10);u.scroll.y=535;u.captureViewport();JSONObject mark=copy(u.viewport());u.scroll.y=0;u.restoreViewport(false,mark);
   if(action.equals("touch"))u.scroll.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN));
   if(action.equals("wheel"))u.scroll.dispatchGenericMotionEvent(new MotionEvent(MotionEvent.ACTION_SCROLL));
   if(action.equals("key"))u.scroll.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN));
   if(action.equals("accessibility")){u.scroll.acceptAccessibility=true;u.scroll.performAccessibilityAction(4096,new android.os.Bundle());}
   u.scroll.scrollTo(0,900);Queue.run();System.out.println("interaction "+action+" expected900 actual"+u.scroll.y);
   check(u.scroll.y==900&&!u.restoring,"new user "+action+" scroll must beat queued reading restore");check(u.viewport().optInt("y")==900,"latest user scroll captured for next rebuild");
  }
 }
 static void changedBody(){
  ConversationsUi u=ui();row(u,9,500,300,10,"original message text to retain",0,10,20);u.scroll.y=555;u.captureViewport();JSONObject mark=copy(u.viewport());
  u.messageRows.clear();row(u,9,200,40,5,"changed",0);u.restoreViewport(false,mark);Queue.run();
  System.out.println("changed body conservative row start expected200 actual"+u.scroll.y);check(u.scroll.y==200,"changed rendered body must fall back to its message start, not apply obsolete intra-body pixels");
  check(u.viewport().optJSONObject("reading_restore").optString("mode").equals("changed-body"),"changed body fallback reason exposed");
 }
 static void captureOwnership(){
  for(String boundary:new String[]{"account","listener"}){
   ConversationsUi u=ui();row(u,1,500,300,10,"owner text",0);u.scroll.y=515;u.captureViewport();String saved=u.c.state.data.toString();u.scroll.y=900;
   if(boundary.equals("account"))u.c.valid=false;else u.c.listener=new Object();u.captureViewport();
   check(u.c.state.data.toString().equals(saved),"stale "+boundary+" capture cannot overwrite retained reading position");
  }
  ConversationsUi u=ui();u.bindScroll();ScrollView old=u.scroll;u.bindScroll();u.scroll.height=300;row(u,1,500,300,10,"owner text",0);u.scroll.y=515;u.captureViewport();JSONObject mark=copy(u.viewport());u.scroll.y=0;u.restoreViewport(false,mark);
  old.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN));Queue.run();check(u.scroll.y==515,"obsolete View interaction cannot cancel replacement View restore");
  System.out.println("PASS capture ownership + obsolete interaction fences");
 }
 static void matrix(){
  int cases=0;
  String text="0123456789abcdefghijKLMNOPQRSTuvwxyz";
  for(int y:new int[]{490,505,510,515,530,545,565,590}){
   ConversationsUi u=ui();row(u,1,500,120,10,text,0,10,20,30);u.scroll.y=y;u.captureViewport();JSONObject mark=copy(u.viewport());
   u.scroll.y=0;u.restoreViewport(false,mark);Queue.run();check(u.scroll.y==y,"unchanged geometry exact raw pixel "+y);cases++;
   // Prepending and header growth move the same message, not the semantic body position.
   u.messages.top=60;u.messageRows.get(1L).top=700;u.restoreViewport(false,mark);Queue.run();check(u.scroll.y==y+260,"prepend/header exact offset "+y);cases++;
  }
  for(int path=0;path<4;path++){
   ConversationsUi u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.captureViewport();JSONObject mark=copy(u.viewport());
   u.messageRows.clear();TextView body=row(u,1,200,160,30,text,0,20);body.layout.lineHeight=32;u.scroll.y=0;
   if(path==3){String serialized=u.c.state.data.toString();u=ui();u.c.state.data=new JSONObject(serialized);body=row(u,1,200,160,30,text,0,20);body.layout.lineHeight=32;mark=copy(u.viewport());}
   for(int n=0;n<=path;n++)u.restoreViewport(false,mark);Queue.run();check(u.scroll.y==267,"intra-message char20 maps to font-reflow line at262 offset-5, path"+path);cases++;
  }
  for(String fallback:new String[]{"legacy-raw","missing-message","missing-body","layout-unavailable","invalid-position"}){
   ConversationsUi u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.captureViewport();JSONObject mark=copy(u.viewport());u.messageRows.clear();TextView body=row(u,1,200,160,30,text,0,20);int expected=200;
   if(fallback.equals("legacy-raw")){mark.remove("reading");expected=255;}
   if(fallback.equals("missing-message")){u.messageRows.clear();row(u,2,200,160,30,text,0,20);expected=555;}
   if(fallback.equals("missing-body"))u.messageRows.get(1L).setTag(null);
   if(fallback.equals("layout-unavailable"))body.layout=null;
   if(fallback.equals("invalid-position"))mark.optJSONObject("reading").put("position",text.length()+1);
   u.restoreViewport(false,mark);Queue.run();check(u.scroll.y==expected,"deterministic fallback "+fallback);check(u.viewport().optJSONObject("reading_restore").optString("mode").equals(fallback),"exposed fallback reason "+fallback);cases++;
  }
  // Non-body-only capture cannot invent a text offset; blank lines cannot be readable anchors.
  ConversationsUi nonbody=ui();row(nonbody,1,500,300,10,"timestamp excluded",0);nonbody.messageRows.get(1L).setTag(null);nonbody.scroll.y=550;nonbody.captureViewport();check(!nonbody.viewport().has("reading"),"non-body-only raw fallback");cases++;
  ConversationsUi whitespace=ui();row(whitespace,1,500,300,10,"\n\n😀 readable",0,1,2);whitespace.scroll.y=510;whitespace.captureViewport();JSONObject reading=whitespace.viewport().optJSONObject("reading");check(reading.optInt("position")==2&&reading.optInt("offset")==40,"skip blank lines without splitting Unicode");cases++;
  // A -> B -> A with queued obsolete callbacks and serialized presentation.
  ConversationsUi u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.captureViewport();JSONObject a=copy(u.viewport());u.restoreViewport(false,a);u.thread="B";u.messageRows.clear();row(u,7,800,200,10,"different B body",0);u.scroll.y=815;u.restoring=false;u.captureViewport();JSONObject b=copy(u.viewport());u.restoreViewport(false,b);u.thread="A";u.messageRows.clear();row(u,1,200,160,30,text,0,20);u.scroll.y=0;u.restoreViewport(false,a);Queue.run();check(u.scroll.y==255,"A/B/A restores A char20 at250 offset-5, never B");check(u.c.state.data.optJSONObject("presentation:B").optInt("y")==815,"B presentation remains independent");cases++;
  for(String boundary:new String[]{"view","thread","account","listener","generation"}){
   u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.captureViewport();a=copy(u.viewport());u.scroll.y=0;ScrollView target=u.scroll;u.restoreViewport(false,a);
   if(boundary.equals("view"))u.scroll=new ScrollView();if(boundary.equals("thread"))u.thread="B";if(boundary.equals("account"))u.c.valid=false;if(boundary.equals("listener"))u.c.listener=new Object();if(boundary.equals("generation"))u.viewportGeneration++;
   Queue.run();check(target.y==0,"stale restore is fenced by "+boundary);cases++;
  }
  // The same real scroll binding captures old Layout before onSizeChanged reflow.
  u=ui();u.bindScroll();u.scroll.height=300;row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.scroll.onSizeChanged(700,300,400,300);u.messageRows.clear();row(u,1,200,160,30,text,0,20);Queue.run();check(u.scroll.y==255,"actual onSizeChanged dispatch retains text position");cases++;
  u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.captureViewport();a=copy(u.viewport());u.restoreViewport(false,a);u.jumpToLatest();Queue.run();check(u.scroll.y==2700,"explicit latest revokes pending reading restore");cases++;
  u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=555;u.captureViewport();u.restoreViewport(true,copy(u.viewport()));Queue.run();check(u.scroll.y==2700&&u.viewport().optJSONObject("reading_restore").optBoolean("follow"),"own-send/follow path still goes to latest");cases++;
  u=ui();row(u,1,500,300,10,text,0,10,20,30);u.scroll.y=400;u.captureViewport();a=copy(u.viewport());u.messageRows.clear();row(u,1,0,160,10,text,0,20);u.restoreViewport(false,a);Queue.run();JSONObject proof=u.viewport().optJSONObject("reading_restore");check(u.scroll.y==0&&proof.optInt("requested_y")==-100&&proof.optBoolean("bounded"),"lower bound preserves raw negative request evidence");cases++;
  for(String release:new String[]{"interaction","jump","follow"}){
   u=ui();u.bindScroll();u.scroll.height=300;u.timeline.height=480;u.scroll.max=180;u.scroll.y=180;u.viewport().put("reading_hold",true);u.captureViewport();String saved=u.c.state.data.toString();
   ConversationsUi recreated=ui();recreated.c.state.data=new JSONObject(saved);recreated.timeline.height=480;recreated.scroll.y=180;recreated.captureViewport();check(!recreated.nearBottom()&&!recreated.viewport().optBoolean("at_bottom"),"serialized bounds hold is not follow intent");
   if(release.equals("interaction")){u.scroll.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN));u.captureViewport();}
   if(release.equals("jump"))u.jumpToLatest();if(release.equals("follow")){u.restoreViewport(true,copy(u.viewport()));Queue.run();}
   check(u.nearBottom()&&u.viewport().optBoolean("at_bottom")&&!u.viewport().optBoolean("reading_hold"),"explicit intent releases bounds hold "+release);cases++;
  }
  System.out.println("PASS reading continuity regression matrix cases="+cases);
 }
 static void fontFamily(){
  int cases=0,losses=0,diagnosticFailures=0;String text="0123456789abcdefghijKLMNOPQRSTuvwxyz";
  for(int oldHeight:new int[]{20,32,40,48})for(int newHeight:new int[]{16,20,32,40})for(int clipped:new int[]{0,5,15,19,25,31,39}){
   if(clipped>=oldHeight)continue;
   ConversationsUi u=ui();TextView before=row(u,1,500,500,10,text,0,10,20,30);before.layout.lineHeight=oldHeight;u.scroll.y=510+clipped;u.captureViewport();JSONObject mark=copy(u.viewport());
   check(510+oldHeight>u.scroll.y,"original character0 readable");u.messageRows.clear();u.scroll.width=700;TextView after=row(u,1,200,300,10,text,0,20);after.layout.lineHeight=newHeight;
   u.restoreViewport(false,mark);Queue.run();boolean adjusted=clipped>=newHeight;int expected=adjusted?210:210+clipped;
   JSONObject proof=u.viewport().optJSONObject("reading_restore");if(210+newHeight<=u.scroll.y)losses++;
   if(u.scroll.y!=expected||proof.optBoolean("readability_adjusted")!=adjusted||proof.optInt("unadjusted_y",-1)!=210+clipped||proof.optInt("requested_y")!=expected||proof.optBoolean("bounded"))diagnosticFailures++;
   cases++;System.out.println("FONT old="+oldHeight+" new="+newHeight+" clipped="+clipped+" expected="+expected+" actual="+u.scroll.y+" proof="+proof);
  }
  System.out.println("FONT_FAMILY cases="+cases+" losses="+losses+" diagnosticFailures="+diagnosticFailures);check(losses==0,"contraction must not hide original character0");check(diagnosticFailures==0,"readability adjustment distinct from content bounds");
  // A nonzero UTF-16 position after blank lines, and a viewport shorter than a line.
  for(int maximum:new int[]{220,200}){
   ConversationsUi u=ui();u.scroll.height=8;TextView before=row(u,1,500,500,10,"\n\n😀 readable text",0,1,2);before.layout.lineHeight=40;u.scroll.y=615;u.captureViewport();JSONObject mark=copy(u.viewport());check(mark.optJSONObject("reading").optInt("position")==2,"original Unicode mark after blank lines");
   u.messageRows.clear();TextView after=row(u,1,200,300,10,"\n\n😀 readable text",0,2);after.layout.lineHeight=16;u.timeline.height=maximum+8;u.scroll.max=maximum;
   u.restoreViewport(false,mark);Queue.run();JSONObject proof=u.viewport().optJSONObject("reading_restore");
   check(proof.optInt("unadjusted_y")==251&&proof.optInt("requested_y")==226&&proof.optBoolean("readability_adjusted")&&proof.optBoolean("bounded"),"readability then real upper bound");check(u.scroll.y==maximum&&!u.viewport().optBoolean("at_bottom"),"nearest real bound preserves intent even when short viewport cannot reach mark");
  }
  System.out.println("PASS contraction Unicode/short-viewport/bounds controls");
 }
 static void unchangedGeometrySweep(){
  int count=0;String text="0123456789abcdefghijKLMNOPQRSTuvwxyz";
  for(int y=350;y<=900;y++){
   ConversationsUi u=ui();row(u,1,400,180,10,text,0,10,20,30);row(u,2,600,220,40,text,0,10,20,30);u.scroll.y=y;u.captureViewport();JSONObject mark=copy(u.viewport());u.scroll.y=0;u.restoreViewport(false,mark);Queue.run();check(u.scroll.y==y,"unchanged exact y="+y+" actual="+u.scroll.y);check(!u.viewport().optJSONObject("reading_restore").optBoolean("readability_adjusted"),"unchanged placement needs no readability adjustment");count++;
  }System.out.println("EXACT_SWEEP cases="+count+" failures=0");
 }
 static void accessibilityFamily(){
  int cases=0,failures=0;
  for(int action:new int[]{128,64,1,16,0,4096,8192,16908344,16908346})for(boolean accepted:new boolean[]{false,true}){
   boolean navigation=action==4096||action==8192||action==16908344||action==16908346;if(navigation&&accepted)continue;
   for(boolean held:new boolean[]{false,true}){
    ConversationsUi u=ui();u.bindScroll();u.scroll.height=300;row(u,9,500,300,10,"terminal message with lines to retain",0,10,20,30);u.scroll.y=535;u.captureViewport();JSONObject mark=copy(u.viewport());
    if(held){u.messageRows.clear();row(u,9,200,250,10,"terminal message with lines to retain",0,20);u.timeline.height=480;u.scroll.max=180;u.restoreViewport(false,mark);Queue.run();check(!u.viewport().optBoolean("at_bottom"),"bounded restore fixture");}
    else{u.scroll.y=0;u.restoreViewport(false,mark);}
    int generation=u.viewportGeneration;String saved=u.c.state.data.toString();u.scroll.acceptAccessibility=accepted;android.os.Bundle arguments=new android.os.Bundle();boolean result=u.scroll.performAccessibilityAction(action,arguments);
    check(result==accepted&&u.scroll.accessibilityCalls==1&&u.scroll.lastAction==action&&u.scroll.lastArguments==arguments,"superclass result and exact action/arguments delivery");
    boolean untouched=generation==u.viewportGeneration&&saved.equals(u.c.state.data.toString());Queue.run();u.captureViewport();int expected=held?180:535;
    boolean passed=untouched&&u.scroll.y==expected&&(!held||u.viewport().optBoolean("reading_hold")&&!u.viewport().optBoolean("at_bottom")&&!u.nearBottom());if(!passed)failures++;cases++;
    System.out.println("ACCESSIBILITY action="+action+" accepted="+accepted+" held="+held+" expected="+expected+" actual="+u.scroll.y+" untouched="+untouched+" pass="+passed);
   }
  }
  System.out.println("ACCESSIBILITY_NOOP cases="+cases+" failures="+failures);check(failures==0,"rejected/non-scroll accessibility must preserve queued restore and non-follow intent");
  for(int action:new int[]{4096,8192,16908344,16908346})for(boolean deferred:new boolean[]{false,true}){
   ConversationsUi u=ui();u.bindScroll();u.scroll.height=300;row(u,1,500,300,10,"original readable text",0,10);u.scroll.y=535;u.captureViewport();JSONObject mark=copy(u.viewport());u.viewport().put("reading_hold",true);u.scroll.y=0;u.restoreViewport(false,mark);int generation=u.viewportGeneration;
   u.scroll.acceptAccessibility=true;u.scroll.accessibilityTarget=900;u.scroll.deferAccessibility=deferred;
   check(u.scroll.performAccessibilityAction(action,new android.os.Bundle()),"accepted navigation result");check(!u.restoring&&u.viewportGeneration>generation&&!u.viewport().optBoolean("reading_hold"),"handled scroll invalidates restore and releases hold");Queue.run();check(u.scroll.y==900&&u.viewport().optInt("y")==900,"genuine scroll wins with synchronous or deferred superclass movement");
  }
  for(String boundary:new String[]{"view","account","listener"}){
   ConversationsUi u=ui();u.bindScroll();ScrollView delivered=u.scroll;if(boundary.equals("view"))u.bindScroll();u.scroll.height=300;row(u,1,500,300,10,"owner readable text",0,10);u.scroll.y=535;u.captureViewport();JSONObject mark=copy(u.viewport());u.viewport().put("reading_hold",true);u.scroll.y=0;u.restoreViewport(false,mark);
   if(boundary.equals("account"))u.c.valid=false;if(boundary.equals("listener"))u.c.listener=new Object();int generation=u.viewportGeneration;String saved=u.c.state.data.toString();delivered.acceptAccessibility=true;
   check(delivered.performAccessibilityAction(4096,new android.os.Bundle()),"stale action still delivered to superclass");check(u.viewportGeneration==generation&&saved.equals(u.c.state.data.toString()),"stale accessibility cannot mutate presentation "+boundary);Queue.run();if(boundary.equals("view"))check(u.scroll.y==535,"old View action cannot cancel current restore");
  }
  System.out.println("PASS accessibility accepted navigation sync/deferred + stale ownership controls");
 }
 static void legacyBounds(){
  int cases=0,failures=0;
  for(String kind:new String[]{"row","missing-row","raw-scroll"})for(int request:new int[]{-100,-1,0,1,49,50,51,500}){
   ConversationsUi u=ui();row(u,1,500,800,10,"legacy original body",0);u.scroll.y=500+request;u.captureViewport();JSONObject mark=copy(u.viewport());mark.remove("reading");
   if(kind.equals("row"))check(mark.optInt("anchor_offset")==-request,"captured legacy wrapper offset");
   u.messageRows.clear();if(kind.equals("row"))row(u,1,0,200,10,"legacy original body",0);else{mark.remove("anchor_id");mark.put("y",request);}
   if(kind.equals("raw-scroll")){mark.remove("y");u.c.state.data.put("scroll:A",request);}
   u.timeline.height=350;u.scroll.max=50;u.restoreViewport(false,mark);Queue.run();JSONObject proof=u.viewport().optJSONObject("reading_restore");
   boolean bounded=request<0||request>50;int expected=Math.max(0,Math.min(request,50));
   boolean passed=proof.optString("mode").equals("legacy-raw")&&proof.optInt("requested_y")==request&&proof.optInt("max_y")==50&&proof.optInt("actual_y")==expected&&proof.optBoolean("bounded")==bounded&&!proof.optBoolean("follow")&&u.scroll.y==expected;
   u.captureViewport();if(bounded)passed=passed&&u.viewport().optBoolean("reading_hold")&&!u.viewport().optBoolean("at_bottom")&&!u.nearBottom();
   String saved=u.c.state.data.toString();ConversationsUi recreated=ui();recreated.c.state.data=new JSONObject(saved);recreated.timeline.height=350;recreated.scroll.max=50;recreated.scroll.y=expected;recreated.captureViewport();if(bounded)passed=passed&&!recreated.viewport().optBoolean("at_bottom")&&!recreated.nearBottom();
   System.out.println("LEGACY kind="+kind+" rawRequested="+request+" actual="+u.scroll.y+" proof="+proof+" pass="+passed);if(!passed)failures++;cases++;
   u.restoreViewport(true,mark);Queue.run();check(u.scroll.y==50&&u.viewport().optBoolean("at_bottom")&&!u.viewport().optBoolean("reading_hold")&&u.viewport().optJSONObject("reading_restore").optBoolean("follow"),"explicit follow releases raw fallback hold");
  }
  System.out.println("LEGACY_BOUNDS cases="+cases+" failures="+failures);check(failures==0,"legacy raw requestedY must reach the explicit bounds decision unchanged");
  check(ChatPresentationPolicy.restoreY(0,100,900)==0&&ChatPresentationPolicy.restoreY(-1,0,-100)==0&&ChatPresentationPolicy.restoreY(200,-55,0)==255,"unrelated shared helper consumers retain clamped contract");
 }
 public static void main(String[] args){
  String test=args.length==0?"all":args[0];
  if(test.equals("f3")||test.equals("all"))legacyBounds();
  if(test.equals("f2")||test.equals("all"))accessibilityFamily();
  if(test.equals("f1")||test.equals("all")){fontFamily();unchangedGeometrySweep();}
  if(!test.equals("all"))return;
  blankTail();boundedTerminal();userWins();changedBody();captureOwnership();matrix();System.out.println("PASS reading continuity actual methods (synthetic Layout/View)");
 }
}
class ConversationsUi {
 final MainActivity a=new MainActivity();final Controller c=new Controller();String thread="";boolean restoring;int viewportGeneration;
 ScrollView scroll=new ScrollView();View timeline=new View(),messages=new View();LinkedHashMap<Long,View> messageRows=new LinkedHashMap<>();
 void updateJump(){}
 void bindScroll(){
 // SCROLL_BINDING
 }
 // PRODUCTION_METHODS
}
class MainActivity {int dp(int value){return value;}}
class Controller {State state=new State();Object listener;boolean valid=true;boolean valid(){return valid;}void save(){}}
class State {JSONObject data=new JSONObject();JSONObject json(){return data;}JSONArray messages(String id){return new JSONArray();}}
class ConversationState {static void put(JSONObject o,String key,Object value){o.put(key,value);}}
interface ViewParent {}
class View implements ViewParent {
 int top,height,width=400,y;View parent;Object tag;
 int getTop(){return top;}int getHeight(){return height;}int getWidth(){return width;}ViewParent getParent(){return parent;}
 Object getTag(){return tag;}void setTag(Object value){tag=value;}int getScrollY(){return y;}
 android.view.ViewTreeObserver observer=new android.view.ViewTreeObserver();android.view.ViewTreeObserver getViewTreeObserver(){return observer;}void invalidate(){}
}
class MotionEvent {static final int ACTION_DOWN=0,ACTION_SCROLL=8;int action;MotionEvent(int a){action=a;}int getActionMasked(){return action;}}
class KeyEvent {static final int ACTION_DOWN=0;int action;KeyEvent(int a){action=a;}int getAction(){return action;}}
class ScrollView extends View {
 int max=100000;ScrollView(){}ScrollView(MainActivity a){}interface Listener {void change(View v,int x,int y,int ox,int oy);}Listener listener;
 void setOnScrollChangeListener(Listener l){listener=l;}void scrollTo(int x,int value){int old=y;y=Math.max(0,Math.min(value,max));if(listener!=null&&old!=y)listener.change(this,0,y,0,old);}
 void setFillViewport(boolean v){}void setClipToPadding(boolean v){}void setSaveEnabled(boolean v){}protected void onSizeChanged(int w,int h,int ow,int oh){}
 public boolean dispatchTouchEvent(MotionEvent e){return false;}public boolean dispatchGenericMotionEvent(MotionEvent e){return false;}public boolean dispatchKeyEvent(KeyEvent e){return false;}
 boolean acceptAccessibility,deferAccessibility;int accessibilityTarget=-1,accessibilityCalls,lastAction;android.os.Bundle lastArguments;
 public boolean performAccessibilityAction(int action,android.os.Bundle args){
  accessibilityCalls++;lastAction=action;lastArguments=args;if(!acceptAccessibility)return false;
  if(accessibilityTarget>=0){final int target=accessibilityTarget;if(deferAccessibility)Queue.pending.add(()->scrollTo(0,target));else scrollTo(0,target);}return true;
 }
}
class TextView extends View {String text="";Layout layout;CharSequence getText(){return text;}Layout getLayout(){return layout;}int getTotalPaddingTop(){return 0;}float getTextSize(){return 16;}}
class Layout {
 String text;int[] starts;int lineHeight;
 Layout(String text,int[] starts,int height){this.text=text;this.starts=starts;lineHeight=height;}
 int getLineCount(){return starts.length;}int getLineStart(int line){return starts[line];}int getLineEnd(int line){return line+1<starts.length?starts[line+1]:text.length();}
 int getLineTop(int line){return line*lineHeight;}int getLineBottom(int line){return (line+1)*lineHeight;}int getLineBaseline(int line){return getLineBottom(line)-4;}
 int getLineForVertical(int y){return Math.max(0,Math.min(starts.length-1,y/lineHeight));}
 int getLineForOffset(int offset){int line=0;while(line+1<starts.length&&starts[line+1]<=offset)line++;return line;}
 int getHeight(){return starts.length*lineHeight;}
}
class Queue {static ArrayDeque<Runnable> pending=new ArrayDeque<>();static void run(){while(!pending.isEmpty())pending.remove().run();}}
class android {
 static class R {static class id {static final int accessibilityActionScrollUp=16908344,accessibilityActionScrollDown=16908346;}}
 static class os {static class Bundle {}}
 static class view {
  static class accessibility {static class AccessibilityNodeInfo {static final int ACTION_SCROLL_FORWARD=4096,ACTION_SCROLL_BACKWARD=8192;}}
  static class ViewTreeObserver {interface OnPreDrawListener {boolean onPreDraw();}boolean isAlive(){return true;}void removeOnPreDrawListener(OnPreDrawListener l){}void addOnPreDrawListener(OnPreDrawListener l){Queue.pending.add(()->l.onPreDraw());}}
 }
}

package com.simon.mytasks;
import org.json.*;
import java.util.*;
/** Production methods injected by runner; geometry/focus doubles, NOT native IME proof. */
public class EmailReplyHingeUiHarness {
 static class View {
  static final int VISIBLE=0,GONE=8;
  android.view.ViewGroup.LayoutParams lp=new LinearLayout.LayoutParams(-1,-1);
  View owner; int width,height,visibility=VISIBLE,x,y,scroll; boolean focused,selected;
  final List<View> children=new ArrayList<>();
  android.view.ViewGroup.LayoutParams getLayoutParams(){return lp;}
  // Android applies requested sizes at a later layout, not synchronously here.
  void setLayoutParams(android.view.ViewGroup.LayoutParams value){lp=value;}
  void addView(View v){if(v.owner!=null)throw new AssertionError("reparented input");children.add(v);v.owner=this;}
  int getChildCount(){return children.size();} View getChildAt(int i){return children.get(i);}
  int getWidth(){return width;} int getHeight(){return height;}
  int getPaddingLeft(){return 0;} int getPaddingRight(){return 0;}
  void getLocationInWindow(int[] p){p[0]=x;p[1]=y;}
  void setVisibility(int value){visibility=value;} int getVisibility(){return visibility;}
  int getScrollY(){return scroll;} boolean hasFocus(){return focused;}
  void requestFocus(){View root=this;while(root.owner!=null)root=root.owner;clearFocus(root);focused=true;}
  static void clearFocus(View v){v.focused=false;for(View child:v.children)clearFocus(child);}
 }
 static class LinearLayout extends View {
  static class LayoutParams extends android.view.ViewGroup.LayoutParams {
   float weight; LayoutParams(int w,int h){super(w,h);} LayoutParams(int w,int h,float v){super(w,h);weight=v;}
  }
 }
 static class FrameLayout extends View {
  static class LayoutParams extends android.view.ViewGroup.LayoutParams {
   int leftMargin,topMargin;LayoutParams(int w,int h){super(w,h);}
  }
 }
 static class EditText extends View {
  String text;int start=2,end=5;EditText(String value){text=value;scroll=17;}
  int getSelectionStart(){return start;}int getSelectionEnd(){return end;}String getText(){return text;}
 }
 static class Button extends View {Runnable action;void click(){action.run();}}
 static class Config {float fontScale;int screenWidthDp;}
 static class Metrics {float density;}
 static class Resources {Config config=new Config();Metrics metrics=new Metrics();Config getConfiguration(){return config;}Metrics getDisplayMetrics(){return metrics;}}
 static class Activity {
  Resources resources=new Resources();List<android.graphics.Rect> composerOcclusions=new ArrayList<>();
  Resources getResources(){return resources;}int dp(int v){return Math.round(v*resources.metrics.density);}
  static class Ui {void selected(Button v,boolean b){v.selected=b;}} Ui ui=new Ui();
 }
 static class Controller {EmailReplyState state=new EmailReplyState(new JSONObject());int saves;void persist(){saves++;}}
 final Activity a=new Activity();final Controller c=new Controller();
 LinearLayout parent=new LinearLayout(),content=new LinearLayout(),workspacePanes=new LinearLayout(),editorPane=new LinearLayout(),assistantPane=new LinearLayout();
 FrameLayout viewport=new FrameLayout();View hingeGap=new View();EditText body=new EditText("body composing"),instruction=new EditText("instruction composing");
 View editorScroll=new View(),assistantScroll=new View();Button emailTab=new Button(),assistantTab=new Button(),hideAssistant=new Button();
 boolean binding,lastSplit,hingeSplit;int hingeLeft,hingeRight;String boundDraft="fixture";
 void render(){throw new AssertionError("mode transition rebuilt inputs");}
 boolean keyboardVisible(){return false;}void keyboardChanged(boolean visible){}void restoreKeyboard(){}void updateModes(){}
 // ACTUAL_METHODS
 EmailReplyHingeUiHarness(float density,float font,int kind)throws Exception {
  a.resources.metrics.density=density;a.resources.config.fontScale=font;a.resources.config.screenWidthDp=1220;
  viewport.width=a.dp(1220);viewport.height=a.dp(900);viewport.x=a.dp(23);viewport.y=a.dp(41);
  parent.width=viewport.width;parent.height=viewport.height;parent.lp=new FrameLayout.LayoutParams(-1,-1);viewport.addView(parent);
  parent.addView(emailTab);parent.addView(content);content.addView(workspacePanes);workspacePanes.addView(editorPane);workspacePanes.addView(hingeGap);workspacePanes.addView(assistantPane);
  editorPane.addView(body);assistantPane.addView(instruction);assistantPane.addView(hideAssistant);
  emailTab.action=this::showEditor;hideAssistant.action=this::showEditor;assistantTab.action=()->openAssistant("improve");
  c.state.data.put("draft",new JSONObject().put("id","fixture").put("status","draft"));
  put("assistant",true);put("focus","instruction");instruction.requestFocus();
  if(kind==1)a.composerOcclusions.add(new android.graphics.Rect(viewport.x+a.dp(600),viewport.y,viewport.x+a.dp(620),viewport.y+viewport.height));
  if(kind==2)a.composerOcclusions.add(new android.graphics.Rect(viewport.x,viewport.y+a.dp(400),viewport.x+viewport.width,viewport.y+a.dp(420)));
  fitPane();reflow(false);settle();
 }
 void settle(){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)parent.lp;parent.width=p.width;parent.height=p.height;fitPane();}
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 void bounds(int x,int y,int w,int h,String label){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)parent.lp;check(p.leftMargin==a.dp(x)&&p.topMargin==a.dp(y)&&p.width==a.dp(w)&&p.height==a.dp(h),label+": parent bounds were "+p.leftMargin+","+p.topMargin+","+p.width+","+p.height);}
 void geometry(int kind,boolean open,String label){
  if(kind==1&&open)bounds(0,0,1220,900,label);else if(kind==1)bounds(0,0,600,900,label);else if(kind==2)bounds(0,420,1220,480,label);else bounds(0,0,1220,900,label);
  check(hingeSplit==(kind==1&&open),label+": hinge split");check(lastSplit==open,label+": split mode");
  check(hingeGap.lp.width==(kind==1&&open?a.dp(20):0),label+": hinge gap");
  check(editorPane.lp.width==(open?(kind==1?a.dp(600):0):-1),label+": editor width");
  check(assistantPane.lp.width==(open?0:-1),label+": assistant width");
  check(editorPane.visibility==View.VISIBLE&&assistantPane.visibility==(open?View.VISIBLE:View.GONE),label+": visible pane");
  check(emailTab.selected==!open&&assistantTab.selected==open,label+": selected tab");
  check(c.state.data.optBoolean("assistant")==open&&c.state.data.optBoolean("collapsed")==!open,label+": explicit mode intent overwritten");
  check(c.state.data.optString("focus").equals(open?"instruction":"body"),label+": explicit focus intent overwritten");
  check((open?instruction:body).hasFocus(),label+": input focus");
  check(hideAssistant.visibility==(open?View.VISIBLE:View.GONE),label+": hide visibility");
  for(View chrome:parent.children)if(chrome!=content)check(chrome.lp.width==(kind==1&&open?a.dp(600):-1),label+": chrome width");
 }
 void transitions(int kind)throws Exception {
  EditText originalBody=body,originalInstruction=instruction;View bodyOwner=body.owner,instructionOwner=instruction.owner;
  for(int repeat=0;repeat<3;repeat++){
   // Both Hide and Email use the real showEditor action, starting with old instruction focus.
   (repeat%2==0?hideAssistant:emailTab).click();geometry(kind,false,"Hide/Email immediate");settle();geometry(kind,false,"Hide/Email settled");
   assistantTab.click();geometry(kind,true,"Show immediate");settle();geometry(kind,true,"Show settled");
   check(body==originalBody&&instruction==originalInstruction&&body.owner==bodyOwner&&instruction.owner==instructionOwner,"input replaced or detached");
   check(body.text.equals("body composing")&&instruction.text.equals("instruction composing")&&body.start==2&&body.end==5&&instruction.start==2&&instruction.end==5,"text/selection changed");
   check(c.state.data.optInt("selection_start")==2&&c.state.data.optInt("instruction_end")==5&&c.state.data.optString("instruction").equals(instruction.text),"capture lost input state");
   check(c.saves==(repeat+1)*2,"mode persistence count");
  }
 }
 public static void main(String[] args)throws Exception {
  int cases=0;
  for(float density:new float[]{1f,1.5f,2f,3f})for(float font:new float[]{1f,1.3f,1.5f})for(int kind=0;kind<3;kind++){
   try{new EmailReplyHingeUiHarness(density,font,kind).transitions(kind);cases++;}
   catch(AssertionError error){throw new AssertionError("density="+density+" font="+font+" occlusion="+kind+" "+error.getMessage(),error);}
  }
  System.out.println("EmailReplyHingeUiHarness passed: "+cases+" actual-method geometry cases, Hide/Email/Show repeated in place; host doubles, no native IME claim");
 }
}

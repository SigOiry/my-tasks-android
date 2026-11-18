import java.util.*;

/** Host-only fixture for the actual ConversationsUi.buildTask body. */
class TaskDiscussionChooserHarness {
 static final class Gravity {static final int CENTER=17,CENTER_VERTICAL=16;}
 static final class R {static final class drawable {static int ic_back=1,ic_more=2;}}
 static class View {
  static final int ACCESSIBILITY_LIVE_REGION_POLITE=1;
  String label=""; int natural,height,live; Runnable click;
  void setAccessibilityLiveRegion(int value){live=value;}
 }
 static class LinearLayout extends View {
  static final class LayoutParams {final int width,height,weight; LayoutParams(int w,int h){this(w,h,0);} LayoutParams(int w,int h,int z){width=w;height=h;weight=z;}}
  List<View> children=new ArrayList<>(); List<LayoutParams> params=new ArrayList<>(); int gravity,top,bottom;
  void addView(View v,LayoutParams lp){children.add(v);params.add(lp);}
  void setGravity(int g){gravity=g;} void setPadding(int l,int t,int r,int b){top=t;bottom=b;}
 }
 static final class ScrollView extends View {
  LinearLayout child;boolean fill;ScrollView(Activity a){} void setFillViewport(boolean b){fill=b;}
  void addView(LinearLayout c){check(child==null,"one scroll child");child=c;}
 }
 static class Activity {
  int muted;String detailsMode="Discuss"; Activity ui=this; List<String> events=new ArrayList<>();
  LinearLayout horizontal(){return new LinearLayout();} LinearLayout vertical(){return new LinearLayout();}
  int dp(int d){return Math.round(2.625f*d);} void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
  View node(String s,int h,Runnable click){View v=new View();v.label=s;v.natural=h;v.click=click;return v;}
  View heading(String s,int size){return node(s,256,null);} View label(String s,int size,int color){return node(s,size==15?300:90,null);}
  View icon(int r,String s,Runnable f){return node(s,126,f);} View button(String s,Runnable f){return node(s,140,f);} View primary(String s,Runnable f){return button(s,f);}
  void renderPage(boolean back){events.add("back:"+back);} void render(){events.add("render");}
 }
 final Activity a=new Activity(); final Controller c=new Controller(); String boundTask;View state;
 class Controller {
  String selected="",notice="Connection notice",visible="new-thread"; Controller state=this;
  String taskSelection(String task){return selected;} void taskSelection(String task,String id){a.events.add("select:"+task+":"+id);}
  void create(String task,Runnable after){a.events.add("create:"+task);after.run();} void save(){a.events.add("save");} void hide(){a.events.add("hide");}
 }
 void attach(){a.events.add("attach");} void buildThread(LinearLayout pane,String id,boolean taskBound){a.events.add("thread:"+id+":"+taskBound);} void taskPicker(String task){a.events.add("picker:"+task);} void emptyTaskMenu(String task){a.events.add("menu:"+task);}
 /* ACTUAL_BUILDER */
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void geometry(View body,int available){
  ScrollView sc=body instanceof ScrollView?(ScrollView)body:null;
  LinearLayout content=sc==null?(LinearLayout)body:sc.child;
  int desired=content.top+content.bottom;
  for(View v:content.children)desired+=v.natural;
  // A bounded vertical column gives later wrap-content siblings only what remains.
  // A ScrollView measures its one content child vertically unbounded, then fills spare space.
  int extent=sc==null?available:(sc.fill?Math.max(available,desired):desired);
  int remaining=extent-content.top-content.bottom;
  for(View v:content.children){v.height=Math.min(v.natural,Math.max(0,remaining));remaining-=v.height;}
  View primary=content.children.get(2),saved=content.children.get(3);
  check(saved.height==saved.natural,"saved chooser action received "+saved.height+" of "+saved.natural+" px at viewport "+available);
  check(primary.height==primary.natural && content.children.get(4).height==90,"other action or notice clipped");
  check(extent>=available && content.gravity==Gravity.CENTER,"keep centered when roomy");
  if(desired>available)check(sc!=null && extent-available>=desired-available,"overflow must be scroll-reachable");
 }
 public static void main(String[] args){
  TaskDiscussionChooserHarness h=new TaskDiscussionChooserHarness();LinearLayout pane=new LinearLayout();h.buildTask(pane,"task-A");
  check(h.a.events.equals(Arrays.asList("attach","hide")),"entering chooser must not create or select");
  check(pane.children.size()==2 && pane.params.get(1).weight==1,"header separate from remaining viewport");
  View body=pane.children.get(1);geometry(body,929);geometry(body,440);geometry(body,1800);
  LinearLayout content=((ScrollView)body).child;check(content.children.size()==5 && h.state==content.children.get(4) && h.state.live==1,"attached notice/order");
  check(content.children.get(2).label.equals("New task conversation") && content.children.get(3).label.equals("Choose saved conversation"),"full original labels");
  h.a.events.clear();content.children.get(3).click.run();check(h.a.events.equals(Arrays.asList("picker:task-A")),"saved picker action");
  h.a.events.clear();content.children.get(2).click.run();check(h.a.events.equals(Arrays.asList("create:task-A","select:task-A:new-thread","save","render")),"original explicit create callback");
  h.a.events.clear();h.c.selected="existing-thread";LinearLayout selected=new LinearLayout();h.buildTask(selected,"task-A");check(selected.children.isEmpty() && h.a.events.equals(Arrays.asList("attach","thread:existing-thread:true")),"existing task thread bypasses chooser");
 }
}

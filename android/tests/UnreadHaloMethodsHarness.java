package com.simon.mytasks;
import java.util.*;
/** Extracted production draw/scheduling methods; host seams, NOT a device render. */
public final class UnreadHaloMethodsHarness {
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
 static class Paint {int alpha,color;float width;void setColor(int c){color=c;}void setAlpha(int a){alpha=a;}void setStrokeWidth(float w){width=w;}}
 static class Canvas {
  final ArrayList<float[]> rings=new ArrayList<>();int categoryMarks;final ArrayList<Integer> colors=new ArrayList<>();
  void drawLine(float a,float b,float c,float d,Paint p){}
  void drawCircle(float x,float y,float r,Paint p){categoryMarks++;}
  void drawRoundRect(float l,float t,float r,float b,float x,float y,Paint p){rings.add(new float[]{l,t,r,b,x,y,p.alpha,p.width});colors.add(p.color);}
 }
 static class android {
  static class graphics {static class drawable {
   static class GradientDrawable {void setCornerRadii(float[] r){}}
   static class InsetDrawable {int l,t,r,b;InsetDrawable(Object fill,int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}}
   static class RippleDrawable {InsetDrawable inset;RippleDrawable(Object color,InsetDrawable inset,Object mask){this.inset=inset;}}
  }}
  static class content {static class res {static class ColorStateList {static Object valueOf(int c){return c;}}}}
 }
 static class Rect {}
 static class Base {protected void onDraw(Canvas c){}}
 static class Ui {int separator=1,unread=2,focus=3,primary=4;android.graphics.drawable.GradientDrawable shape(int c,int s,int r){return new android.graphics.drawable.GradientDrawable();}boolean motion=true;float density=1f;int dp(float n){return Math.round(n*density);}boolean motionEnabled(){return motion;}}
 static class TasksUi {boolean rest=true;boolean effectsAtRest(){return rest;}}
 static class Activity {String colorMode="Project";boolean foreground=true;TasksUi tasksUi=new TasksUi();}
 static class WorkspaceUi {static final int RADIUS_ROW=12;}
 static class Animator {}
 static class AnimatorListenerAdapter {public void onAnimationEnd(Animator a){}}
 static class LinearInterpolator {}
 interface Frame {void run(ValueAnimator frame);}
 static class ValueAnimator extends Animator {
  static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}
  Frame frame;AnimatorListenerAdapter listener;long duration,elapsed;int repeats;float value;boolean started;
  void setDuration(long d){duration=d;}void setRepeatCount(int n){repeats=n;}void setInterpolator(Object o){}
  void addUpdateListener(Frame f){frame=f;}void addListener(AnimatorListenerAdapter l){listener=l;}
  void removeAllUpdateListeners(){frame=null;}void removeAllListeners(){listener=null;}void cancel(){started=false;}
  Object getAnimatedValue(){return value;}long getCurrentPlayTime(){return elapsed;}void start(){started=true;}
  void step(long t,float v){elapsed=t;value=v;if(frame!=null)frame.run(this);}
  void finish(){if(listener!=null)listener.onAnimationEnd(this);}
 }
 static final class TaskRow extends Base {
  static final int VISIBLE=0;
  Ui ui=new Ui();Activity a=new Activity();UpdateEmphasis emphasis=new UpdateEmphasis();
  String taskId="qa-task",token="exact-token";boolean unread=true,paused,ownsEmphasis,arrival,shown=true,attached=true;
  int tint;android.graphics.drawable.RippleDrawable background;int[] padding;void setBackground(android.graphics.drawable.RippleDrawable b){background=b;}void setPadding(int l,int t,int r,int b){padding=new int[]{l,t,r,b};}
  float breath;int category=0xff8972bb,cue=0xff6b5992;Paint halo=new Paint(),categoryPaint=new Paint();Rect visible=new Rect();ValueAnimator animator;
  final Runnable continueEmphasis=this::startEmphasis;final ArrayList<Runnable> scheduled=new ArrayList<>();
  int width=400,height=84;boolean isSelected(){return false;}int getWidth(){return width;}int getHeight(){return height;}
  boolean isAttachedToWindow(){return attached;}int getWindowVisibility(){return VISIBLE;}boolean isShown(){return shown;}boolean getGlobalVisibleRect(Rect r){return shown;}
  void invalidate(){}void postDelayed(Runnable r,long ms){scheduled.add(r);}void removeCallbacks(Runnable r){scheduled.removeIf(v->v==r);}
  void dispatch(){ArrayList<Runnable> callbacks=new ArrayList<>(scheduled);scheduled.clear();for(Runnable r:callbacks)r.run();}
// METHODS
 }
 static void checkDensityBounds(){
  // Independently inspect the FULL painted stroke, not just its centerline.
  // Rounded dp(1) can be smaller than half of rounded dp(2), on either gutter edge.
  for(float density:new float[]{2.25f,.75f,1f,1.25f,1.5f,1.75f,2f,2.625f,2.75f,3f,3.5f,4f}){
   for(int widthDp:new int[]{280,320,600})for(int heightDp:new int[]{84,180,320}){
    TaskRow row=new TaskRow();row.ui.density=density;
    row.width=Math.round(widthDp*density);row.height=Math.round(heightDp*density);
    row.groupCorners(true,true);
    android.graphics.drawable.InsetDrawable bg=row.background.inset;
    int gutter=Math.round(16*density);
    check(bg.l==gutter&&bg.t==gutter&&bg.r==gutter&&bg.b==gutter,"16dp background gutter preserved at density="+density);
    check(row.padding[0]==gutter&&row.padding[2]==gutter,"children remain inside background at density="+density);
    Canvas still=null;
    for(float breath:new float[]{0f,.6f,1f}){
     row.breath=breath;Canvas canvas=new Canvas();row.onDraw(canvas);
     check(canvas.rings.size()==16,"fifteen falloff rings plus rim at density="+density);
     for(int i=0;i<canvas.rings.size();i++){
      float[] r=canvas.rings.get(i);float half=r[7]/2f;
      float left=r[0]-half,top=r[1]-half,right=r[2]+half,bottom=r[3]+half;
      String label="density="+density+" ring="+(15-i)+" breath="+breath+" painted=["+left+","+top+","+right+","+bottom+"]";
      check(left>=0&&top>=0&&right<=row.width&&bottom<=row.height,"whole stroke inside View: "+label);
      if(i<15){
       check(r[0]+half<=bg.l&&r[1]+half<=bg.t&&r[2]-half>=row.width-bg.r&&r[3]-half>=row.height-bg.b,"whole halo stroke outside background: "+label);
       check(r[6]>0&&canvas.colors.get(i)==row.category,"visible task-hue halo: "+label);
      }else check(r[0]==bg.l&&r[1]==bg.t&&r[2]==row.width-bg.r&&r[3]==row.height-bg.b,"rim follows actual background: "+label);
      if(still!=null)for(int c=0;c<6;c++)check(r[c]==still.rings.get(i)[c],"breathing never changes geometry: "+label);
     }
     if(still==null)still=canvas;
    }
   }
   System.out.println("Whole-stroke View/background bounds passed at density="+density+" (9 sizes, 3 breath levels)");
  }
 }
 public static void main(String[] args){
  checkDensityBounds();
  TaskRow row=new TaskRow();row.groupCorners(true,true);
  check(row.padding[0]==16&&row.padding[2]==16&&row.padding[1]>=18&&row.padding[3]>=18,"all children and full-size touch targets are inside the reading surface, never in the halo gutter");
  check(row.background.inset.l==16&&row.background.inset.t==16&&row.background.inset.r==16&&row.background.inset.b==16,"actual background matches external draw geometry");
  Canvas still=new Canvas();row.onDraw(still);
  check(still.rings.size()==16,"static unread has fifteen external falloff rings plus crisp edge");
  float[] edge=still.rings.get(15);check(edge[0]==16&&edge[1]==16&&edge[2]==384&&edge[3]==68,"edge reserves a 16dp inside-View gutter");
  for(int i=0;i<15;i++){float[] ring=still.rings.get(i);check(ring[0]<edge[0]&&ring[2]>edge[2],"halo is outside edge, not border replacement");check(ring[0]-ring[7]/2>=0&&ring[2]+ring[7]/2<=400,"halo stroke stays inside clipped View bounds");}
  check(still.rings.get(7)[6]>=35,"static falloff remains obvious eight dp outside surface");
  check(still.rings.get(0)[6]>=6,"large-radius falloff remains present fifteen dp outside surface");
  for(int i=0;i<15;i++)check(still.colors.get(i)==row.category,"external halo uses task hue, not fixed unread blue");
  check(still.categoryMarks==0,"no project dot over checkbox");
  check(edge[6]<=180&&edge[7]<=1,"crisp rim is secondary, not a dominant hard outline");
  for(int width:new int[]{280,320,600})for(int height:new int[]{84,180,320}){
   row.width=width;row.height=height;Canvas sized=new Canvas();row.onDraw(sized);
   for(int i=0;i<15;i++){float[] r=sized.rings.get(i);check(r[0]-r[7]/2>=0&&r[1]-r[7]/2>=0&&r[2]+r[7]/2<=width&&r[3]+r[7]/2<=height,"compact/split/large-text halo never paints outside View");check(r[0]+r[7]/2<=16&&r[1]+r[7]/2<=16,"halo stays external to reading surface at every size");}
  }
  row.width=400;row.height=84;
  row.breath=.6f;Canvas peak=new Canvas();row.onDraw(peak);check(peak.rings.get(6)[6]>still.rings.get(6)[6],"resting breath changes actual external paint alpha");
  for(int i=0;i<16;i++)for(int c=0;c<6;c++)check(peak.rings.get(i)[c]==still.rings.get(i)[c],"draw geometry never moves during breath");
  row.startEmphasis();check(row.animator!=null&&row.arrival,"new exact token receives arrival");
  row.animator.step(800,.5f);check(row.breath==1,"first arrival is stronger");
  row.animator.step(2400,.5f);check(row.breath<.7f&&row.breath>.3f,"later cycles use lower amplitude rest");
  for(int batch=0;batch<4;batch++){ValueAnimator old=row.animator;check(old.duration*(old.repeats+1)==6400,"bounded scheduling quantum");old.finish();row.dispatch();check(row.animator!=null&&!row.arrival,"sustained rest continues beyond 8 seconds without replaying arrival");}
  row.a.tasksUi.rest=false;row.animator.step(500,.5f);check(row.animator==null,"interaction stops actual animation");
  row.a.tasksUi.rest=true;row.resume();check(row.animator!=null&&!row.arrival,"idle resumes gentle rest, not arrival");
  row.pause();row.dispatch();check(row.animator==null&&row.emphasis.activeCount()==0,"background/detachment releases effect and callbacks");
  row.ui.motion=false;row.resume();check(row.animator==null,"reduced motion keeps static draw only");
  row.ui.motion=true;row.token="newer-token";row.resume();check(row.animator!=null&&row.arrival,"new exact canonical token receives fresh arrival");
  row.unread=false;row.stopEmphasis();row.groupCorners(false,false);check(row.padding[1]==10&&row.padding[3]==10,"recycled ordinary row restores compact height");Canvas reviewed=new Canvas();row.onDraw(reviewed);check(reviewed.rings.isEmpty()&&reviewed.categoryMarks==0,"read task has no category rail/dot intersecting completion target");
  System.out.println("Actual unread draw geometry and 25.6-second scheduling checks passed (host seams)");
 }
}

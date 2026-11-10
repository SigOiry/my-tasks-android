package com.simon.mytasks;
import java.util.*;
/** Extracted production draw/scheduling methods; host seams, NOT a device render. */
public final class UnreadHaloMethodsHarness {
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
 static class Paint {int alpha,color;float width;void setColor(int c){color=c;}void setAlpha(int a){alpha=a;}void setStrokeWidth(float w){width=w;}}
 static class Canvas {
  final ArrayList<float[]> rings=new ArrayList<>();
  void drawLine(float a,float b,float c,float d,Paint p){}
  void drawCircle(float x,float y,float r,Paint p){}
  void drawRoundRect(float l,float t,float r,float b,float x,float y,Paint p){rings.add(new float[]{l,t,r,b,x,y,p.alpha,p.width});}
 }
 static class Rect {}
 static class Base {protected void onDraw(Canvas c){}}
 static class Ui {int separator=1,unread=2,focus=3;boolean motion=true;int dp(float n){return Math.round(n);}boolean motionEnabled(){return motion;}}
 static class TasksUi {boolean rest=true;boolean effectsAtRest(){return rest;}}
 static class Activity {String colorMode="Neutral";boolean foreground=true;TasksUi tasksUi=new TasksUi();}
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
  float breath;int category;Paint halo=new Paint(),categoryPaint=new Paint();Rect visible=new Rect();ValueAnimator animator;
  final Runnable continueEmphasis=this::startEmphasis;final ArrayList<Runnable> scheduled=new ArrayList<>();
  boolean isSelected(){return false;}int getWidth(){return 400;}int getHeight(){return 80;}
  boolean isAttachedToWindow(){return attached;}int getWindowVisibility(){return VISIBLE;}boolean isShown(){return shown;}boolean getGlobalVisibleRect(Rect r){return shown;}
  void invalidate(){}void postDelayed(Runnable r,long ms){scheduled.add(r);}void removeCallbacks(Runnable r){scheduled.removeIf(v->v==r);}
  void dispatch(){ArrayList<Runnable> callbacks=new ArrayList<>(scheduled);scheduled.clear();for(Runnable r:callbacks)r.run();}
// METHODS
 }
 public static void main(String[] args){
  TaskRow row=new TaskRow();Canvas still=new Canvas();row.onDraw(still);
  check(still.rings.size()==8,"static unread has seven external falloff rings plus crisp edge");
  float[] edge=still.rings.get(7);check(edge[0]==8&&edge[1]==8&&edge[2]==392&&edge[3]==72,"edge reserves an 8dp inside-View gutter");
  for(int i=0;i<7;i++){float[] ring=still.rings.get(i);check(ring[0]<edge[0]&&ring[2]>edge[2],"halo is outside edge, not border replacement");check(ring[0]-ring[7]/2>=0&&ring[2]+ring[7]/2<=400,"halo stroke stays inside clipped View bounds");}
  check(still.rings.get(6)[6]>0,"reduced-motion/static halo is still visible");
  row.breath=.6f;Canvas peak=new Canvas();row.onDraw(peak);check(peak.rings.get(6)[6]>still.rings.get(6)[6],"resting breath changes actual external paint alpha");
  for(int i=0;i<8;i++)for(int c=0;c<6;c++)check(peak.rings.get(i)[c]==still.rings.get(i)[c],"draw geometry never moves during breath");
  row.startEmphasis();check(row.animator!=null&&row.arrival,"new exact token receives arrival");
  row.animator.step(800,.5f);check(row.breath==1,"first arrival is stronger");
  row.animator.step(2400,.5f);check(row.breath<.7f&&row.breath>.3f,"later cycles use lower amplitude rest");
  for(int batch=0;batch<4;batch++){ValueAnimator old=row.animator;check(old.duration*(old.repeats+1)==6400,"bounded scheduling quantum");old.finish();row.dispatch();check(row.animator!=null&&!row.arrival,"sustained rest continues beyond 8 seconds without replaying arrival");}
  row.a.tasksUi.rest=false;row.animator.step(500,.5f);check(row.animator==null,"interaction stops actual animation");
  row.a.tasksUi.rest=true;row.resume();check(row.animator!=null&&!row.arrival,"idle resumes gentle rest, not arrival");
  row.pause();row.dispatch();check(row.animator==null&&row.emphasis.activeCount()==0,"background/detachment releases effect and callbacks");
  row.ui.motion=false;row.resume();check(row.animator==null,"reduced motion keeps static draw only");
  row.ui.motion=true;row.token="newer-token";row.resume();check(row.animator!=null&&row.arrival,"new exact canonical token receives fresh arrival");
  row.unread=false;row.stopEmphasis();Canvas reviewed=new Canvas();row.onDraw(reviewed);check(reviewed.rings.isEmpty(),"only canonical read state removes unread halo");
  System.out.println("Actual unread draw geometry and 25.6-second scheduling checks passed (host seams)");
 }
}

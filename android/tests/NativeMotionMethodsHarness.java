public class NativeMotionMethodsHarness {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static class View {
  int left,top,width,height;float x,y,alpha; boolean shown=true; FrameLayout.LayoutParams params; Animator anim=new Animator();
  View(){} View(int l,int t,int w,int h){left=l;top=t;width=w;height=h;}
  int getLeft(){return left;} int getTop(){return top;} int getWidth(){return width;} int getHeight(){return height;}
  float getTranslationX(){return x;} float getTranslationY(){return y;} Object getTag(){return tag;}Object tag;void setTranslationX(float v){x=v;} void setTranslationY(float v){y=v;} void setAlpha(float v){alpha=v;}
  boolean isShown(){return shown;} void setLayoutParams(FrameLayout.LayoutParams p){params=p;} Animator animate(){return anim;}
 }
 static class ViewGroup extends View {View[] children; View getChildAt(int i){return children[i];}}
 static class FrameLayout {static class LayoutParams {int width,height,topMargin;LayoutParams(int w,int h){width=w;height=h;}}}
 static class Animator {float targetX,targetY;long duration;boolean started,cancelled;void cancel(){cancelled=true;} Animator translationX(float x){targetX=x;return this;} Animator translationY(float y){targetY=y;return this;} Animator setDuration(long n){duration=n;return this;} Animator setInterpolator(Object i){return this;}void start(){started=true;}}
 static class android {static class view {static class animation {static class DecelerateInterpolator {}}}}
 static class WorkspaceUi {
  static final long MOTION_NAVIGATION=280;boolean enabled=true;boolean motionEnabled(){return enabled;}int dp(int n){return n;}
// METHODS
 }
 static class NativeMotionPolicy {static long duration(String s){return 280;}}
 static class Activity {String filter,selected="",pendingSummary="",draft,detailsMode;boolean rendered,busy,editing,unsaved;int detailY;Runnable confirmed;
 WorkspaceUi ui=new WorkspaceUi();java.util.List<Object> dialogClosers=new java.util.ArrayList<>();String destination="Tasks";View root=new View(),listPane=new View(),detailPane=new View();
 void captureDraft(){}void saveWorkspacePreferences(){}void render(){rendered=true;}boolean dirty(){return unsaved;}void maybeSummary(){}void notice(String a,String b){}void confirm(String a,String b,Runnable r){confirmed=r;}
// ACTIVITY_METHODS
 }
 static class ListView extends ViewGroup {int getChildCount(){return children.length;}}
 static class TaskRowView extends View {TaskRowView(String id,int top){tag=id;this.top=top;}}
 static class TasksUi {Activity a=new Activity();View scopeCapsule;ViewGroup scopeTrack;ListView list;float scopeOrigin;boolean reset,paused,touching,scrolling;void resetAnchor(){reset=true;}
// TASK_METHODS
 }
 public static void main(String[] args){
// ASSERTIONS
 System.out.println("Actual native motion methods passed");
 }
}

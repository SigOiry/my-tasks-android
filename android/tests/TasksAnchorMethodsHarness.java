import java.util.ArrayList;

/** Host geometry/scheduler seams only; extracted production methods run unchanged. */
public final class TasksAnchorMethodsHarness {
 private ListView list=new ListView();
 private final ArrayList<Entry> entries=new ArrayList<>();
 private final Anchor anchor=new Anchor();
 private Runnable restore;
 private boolean anchorReset,filtersMatch=true;
 private boolean sameFilters(){return filtersMatch;}
 // PRODUCTION_METHODS
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static TasksAnchorMethodsHarness setup(int first,int... tops){
  TasksAnchorMethodsHarness h=new TasksAnchorMethodsHarness();
  h.entries.add(new Entry(null));h.entries.add(new Entry(new Task("a")));h.entries.add(new Entry(new Task("b")));
  h.list.first=first;for(int top:tops)h.list.children.add(new View(top));return h;
 }
 public static void main(String[] args){
  // Project -> Smart adds the sorted-by line. Color changes keep it;
  // returning to Project removes it. Capture runs more than once per rebuild.
  for(int offset:new int[]{0,-12}){
   TasksAnchorMethodsHarness h=setup(0,offset,100+offset,136+offset,208+offset);
   for(String mode:new String[]{"Project","Smart","Importance color","Neutral color","Project"}){
    h.capture();h.capture();
    h.restoreAnchor();h.list.flush();
    check(h.list.selected==0&&h.list.selectedTop==offset,
      "visible toolbar must anchor header at "+offset+" in "+mode+"; got position="+h.list.selected+" top="+h.list.selectedTop);
    int height=mode.equals("Project")?100:124;
    h.list.children.clear();h.list.children.add(new View(offset));h.list.children.add(new View(height+offset));h.list.children.add(new View(height+36+offset));
   }
  }
  // Once the toolbar is offscreen, keep task identity rather than a group heading.
  TasksAnchorMethodsHarness scrolled=setup(1,-22,14,86);
  scrolled.capture();
  check(scrolled.anchor.anchorId.equals("a")&&scrolled.anchor.anchorTop==14,"scrolled group anchors first task");
  scrolled.entries.add(0,new Entry(new Task("inserted")));
  scrolled.restoreAnchor();scrolled.list.flush();
  check(scrolled.list.selected==3&&scrolled.list.selectedTop==14,"insertion follows scrolled task identity");
  TasksAnchorMethodsHarness task=setup(2,-19,53);
  task.list.padding=8;task.capture();task.restoreAnchor();task.list.flush();
  check(task.list.selected==2&&task.list.selectedTop==-27,"partly visible task keeps padding-relative offset");
  // Pending restore and unlaid-out/filter-changing lists cannot overwrite an anchor.
  task.restoreAnchor();task.list.children.clear();task.capture();task.list.flush();
  check(task.anchor.anchorId.equals("a")&&task.anchor.anchorTop==-27,"empty rebuilding list keeps saved anchor");
  task.list.children.add(new View(0));task.filtersMatch=false;task.capture();
  check(task.anchor.anchorId.equals("a"),"new preferences cannot recapture stale geometry");
  task.filtersMatch=true;task.restoreAnchor();task.capture();task.list.flush();
  check(task.anchor.anchorId.equals("a"),"queued restoration cannot be recaptured");
  task.resetAnchor();task.capture();task.restoreAnchor();task.list.flush();
  check(task.list.selected==0&&task.list.selectedTop==0,"filter reset still returns to header");
  System.out.println("visible header stays fixed across repeated mode captures/restores; scrolled task identity, offsets and rebuild guards preserved");
 }
 static final class View {final int top;View(int top){this.top=top;}int getTop(){return top;}}
 static final class ListView {
  final ArrayList<View> children=new ArrayList<>();final ArrayList<Runnable> queue=new ArrayList<>();
  int first,padding,selected=-1,selectedTop;
  int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
  int getFirstVisiblePosition(){return first;}int getPaddingTop(){return padding;}
  int getHeaderViewsCount(){return 1;}int getCount(){return 5;}
  void setSelectionFromTop(int position,int top){selected=position;selectedTop=top;}
  void removeCallbacks(Runnable r){queue.remove(r);}void post(Runnable r){queue.add(r);}
  void flush(){while(!queue.isEmpty())queue.remove(0).run();}
 }
 static final class Entry {final Task task;Entry(Task task){this.task=task;}}
 static final class Task {final String id;Task(String id){this.id=id;}String optString(String key,String fallback){return id;}}
}

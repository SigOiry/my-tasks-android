package com.simon.mytasks;

/** Executes the same pure presentation decisions consumed by the native Views. */
public final class TaskPresentationTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static double luminance(int color){
  double result=0;double[] weights={.2126,.7152,.0722};
  for(int i=0;i<3;i++){double channel=((color>>(16-i*8))&255)/255.0;result+=weights[i]*(channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4));}
  return result;
 }
 private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 public static void main(String[] args){
  String longMetadata="A project with a very long name · Thursday, November 12, 2026";
  check(TaskPresentation.context(longMetadata,true).equals("● New update · "+longMetadata),"Unread meaning must precede metadata, never disappear after its ellipsis");
  check(TaskPresentation.context("",true).equals("● New update"),"Unread does not depend on due/project/status context");
  check(TaskPresentation.context(longMetadata,false).equals(longMetadata),"Read rows retain quiet metadata without unread meaning");
  check(TaskPresentation.wrapContext(true),"Unread text must wrap without line limit/ellipsis at 280dp and 200% type");
  check(!TaskPresentation.wrapContext(false),"Ordinary rows keep compact single-line context");
  for(String current:new String[]{"Active","Today","Week","All","Trash"}){
   String[] visible=TaskPresentation.visibleScopes(current);
   check(visible.length==3,"Keep exactly three compact scope controls");
   check(java.util.Arrays.asList(visible).contains(current),"Current "+current+" scope must be visibly selected, including restored Week/Trash");
   int selected=TaskPresentation.selectedScope(current);
   check(selected>=0&&TaskPresentation.menuScopes()[selected].equals(current),"Menu checked state uses exact current scope, never fallback Active");
  }
  check(TaskPresentation.selectedScope("unknown")==-1,"Unknown scope must not falsely mark Active selected");
  for(boolean dark:new boolean[]{false,true}){
   for(int background:dark?new int[]{0xff1a1d24,0xff202e46}:new int[]{0xffffffff,0xffedf3ff}){
    for(String priority:new String[]{"high","medium"}){
     int color=TaskPresentation.flagColor(priority,dark,0xff63636b);double ratio=contrast(color,background);
     check(ratio>=3,"Meaningful "+priority+" flag contrast >=3:1 on "+(dark?"dark":"light")+" ordinary/selected/unread row; got "+ratio);
    }
   }
  }
  check(TaskPresentation.flagColor("high",false,0)==0xffb55746,"High flag uses coordinated muted coral");
  check(TaskPresentation.flagColor("medium",true,0)==0xffd4b26f,"Medium flag uses coordinated muted amber");
  check(TaskPresentation.flag("high")==2,"High has a filled flag, not the medium outline in a different color");
  check(TaskPresentation.flag("medium")==1,"Medium has an outline flag");
  check(TaskPresentation.flag("low")==0,"Low has no flag, retaining its separate actions affordance");
  check(TaskPresentation.flag("")==0,"Unknown priority does not invent urgency");
  check(TaskPresentation.flagColor("low",true,0xffaaaab3)==0xffaaaab3,"Low actions use neutral foreground");
  // Unread is neither selected nor urgency: even a low task keeps its update text.
  check(TaskPresentation.context("Todo",true).startsWith("● New update"),"Unread text independent of priority/selection/status");
  System.out.println("TaskPresentationTest: "+checks+" checks passed");
 }
}

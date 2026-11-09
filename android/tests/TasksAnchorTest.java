package com.simon.mytasks;
import java.util.Arrays;
import java.util.Collections;
/** Real retained anchor logic on the host JVM; no View execution. */
public final class TasksAnchorTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  TasksUi.Anchor anchor=new TasksUi.Anchor();
  anchor.remember("b",-17,3);
  check(anchor.position(Arrays.asList("","a","b","c"),1,6)==3,"unchanged list restores row");
  check(anchor.position(Arrays.asList("","new","a","b","c"),1,7)==4,"insertion above follows task identity");
  check(anchor.anchorTop==-17,"pixel top offset survives insertion");
  check(anchor.position(Arrays.asList("","c","b","a"),1,6)==3,"sort/group changes follow identity");
  check(anchor.position(Arrays.asList("","a"),1,4)==3,"missing task retains nearest position");
  check(anchor.position(Collections.singletonList("a"),1,3)==2,"missing task fallback clamps to list end");
  anchor.reset();
  check(anchor.anchorId.isEmpty()&&anchor.anchorTop==0&&anchor.position(Arrays.asList("","a"),1,4)==0,"filter reset returns to top");
  anchor.remember("",-29,0);
  check(anchor.position(Arrays.asList("","a"),1,4)==0&&anchor.anchorTop==-29,"scrolling header retains header offset");
  check(anchor.position(Collections.emptyList(),0,0)==0,"empty list safely starts at zero");
  System.out.println("TasksAnchorTest: "+checks+" checks passed");
 }
}

package com.simon.mytasks;
import org.json.*;
public final class CompactPolicyTest {
 static int n;static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  JSONObject t=new JSONObject().put("title","Title").put("priority","low").put("project","Work").put("status","todo");
  check(CompactPolicy.title(t).equals("Title"),"title not crowded by status badges");
  check(CompactPolicy.detail(t,true,"").isEmpty(),"no redundant project or unset due");
  check(CompactPolicy.detail(t,false,"").equals("Work"),"project when not grouped");
  check(CompactPolicy.detail(t,true,"Today").equals("Today"),"due when useful");
  t.put("priority","high");t.put("notification",new JSONObject().put("unread",true));check(CompactPolicy.title(t).equals("● ! Title"),"priority and unread non-color indicators");
  check(!CompactPolicy.expanded(719)&&CompactPolicy.expanded(720),"current window split threshold");
  check(!CompactPolicy.expanded(841,2f)&&CompactPolicy.expanded(841,1f),"large text switches the fold to a useful single pane");
  check(CompactPolicy.expanded(1600,2f),"a genuinely larger window can still support two enlarged panes");
  check(CompactPolicy.secondaryChrome(false,420)&&!CompactPolicy.secondaryChrome(true,420)&&!CompactPolicy.secondaryChrome(false,290),"short or IME windows hide utility chrome");
  check(CompactPolicy.composerLines(240)==1&&CompactPolicy.composerLines(480)==3,"short windows keep multiline draft internally scrollable rather than starving message viewport");
  check(CompactPolicy.globalNavigation(false,"Tasks","","") ,"Tasks home keeps floating navigation");
  check(!CompactPolicy.globalNavigation(false,"Tasks","task-1","") ,"compact details and task chat do not stack global navigation");
  check(!CompactPolicy.globalNavigation(false,"Discussions","","thread-1"),"full screen chat owns one composer, no global footer");
  check(CompactPolicy.globalNavigation(true,"Discussions","","thread-1"),"expanded thread retains rail");
  check(CompactPolicy.masterWidth(841)==340,"Pixel Fold-sized window uses readable 340dp master instead of wrapping nearly every title");
  check(CompactPolicy.masterWidth(720)>=280&&CompactPolicy.masterWidth(720)<=300,"smaller split keeps the detail pane useful");
  check(CompactPolicy.masterWidth(1200)==340,"large windows retain readable list measure");
  System.out.println("CompactPolicyTest: "+n+" assertions passed");
 }
}

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
  check(CompactPolicy.secondaryChrome(false,420)&&!CompactPolicy.secondaryChrome(true,420)&&!CompactPolicy.secondaryChrome(false,290),"short or IME windows hide utility chrome");
  check(CompactPolicy.composerLines(240)==1&&CompactPolicy.composerLines(480)==3,"short windows keep multiline draft internally scrollable rather than starving message viewport");
  System.out.println("CompactPolicyTest: "+n+" assertions passed");
 }
}

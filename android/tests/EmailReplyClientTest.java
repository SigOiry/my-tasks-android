package com.simon.mytasks;
import org.json.*;
public class EmailReplyClientTest {
 public static void main(String[] args)throws Exception{
  final String[] got={""};EmailReplyClient c=new EmailReplyClient((m,p,b)->{got[0]=m+" "+p+" "+(b==null?"":b.toString());return new JSONObject();});
  c.sources("t/a","u +");if(!got[0].equals("GET /api/tasks/t%2Fa/email-sources?update_token=u%20%2B "))throw new AssertionError(got[0]);
  c.create("r","t","s",null);if(!got[0].contains("\"update_token\":null")||!got[0].contains("\"reply_all\":false"))throw new AssertionError(got[0]);
  c.operation("d","send",new JSONObject().put("confirmed",true));if(!got[0].startsWith("POST /api/email/drafts/d/send"))throw new AssertionError(got[0]);
  c.detail("t","s");if(!got[0].startsWith("GET /api/tasks/t/email-sources/s"))throw new AssertionError(got[0]);System.out.println("EmailReplyClientTest passed");
 }
}

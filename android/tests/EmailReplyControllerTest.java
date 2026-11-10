package com.simon.mytasks;
import org.json.*;
public class EmailReplyControllerTest {
 public static void main(String[] args)throws Exception{
  final int[] calls={0};final JSONObject[] saved={new JSONObject()};EmailReplyController c=new EmailReplyController(new EmailReplyClient((m,p,b)->{calls[0]++;return new JSONObject().put("id","d").put("revision",2).put("status","draft").put("body",b==null?"":b.optString("body"));}),new EmailReplyController.Host(){public boolean valid(){return true;}public JSONObject read(){return saved[0];}public boolean save(JSONObject d){saved[0]=d;return true;}public void work(Runnable r){r.run();}public void ui(Runnable r){r.run();}});
  c.state.accept(new JSONObject().put("id","d").put("revision",1).put("status","draft").put("body","old"));c.edit("new");c.flush();if(c.state.dirty()||!c.state.body().equals("new")||calls[0]!=1)throw new AssertionError("CAS autosave");
  c.state.edit("unsaved");c.send();if(calls[0]!=1)throw new AssertionError("send without reviewed revision");
  c.open("task-a",null);c.state.accept(new JSONObject().put("id","d").put("revision",3).put("status","sent").put("body","sent text"));c.open("task-b",null);if(c.state.draft()!=null)throw new AssertionError("prior task receipt leaked into new task chooser");
  c.state.accept(new JSONObject().put("id","b").put("revision",1).put("status","draft").put("body","server"));c.edit("private unsaved body");c.open("task-c",null);if(c.state.draft()!=null)throw new AssertionError("prior task draft visible");c.state.accept(new JSONObject().put("id","b").put("revision",1).put("status","draft").put("body","server"));if(!c.state.body().equals("private unsaved body"))throw new AssertionError("task switching lost cached local writing");
  System.out.println("EmailReplyControllerTest passed");
 }
}

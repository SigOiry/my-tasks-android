package com.simon.mytasks;
import org.json.*;
public final class ChatRowPolicyTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  JSONObject turn=new JSONObject("{\"id\":1,\"role\":\"user\",\"content\":\"hello\",\"created_at\":\"2026-11-09T10:00:00Z\"}");
  String key=ChatRowPolicy.binding(null,turn,null);
  turn.put("content","changed");check(!key.equals(ChatRowPolicy.binding(null,turn,null)),"Edited text must rebind");
  JSONObject next=new JSONObject("{\"id\":2,\"role\":\"user\",\"content\":\"next\",\"created_at\":\"2026-11-09T10:01:00Z\"}");
  check(!ChatRowPolicy.binding(null,turn,null).equals(ChatRowPolicy.binding(null,turn,next)),"New neighbor must update grouped tail");
  check(ChatRowPolicy.animate(true,true,false,false,true),"New local insertion at end animates");
  check(!ChatRowPolicy.animate(true,false,false,false,true),"Offscreen reader stays still");
  check(!ChatRowPolicy.animate(true,true,true,false,true),"Initial history never animates");
  check(!ChatRowPolicy.animate(true,true,false,true,true),"Pagination never animates");
  check(!ChatRowPolicy.animate(true,true,false,false,false),"Reduced motion respected");
  check(!ChatRowPolicy.animate(false,true,false,false,true),"Remote replies never animate as local sends");
  check(ChatRowPolicy.masterWidth(1200)==340&&ChatRowPolicy.masterWidth(840)==280,"Measured list stays bounded");
  System.out.println("ChatRowPolicyTest: passed");
 }
}

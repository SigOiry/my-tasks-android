package com.simon.mytasks;
import org.json.*;
public final class ConversationClientTest {
 static int n;static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
 static String method,path;static JSONObject payload;
 public static void main(String[] args)throws Exception{
  ConversationClient c=new ConversationClient((m,p,b)->{method=m;path=p;payload=b;return new JSONObject();});
  c.list("task","exact task/id",false,"opaque+cursor=");check(method.equals("GET")&&path.equals("/api/conversations?scope=task&limit=30&archived=false&task_id=exact%20task%2Fid&cursor=opaque%2Bcursor%3D"),"exact list contract and opaque cursor encoding");
  c.create(null,"New conversation");check(method.equals("POST")&&path.equals("/api/conversations")&&payload.isNull("task_id"),"free creation is explicit null");
  c.create("saved task",null);check(payload.getString("task_id").equals("saved task")&&!payload.has("history"),"only saved task association");
  c.get("abc","older/cursor");check(path.equals("/api/conversations/abc?limit=40&before=older%2Fcursor"),"history pagination contract");
  c.patch("abc",8,"renamed",null);check(payload.getInt("expected_revision")==8&&!payload.has("archived"),"rename uses optimistic revision");
  c.patch("abc",9,null,false);check(!payload.has("title")&&!payload.getBoolean("archived"),"recover archive explicit false");
  JSONObject body=new JSONObject().put("request_id","a fixed UUID").put("content","hello").put("mode","question").put("expected_revision",10);
  c.send("abc",body);check(path.equals("/api/conversations/abc/messages")&&method.equals("POST")&&payload==body,"post identity envelope unchanged");
  c.request("abc","id+exact");check(path.equals("/api/conversations/abc/requests/id%2Bexact"),"request lookup binds exact conversation");
  System.out.println("ConversationClientTest: "+n+" assertions passed");
 }
}

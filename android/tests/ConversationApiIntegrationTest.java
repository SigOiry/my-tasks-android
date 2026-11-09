package com.simon.mytasks;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Optional real HTTP contract integration, against scratch-only server fixture. */
public final class ConversationApiIntegrationTest {
 static int n;static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
 static final class HttpFailure extends IOException {final int code;HttpFailure(int c){super("Scratch HTTP "+c);code=c;}}
 static JSONObject http(String base,String token,String method,String path,JSONObject body)throws Exception{
  if(!base.startsWith("http://127.0.0.1:"))throw new AssertionError("Integration fixture must be loopback only");
  java.net.http.HttpRequest.Builder builder=java.net.http.HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json");
  builder.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
  java.net.http.HttpResponse<String> response=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build().send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(response.statusCode()<200||response.statusCode()>=300){System.err.println("Scratch API error "+response.statusCode()+": "+response.body());throw new HttpFailure(response.statusCode());}return new JSONObject(response.body());
 }
 public static void main(String[] args)throws Exception{
  String base=args[0],token=args[1];ConversationClient c=new ConversationClient((method,path,body)->http(base,token,method,path,body));
  JSONObject task=http(base,token,"POST","/api/tasks",new JSONObject().put("title","Android scratch-only contract task").put("description","").put("notes","").put("priority","medium").put("status","todo").put("project",JSONObject.NULL).put("due_date",JSONObject.NULL));String tid=task.getString("id");
  JSONObject free=c.create(null,"Android synthetic fixture"),taskOne=c.create(tid,"Task thread one"),taskTwo=c.create(tid,"Task thread two");String id=free.getString("id");
  check(c.list("all","",false,"").getJSONArray("conversations").length()==3,"all app threads");check(c.list("free","",false,"").getJSONArray("conversations").length()==1,"free filter");check(c.list("task",tid,false,"").getJSONArray("conversations").length()==2,"multiple exact task conversations");check(!taskOne.getString("id").equals(taskTwo.getString("id")),"distinct task thread IDs");
  free=c.patch(id,free.getInt("revision"),"Renamed fixture",null);check(free.getString("title").equals("Renamed fixture"),"revision rename");boolean conflict=false;try{c.patch(id,1,"stale",null);}catch(HttpFailure e){conflict=e.code==409;}check(conflict,"stale revision rejected");free=c.patch(id,free.getInt("revision"),null,true);check(c.list("free","",true,"").getJSONArray("conversations").length()==1,"archive is recoverable");free=c.patch(id,free.getInt("revision"),null,false);check(!free.getBoolean("archived"),"reopen without deleting history");
  ConversationState state=new ConversationState();state.select(id);JSONObject latestBody=null;String request="";
  for(int i=0;i<21;i++){
   JSONObject page=c.get(id,null);state.page(id,page,false);state.draft(id,"Synthetic fixture question "+i);state.prepare(id,page.getJSONObject("conversation").getInt("revision"),"question");latestBody=state.body(id);request=latestBody.getString("request_id");JSONObject accepted=c.send(id,latestBody);check(accepted.getJSONObject("request").getString("id").equals(request),"202 identity retained");
   JSONObject status=null;for(int poll=0;poll<150;poll++){status=c.request(id,request);if(!ConversationState.pendingStatus(status.getString("status")))break;Thread.sleep(20);}check(status!=null&&status.getString("status").equals("succeeded"),"persisted request reaches terminal status");state.accept(id,status);
  }
  JSONObject replay=c.send(id,latestBody);check(replay.getJSONObject("request").getString("id").equals(request),"idempotent exact replay after terminal and stale revision");
  JSONObject page=c.get(id,null);check(page.getJSONArray("messages").length()==40&&page.getBoolean("has_more"),"latest page has 40 with explicit older cursor");ConversationState complete=new ConversationState();complete.page(id,page,false);JSONObject older=c.get(id,page.getString("before_cursor"));complete.page(id,older,true);complete.page(id,page,false);check(complete.messages(id).length()==42&&!complete.page(id).getBoolean("has_more"),"native prepend merges all42 without duplicate replay/truncation");
  check(http(base,token,"GET","/api/tasks/"+ConversationClient.encode(tid),null).toString().equals(task.toString()),"chat does not mutate canonical task");
  JSONObject result=new JSONObject().put("assertions",n).put("conversation_id",id).put("messages",complete.messages(id).length()).put("synthetic_provider",true);System.out.println(result);
 }
}

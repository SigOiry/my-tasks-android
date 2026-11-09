package com.simon.mytasks;
import org.json.*;
import java.net.URI;
import java.net.http.*;
public final class AuthApiIntegrationTest {
 static int checks;static HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build();static String base;
 static final class Status extends Exception {final int code;Status(int n){super("HTTP "+n);code=n;}}
 static void ok(boolean value){checks++;if(!value)throw new AssertionError("auth integration "+checks);}
 static JSONObject call(String method,String path,JSONObject body,String proof)throws Exception{
  HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).header("Accept","application/json");if(proof!=null&&!proof.isEmpty())b.header("Authorization","Bearer "+proof);
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString()));if(body!=null)b.header("Content-Type","application/json");
  HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300)throw new Status(r.statusCode());return new JSONObject(r.body());
 }
 static void denied(int status,String method,String path,String proof)throws Exception{try{call(method,path,null,proof);throw new AssertionError("data admitted");}catch(Status s){ok(s.code==status);}}
 public static void main(String[] args)throws Exception{
  base=args[0];String owner=args[1],initial="Fixture initial phrase only",next="Distinct private integration phrase!";AuthClient auth=new AuthClient(AuthApiIntegrationTest::call);
  try{auth.login("oiry-s",initial,"");throw new AssertionError("public initial admitted");}catch(Status s){if(s.code!=428)throw s;ok(true);}
  JSONObject legacy=auth.session(owner);ok(AuthPolicy.state(legacy,System.currentTimeMillis()/1000)==AuthPolicy.State.CHANGE);denied(428,"GET","/api/tasks",owner);
  JSONObject restricted=auth.login("oiry-s",initial,owner);String first=restricted.getString("session_token");ok(first.matches("[a-f0-9]{64}"));ok(restricted.optBoolean("password_change_required"));ok(AuthPolicy.state(auth.session(first),System.currentTimeMillis()/1000)==AuthPolicy.State.CHANGE);denied(428,"GET","/api/tasks",first);
  JSONObject changed=auth.password(initial,next,next,first);String full=changed.getString("session_token");ok(!full.equals(first));ok(!changed.optBoolean("password_change_required"));ok(AuthPolicy.state(auth.session(full),System.currentTimeMillis()/1000)==AuthPolicy.State.FULL);ok(call("GET","/api/tasks",null,full).has("tasks"));
  ok(!auth.session(owner).optBoolean("authenticated"));ok(!auth.session(first).optBoolean("authenticated"));denied(401,"GET","/api/tasks",owner);denied(401,"GET","/api/tasks",first);
  ConversationClient chats=new ConversationClient((m,p,b)->call(m,p,b,full));JSONObject thread=chats.create(null,"Native authenticated durable thread");ok(thread.has("id"));ok(chats.get(thread.getString("id"),"").has("messages"));
  JSONObject login=auth.login("oiry-s",next,"");String second=login.getString("session_token");ok(!login.optBoolean("password_change_required"));ok(call("GET","/api/tasks",null,second).has("tasks"));auth.logout(second);ok(!auth.session(second).optBoolean("authenticated"));denied(401,"GET","/api/tasks",second);
  auth.logout(full);ok(!auth.session(full).optBoolean("authenticated"));
  System.out.println(new JSONObject().put("checks",checks).put("scratch_only",true).put("auth_client_http",true).put("android_runtime",false));
 }
}

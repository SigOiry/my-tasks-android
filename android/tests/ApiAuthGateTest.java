package com.simon.mytasks;
import org.json.*;
public final class ApiAuthGateTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("gate check "+checks);}
 public static void main(String[] args)throws Exception{
  Store.Account account=new Store.Account("https://fixture.invalid","fixture-private-session","fixture-epoch");final int[] data={0},auth={0},blocked={0};final JSONObject[] session={new JSONObject().put("authenticated",true).put("password_change_required",true)};
  Api api=new Api(account,(m,p,b)->{if(p.equals("/auth/session")){auth[0]++;return session[0];}data[0]++;return new JSONObject();});Api.authObserver=(a,status)->{ok(a==account);blocked[0]=status;};
  try{api.call("POST","/api/conversations",new JSONObject());throw new AssertionError("restricted data admitted");}catch(Api.Failure f){ok(f.code==428);}
  ok(data[0]==0&&auth[0]==1&&blocked[0]==428);
  session[0]=new JSONObject().put("authenticated",false);try{api.call("GET","/api/tasks",null);throw new AssertionError();}catch(Api.Failure f){ok(f.code==401);}ok(data[0]==0&&blocked[0]==401);
  session[0]=new JSONObject().put("authenticated",true).put("expires_at",1);try{api.call("POST","/api/conversations/x/messages",new JSONObject());throw new AssertionError();}catch(Api.Failure f){ok(f.code==401);}ok(data[0]==0);
  session[0]=new JSONObject().put("authenticated",true).put("expires_at",System.currentTimeMillis()/1000+600);api.call("POST","/api/conversations",new JSONObject());ok(data[0]==1);
  final int[] attempts={0};Api password=new Api(account,(m,p,b)->{attempts[0]++;throw new Api.Failure(503);});try{password.call("POST","/auth/native/password",new JSONObject());throw new AssertionError();}catch(Api.Failure expected){ok(attempts[0]==1);}
  Api.authObserver=null;System.out.println("ApiAuthGateTest: "+checks+" checks passed");
 }
}

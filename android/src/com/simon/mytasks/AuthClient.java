package com.simon.mytasks;
import org.json.JSONObject;
/** Exact native auth protocol. POSTs are single attempts, never automatic retries. */
final class AuthClient {
 interface Transport {JSONObject call(String method,String path,JSONObject body,String proof)throws Exception;}
 final Transport transport;AuthClient(Transport t){transport=t;}
 JSONObject login(String username,String password,String proof)throws Exception{AuthPolicy.passwordValue(password);return issued(transport.call("POST","/auth/native/login",new JSONObject().put("username",username).put("password",password),proof));}
 JSONObject password(String current,String next,String confirm,String proof)throws Exception{AuthPolicy.password(current,next,confirm);return issued(transport.call("POST","/auth/native/password",new JSONObject().put("current_password",current).put("new_password",next),proof));}
 JSONObject session(String proof)throws Exception{return transport.call("GET","/auth/session",null,proof);}
 void logout(String proof)throws Exception{transport.call("POST","/auth/native/logout",new JSONObject(),proof);}
 private JSONObject issued(JSONObject result)throws Exception{if(!result.optBoolean("authenticated"))throw new IllegalArgumentException("Sign-in was not confirmed.");AuthPolicy.sessionToken(result.optString("session_token"));return result;}
}

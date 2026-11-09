package com.simon.mytasks;
import org.json.*;
public final class AuthPolicyTest {
 static int count;static void ok(boolean b){count++;if(!b)throw new AssertionError("auth check "+count);}static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
 public static void main(String[] args)throws Exception{
  ok(AuthPolicy.state(new JSONObject().put("authenticated",false),100)==AuthPolicy.State.LOGIN);
  ok(AuthPolicy.state(new JSONObject().put("authenticated",true).put("password_change_required",true).put("expires_at",200),100)==AuthPolicy.State.CHANGE);
  ok(AuthPolicy.state(new JSONObject().put("authenticated",true).put("expires_at",200),100)==AuthPolicy.State.FULL);
  ok(AuthPolicy.state(new JSONObject().put("authenticated",true).put("expires_at",99),100)==AuthPolicy.State.LOGIN);
  ok(AuthPolicy.state(new JSONObject().put("activation_required",true),100)==AuthPolicy.State.ACTIVATION);
  ok(AuthPolicy.sessionToken(repeat("a",64)).length()==64);
  for(String s:new String[]{"abc",repeat("a",65)," "+repeat("a",64),repeat("g",64)}){try{AuthPolicy.sessionToken(s);throw new AssertionError();}catch(IllegalArgumentException expected){ok(true);}}
  AuthPolicy.password("current password!","new private password!","new private password!");ok(true);
  for(String[] p:new String[][]{{"old","short","short"},{"old","new private password!","other"},{"same password long","same password long","same password long"}}){try{AuthPolicy.password(p[0],p[1],p[2]);throw new AssertionError();}catch(IllegalArgumentException expected){ok(true);}}
  AuthPolicy.password("old",repeat("😀",15),repeat("😀",15));ok(true);
  for(String invalid:new String[]{"\ud800", "\udc00", "valid chars but trailing \ud800"}){try{AuthPolicy.password("current",invalid,invalid);throw new AssertionError();}catch(IllegalArgumentException expected){ok(true);}try{AuthPolicy.passwordValue(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){ok(true);}}
  String storage=AuthPolicy.storageFailure(new java.io.IOException("private input must never appear"));ok(storage.contains("IOException")&&!storage.contains("private input")&&!storage.contains("No token was sent"));
  final String[] method={""},path={""},proof={""};final JSONObject[] body={null};
  AuthClient c=new AuthClient((m,p,b,a)->{method[0]=m;path[0]=p;body[0]=b;proof[0]=a;return new JSONObject().put("authenticated",true).put("session_token",repeat("b",64));});
  c.login("oiry-s","input not stored","legacy-owner-proof");ok(path[0].equals("/auth/native/login"));ok(proof[0].equals("legacy-owner-proof"));ok(body[0].length()==2&&body[0].getString("password").equals("input not stored"));
  c.password("old","new private password!","new private password!","restricted-proof");ok(path[0].equals("/auth/native/password"));ok(body[0].length()==2&&!body[0].has("confirm"));ok(proof[0].equals("restricted-proof"));
  c.session("saved-proof");ok(method[0].equals("GET")&&path[0].equals("/auth/session")&&body[0]==null&&proof[0].equals("saved-proof"));
  c.logout("saved-proof");ok(path[0].equals("/auth/native/logout")&&method[0].equals("POST"));
  System.out.println("AuthPolicyTest: "+count+" checks passed");
 }
}

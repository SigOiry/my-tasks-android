package com.simon.mytasks;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class Api {
 static final ExecutorService IO=Executors.newSingleThreadExecutor();
 interface AuthObserver {void blocked(Store.Account account,int status);}
 static volatile AuthObserver authObserver;
 static AuthClient auth(String server){return new AuthClient((method,path,body,proof)->new Api(new Store.Account(server,proof==null?"":proof,"auth")).call(method,path,body));}
 interface Transport {JSONObject request(String method,String path,String body)throws Exception;}
 final Store.Account account;final Transport transport;
 Api(Store.Account a){account=a;transport=this::request;}
 Api(Store.Account a,Transport t){account=a;transport=t;}
 static String segment(String id){return Core.path(id);}
 static final class Failure extends IOException {
  final int code;
  Failure(int code){super(code==401?"Sign-in expired or credentials were rejected. Sign in again.":code==428?"Owner activation or password change is required before opening the workspace.":code==409?"Task changed on the server. Reload the latest task before saving.":code==413?Core.chatFailure(413):code==422?"The server rejected these fields. Check your input.":code==429?"Too many attempts. Wait before trying again.":code==503?"The server or Hermes provider is unavailable. Try again later.":"Server returned HTTP "+code+". Retry when available.");this.code=code;}
 }
 JSONObject call(String method,String path,JSONObject body)throws Exception {
  if(path.startsWith("/api/")){
   try{AuthPolicy.State state=AuthPolicy.state(transport.request("GET","/auth/session",null),System.currentTimeMillis()/1000);if(state!=AuthPolicy.State.FULL)throw new Failure(state==AuthPolicy.State.CHANGE||state==AuthPolicy.State.ACTIVATION?428:401);}
   catch(Failure failure){blocked(failure);throw failure;}
  }
  int attempts=method.equals("GET")?2:1;
  for(int n=0;;n++){
   try{return transport.request(method,path,body==null?null:body.toString());}catch(Exception e){
    if(e instanceof Failure&&path.startsWith("/api/"))blocked((Failure)e);
    boolean retry=e instanceof IOException&&(!(e instanceof Failure)||((Failure)e).code>=500);
    if(!retry||n+1>=attempts)throw e;
    Thread.sleep(500);
   }
  }
 }

 private JSONObject request(String method,String path,String body)throws Exception{
  Core.server(account.server);
  HttpURLConnection c=(HttpURLConnection)new URL(account.server+path).openConnection();
  c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestMethod(method);
  if(!account.token.isEmpty())c.setRequestProperty("Authorization","Bearer "+account.token);c.setRequestProperty("Accept","application/json");
  try{
   if(body!=null){byte[] b=body.getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(b.length);try(OutputStream out=c.getOutputStream()){out.write(b);}}
   int status=c.getResponseCode();if(status<200||status>=300)throw new Failure(status);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[8192];int count;while((count=in.read(b))!=-1){if(out.size()+count>4194304)throw new IOException("Response too large");out.write(b,0,count);}return new JSONObject(out.toString("UTF-8"));
   }
  }finally{c.disconnect();}
 }
 void blocked(Failure e){AuthObserver observer=authObserver;if((e.code==401||e.code==428)&&observer!=null)observer.blocked(account,e.code);}
 static String friendly(Exception e){if(e instanceof Failure)return e.getMessage();if(e instanceof IllegalArgumentException)return e.getMessage();return "Connection failed. Check HTTPS, connectivity and Settings, then retry.";}
}

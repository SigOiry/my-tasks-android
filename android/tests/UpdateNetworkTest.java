package com.simon.mytasks;
import java.io.*;import java.net.*;import java.util.*;
public final class UpdateNetworkTest {
 static int n;
 static class Connection extends HttpURLConnection {
  String location;byte[] body;int code;boolean disconnected;
  Connection(String url,int c,String l,byte[] b)throws Exception{super(new URL(url));code=c;location=l;body=b;}
  public void disconnect(){disconnected=true;} public boolean usingProxy(){return false;} public void connect(){}
  public int getResponseCode(){return code;}public String getHeaderField(String k){return "Location".equals(k)?location:null;}
  public long getContentLengthLong(){return body.length;} public InputStream getInputStream(){return new ByteArrayInputStream(body);}
 }
 static void check(boolean v){n++;if(!v)throw new AssertionError("assertion "+n);}
 public static void main(String[] args)throws Exception{
  String url="https://github.com/SigOiry/my-tasks-android/releases/download/v2/my-tasks.apk";
  Connection first=new Connection(url,302,"https://release-assets.githubusercontent.com/github-production-release-asset/123/a?sig=123",new byte[0]);Connection last=new Connection("https://release-assets.githubusercontent.com/github-production-release-asset/123/a?sig=123",200,null,new byte[]{1,2});
  Queue<Connection> queue=new ArrayDeque<>();queue.add(first);queue.add(last);ByteArrayOutputStream out=new ByteArrayOutputStream();
  UpdateNetwork.fetch(url,out,2,()->false,u->queue.remove());check(out.size()==2);check(first.disconnected&&last.disconnected);check(last.getRequestProperty("Authorization")==null);check(!last.getInstanceFollowRedirects());
  for(String evil:new String[]{"http://github.com/a","https://evil.com/a","https://github.com:8443/a","https://github.com@evil.com/a"}){
   Connection c=new Connection(url,302,evil,new byte[0]);try{UpdateNetwork.fetch(url,new ByteArrayOutputStream(),5,()->false,u->c);throw new AssertionError("redirect accepted");}catch(IOException e){n++;}check(c.disconnected);
  }
  Connection loop=new Connection(url,302,url,new byte[0]);try{UpdateNetwork.fetch(url,new ByteArrayOutputStream(),5,()->false,u->loop);throw new AssertionError("redirect loop accepted");}catch(IOException e){n++;}
  Connection large=new Connection(url,200,null,new byte[6]);try{UpdateNetwork.fetch(url,new ByteArrayOutputStream(),5,()->false,u->large);throw new AssertionError("oversize accepted");}catch(IOException e){n++;}
  Connection error=new Connection(url,403,null,new byte[0]);try{UpdateNetwork.fetch(url,new ByteArrayOutputStream(),5,()->false,u->error);throw new AssertionError("403 accepted");}catch(IOException e){n++;}
  System.out.println("UpdateNetwork: "+n+" assertions passed");
 }
}

package com.simon.mytasks;
import java.io.*;import java.net.*;import java.util.function.BooleanSupplier;
/** Dedicated public GitHub transport. Never accepts account state or authentication headers. */
public final class UpdateNetwork {
 public interface Factory {HttpURLConnection open(URL u)throws IOException;}
 public static void fetch(String url,OutputStream out,long limit,BooleanSupplier canceled)throws IOException{fetch(url,out,limit,canceled,u->(HttpURLConnection)u.openConnection());}
 static void fetch(String url,OutputStream out,long limit,BooleanSupplier canceled,Factory factory)throws IOException{
  for(int hops=0;hops<=5;hops++){
   if(canceled.getAsBoolean())throw new IOException("Update canceled");if(!UpdatePolicy.allowed(url))throw new IOException("Untrusted update URL");
   HttpURLConnection c=factory.open(new URL(url));
   try{
    c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setUseCaches(false);
    c.setRequestProperty("User-Agent","MyTasks-Android-Updater");c.setRequestProperty("Accept","application/vnd.github+json, application/octet-stream;q=0.9");c.setRequestProperty("Accept-Encoding","identity");
    int status=c.getResponseCode();
    if(status==301||status==302||status==303||status==307||status==308){String next=c.getHeaderField("Location");if(next==null)throw new IOException("Missing redirect location");url=new URL(new URL(url),next).toString();continue;}
    if(status!=200)throw new IOException(status==404?"No published update is available yet":status==403||status==429?"GitHub rate limit or access restriction; try again later":"GitHub update request failed (HTTP "+status+")");
    if(c.getContentLengthLong()>limit)throw new IOException("Update exceeds allowed size");
    try(InputStream in=c.getInputStream()){UpdateIO.copy(in,out,limit,canceled);}return;
   }finally{c.disconnect();}
  }
  throw new IOException("Too many update redirects");
 }
}

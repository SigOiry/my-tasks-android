package com.simon.mytasks;
import java.io.*;
import java.util.function.BooleanSupplier;
/** Bounded streaming primitive shared by metadata and APK requests. */
public final class UpdateIO {
 public static void verify(File file,long size,String hash)throws IOException{
  if(size<1||size>UpdatePolicy.MAX_APK||file.length()!=size)throw new IOException("APK size mismatch");
  try{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buf=new byte[16384];int n;long read=0;while((n=in.read(buf))!=-1){read+=n;if(read>size)throw new IOException("APK grew during verification");digest.update(buf,0,n);}if(read!=size||!UpdatePolicy.hex(digest.digest()).equalsIgnoreCase(hash))throw new IOException("APK checksum mismatch");}}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
 }
 private UpdateIO(){}
 public static long copy(InputStream in,OutputStream out,long limit,BooleanSupplier canceled)throws IOException{
  long total=0,deadline=System.nanoTime()+180000000000L;byte[] buffer=new byte[16384];
  while(true){if(canceled.getAsBoolean())throw new IOException("Update canceled");if(System.nanoTime()>deadline)throw new IOException("Update timed out");int n=in.read(buffer);if(n<0)return total;total+=n;if(total>limit)throw new IOException("Update exceeds allowed size");out.write(buffer,0,n);}
 }
}

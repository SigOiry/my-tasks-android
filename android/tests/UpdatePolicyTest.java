package com.simon.mytasks;
public final class UpdatePolicyTest {
 static int n;
 static void check(boolean v){n++;if(!v)throw new AssertionError("assertion "+n);}
 static void reject(Runnable r){n++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection "+n);}
 public static void main(String[] args)throws Exception{
  check(UpdatePolicy.newer(2,1));check(!UpdatePolicy.newer(1,1));check(!UpdatePolicy.newer(1,2));check(UpdatePolicy.newer(100,9));
  reject(()->UpdatePolicy.newer(0,1));
  check(UpdatePolicy.daily("2026-11-09","2026-11-08",false));
  check(!UpdatePolicy.daily("2026-11-09","2026-11-09",false));check(!UpdatePolicy.daily("2026-11-10","2026-11-09",true));
  check(UpdatePolicy.stable(false,false,"2026-11-09T00:00:00Z"));check(!UpdatePolicy.stable(true,false,"x"));check(!UpdatePolicy.stable(false,true,"x"));check(!UpdatePolicy.stable(false,false,""));
  check(UpdatePolicy.allowed("https://api.github.com/repos/SigOiry/my-tasks-android/releases/latest"));
  check(UpdatePolicy.allowed("https://release-assets.githubusercontent.com/github-production-release-asset/a?sig=x"));
  for(String u:new String[]{"http://github.com/x","https://github.com.evil/x","https://evilgithub.com/x","https://github.com:444/x","https://a@github.com/x","https://objects.githubusercontent.com.evil/x","file:///x","https://github.com/x#fragment"})check(!UpdatePolicy.allowed(u));
  check(UpdatePolicy.assetUrl("https://github.com/SigOiry/my-tasks-android/releases/download/v2/my-tasks.apk","my-tasks.apk"));
  check(!UpdatePolicy.assetUrl("https://github.com/evil/repo/releases/download/v2/my-tasks.apk","my-tasks.apk"));
  String hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
  check(UpdatePolicy.digest("abc".getBytes("UTF-8")).equals(hash));
  UpdatePolicy.metadata(1,2,"1.1.0","com.simon.mytasks","my-tasks.apk",hash,3,hash,hash);
  reject(()->UpdatePolicy.metadata(2,2,"1.1.0","com.simon.mytasks","my-tasks.apk",hash,3,hash,hash));
  reject(()->UpdatePolicy.metadata(1,2,"1.1.0","evil.package","my-tasks.apk",hash,3,hash,hash));
  reject(()->UpdatePolicy.metadata(1,2,"1.1.0","com.simon.mytasks","evil.apk",hash,3,hash,hash));
  reject(()->UpdatePolicy.metadata(1,2,"1.1.0","com.simon.mytasks","my-tasks.apk","bad",3,hash,hash));
  reject(()->UpdatePolicy.metadata(1,2,"1.1.0","com.simon.mytasks","my-tasks.apk",hash,104857601L,hash,hash));
  reject(()->UpdatePolicy.metadata(1,2,"1.1.0","com.simon.mytasks","my-tasks.apk",hash,0,hash,hash));
  reject(()->UpdatePolicy.metadata(1,2,"1.1.0","com.simon.mytasks","my-tasks.apk",hash,3,hash,"0000000000000000000000000000000000000000000000000000000000000000"));
  java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
  check(UpdateIO.copy(new java.io.ByteArrayInputStream(new byte[]{1,2,3}),out,3,()->false)==3);
  check(out.toByteArray().length==3);
  try{UpdateIO.copy(new java.io.ByteArrayInputStream(new byte[4]),new java.io.ByteArrayOutputStream(),3,()->false);throw new AssertionError("oversized body accepted");}catch(java.io.IOException expected){n++;}
  try{UpdateIO.copy(new java.io.ByteArrayInputStream(new byte[1]),new java.io.ByteArrayOutputStream(),3,()->true);throw new AssertionError("cancel ignored");}catch(java.io.IOException expected){n++;}
  for(String u:new String[]{"https://api.github.com/user","https://github.com/evil/repo/releases/download/v2/my-tasks.apk","https://release-assets.githubusercontent.com/unrelated","https://github.com/SigOiry/my-tasks-android/releases/download/../my-tasks.apk","https://github.com/SigOiry/my-tasks-android/releases/download/%2e%2e/my-tasks.apk"})check(!UpdatePolicy.allowed(u));
  check(UpdatePolicy.allowed("https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/a?sig=x"));
  UpdatePolicy.identity("com.simon.mytasks",2,"1.1.0",hash,2,"1.1.0",hash,1);
  reject(()->UpdatePolicy.identity("evil.package",2,"1.1.0",hash,2,"1.1.0",hash,1));
  reject(()->UpdatePolicy.identity("com.simon.mytasks",1,"1.1.0",hash,2,"1.1.0",hash,1));
  reject(()->UpdatePolicy.identity("com.simon.mytasks",2,"1.0.0",hash,2,"1.1.0",hash,1));
  reject(()->UpdatePolicy.identity("com.simon.mytasks",2,"1.1.0","0"+hash.substring(1),2,"1.1.0",hash,1));
  reject(()->UpdatePolicy.identity("com.simon.mytasks",2,"1.1.0",hash,2,"1.1.0",hash,2));
  java.io.File f=java.io.File.createTempFile("update-test-", ".apk",new java.io.File(UpdatePolicyTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
  try{java.nio.file.Files.write(f.toPath(),"abc".getBytes("UTF-8"));UpdateIO.verify(f,3,hash);n++;
   try{UpdateIO.verify(f,2,hash);throw new AssertionError("wrong size accepted");}catch(java.io.IOException expected){n++;}
   try{UpdateIO.verify(f,3,"0"+hash.substring(1));throw new AssertionError("wrong hash accepted");}catch(java.io.IOException expected){n++;}
  }finally{f.delete();}
  check(!UpdatePolicy.stable(false,false,"not-a-timestamp"));
  System.out.println("UpdatePolicy: "+n+" assertions passed");
 }
}

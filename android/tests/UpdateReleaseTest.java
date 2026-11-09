package com.simon.mytasks;
import org.json.*;
public final class UpdateReleaseTest {
 static int n;static String hash="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
 static void check(boolean v){n++;if(!v)throw new AssertionError("assertion "+n);}
 interface Action{void run()throws Exception;}
 static void reject(Action a)throws Exception{n++;try{a.run();}catch(IllegalArgumentException|JSONException e){return;}throw new AssertionError("Expected rejection "+n);}
 static JSONObject asset(String name)throws Exception{return new JSONObject().put("name",name).put("state","uploaded").put("size",20).put("browser_download_url","https://github.com/SigOiry/my-tasks-android/releases/download/v2/"+name);}
 static JSONObject release()throws Exception{return new JSONObject().put("tag_name","v2").put("draft",false).put("prerelease",false).put("published_at","2026-11-09T12:00:00Z").put("body","<b>plain text only</b>").put("assets",new JSONArray().put(asset("update.json")).put(asset("my-tasks.apk")));}
 static JSONObject metadata()throws Exception{return new JSONObject().put("schema_version",1).put("version_code",2).put("version_name","1.1.0").put("package_name","com.simon.mytasks").put("apk_asset","my-tasks.apk").put("sha256",hash).put("size",20).put("certificate_sha256",hash);}
 public static void main(String[] args)throws Exception{
  UpdateRelease.Release r=UpdateRelease.release(release());check(r.notes.equals("<b>plain text only</b>"));check(r.apkSize==20);
  reject(()->UpdateRelease.release(release().put("draft",true)));reject(()->UpdateRelease.release(release().put("prerelease",true)));reject(()->UpdateRelease.release(release().put("published_at",JSONObject.NULL)));
  reject(()->UpdateRelease.release(release().put("assets",new JSONArray().put(asset("update.json")))));
  reject(()->UpdateRelease.release(release().put("assets",new JSONArray().put(asset("update.json")).put(asset("update.json")).put(asset("my-tasks.apk")))));
  UpdateRelease.Metadata m=UpdateRelease.metadata(metadata(),hash,r);check(m.code==2);check(m.size==20);
  reject(()->UpdateRelease.metadata(metadata().put("version_code","2"),hash,r));reject(()->UpdateRelease.metadata(metadata().put("version_code",2.5),hash,r));reject(()->UpdateRelease.metadata(metadata().put("size",21),hash,r));
  reject(()->UpdateRelease.metadata(metadata().put("version_code",-1),hash,r));reject(()->UpdateRelease.metadata(metadata().put("certificate_sha256","b"+hash.substring(1)),hash,r));
  check(UpdateRelease.release(release().put("body",new String(new char[20000]).replace('\0','x'))).notes.length()<=8000);
  reject(()->UpdateRelease.release(release().put("tag_name","different-tag")));
  reject(()->UpdateRelease.release(release().put("assets",new JSONArray().put(asset("update.json")).put(asset("my-tasks.apk").put("browser_download_url","https://github.com/SigOiry/my-tasks-android/releases/download/other/my-tasks.apk")))));
  check(UpdateRelease.release(release().put("body","safe\u202ehidden\u0000\ntext")).notes.equals("safehidden\ntext"));
  System.out.println("UpdateRelease: "+n+" assertions passed");
 }
}

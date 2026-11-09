package com.simon.mytasks;
import org.json.*;
/** Parse untrusted release data; numbers are never coerced from strings/floats. */
public final class UpdateRelease {
 public static final class Release {public final String metadataUrl,apkUrl,notes;public final long apkSize;Release(String m,String a,String n,long s){metadataUrl=m;apkUrl=a;notes=n;apkSize=s;}}
 public static final class Metadata {public final long code,size;public final String name,hash,cert;Metadata(long c,String n,String h,long s,String f){code=c;name=n;hash=h;size=s;cert=f;}}
 static long integer(JSONObject o,String key)throws JSONException{Object x=o.get(key);if(!(x instanceof Integer)&&!(x instanceof Long))throw new IllegalArgumentException("Expected integer: "+key);return ((Number)x).longValue();}
 static String string(JSONObject o,String key)throws JSONException{Object x=o.get(key);if(!(x instanceof String))throw new IllegalArgumentException("Expected text: "+key);return (String)x;}
 public static Release release(JSONObject o)throws JSONException{
  if(!(o.get("draft") instanceof Boolean)||!(o.get("prerelease") instanceof Boolean)||!UpdatePolicy.stable(o.getBoolean("draft"),o.getBoolean("prerelease"),string(o,"published_at")))throw new IllegalArgumentException("No stable published release");
  String tag=string(o,"tag_name");if(tag.isEmpty()||tag.length()>200)throw new IllegalArgumentException("Invalid release tag");
  JSONArray assets=o.getJSONArray("assets");String meta=null,apk=null;long size=0;
  for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);String name=string(a,"name");if(!name.equals("update.json")&&!name.equals("my-tasks.apk"))continue;
   String url=string(a,"browser_download_url");long bytes=integer(a,"size");if(!"uploaded".equals(string(a,"state"))||!UpdatePolicy.assetUrl(url,name)||bytes<1||bytes>(name.equals("update.json")?16384:UpdatePolicy.MAX_APK))throw new IllegalArgumentException("Invalid release asset");
   if(!java.net.URI.create(url).getPath().equals("/SigOiry/my-tasks-android/releases/download/"+tag+"/"+name))throw new IllegalArgumentException("Asset does not belong to this release tag");
   if(name.equals("update.json")){if(meta!=null)throw new IllegalArgumentException("Duplicate metadata");meta=url;}else{if(apk!=null)throw new IllegalArgumentException("Duplicate APK");apk=url;size=bytes;}
  }
  if(meta==null||apk==null)throw new IllegalArgumentException("Release is missing update.json or my-tasks.apk");
  // Both assets must belong to the same release tag, not merely the same repository.
  if(!meta.substring(0,meta.lastIndexOf('/')).equals(apk.substring(0,apk.lastIndexOf('/'))))throw new IllegalArgumentException("Assets from different releases");
  String notes=o.isNull("body")?"":string(o,"body");notes=notes.replaceAll("[\\p{Cntrl}\\p{Cf}&&[^\\n\\t]]", "");if(notes.length()>8000)notes=notes.substring(0,7999)+"…";
  return new Release(meta,apk,notes,size);
 }
 public static Metadata metadata(JSONObject o,String installedCert,Release r)throws JSONException{
  long schema=integer(o,"schema_version"),code=integer(o,"version_code"),size=integer(o,"size");String name=string(o,"version_name"),hash=string(o,"sha256"),cert=string(o,"certificate_sha256");
  UpdatePolicy.metadata(schema,code,name,string(o,"package_name"),string(o,"apk_asset"),hash,size,cert,installedCert);
  if(size!=r.apkSize)throw new IllegalArgumentException("APK size disagrees with release");return new Metadata(code,name,hash,size,cert);
 }
}

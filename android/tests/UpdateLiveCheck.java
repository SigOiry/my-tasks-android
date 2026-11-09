package com.simon.mytasks;
import java.io.*;import org.json.*;
/** Opt-in public-network smoke test; not part of deterministic/offline test suite. */
public final class UpdateLiveCheck {
 static JSONObject json(String url,long max)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();UpdateNetwork.fetch(url,b,max,()->false);return new JSONObject(new String(b.toByteArray(),"UTF-8"));}
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("Usage: UpdateLiveCheck installed_version_code installed_certificate_sha256");
  UpdateRelease.Release r=UpdateRelease.release(json("https://api.github.com/repos/SigOiry/my-tasks-android/releases/latest",524288));
  UpdateRelease.Metadata m=UpdateRelease.metadata(json(r.metadataUrl,16384),args[1],r);
  System.out.println("Real GitHub latest stable metadata: version="+m.name+" code="+m.code+" APK bytes="+m.size+" newerThanInstalled="+UpdatePolicy.newer(m.code,Long.parseLong(args[0])));
 }
}

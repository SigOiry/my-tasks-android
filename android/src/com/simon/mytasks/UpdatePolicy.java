package com.simon.mytasks;
/** Android-independent security and scheduling policy. */
public final class UpdatePolicy {
 public static boolean daily(String today,String last,boolean busy){return !busy&&!today.equals(last);}
 public static boolean stable(boolean draft,boolean prerelease,String published){if(draft||prerelease||published==null)return false;try{java.time.Instant.parse(published);return true;}catch(java.time.format.DateTimeParseException e){return false;}}
 public static boolean allowed(String url){try{
  java.net.URI u=new java.net.URI(url);String h=u.getHost(),p=u.getRawPath();
  if(!"https".equals(u.getScheme())||u.getRawUserInfo()!=null||u.getRawFragment()!=null||(u.getPort()!=-1&&u.getPort()!=443)||p==null||!p.equals(u.normalize().getRawPath())||p.toLowerCase(java.util.Locale.ROOT).matches(".*%(2e|2f|5c|00).*"))return false;
  if("api.github.com".equals(h))return p.equals("/repos/SigOiry/my-tasks-android/releases/latest")&&u.getRawQuery()==null;
  if("github.com".equals(h))return p.startsWith("/SigOiry/my-tasks-android/releases/download/")&&(p.endsWith("/my-tasks.apk")||p.endsWith("/update.json"))&&u.getRawQuery()==null;
  return ("release-assets.githubusercontent.com".equals(h)||"objects.githubusercontent.com".equals(h))&&p.matches("/github-production-release-asset(?:-2e65be)?/[A-Za-z0-9/_+.%~-]+");
 }catch(Exception e){return false;}}
 public static boolean assetUrl(String url,String name){if(!allowed(url))return false;try{java.net.URI u=new java.net.URI(url);String p=u.getRawPath();return "github.com".equals(u.getHost())&&p.startsWith("/SigOiry/my-tasks-android/releases/download/")&&p.endsWith("/"+name)&&u.getRawQuery()==null&&!p.contains("/../");}catch(Exception e){return false;}}
 public static final long MAX_APK=100L*1024*1024;
 public static void metadata(long schema,long code,String name,String pkg,String asset,String hash,long size,String cert,String installedCert){
  if(schema!=1||code<1||code>Integer.MAX_VALUE||name==null||name.isEmpty()||name.length()>80||!name.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]*")||!"com.simon.mytasks".equals(pkg)||!"my-tasks.apk".equals(asset)||!hexHash(hash)||size<1||size>MAX_APK||!hexHash(cert)||!cert.equalsIgnoreCase(installedCert))throw new IllegalArgumentException("Release metadata is invalid or has a different signing identity");
 }
 public static boolean hexHash(String h){return h!=null&&h.matches("[a-fA-F0-9]{64}");}
 public static String digest(byte[] data){try{return hex(java.security.MessageDigest.getInstance("SHA-256").digest(data));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
 public static String hex(byte[] data){StringBuilder b=new StringBuilder();for(byte v:data)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}
 public static void identity(String pkg,long actualCode,String actualName,String actualCert,long expectedCode,String expectedName,String currentCert,long installedCode){
  if(!"com.simon.mytasks".equals(pkg)||actualCode!=expectedCode||!expectedName.equals(actualName)||!hexHash(actualCert)||!actualCert.equalsIgnoreCase(currentCert)||!newer(actualCode,installedCode))throw new IllegalArgumentException("APK package, version or current signer does not match");
 }
 private UpdatePolicy(){}
 public static boolean newer(long candidate,long installed){if(candidate<1||installed<1)throw new IllegalArgumentException("Invalid version code");return candidate>installed;}
}

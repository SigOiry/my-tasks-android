package com.simon.mytasks;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-owned updater: no Activity, server account or bearer token retained by worker jobs. */
final class UpdateManager {
 static final String ENDPOINT="https://api.github.com/repos/SigOiry/my-tasks-android/releases/latest";
 static final int PERMISSION_REQUEST=4101,INSTALL_REQUEST=4102;
 private static UpdateManager instance;
 static synchronized UpdateManager get(Context context){if(instance==null)instance=new UpdateManager(context.getApplicationContext());return instance;}
 static synchronized UpdateManager existing(){return instance;}
 final Context app;final File directory;final SharedPreferences prefs;
 final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"github-updater"));
 final Handler main=new Handler(Looper.getMainLooper());final UpdateState state=new UpdateState();
 WeakReference<MainActivity> host=new WeakReference<>(null);AlertDialog dialog;
 UpdateRelease.Release release;UpdateRelease.Metadata metadata;File acceptedFile;Uri grantedUri;
 long acceptedAt,verifiedAt;boolean verified;String message,sessionDay="";
 private UpdateManager(Context context){
  app=context;prefs=app.getSharedPreferences("app_updater",Context.MODE_PRIVATE);directory=new File(app.getCacheDir(),"accepted-updates");directory.mkdirs();
  // Process death never restores consent or an installer grant. Stale downloads are disposable.
  File[] stale=directory.listFiles();if(stale!=null)for(File f:stale)if(f.isFile())f.delete();
 }
 void attach(MainActivity activity){host=new WeakReference<>(activity);String today=LocalDate.now().toString();
  if(state.dropStaleOffer(today,sessionDay)){dismiss();release=null;metadata=null;}
  show();
  if(UpdatePolicy.daily(today,prefs.getString("last_attempt_day",""),state.stage!=UpdateState.Stage.IDLE)&&!today.equals(sessionDay))check(false);
 }
 void detach(MainActivity activity){if(host.get()==activity){dismiss();host.clear();}if(activity.isFinishing())cancel();}
 MainActivity activity(){MainActivity a=host.get();return a==null||a.isFinishing()||a.isDestroyed()?null:a;}
 void check(boolean manual){
  if(!state.beginCheck(manual)){if(manual){show();MainActivity a=activity();if(a!=null)Toast.makeText(a,"Update operation already in progress",Toast.LENGTH_SHORT).show();}return;}
  String day=LocalDate.now().toString();sessionDay=day;prefs.edit().putString("last_attempt_day",day).apply();
  message=null;int token=state.generation;show();
  worker.execute(()->{try{
   UpdateRelease.Release r=UpdateRelease.release(json(ENDPOINT,524288,token));
   String cert=currentSigner();UpdateRelease.Metadata m=UpdateRelease.metadata(json(r.metadataUrl,16384,token),cert,r);
   boolean newer=UpdatePolicy.newer(m.code,installedCode());
   main.post(()->{if(token!=state.generation)return;if(newer){release=r;metadata=m;state.offer();show();}else{boolean report=state.manual;state.cancel();if(report)message="My Tasks is up to date. Installed version is "+installedName()+".";show();}});
  }catch(Exception e){main.post(()->{if(token!=state.generation)return;boolean report=state.manual;cancel();if(report){message=friendly(e);show();}});}});
 }
 JSONObject json(String url,long limit,int token)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();UpdateNetwork.fetch(url,bytes,limit,()->token!=state.generation);
  return new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
 }
 int signingFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
 PackageInfo installed()throws Exception{return app.getPackageManager().getPackageInfo(app.getPackageName(),signingFlags());}
 long code(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
 long installedCode()throws Exception{return code(installed());}
 String installedName(){try{return installed().versionName;}catch(Exception e){return "unknown";}}
 String signer(PackageInfo p)throws IOException{
  Signature[] signatures=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
  // Deliberately conservative: no signer rotation or multi-signer migration in schema v1.
  if(signatures==null||signatures.length!=1)throw new IOException("Unable to establish a single current APK signer");
  return UpdatePolicy.digest(signatures[0].toByteArray());
 }
 String currentSigner()throws Exception{return signer(installed());}
 void verify(File file,UpdateRelease.Metadata m)throws Exception{
  UpdateIO.verify(file,m.size,m.hash);PackageInfo current=installed();
  PackageInfo archive=app.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signingFlags());
  if(archive==null)throw new IOException("Downloaded file is not a valid signed APK");
  String currentCert=signer(current);if(!m.cert.equalsIgnoreCase(currentCert))throw new IOException("Installed signing identity changed");
  UpdatePolicy.identity(archive.packageName,code(archive),archive.versionName,signer(archive),m.code,m.name,currentCert,code(current));
 }
 void download(){
  if(!state.accept())return;acceptedAt=SystemClock.elapsedRealtime();verified=false;verifiedAt=0;int token=state.generation;
  UpdateRelease.Release r=release;UpdateRelease.Metadata m=metadata;File file=new File(directory,UUID.randomUUID()+".apk");show();
  worker.execute(()->{try{
   try(OutputStream out=new FileOutputStream(file)){UpdateNetwork.fetch(r.apkUrl,out,m.size,()->token!=state.generation);}
   if(token!=state.generation){file.delete();return;}verify(file,m);if(!file.setReadOnly())throw new IOException("Unable to protect downloaded APK");
   main.post(()->{if(token!=state.generation){file.delete();return;}acceptedFile=file;state.ready();show();});
  }catch(Exception e){file.delete();main.post(()->{if(token!=state.generation)return;cancel();message=friendly(e);show();});}});
 }
 boolean expired(){return acceptedAt==0||SystemClock.elapsedRealtime()-acceptedAt>20*60*1000L;}
 void prepareInstaller(){
  if(acceptedFile==null||expired()){cancel();message="Accepted update expired. Check for updates again.";show();return;}
  if(!state.verify())return;verified=false;File file=acceptedFile;UpdateRelease.Metadata m=metadata;int token=state.generation;show();
  worker.execute(()->{try{verify(file,m);main.post(()->{if(token!=state.generation)return;verified=true;verifiedAt=SystemClock.elapsedRealtime();show();});}
   catch(Exception e){main.post(()->{if(token!=state.generation)return;cancel();message=friendly(e);show();});}});
 }
 void result(int request){
  if(request==PERMISSION_REQUEST&&state.stage==UpdateState.Stage.PERMISSION){
   if(acceptedFile!=null&&!expired()&&app.getPackageManager().canRequestPackageInstalls())prepareInstaller();
   else{cancel();message="Installation was not allowed. Nothing was installed. You can try again from Settings.";show();}
  }else if(request==INSTALL_REQUEST&&state.stage==UpdateState.Stage.INSTALLING){cancel();}
 }
 void show(){
  MainActivity a=activity();if(a==null)return;
  dismiss();
  if(message!=null){String text=message;message=null;dialog=new AlertDialog.Builder(a).setTitle("App updates").setMessage(text).setPositiveButton("OK",null).create();dialog.show();return;}
  switch(state.stage){
   case CHECKING:if(state.manual)progress(a,"Checking GitHub for updates…");break;
   case OFFER:
    dialog=new AlertDialog.Builder(a).setTitle("Update to "+metadata.name+"?")
     .setMessage("A newer My Tasks version is available.\n\n"+release.notes+"\n\nSave any unsaved task edits and chat drafts first: Android may restart the app during installation. Downloads come from the public GitHub release, not your task server. Android will ask you to confirm installation.")
     .setNegativeButton("Later",(d,w)->cancel()).setPositiveButton("Update",(d,w)->download()).setOnCancelListener(d->cancel()).create();dialog.show();break;
   case DOWNLOADING:progress(a,"Downloading and verifying update…");break;
   case PERMISSION:
    // Some Settings/OEM routes resume us without delivering an Activity result.
    // Never infer consent from permission alone: only this accepted private file can resume.
    if(acceptedFile==null||expired()){cancel();message="Accepted update expired. Check again.";show();}
    else if(app.getPackageManager().canRequestPackageInstalls())prepareInstaller();
    else{state.stage=UpdateState.Stage.READY;show();}
    break;
   case READY:
    if(expired()){cancel();message="Accepted update expired. Check again.";show();break;}
    if(!app.getPackageManager().canRequestPackageInstalls()){
     dialog=new AlertDialog.Builder(a).setTitle("Allow this app to request installation?")
      .setMessage("Android requires per-app permission to install updates downloaded outside an app store. If you continue, enable ‘Allow from this source’ for My Tasks, then return here. This only permits requests: you must still confirm this update in Android's installer. You can revoke the permission later in Android Settings. No security protection needs to be disabled.")
      .setNegativeButton("Cancel",(d,w)->cancel()).setPositiveButton("Open Android Settings",(d,w)->{
       if(!state.permission())return;try{a.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+app.getPackageName())),PERMISSION_REQUEST);}catch(Exception e){cancel();message="Android installation settings are unavailable on this device.";show();}
      }).setOnCancelListener(d->cancel()).create();dialog.show();
    }else prepareInstaller();break;
   case VERIFYING:
    if(!verified){progress(a,"Rechecking APK before Android installation…");break;}
    if(expired()){cancel();message="Accepted update expired. Check again.";show();break;}
    if(SystemClock.elapsedRealtime()-verifiedAt>15000){ // Revalidate after a long background pause.
     state.stage=UpdateState.Stage.READY;prepareInstaller();break;
    }
    if(!app.getPackageManager().canRequestPackageInstalls()){state.stage=UpdateState.Stage.READY;show();break;}
    try{
     grantedUri=new Uri.Builder().scheme("content").authority(app.getPackageName()+".updates").appendPath("apk").appendPath(UUID.randomUUID().toString()).build();
     Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(grantedUri,"application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
     intent.setClipData(ClipData.newRawUri("Verified My Tasks update",grantedUri));
     java.util.List<ResolveInfo> installers=app.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_SYSTEM_ONLY);
     if(installers.isEmpty())throw new IOException("No system package installer is available");
     ActivityInfo installer=installers.get(0).activityInfo;intent.setComponent(new ComponentName(installer.packageName,installer.name));
     state.installing();a.startActivityForResult(intent,INSTALL_REQUEST);
    }catch(Exception e){cancel();message="Android's installer could not be opened. Device policy may block installation.";show();}break;
   default:break;
  }
 }
 void progress(MainActivity a,String text){dialog=new AlertDialog.Builder(a).setTitle("App update").setMessage(text).setNegativeButton("Cancel",(d,w)->cancel()).setOnCancelListener(d->cancel()).create();dialog.show();}
 void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
 void cancel(){dismiss();state.cancel();verified=false;release=null;metadata=null;acceptedAt=0;
  if(grantedUri!=null){app.revokeUriPermission(grantedUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);grantedUri=null;}
  if(acceptedFile!=null){acceptedFile.delete();acceptedFile=null;}
 }
 synchronized File sharedFile(Uri uri)throws FileNotFoundException{
  UpdateState.Stage stage=state.stage;File file=acceptedFile;Uri granted=grantedUri;
  if(stage!=UpdateState.Stage.INSTALLING||granted==null||!granted.equals(uri)||file==null||expired()||!file.isFile())throw new FileNotFoundException("No accepted APK for this URI");return file;
 }
 static String friendly(Exception e){if(e instanceof java.net.UnknownHostException)return "Cannot reach GitHub. Check your internet connection and try again.";
  if(e instanceof java.net.SocketTimeoutException)return "GitHub update check timed out. Try again later.";
  if(e instanceof javax.net.ssl.SSLException)return "Could not securely connect to GitHub. No update was accepted.";
  if(e instanceof IOException||e instanceof IllegalArgumentException)return e.getMessage()==null?"Update could not be verified.":e.getMessage();
  return "GitHub release metadata could not be verified. No update was installed. Try again later.";
 }
}

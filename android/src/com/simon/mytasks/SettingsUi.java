package com.simon.mytasks;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Grouped native utility page. Android owns permissions, system bars and installer. */
final class SettingsUi {
 final MainActivity a;Dialog dialog;ScrollView scroll;LinearLayout content;int anchor;
 SettingsUi(MainActivity activity){a=activity;}
 LinearLayout group(String heading){
  TextView label=a.label(heading.toUpperCase(java.util.Locale.getDefault()),12,a.muted);label.setPadding(a.dp(12),a.dp(20),a.dp(12),a.dp(8));if(Build.VERSION.SDK_INT>=28)label.setAccessibilityHeading(true);a.add(content,label);
  LinearLayout box=a.vertical();box.setPadding(a.dp(12),a.dp(4),a.dp(12),a.dp(4));box.setBackground(a.ui.shape(a.card,0,20));a.add(content,box);return box;
 }
 void row(LinearLayout box,String name,String value,Runnable action){
  Button b=a.button(name+(value.isEmpty()?"":"  ·  "+value)+(action==null?"":"  ›"),action==null?()->{}:action);a.ui.quiet(b);b.setTextColor(a.ink);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(a.dp(4),a.dp(12),a.dp(4),a.dp(12));b.setEnabled(action!=null);a.add(box,b);
 }
 void toggle(LinearLayout box,String name,String key){
  Switch view=new Switch(a);view.setText(name);view.setTextSize(16);view.setTypeface(a.ui.font);view.setTextColor(a.ink);view.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(8));view.setMinimumHeight(a.dp(52));view.setChecked(a.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean(key,false));view.setContentDescription(name);view.setOnCheckedChangeListener((v,checked)->{a.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean(key,checked).apply();reopen(false);});a.add(box,view);
 }
 void reopen(boolean applyTheme){anchor=scroll.getScrollY();dialog.dismiss();if(applyTheme)a.applyAppearance();a.render();show();scroll.post(()->scroll.scrollTo(0,anchor));}
 void show(){
  a.captureDraft();dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);a.trackDialog(dialog);
  LinearLayout page=a.vertical();page.setBackgroundColor(a.bg);page.setPadding(a.dp(16),0,a.dp(16),0);
  LinearLayout bar=a.horizontal();bar.addView(a.ui.icon(R.drawable.ic_back,"Close Settings",dialog::dismiss),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));bar.addView(a.heading("Settings",26),new LinearLayout.LayoutParams(0,-2,1));a.add(page,bar);
  scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);content=a.vertical();content.setPadding(0,0,0,a.dp(24));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout appearance=group("Appearance");
  LinearLayout modes=a.horizontal();String current=a.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("theme","System");
  for(String theme:new String[]{"System","Light","Dark"}){Button b=a.button(theme,()->{a.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("theme",theme).apply();reopen(true);});a.ui.selected(b,current.equals(theme));modes.addView(b,new LinearLayout.LayoutParams(0,-2,1));}a.add(appearance,modes);
  toggle(appearance,"Reduce motion","reduce_motion");toggle(appearance,"Reduce transparency","reduce_transparency");
  LinearLayout notifications=group("Notifications");
  a.notificationState=a.label("",13,a.muted);a.notificationState.setPadding(a.dp(4),a.dp(8),a.dp(4),0);a.add(notifications,a.notificationState);a.updateNotificationState();
  boolean permission=Build.VERSION.SDK_INT<33||a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
  row(notifications,permission?"Android notification settings":"Allow notifications","",()->{dialog.dismiss();if(Build.VERSION.SDK_INT>=33&&!permission)a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);else a.startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,a.getPackageName()));});
  row(notifications,"About update alerts","",()->a.notice("Task update alerts","Android schedules best-effort background checks, not instant push. Doze, battery settings, force-stop and connection problems can delay checks. Refresh checks now; the first connection quietly establishes a baseline."));
  LinearLayout connection=group("Connection & privacy");
  TextView server=a.label(a.store.server(),14,a.muted);server.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(8));server.setTextIsSelectable(true);a.add(connection,server);
  if(a.account!=null){if(a.authState==AuthPolicy.State.FULL)row(connection,"Change password","",()->{dialog.dismiss();a.beginPasswordChange();});row(connection,"Sign out or change server","",()->{dialog.dismiss();a.disconnectAccount();});}else row(connection,"Sign in","",dialog::dismiss);
  row(connection,"Privacy and speech","",()->a.notice("Privacy","Only your issued session is saved, encrypted by Android Keystore. Passwords are never saved. Speech uses your chosen recognition app, which may send audio to its provider; you review the transcript before sending. Hermès can discuss and draft, not send email or operate unrestricted tools."));
  LinearLayout app=group("About");
  TextView version=a.label("My Tasks  ·  "+UpdateManager.get(a).installedName(),15,a.ink);version.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(8));a.add(app,version);
  row(app,"Check for updates","",()->UpdateManager.get(a).check(true));
  row(app,"Inter font license","",()->{try(java.io.InputStream in=a.getResources().openRawResource(R.raw.inter_license);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);a.notice("Inter · SIL Open Font License",out.toString("UTF-8"));}catch(Exception e){a.notice("Font license","Inter is distributed under the SIL Open Font License 1.1. The license is bundled with this APK.");}});
  dialog.setContentView(page);dialog.setOnShowListener(ignored->{Window w=dialog.getWindow();if(w==null)return;w.setBackgroundDrawable(new ColorDrawable(a.bg));w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);w.setDimAmount(0);w.setGravity(Gravity.CENTER);w.setLayout(a.wide?a.dp(480):ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(Build.VERSION.SDK_INT>=30)w.setDecorFitsSystemWindows(false);w.setStatusBarColor(a.bg);w.setNavigationBarColor(a.bg);if(Build.VERSION.SDK_INT>=29){w.setNavigationBarContrastEnforced(false);w.setStatusBarContrastEnforced(false);}w.getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility());page.setOnApplyWindowInsetsListener((v,i)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=b.top;bottom=b.bottom;}else{top=i.getSystemWindowInsetTop();bottom=i.getSystemWindowInsetBottom();}v.setPadding(a.dp(16),top,a.dp(16),bottom);return i;});page.requestApplyInsets();});dialog.show();
 }
}

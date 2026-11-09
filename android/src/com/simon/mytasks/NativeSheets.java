package com.simon.mytasks;
import android.app.Dialog;import android.graphics.Color;import android.graphics.Rect;import android.graphics.drawable.ColorDrawable;import android.view.*;import android.view.animation.DecelerateInterpolator;import android.widget.*;import java.util.function.IntConsumer;import org.json.JSONObject;
/** Bounded native glass chrome; prose remains on an opaque reading surface. */
final class NativeSheets {
 private NativeSheets(){}
 static int boundedHeight(int content,int available){return Math.max(1,Math.min(content,(int)(available*.86f)));}
 static void fit(MainActivity a,Dialog dialog,View panel,boolean fixedHeight){
  Window window=dialog.getWindow();if(window==null)return;
  window.setBackgroundDrawable(a.ui.shape(a.card,0,28));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.25f);window.setWindowAnimations(0);window.setGravity(a.wide?Gravity.CENTER:Gravity.BOTTOM);
  // GlassLayout cannot sample another window. Native background blur supplies the
  // actual Activity backdrop for this dialog; opaque fallback remains readable.
  if(android.os.Build.VERSION.SDK_INT>=31){boolean reduced=a.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE).getBoolean("reduce_transparency",false);try{for(String key:new String[]{"high_text_contrast_enabled","accessibility_reduce_transparency","reduce_transparency"})reduced|=android.provider.Settings.Secure.getInt(a.getContentResolver(),key,0)==1;}catch(SecurityException ignored){}WindowManager manager=a.getSystemService(WindowManager.class);if(!reduced&&manager!=null&&manager.isCrossWindowBlurEnabled()){window.setBackgroundDrawable(a.ui.shape(Color.argb(32,Color.red(a.card),Color.green(a.card),Color.blue(a.card)),0,28));window.setBackgroundBlurRadius(a.dp(24));}else{window.setBackgroundBlurRadius(0);window.setBackgroundDrawable(a.ui.shape(a.card,0,28));}}
  int width=Math.min(a.getResources().getDisplayMetrics().widthPixels-a.dp(24),a.dp(620));int available=a.getWindow().getDecorView().getHeight();if(available<=0)available=a.getResources().getDisplayMetrics().heightPixels;
  panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int height=boundedHeight(panel.getMeasuredHeight(),available);window.setLayout(width,height);
  if(!a.wide){WindowManager.LayoutParams attributes=window.getAttributes();attributes.y=a.dp(8);window.setAttributes(attributes);}
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility());
  if(a.ui.motionEnabled()){panel.setTranslationY(a.dp(a.wide?12:24));panel.setAlpha(.85f);panel.animate().translationY(0).alpha(1).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();}
 }
 static void positionMenu(MainActivity a,Dialog dialog,View material,View anchor){
  fit(a,dialog,material,false);Window w=dialog.getWindow();if(w==null)return;Rect visible=new Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);int width=Math.min(a.dp(anchor==null?360:320),visible.width()-a.dp(24));material.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int height=boundedHeight(material.getMeasuredHeight(),visible.height());w.setLayout(width,height);
  if(anchor!=null&&anchor.isAttachedToWindow()){Rect bounds=new Rect();anchor.getGlobalVisibleRect(bounds);int x=Math.max(visible.left+a.dp(12),Math.min(bounds.left,visible.right-width-a.dp(12)));int y=bounds.bottom+a.dp(4);if(y+height>visible.bottom-a.dp(12))y=Math.max(visible.top+a.dp(12),bounds.top-height-a.dp(4));w.setGravity(Gravity.TOP|Gravity.LEFT);WindowManager.LayoutParams p=w.getAttributes();p.x=x;p.y=y;p.width=width;p.height=height;w.setAttributes(p);}
 }
 /** Resolve canonical identity before looking up a row tag; duplicate titles fall back. */
 static View findAnchor(MainActivity a,String title){
  if(a.listPane==null)return null;
  String id=null;for(int i=0;i<a.tasks.length();i++){JSONObject t=a.tasks.optJSONObject(i);if(t!=null&&title.equals(t.optString("title"))){if(id!=null)return null;id=t.optString("id");}}
  View row=id==null?null:a.listPane.findViewWithTag(id);Rect visible=new Rect();return row!=null&&row.isShown()&&row.getGlobalVisibleRect(visible)&&visible.height()>=a.dp(24)?row:null;
 }
 static Dialog menu(MainActivity a,String title,String[] options,IntConsumer selected){
  final Store.Account owner=a.account;final long generation=a.surfaces.generation();final View anchor=findAnchor(a,title);final View previous=anchor!=null?anchor:a.getCurrentFocus();Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(true);
  FrameLayout material=a.ui.glassSurface();LinearLayout panel=a.vertical();panel.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));material.addView(panel,new FrameLayout.LayoutParams(-1,-2));
  LinearLayout heading=a.horizontal();TextView text=a.heading(title,16);text.setMaxLines(2);heading.addView(text,new LinearLayout.LayoutParams(0,-2,1));Button close=a.button("Close",dialog::dismiss);a.ui.quiet(close);close.setContentDescription("Close actions");heading.addView(close);a.add(panel,heading);
  LinearLayout choices=a.vertical();for(int i=0;i<options.length;i++){final int index=i;Button button=a.button(options[i],()->{if(!a.alive(owner)||!a.surfaces.current(generation))return;dialog.dismiss();selected.accept(index);});button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setMinHeight(a.dp(48));if(options[i].equals("Move to trash"))a.ui.danger(button);else a.ui.quiet(button);a.add(choices,button);}
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(choices);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));dialog.setContentView(material);a.trackDialog(dialog);
  dialog.setOnDismissListener(d->{material.animate().cancel();a.forgetDialog(dialog);if(previous!=null&&a.alive(owner)&&a.surfaces.current(generation)&&previous.isAttachedToWindow())previous.post(()->{if(a.alive(owner)&&a.surfaces.current(generation)&&previous.isAttachedToWindow())previous.requestFocus();});});
  dialog.setOnShowListener(d->positionMenu(a,dialog,material,anchor));positionMenu(a,dialog,material,anchor);dialog.show();return dialog;
 }
}

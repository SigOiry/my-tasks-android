package com.simon.mytasks;
import android.app.Dialog;import android.graphics.Color;import android.graphics.drawable.ColorDrawable;import android.view.*;import android.view.animation.DecelerateInterpolator;import android.widget.*;import java.util.function.IntConsumer;
/** Native window sheets; opaque prose and labelled controls, never a WebView. */
final class NativeSheets {
 private NativeSheets(){}
 static void fit(MainActivity a,Dialog dialog,View panel,boolean fixedHeight){
  Window window=dialog.getWindow();if(window==null)return;
  window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.25f);window.setWindowAnimations(0);window.setGravity(a.wide?Gravity.CENTER:Gravity.BOTTOM);
  int width=Math.min(a.getResources().getDisplayMetrics().widthPixels-a.dp(16),a.dp(620));int available=a.getResources().getDisplayMetrics().heightPixels;int height=Math.min(a.dp(760),(int)(available*.86f));window.setLayout(width,height);
  if(!a.wide){WindowManager.LayoutParams attributes=window.getAttributes();attributes.y=a.dp(8);window.setAttributes(attributes);}
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility());
  if(a.ui.motionEnabled()){panel.setTranslationY(a.dp(a.wide?12:24));panel.setAlpha(.85f);panel.animate().translationY(0).alpha(1).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();}
 }
 static Dialog menu(MainActivity a,String title,String[] options,IntConsumer selected){
  final Store.Account owner=a.account;final long generation=a.surfaces.generation();Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(true);
  LinearLayout panel=a.vertical();panel.setPadding(a.dp(16),a.dp(12),a.dp(16),a.dp(16));panel.setBackground(a.ui.shape(a.card,a.line,28));LinearLayout heading=a.horizontal();TextView text=a.heading(title,20);heading.addView(text,new LinearLayout.LayoutParams(0,-2,1));Button close=a.button("Close",dialog::dismiss);a.ui.quiet(close);heading.addView(close);a.add(panel,heading);a.space(panel,8);
  LinearLayout choices=a.vertical();for(int i=0;i<options.length;i++){final int index=i;Button button=a.button(options[i],()->{if(!a.alive(owner)||!a.surfaces.current(generation))return;dialog.dismiss();selected.accept(index);});button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setMinHeight(a.dp(52));if(options[i].equals("Move to trash"))a.ui.danger(button);else a.ui.quiet(button);a.add(choices,button);a.space(choices,4);}
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(choices);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(panel);a.trackDialog(dialog);dialog.setOnShowListener(d->fit(a,dialog,panel,true));dialog.show();return dialog;
 }
}

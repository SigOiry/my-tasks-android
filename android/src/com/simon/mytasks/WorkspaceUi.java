package com.simon.mytasks;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Shared native tokens; real backdrop surfaces are containers, not label effects. */
final class WorkspaceUi {
 // dp/sp and millisecond semantic scales; keep legacy methods source-compatible.
 static final int SPACE_UNIT=4, PAGE_GUTTER=20, ROW_MIN=72, ACTION_MIN=48;
 static final int TYPE_TITLE=16, TYPE_CONTEXT=13, TYPE_HEADING=30;
 static final int RADIUS_ROW=12, RADIUS_CONTROL=24, RADIUS_SHEET=28;
 static final int ELEVATION_FLOATING=6, ELEVATION_SHEET=12;
 static final long MOTION_FEEDBACK=140L, MOTION_NAVIGATION=220L, MOTION_SHEET=280L;
 final Context context;final Typeface font;
 final int canvas,surface,ink,secondary,border,primary,soft,danger,onPrimary;
 final int unread,focus,separator;
 WorkspaceUi(Context c,boolean dark){
  context=c;Typeface loaded;try{loaded=c.getResources().getFont(R.font.inter_variable);}catch(Exception e){loaded=Typeface.create("sans-serif",Typeface.NORMAL);}font=loaded;
  canvas=color(dark?"#0D0F14":"#F5F5F7");surface=color(dark?"#1A1D24":"#ffffff");
  ink=color(dark?"#f5f5f7":"#1c1c1e");secondary=color(dark?"#aaaab3":"#63636b");border=color(dark?"#34363d":"#d9d9df");
  primary=color(dark?"#66aaff":"#0068E5");soft=color(dark?"#202e46":"#EDF3FF");danger=color(dark?"#ffb4ab":"#a3322c");onPrimary=color(dark?"#0b0d12":"#ffffff");
  unread=primary;focus=primary;separator=color(dark?"#30333B":"#DFDFE4");
 }
 static int color(String s){return Color.parseColor(s);}
 boolean motionEnabled(){return !context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("reduce_motion",false)&&android.animation.ValueAnimator.areAnimatorsEnabled()&&android.provider.Settings.Global.getFloat(context.getContentResolver(),android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,1)>0&&android.provider.Settings.Global.getFloat(context.getContentResolver(),android.provider.Settings.Global.WINDOW_ANIMATION_SCALE,1)>0;}
 /** Real backdrop container: add a LinearLayout for rows, or FrameLayout children. */
 GlassLayout glassSurface(){return new GlassLayout(this);}
 /** Compatibility fallback only. A Drawable cannot sample content behind a View. */
 Drawable glass(){
  return shape(surface,0,26);
 }
 int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 Drawable touch(int fill,int stroke,int radius){return new RippleDrawable(ColorStateList.valueOf((primary&0x00ffffff)|0x24000000),shape(fill,stroke,radius),null);}
 TextView text(String value,int size,int color,boolean strong){
  TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);
  t.setTypeface(android.os.Build.VERSION.SDK_INT>=28?Typeface.create(font,strong?600:400,false):Typeface.create(font,strong?Typeface.BOLD:Typeface.NORMAL));t.setPadding(0,dp(5),0,dp(5));t.setLineSpacing(dp(3),1f);return t;
 }
 Button button(String name,Runnable action){
  Button b=new Button(context);b.setText(name);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(android.os.Build.VERSION.SDK_INT>=28?Typeface.create(font,500,false):Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(48));b.setMinimumWidth(dp(48));press(b);
  b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setStateListAnimator(null);secondary(b);b.setOnClickListener(v->action.run());return b;
 }
 void tone(Button b,int fill,int stroke,int foreground){
  StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},shape(canvas,border,24));states.addState(new int[]{},touch(fill,stroke,24));b.setBackground(states);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{secondary,foreground}));
 }
 void primary(Button b){tone(b,primary,0,onPrimary);}
 void secondary(Button b){tone(b,surface,0,primary);}
 void quiet(Button b){tone(b,Color.TRANSPARENT,0,secondary);}
 void danger(Button b){tone(b,Color.TRANSPARENT,border,danger);}
 void selected(Button b,boolean selected){tone(b,selected?((soft&0x00ffffff)|0x99000000):Color.TRANSPARENT,0,selected?primary:secondary);b.setSelected(selected);b.setContentDescription(b.getText()+(selected?", selected":""));}
 void leadingIcon(Button button,int resource){Drawable icon=context.getDrawable(resource).mutate();icon.setTint(button.getCurrentTextColor());icon.setBounds(0,0,dp(20),dp(20));button.setCompoundDrawablesRelative(icon,null,null,null);button.setCompoundDrawablePadding(dp(8));}
 ImageButton icon(int resource,String name,Runnable action){
  ImageButton b=new ImageButton(context);b.setImageResource(resource);b.setImageTintList(ColorStateList.valueOf(ink));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(touch(Color.TRANSPARENT,0,24));b.setContentDescription(name);b.setTooltipText(name);b.setOnClickListener(v->action.run());b.setMinimumHeight(dp(48));b.setMinimumWidth(dp(48));press(b);return b;
 }
 void press(View v){v.setOnTouchListener((view,event)->{if(!motionEnabled()){view.animate().cancel();view.setScaleX(1f);view.setScaleY(1f);return false;}int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)view.animate().scaleX(.975f).scaleY(.975f).setDuration(160).start();else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).setDuration(200).start();return false;});}
 void input(EditText e){e.setTypeface(font);e.setTextColor(ink);e.setHintTextColor(secondary);e.setTextSize(16);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setMinHeight(dp(48));e.setBackground(shape(surface,border,14));}
 TextView badge(String value,boolean emphasized){TextView t=text(value,12,emphasized?primary:secondary,emphasized);t.setPadding(dp(8),dp(4),dp(8),dp(4));t.setBackground(shape(emphasized?soft:canvas,0,6));return t;}
}

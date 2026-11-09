package com.simon.mytasks;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Shared native components; one restrained visual vocabulary for every surface. */
final class WorkspaceUi {
 final Context context;
 final int canvas,surface,ink,secondary,border,primary,soft,danger,onPrimary;
 WorkspaceUi(Context c,boolean dark){
  context=c;canvas=color(dark?"#111b16":"#f7f8f5");surface=color(dark?"#1a2820":"#ffffff");
  ink=color(dark?"#eef4ef":"#17251f");secondary=color(dark?"#a7b9ad":"#63746a");border=color(dark?"#31453a":"#dde5df");
  primary=color(dark?"#abd8ba":"#23674b");soft=color(dark?"#263c2f":"#edf5ee");danger=color(dark?"#ffb4ab":"#a3322c");onPrimary=color(dark?"#17251f":"#ffffff");
 }
 static int color(String s){return Color.parseColor(s);}
 int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 Drawable touch(int fill,int stroke,int radius){return new RippleDrawable(ColorStateList.valueOf((primary&0x00ffffff)|0x24000000),shape(fill,stroke,radius),null);}
 TextView text(String value,int size,int color,boolean strong){
  TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);
  t.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setPadding(0,dp(5),0,dp(5));t.setLineSpacing(dp(3),1f);return t;
 }
 Button button(String name,Runnable action){
  Button b=new Button(context);b.setText(name);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(48));b.setMinimumWidth(dp(48));
  b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setStateListAnimator(null);secondary(b);b.setOnClickListener(v->action.run());return b;
 }
 void tone(Button b,int fill,int stroke,int foreground){
  StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},shape(canvas,border,8));states.addState(new int[]{},touch(fill,stroke,8));b.setBackground(states);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{secondary,foreground}));
 }
 void primary(Button b){tone(b,primary,0,onPrimary);}
 void secondary(Button b){tone(b,surface,border,primary);}
 void quiet(Button b){tone(b,Color.TRANSPARENT,0,secondary);}
 void danger(Button b){tone(b,Color.TRANSPARENT,border,danger);}
 void selected(Button b,boolean selected){tone(b,selected?soft:Color.TRANSPARENT,selected?primary:0,selected?primary:secondary);b.setSelected(selected);b.setContentDescription(b.getText()+(selected?", selected":""));}
 void leadingIcon(Button button,int resource){Drawable icon=context.getDrawable(resource).mutate();icon.setTint(button.getCurrentTextColor());icon.setBounds(0,0,dp(20),dp(20));button.setCompoundDrawablesRelative(icon,null,null,null);button.setCompoundDrawablePadding(dp(8));}
 ImageButton icon(int resource,String name,Runnable action){
  ImageButton b=new ImageButton(context);b.setImageResource(resource);b.setImageTintList(ColorStateList.valueOf(primary));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(touch(Color.TRANSPARENT,0,8));b.setContentDescription(name);b.setTooltipText(name);b.setOnClickListener(v->action.run());b.setMinimumHeight(dp(48));b.setMinimumWidth(dp(48));return b;
 }
 void input(EditText e){e.setTextColor(ink);e.setHintTextColor(secondary);e.setTextSize(16);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setMinHeight(dp(48));e.setBackground(shape(surface,border,8));}
 TextView badge(String value,boolean emphasized){TextView t=text(value,12,emphasized?primary:secondary,emphasized);t.setPadding(dp(8),dp(4),dp(8),dp(4));t.setBackground(shape(emphasized?soft:canvas,0,4));return t;}
}

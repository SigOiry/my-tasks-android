package com.simon.mytasks;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Opaque content row. Only the thin accent outline breathes, never its text. */
final class TaskRowView extends LinearLayout {
 private final MainActivity a;
 private final WorkspaceUi ui;
 private final UpdateEmphasis emphasis;
 private final CheckBox completion;
 private final ImageButton overflow;
 private final TextView title,context;
 private final Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Rect visible=new Rect();
 private JSONObject task;
 private String taskId="",token="";
 private boolean unread,paused,ownsEmphasis;
 private float breath;
 private ValueAnimator animator;

 TaskRowView(MainActivity activity,UpdateEmphasis ledger){
  super(activity);a=activity;ui=a.ui;emphasis=ledger;
  setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
  setMinimumHeight(ui.dp(64));
  setPadding(0,ui.dp(6),0,ui.dp(6));
  setWillNotDraw(false);setClickable(true);setFocusable(true);
  setOnClickListener(v->{if(task!=null)a.navigate(taskId);});
  setOnLongClickListener(v->{if(task==null)return false;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);a.showTaskActions(task);return true;});
  completion=new CheckBox(a);completion.setButtonDrawable(new CompletionMark(ui));completion.setButtonTintList(null);completion.setGravity(Gravity.CENTER);completion.setPadding(ui.dp(8),0,0,0);
  completion.setMinimumWidth(ui.dp(WorkspaceUi.ACTION_MIN));completion.setMinimumHeight(ui.dp(WorkspaceUi.ACTION_MIN));
  addView(completion,new LayoutParams(ui.dp(WorkspaceUi.ACTION_MIN),ui.dp(WorkspaceUi.ACTION_MIN)));
  completion.setOnClickListener(v->{if(task==null)return;boolean done="completed".equals(task.optString("status"));completion.setChecked(done);a.quickChange(task,"status",done?"todo":"completed");});
  LinearLayout words=new LinearLayout(a);words.setOrientation(VERTICAL);
  words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  title=ui.text("",WorkspaceUi.TYPE_TITLE,ui.ink,true);title.setPadding(0,0,0,0);
  title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
  title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  context=ui.text("",WorkspaceUi.TYPE_CONTEXT,ui.secondary,false);context.setPadding(0,ui.dp(4),0,0);
  context.setSingleLine(true);context.setEllipsize(TextUtils.TruncateAt.END);
  context.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  words.addView(title,new LayoutParams(-1,-2));words.addView(context,new LayoutParams(-1,-2));
  addView(words,new LayoutParams(0,-2,1));
  overflow=ui.icon(R.drawable.ic_more,"Task actions",()->{if(task!=null)a.showTaskActions(task);});
  addView(overflow,new LayoutParams(ui.dp(WorkspaceUi.ACTION_MIN),ui.dp(WorkspaceUi.ACTION_MIN)));
  halo.setStyle(Paint.Style.STROKE);halo.setStrokeWidth(ui.dp(1.5f));halo.setColor(ui.unread);
 }
 void bind(JSONObject value,boolean grouped){
  String id=value.optString("id","");JSONObject n=value.optJSONObject("notification");
  String nextToken=n==null?"":n.optString("token","");
  boolean nextUnread=n!=null&&n.optBoolean("unread");
  if(!id.equals(taskId)||!nextToken.equals(token)||nextUnread!=unread)stopEmphasis();
  task=value;taskId=id;token=nextToken;unread=nextUnread;paused=false;setTag(taskId);
  String name=value.optString("title","");boolean largeText=getResources().getConfiguration().fontScale>=1.5f;title.setMaxLines(largeText?Integer.MAX_VALUE:2);title.setEllipsize(largeText?null:TextUtils.TruncateAt.END);title.setText(name);
  String due=PresentationPolicy.value(value,"due_date").isEmpty()?"":a.dueLabel(value);
  String detail=CompactPolicy.detail(value,grouped,due);
  String priority=value.optString("priority","");
  // Keep important meaning in text, not just color or a punctuation glyph.
  // Priority is a separate flag; do not repeat it in the context line.
  if(detail.isEmpty())detail=a.human(value.optString("status","todo"));
  SpannableString secondary=new SpannableString(TaskPresentation.context(detail,unread));
  if(unread)secondary.setSpan(new ForegroundColorSpan(ui.unread),0,TaskPresentation.UPDATE.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  // Essential unread text is first and unbounded; long metadata may wrap below it.
  // Reset both policies on recycle so ordinary rows remain compact.
  boolean wrap=TaskPresentation.wrapContext(unread);
  context.setSingleLine(!wrap);context.setMaxLines(wrap?Integer.MAX_VALUE:1);
  context.setEllipsize(wrap?null:TextUtils.TruncateAt.END);
  context.setText(secondary);context.setVisibility(VISIBLE);
  boolean completed="completed".equals(value.optString("status"));completion.setChecked(completed);
  completion.setEnabled(a.online&&!a.busy&&!value.optBoolean("deleted"));
  completion.setContentDescription((completed?"Reopen ":"Complete ")+name);
  int flag=TaskPresentation.flag(priority);
  if(flag==TaskPresentation.FLAG_FILLED)overflow.setImageDrawable(filledFlag());
  else overflow.setImageResource(flag==TaskPresentation.FLAG_OUTLINE?R.drawable.ic_flag:R.drawable.ic_more);
  overflow.setImageTintList(ColorStateList.valueOf(TaskPresentation.flagColor(priority,android.graphics.Color.red(ui.canvas)<100,ui.secondary)));
  overflow.setContentDescription(a.human(priority)+" priority. Actions for "+name);overflow.setTooltipText("Task actions");
  boolean selected=taskId.equals(a.selected);setSelected(selected);
  setBackground(ui.touch(selected||unread?ui.soft:android.graphics.Color.TRANSPARENT,0,18));
  setContentDescription(name+", "+a.human(priority)+" priority, "+a.human(value.optString("status"))+", "+PresentationPolicy.project(value)+", "+a.dueLabel(value)+(unread?", New update":"")+(selected?", selected":""));
  setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  startEmphasis();
 }
 /** Filled counterpart of the same 24dp fine-line flag used for medium. */
 private android.graphics.drawable.Drawable filledFlag(){
  android.graphics.Path path=new android.graphics.Path();
  path.moveTo(5,4);path.cubicTo(9,0,14,8,20,3);path.lineTo(20,14);
  path.cubicTo(14,19,10,10,5,14);path.close();
  path.addRect(4.2f,4,5.8f,21,android.graphics.Path.Direction.CW);
  android.graphics.drawable.ShapeDrawable flag=new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.PathShape(path,24,24));
  flag.getPaint().setAntiAlias(true);flag.setIntrinsicWidth(ui.dp(24));flag.setIntrinsicHeight(ui.dp(24));return flag;
 }
 void groupCorners(boolean first,boolean last){
  float corner=ui.dp(18);android.graphics.drawable.GradientDrawable fill=ui.shape(isSelected()||unread?ui.soft:ui.surface,0,0);fill.setCornerRadii(new float[]{first?corner:0,first?corner:0,first?corner:0,first?corner:0,last?corner:0,last?corner:0,last?corner:0,last?corner:0});setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((ui.primary&0x00ffffff)|0x18000000),fill,null));
 }
 private void startEmphasis(){
  if(animator!=null||paused||!a.foreground||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE||!isShown()||!getGlobalVisibleRect(visible))return;
  if(!emphasis.claim(taskId,token,unread,ui.motionEnabled()))return;
  ownsEmphasis=true;
  animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(UpdateEmphasis.CYCLE_MS);
  animator.setRepeatCount(UpdateEmphasis.CYCLES-1);animator.setInterpolator(new LinearInterpolator());
  animator.addUpdateListener(v->{if(!ui.motionEnabled()||!isShown()||!getGlobalVisibleRect(visible)){stopEmphasis();return;}breath=(float)Math.sin(Math.PI*(Float)v.getAnimatedValue());invalidate();});
  animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){if(ownsEmphasis)emphasis.release(taskId,token);ownsEmphasis=false;animator=null;breath=0;invalidate();}});
  animator.start();
 }
 void stopEmphasis(){
  if(animator!=null){ValueAnimator old=animator;animator=null;old.removeAllUpdateListeners();old.removeAllListeners();old.cancel();}
  if(ownsEmphasis)emphasis.release(taskId,token);ownsEmphasis=false;breath=0;invalidate();
 }
 void pause(){paused=true;stopEmphasis();}
 void resume(){paused=false;startEmphasis();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(this::startEmphasis);}
 @Override protected void onDetachedFromWindow(){stopEmphasis();super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(emphasis==null)return;if(visibility!=VISIBLE)stopEmphasis();else post(this::startEmphasis);}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  if(!unread&&!isSelected()){halo.setColor(ui.separator);halo.setAlpha(95);halo.setStrokeWidth(1);canvas.drawLine(ui.dp(48),getHeight()-1,getWidth()-ui.dp(12),getHeight()-1,halo);}halo.setColor(ui.unread);halo.setStrokeWidth(ui.dp(1.2f));
  if(unread){halo.setAlpha(Math.round(35+breath*135));float inset=ui.dp(1.5f);canvas.drawRoundRect(inset,inset,getWidth()-inset,getHeight()-inset,ui.dp(WorkspaceUi.RADIUS_ROW),ui.dp(WorkspaceUi.RADIUS_ROW),halo);}
 }
}

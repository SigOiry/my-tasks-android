package com.simon.mytasks;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

/** Three-dot wave for one durable running request. No timers when hidden/static. */
final class PendingDots extends View {
 private final MainActivity a;private final WorkspaceUi ui;private final String requestId;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Rect visible=new Rect();
 private final long started=SystemClock.uptimeMillis();
 private final Runnable frame=()->invalidate();
 private final ViewTreeObserver.OnScrollChangedListener scrolled=()->invalidate();
 private final ViewTreeObserver.OnWindowFocusChangeListener focused=hasFocus->{if(hasFocus)invalidate();else removeCallbacks(frame);};
 PendingDots(MainActivity owner,String id){super(owner);a=owner;ui=a.ui;requestId=id;setMinimumWidth(ui.dp(48));setMinimumHeight(ui.dp(24));setContentDescription("Hermès reply in progress");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);removeCallbacks(frame);
  boolean wave=NativeMotionPolicy.pendingWave("running",requestId,a.foreground,hasWindowFocus()&&isShown()&&isAttachedToWindow()&&getGlobalVisibleRect(visible),ui.motionEnabled());
  paint.setColor(ui.secondary);long elapsed=SystemClock.uptimeMillis()-started;
  for(int i=0;i<3;i++){float level=wave?NativeMotionPolicy.dotLevel(elapsed,i):.65f;paint.setAlpha(Math.round(100+155*level));canvas.drawCircle(ui.dp(9+12*i),getHeight()/2f-(wave?ui.dp(3)*level:0),ui.dp(2.5f),paint);}
  if(wave)postDelayed(frame,40);
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scrolled);getViewTreeObserver().addOnWindowFocusChangeListener(focused);invalidate();}
 @Override protected void onDetachedFromWindow(){removeCallbacks(frame);if(getViewTreeObserver().isAlive()){getViewTreeObserver().removeOnScrollChangedListener(scrolled);getViewTreeObserver().removeOnWindowFocusChangeListener(focused);}super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(frame==null)return;removeCallbacks(frame);if(visibility==VISIBLE)invalidate();}
}

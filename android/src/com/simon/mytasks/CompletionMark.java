package com.simon.mytasks;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Fine circular completion affordance. Its host retains the full 48dp target. */
final class CompletionMark extends Drawable {
 private final WorkspaceUi ui;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 CompletionMark(WorkspaceUi ui){this.ui=ui;}
 @Override public int getIntrinsicWidth(){return ui.dp(24);}
 @Override public int getIntrinsicHeight(){return ui.dp(24);}
 @Override public boolean isStateful(){return true;}
 @Override protected boolean onStateChange(int[] states){invalidateSelf();return true;}
 @Override public void draw(Canvas c){boolean done=false;for(int state:getState())if(state==android.R.attr.state_checked)done=true;Rect b=getBounds();float cx=b.exactCenterX(),cy=b.exactCenterY(),r=Math.min(b.width(),b.height())/2f-ui.dp(1.5f);paint.setStrokeWidth(ui.dp(1.35f));paint.setColor(done?ui.primary:ui.secondary);paint.setStyle(done?Paint.Style.FILL:Paint.Style.STROKE);c.drawCircle(cx,cy,r,paint);if(done){paint.setColor(ui.onPrimary);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);Path p=new Path();p.moveTo(cx-r*.48f,cy);p.lineTo(cx-r*.12f,cy+r*.35f);p.lineTo(cx+r*.5f,cy-r*.35f);c.drawPath(p,paint);}}
 @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

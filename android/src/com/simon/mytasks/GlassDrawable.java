package com.simon.mytasks;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Draws only the sampled material; GlassLayout draws child labels afterwards, sharply. */
final class GlassDrawable extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final RectF rect=new RectF(), destination=new RectF();
 private final Path clip=new Path();
 private final int surface;
 private final boolean dark;
 private float radius, inset;
 private Bitmap sample;
 private boolean opaque;
 GlassDrawable(int surface,boolean dark,float radius){this.surface=surface;this.dark=dark;this.radius=radius;}
 void sample(Bitmap sample,float inset){this.sample=sample;this.inset=inset;}
 void opaque(boolean value){opaque=value;}
 void radius(float value){radius=value;invalidateSelf();}
 @Override public void draw(Canvas c){
  rect.set(getBounds());clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);
  int saved=c.save();c.clipPath(clip);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
  if(opaque||sample==null){paint.setColor(surface);c.drawRect(rect,paint);}
  else {
   paint.setColor(Color.WHITE);
   destination.set(rect.left-inset,rect.top-inset,rect.right+inset,rect.bottom+inset);
   c.drawBitmap(sample,null,destination,paint);
   // Optical tint stays translucent (light 34%, dark 40%), not a fake opaque gradient.
   paint.setColor((surface&0xffffff)|(dark?0x66000000:0x57000000));c.drawRect(rect,paint);
  }
  paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,
   dark?new int[]{0x22ffffff,0x03ffffff}:new int[]{0x60ffffff,0x08ffffff},null,Shader.TileMode.CLAMP));
  c.drawRect(rect,paint);paint.setShader(null);
  c.restoreToCount(saved);
  // Half-dp directional highlight, no heavy selected outline.
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(.7f,radius/52f));
  paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,dark?0x35ffffff:0xb0ffffff,dark?0x0cffffff:0x22777d90,Shader.TileMode.CLAMP));
  rect.inset(.5f,.5f);c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
 }
 @Override public void setAlpha(int alpha){}
 @Override public void setColorFilter(ColorFilter filter){}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

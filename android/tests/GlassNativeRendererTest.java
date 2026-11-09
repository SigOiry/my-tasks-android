package com.simon.mytasks;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Standalone actual Android graphics probe; run with app_process, NOT desktop JVM.
 * Does not launch, sign in, or mutate any application data. */
public final class GlassNativeRendererTest {
 public static void main(String[] args) {
  int w=180,h=80;
  int[] pixels=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=x<w/2?Color.BLACK:Color.WHITE;
  GlassPixels.blur(pixels,new int[pixels.length],w,h,6);
  check((pixels[h/2*w+w/2-2]&255)>0,"backdrop boundary must actually blur");
  Bitmap sample=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
  GlassDrawable material=new GlassDrawable(Color.WHITE,false,24);
  material.setBounds(0,0,w,h);material.sample(sample,0);
  Bitmap output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(output);material.draw(canvas);
  int dark=Color.red(output.getPixel(30,40)),light=Color.red(output.getPixel(150,40));
  check(dark>40&&dark<180,"default glass must retain dark sampled backdrop, not near-opaque white");
  check(light>dark+70,"moving-content contrast must survive the tint");
  Paint foreground=new Paint();foreground.setColor(Color.MAGENTA);
  canvas.drawRect(40,25,52,55,foreground);
  check(output.getPixel(40,40)==Color.MAGENTA,"foreground must remain exact and sharp");
  check(output.getPixel(39,40)!=Color.MAGENTA,"foreground must not diffuse into neighbor");
  material.opaque(true);material.draw(canvas);
  check(output.getPixel(30,40)==Color.WHITE,"reduced transparency must be stable opaque material");
  System.out.println("NATIVE GLASS PASS: blurred backdrop, translucent sampled contrast, sharp foreground, opaque fallback");
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

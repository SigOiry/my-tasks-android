package com.simon.mytasks;

/** Allocation-free separable box blur on an already downsampled, bounded backdrop. */
final class GlassPixels {
 private GlassPixels() {}
 static int sampleScale(int width,int height) {
  int scale=6;
  while (((long)(width+scale-1)/scale)*((height+scale-1)/scale)>65536L) scale++;
  return scale;
 }
 static void blur(int[] pixels,int[] scratch,int width,int height,int radius) {
  if(width<=0||height<=0||radius<1)return;
  pass(pixels,scratch,width,height,radius,true);
  pass(scratch,pixels,width,height,radius,false);
 }
 private static void pass(int[] from,int[] to,int w,int h,int r,boolean horizontal) {
  int lines=horizontal?h:w, length=horizontal?w:h, step=horizontal?1:w;
  int count=2*r+1;
  for(int line=0;line<lines;line++) {
   int base=horizontal?line*w:line;
   long a=0,red=0,g=0,b=0;
   for(int k=-r;k<=r;k++) {int c=from[base+Math.max(0,Math.min(length-1,k))*step];a+=c>>>24;red+=(c>>>16)&255;g+=(c>>>8)&255;b+=c&255;}
   for(int x=0;x<length;x++) {
    to[base+x*step]=((int)(a/count)<<24)|((int)(red/count)<<16)|((int)(g/count)<<8)|(int)(b/count);
    int old=from[base+Math.max(0,x-r)*step],next=from[base+Math.min(length-1,x+r+1)*step];
    a+=(next>>>24)-(old>>>24);red+=((next>>>16)&255)-((old>>>16)&255);g+=((next>>>8)&255)-((old>>>8)&255);b+=(next&255)-(old&255);
   }
  }
 }
}

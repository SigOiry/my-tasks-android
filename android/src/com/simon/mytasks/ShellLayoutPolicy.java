package com.simon.mytasks;
import java.util.ArrayList;
import java.util.List;
/** Ordinary workspace only. Email retains its attached-input geometry contract. */
final class ShellLayoutPolicy {
 static final class Plan {
  final int left,top,right,bottom,railWidth,masterWidth;final boolean rail,dual;
  Plan(int l,int t,int r,int b,boolean rail,boolean dual,int rw,int mw){left=l;top=t;right=r;bottom=b;this.rail=rail;this.dual=dual;railWidth=rw;masterWidth=mw;}
  boolean sameStructure(Plan other){return other!=null&&rail==other.rail&&dual==other.dual&&railWidth==other.railWidth&&masterWidth==other.masterWidth;}
 }
 static Plan measure(int width,int height,float density,float fontScale,int left,int top,int right,int bottom,List<int[]> occlusions){
  float d=density>0&&Float.isFinite(density)?density:1,scale=fontScale>0&&Float.isFinite(fontScale)?Math.max(1,fontScale):1;
  int w=Math.max(0,width),h=Math.max(0,height),l=Math.min(w,Math.max(0,left)),t=Math.min(h,Math.max(0,top)),r=Math.max(l,w-Math.max(0,right)),b=Math.max(t,h-Math.max(0,bottom));
  ArrayList<int[]> spaces=new ArrayList<>();spaces.add(new int[]{l,t,r,b});
  if(occlusions!=null)for(int[] feature:occlusions){
   if(feature==null||feature.length<4||feature[2]<=feature[0]||feature[3]<=feature[1])continue;
   ArrayList<int[]> next=new ArrayList<>();
   for(int[] area:spaces){int x1=Math.max(area[0],feature[0]),y1=Math.max(area[1],feature[1]),x2=Math.min(area[2],feature[2]),y2=Math.min(area[3],feature[3]);if(x2<=x1||y2<=y1){next.add(area);continue;}
    // Each candidate is contiguous and entirely outside this feature. A fold
    // line has no area and never invents a hinge or consumes usable width.
    add(next,area[0],area[1],x1,area[3]);add(next,x2,area[1],area[2],area[3]);add(next,area[0],area[1],area[2],y1);add(next,area[0],y2,area[2],area[3]);
   }
   next.sort((a,c)->Long.compare(area(c),area(a)));if(next.size()>64)next.subList(64,next.size()).clear();spaces=next;
  }
  int[] safe={l,t,l,t};for(int[] candidate:spaces)if(area(candidate)>area(safe))safe=candidate;
  int available=(int)Math.floor((safe[2]-safe[0])/d)-32,availableHeight=(int)Math.floor((safe[3]-safe[1])/d);
  int railWidth=(int)Math.ceil(184*scale),masterWidth=(int)Math.ceil(300*scale),detailMin=(int)Math.ceil(360*scale);
  boolean rail=available>=railWidth+16+detailMin&&availableHeight>=160;
  int workspace=available-(rail?railWidth+16:0);
  boolean dual=workspace>=masterWidth+29+detailMin&&availableHeight>=240;
  return new Plan(safe[0],safe[1],safe[2],safe[3],rail,dual,railWidth,masterWidth);
 }
 private static long area(int[] r){return (long)Math.max(0,r[2]-r[0])*Math.max(0,r[3]-r[1]);}
 private static void add(List<int[]> spaces,int l,int t,int r,int b){if(r>l&&b>t)spaces.add(new int[]{l,t,r,b});}
}

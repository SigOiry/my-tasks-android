package com.simon.mytasks;
/** Input is measured usable dp AFTER system/IME/cutout insets. */
final class ComposerLayoutPolicy {
 /** Select the largest actual unobscured rectangle; no model-specific geometry. */
 static int[] usablePane(int width,int height,int left,int top,int right,int bottom){left=Math.max(0,Math.min(width,left));right=Math.max(0,Math.min(width,right));top=Math.max(0,Math.min(height,top));bottom=Math.max(0,Math.min(height,bottom));if(right<=left||bottom<=top)return new int[]{0,0,width,height};int[][] candidates={{0,0,left,height},{right,0,width-right,height},{0,0,width,top},{0,bottom,width,height-bottom}};int[] best=candidates[0];for(int[] p:candidates)if((long)p[2]*p[3]>(long)best[2]*best[3])best=p;return best;}
 static boolean splitAroundHinge(int width,int height,int left,int top,int right,int bottom,float fontScale,boolean collapsed){float scale=Math.max(1,fontScale);return !collapsed&&right>left&&top<=0&&bottom>=height&&left>=400*scale&&width-right>=300*scale;}
 static boolean split(int usable,float fontScale,boolean collapsed,int reportedHingeWidth){int width=Math.max(0,usable-Math.max(0,reportedHingeWidth));float scale=Math.max(1,fontScale);return !collapsed&&width*.64f>=400*scale&&width*.36f>=280*scale;}
}

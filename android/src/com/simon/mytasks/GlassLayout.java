package com.simon.mytasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;

/**
 * FrameLayout contract: add children with FrameLayout.LayoutParams (or add one
 * horizontal/vertical LinearLayout). Overlay this on content that actually runs
 * beneath it. Captures only this surface + an 18dp blur halo from the decor root.
 * All GlassLayout subtrees are omitted during capture, including foreground labels.
 * Ordinary content must NOT be wrapped in glass or it too will be omitted.
 * Software snapshots cannot sample SurfaceView/video or cross-window content.
 */
public final class GlassLayout extends FrameLayout {
 // Android View drawing is UI-thread confined. Shared guard excludes EVERY glass
 // subtree, not just this instance, without hiding/re-laying-out real views.
 private static int captureDepth;
 private final WorkspaceUi ui;
 private final GlassDrawable material;
 private final Rect visible=new Rect();
 private final int[] here=new int[2],rootPosition=new int[2];
 private final SharedPreferences prefs;
 private Bitmap bitmap;
 private Canvas captureCanvas;
 private int[] pixels,scratch;
 private ViewTreeObserver observer;
 private long lastCapture;
 private boolean failed,opaque,settingsRegistered;
 private final Runnable trailing=()->{if(isAttachedToWindow())invalidate();};
 private final ViewTreeObserver.OnPreDrawListener preDraw=()->{captureBackdrop();return true;};
 private final ViewTreeObserver.OnScrollChangedListener scroll=()->{
  removeCallbacks(trailing);postDelayed(trailing,50);invalidate();
 };
 private final SharedPreferences.OnSharedPreferenceChangeListener preferences=(p,k)->refreshPolicy();
 private final ContentObserver settings=new ContentObserver(new Handler(Looper.getMainLooper())) {
  @Override public void onChange(boolean self){refreshPolicy();}
 };
 GlassLayout(WorkspaceUi ui){
  super(ui.context);this.ui=ui;
  prefs=getContext().getSharedPreferences("appearance",Context.MODE_PRIVATE);
  material=new GlassDrawable(ui.surface,Color.red(ui.canvas)<100,ui.dp(26));
  setBackground(material);setClipToOutline(true);
  setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,getWidth(),getHeight(),cornerRadius);}});
  cornerRadius=ui.dp(26);setElevation(ui.dp(3));
 }
 private float cornerRadius;
 public void setCornerRadiusDp(float dp){cornerRadius=ui.dp(dp);material.radius(cornerRadius);invalidateOutline();}
 /** Explicit refresh for data changes outside normal native invalidation/scroll. */
 public void refreshBackdrop(){lastCapture=0;invalidate();}
 @Override protected void onAttachedToWindow(){
  super.onAttachedToWindow();observer=getRootView().getViewTreeObserver();
  observer.addOnPreDrawListener(preDraw);observer.addOnScrollChangedListener(scroll);
  prefs.registerOnSharedPreferenceChangeListener(preferences);
  try {
   for(String key:new String[]{"high_text_contrast_enabled","accessibility_reduce_transparency","reduce_transparency"})
    getContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key),false,settings);
   settingsRegistered=true;
  }catch(SecurityException ignored){}
  refreshPolicy();
 }
 @Override protected void onDetachedFromWindow(){
  if(observer!=null&&observer.isAlive()){
   observer.removeOnPreDrawListener(preDraw);observer.removeOnScrollChangedListener(scroll);
  }
  observer=null;prefs.unregisterOnSharedPreferenceChangeListener(preferences);
  if(settingsRegistered)getContext().getContentResolver().unregisterContentObserver(settings);
  settingsRegistered=false;removeCallbacks(trailing);release();super.onDetachedFromWindow();
 }
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){refreshPolicy();refreshBackdrop();}}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)release();}
 private void refreshPolicy(){
  opaque=prefs.getBoolean("reduce_transparency",false)||highContrast()||secure("accessibility_reduce_transparency")||secure("reduce_transparency");
  material.opaque(opaque);if(opaque)release();lastCapture=0;invalidate();
 }
 private boolean highContrast(){
  // Newer public API is reflected solely to keep the SDK35 direct build intact.
  Object manager=getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
  if(manager!=null)try {
   if(Boolean.TRUE.equals(manager.getClass().getMethod("isHighContrastTextEnabled").invoke(manager)))return true;
  }catch(ReflectiveOperationException|SecurityException unavailable){}
  return secure("high_text_contrast_enabled");
 }
 private boolean secure(String key){try{return Settings.Secure.getInt(getContext().getContentResolver(),key,0)==1;}catch(SecurityException e){return false;}}
 private void release(){
  // Do not recycle a Bitmap still referenced by the hardware display list.
  bitmap=null;captureCanvas=null;pixels=null;scratch=null;
  if(material!=null)material.sample(null,0);
 }
 private void captureBackdrop(){
  if(captureDepth>0||opaque||failed||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE||!hasWindowFocus()||!isShown()||getAlpha()==0||!getGlobalVisibleRect(visible)||getWidth()==0||getHeight()==0)return;
  long now=SystemClock.uptimeMillis();if(bitmap!=null&&now-lastCapture<48)return;
  View root=getRootView();if(root==this)return;
  int halo=ui.dp(18), width=getWidth()+2*halo,height=getHeight()+2*halo;
  int scale=GlassPixels.sampleScale(width,height);
  int w=(width+scale-1)/scale,h=(height+scale-1)/scale;
  try {
   if(bitmap==null||bitmap.getWidth()!=w||bitmap.getHeight()!=h){
    bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);captureCanvas=new Canvas(bitmap);
    pixels=new int[w*h];scratch=new int[w*h];
   }
   bitmap.eraseColor(ui.canvas);getLocationInWindow(here);root.getLocationInWindow(rootPosition);
   int save=captureCanvas.save();captureCanvas.scale((float)w/width,(float)h/height);
   captureCanvas.translate(halo-(here[0]-rootPosition[0]),halo-(here[1]-rootPosition[1]));
   captureDepth++;
   try { root.draw(captureCanvas); } finally { captureDepth--; }
   captureCanvas.restoreToCount(save);
   bitmap.getPixels(pixels,0,w,0,0,w,h);
   // Two low-resolution passes approximate a Gaussian more softly than one box.
   int radius=Math.max(1,Math.round((float)halo/scale/2));
   GlassPixels.blur(pixels,scratch,w,h,radius);GlassPixels.blur(pixels,scratch,w,h,radius);
   bitmap.setPixels(pixels,0,w,0,0,w,h);material.sample(bitmap,halo);lastCapture=now;
  }catch(RuntimeException|OutOfMemoryError unavailable){
   failed=true;release();material.opaque(true); // unsupported software content: readable fallback
  }
 }
 @Override public void draw(Canvas canvas){
  if (captureDepth > 0) return;
  super.draw(canvas); // material first; labels and icons remain unfiltered native children
 }
}

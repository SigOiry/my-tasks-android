package com.simon.mytasks;
import android.app.Activity;import android.graphics.Rect;import java.lang.reflect.*;import java.util.*;
/** Optional OEM extensions only. Absence is a measured-viewport fallback, never guessed hinges. */
final class ComposerWindowObserver {
 interface Listener{void changed(List<Rect> occlusions);}
 final Activity activity;final Listener listener;Object component,consumer;Method remove;boolean active;int generation;
 ComposerWindowObserver(Activity a,Listener l){activity=a;listener=l;}
 void start(){stop();active=true;final int ticket=++generation;try{
  Class<?> provider=Class.forName("androidx.window.extensions.WindowExtensionsProvider");Object extensions=provider.getMethod("getWindowExtensions").invoke(null);component=extensions.getClass().getMethod("getWindowLayoutComponent").invoke(extensions);if(component==null)return;
  Method add=null;for(Method m:component.getClass().getMethods()){Class<?>[] p=m.getParameterTypes();if(m.getName().equals("addWindowLayoutInfoListener")&&p.length==2&&p[0].isAssignableFrom(activity.getClass())&&p[1].isInterface()){add=m;break;}}if(add==null)return;final Class<?> consumerType=add.getParameterTypes()[1];
  consumer=Proxy.newProxyInstance(consumerType.getClassLoader(),new Class<?>[]{consumerType},(proxy,method,args)->{if(method.getName().equals("accept")&&args!=null&&args.length==1){List<Rect> bounds=occlusions(args[0]);activity.runOnUiThread(()->{if(active&&ticket==generation)listener.changed(bounds);});return null;}if(method.getName().equals("equals"))return proxy==args[0];if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("toString"))return "EmailComposerWindowListener";return null;});
  remove=component.getClass().getMethod("removeWindowLayoutInfoListener",consumerType);add.invoke(component,activity,consumer);
 }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){stop();}}
 static List<Rect> occlusions(Object layout){ArrayList<Rect> result=new ArrayList<>();try{Object features=layout.getClass().getMethod("getDisplayFeatures").invoke(layout);if(features instanceof Iterable)for(Object feature:(Iterable<?>)features){Object b=feature.getClass().getMethod("getBounds").invoke(feature);if(!(b instanceof Rect))continue;Rect rect=new Rect((Rect)b);if(rect.width()<=0||rect.height()<=0)continue;/* Nonzero-area reported feature is an actual occlusion; a fold line is not. */result.add(rect);}}catch(ReflectiveOperationException|RuntimeException ignored){}return result;}
 void stop(){active=false;generation++;if(component!=null&&consumer!=null&&remove!=null)try{remove.invoke(component,consumer);}catch(ReflectiveOperationException|RuntimeException ignored){}component=consumer=null;remove=null;}
}

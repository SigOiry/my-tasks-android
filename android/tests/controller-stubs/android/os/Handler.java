package android.os;
import java.util.*;
/** Host-only deterministic dispatcher. Delays never run implicitly. */
public final class Handler {
 static final ArrayDeque<Runnable> immediate=new ArrayDeque<>();static final Set<Runnable> delayed=new HashSet<>();
 public Handler(Looper ignored){}public void post(Runnable r){immediate.add(r);}public void postDelayed(Runnable r,long delay){delayed.add(r);}public void removeCallbacks(Runnable r){delayed.remove(r);immediate.remove(r);}
 public static boolean hasImmediate(){return !immediate.isEmpty();}public static boolean hasDelayed(){return !delayed.isEmpty();}
 public static void drainImmediate(){while(!immediate.isEmpty())immediate.remove().run();}
 public static void runDelayed(){ArrayList<Runnable> copy=new ArrayList<>(delayed);delayed.clear();for(Runnable r:copy)r.run();}
}

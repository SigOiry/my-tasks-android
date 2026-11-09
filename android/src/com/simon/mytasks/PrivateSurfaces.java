package com.simon.mytasks;
import java.util.*;
/** Main-thread privacy boundary: close every registered layer before gating content. */
final class PrivateSurfaces {
 private long generation;private final Set<Runnable> dismissals=new LinkedHashSet<>();
 long generation(){return generation;}boolean current(long value){return value==generation;}
 void register(Runnable dismiss){dismissals.add(dismiss);}void forget(Runnable dismiss){dismissals.remove(dismiss);}
 void invalidate(){generation++;ArrayList<Runnable> previous=new ArrayList<>(dismissals);dismissals.clear();for(Runnable close:previous)try{close.run();}catch(RuntimeException ignored){/* Keep closing remaining private surfaces. */}}
}

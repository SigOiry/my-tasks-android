package com.simon.mytasks;
/** Main-thread ticket gate: pause, session changes and writes fence older quiet reads. */
public final class UiReadGate {
 private long generation;private String owner="";
 public UiReadGate(){}
 public long begin(String epoch){owner=epoch;return ++generation;}
 public void invalidate(){++generation;owner="";}
 public boolean current(long ticket,String epoch){return ticket==generation&&owner.equals(epoch);}
}

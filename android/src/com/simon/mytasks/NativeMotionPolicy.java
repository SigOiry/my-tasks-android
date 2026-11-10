package com.simon.mytasks;
/** Presentation-only motion; request state and identity always come from the server. */
final class NativeMotionPolicy {
 static long duration(String kind){return "tab".equals(kind)?210L:"insertion".equals(kind)?200L:280L;}
 static boolean pendingWave(String status,String requestId,boolean foreground,boolean visible,boolean motion){return "running".equals(status)&&requestId!=null&&!requestId.isEmpty()&&foreground&&visible&&motion;}
 static float dotLevel(long elapsed,int index){return .35f+.65f*(float)((1+Math.sin(elapsed*2*Math.PI/1200-index*2*Math.PI/3))/2);}
}

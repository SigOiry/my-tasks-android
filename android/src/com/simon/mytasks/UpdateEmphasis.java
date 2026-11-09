package com.simon.mytasks;

/** Session-only visual emphasis; never acknowledges an update. */
final class UpdateEmphasis {
 static final int MAX_ACTIVE=2;
 static final long CYCLE_MS=2300L;
 static final int CYCLES=2;
 private final java.util.Set<Identity> seen=new java.util.HashSet<>();
 private final java.util.Set<Identity> active=new java.util.HashSet<>();
 boolean claim(String task,String token,boolean unread,boolean motion){
  if(!unread||task==null||token==null||task.isEmpty()||token.isEmpty())return false;
  Identity key=new Identity(task,token);
  // Even suppressed emphasis is observed: scroll/rebind is never a replay queue.
  if(!seen.add(key)||!motion||active.size()>=MAX_ACTIVE)return false;
  active.add(key);return true;
 }
 void observe(String task,String token){
  if(task!=null&&token!=null&&!task.isEmpty()&&!token.isEmpty())seen.add(new Identity(task,token));
 }
 void release(String task,String token){active.remove(new Identity(task,token));}
 int activeCount(){return active.size();}
 void pause(){active.clear();}
 void clear(){pause();seen.clear();}
 private static final class Identity {
  final String task,token;
  Identity(String task,String token){this.task=task;this.token=token;}
  @Override public boolean equals(Object other){if(!(other instanceof Identity))return false;Identity k=(Identity)other;return java.util.Objects.equals(task,k.task)&&java.util.Objects.equals(token,k.token);}
  @Override public int hashCode(){return java.util.Objects.hash(task,token);}
 }
}

package com.simon.mytasks;
public final class UpdateStateTest {
 static int n;static void check(boolean v){n++;if(!v)throw new AssertionError("assertion "+n);}
 public static void main(String[] args){
  UpdateState s=new UpdateState();check(s.beginCheck(false));int token=s.generation;check(!s.beginCheck(true));check(s.manual);check(s.generation==token);
  s.offer();check(s.stage==UpdateState.Stage.OFFER);check(!s.beginCheck(true));check(s.accept());check(s.stage==UpdateState.Stage.DOWNLOADING);
  s.ready();check(s.stage==UpdateState.Stage.READY);check(s.permission());check(s.stage==UpdateState.Stage.PERMISSION);check(!s.accept());
  s.cancel();check(s.stage==UpdateState.Stage.IDLE);check(s.generation!=token);check(!s.permission());check(!s.accept());
  check(s.beginCheck(true));s.offer();check(s.accept());s.ready();check(s.verify());check(s.stage==UpdateState.Stage.VERIFYING);s.installing();check(s.stage==UpdateState.Stage.INSTALLING);check(!s.beginCheck(true));s.cancel();
  check(s.beginCheck(false));s.offer();check(!s.dropStaleOffer("2026-11-09","2026-11-09"));check(s.stage==UpdateState.Stage.OFFER);
  check(s.dropStaleOffer("2026-11-10","2026-11-09"));check(s.stage==UpdateState.Stage.IDLE);
  check(s.beginCheck(false));s.offer();s.accept();check(!s.dropStaleOffer("2026-11-10","2026-11-09"));check(s.stage==UpdateState.Stage.DOWNLOADING);s.cancel();
  System.out.println("UpdateState: "+n+" assertions passed");
 }
}

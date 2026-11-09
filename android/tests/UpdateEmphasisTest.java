package com.simon.mytasks;

/** Host JVM behavior tests: real ledger, no Android stubs. */
public final class UpdateEmphasisTest {
 private static int checks;
 private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  UpdateEmphasis ledger=new UpdateEmphasis();
  check(ledger.claim("task-a","token-1",true,true),"first visible unread update breathes");
  check(!ledger.claim("task-a","token-1",true,true),"same token cannot replay on bind/build");
  ledger.release("task-a","token-1");
  check(!ledger.claim("task-a","token-1",true,true),"finished animation is still observed");
  check(ledger.claim("task-a","token-2",true,true),"new token for same task breathes");
  check(ledger.claim("task-b","token-2",true,true),"same token on distinct task is independent");
  check(ledger.activeCount()==2,"two active halos");
  check(!ledger.claim("task-c","token-3",true,true),"third concurrent update stays static");
  ledger.pause();
  check(ledger.activeCount()==0,"background releases all active slots");
  check(!ledger.claim("task-a","token-2",true,true),"background/navigation never rearms seen update");
  check(!ledger.claim("task-c","token-3",true,true),"overflow does not replay after scrolling");
  check(!ledger.claim("task-d","token-4",true,false),"reduced motion is static");
  check(!ledger.claim("task-d","token-4",true,true),"preference change does not replay");
  check(!ledger.claim("task-e","token-5",false,true),"read state never animates");
  check(ledger.claim("task-e","token-5",true,true),"read observation did not consume an unread event");
  ledger.release("task-e","wrong-token");
  check(ledger.activeCount()==1,"stale release does not release another identity");
  check(!ledger.claim("","token-x",true,true),"missing task identity is static");
  check(!ledger.claim("task-x","",true,true),"missing notification identity is static");
  check(!ledger.claim(null,"token-x",true,true),"null identity is static");
  ledger.clear();
  check(ledger.activeCount()==0,"logout clears active slots");
  check(ledger.claim("task-a","token-1",true,true),"new session has a clean ledger");
  ledger.clear();
  check(ledger.claim("task:a","b",true,true),"delimiter in task is literal");
  check(ledger.claim("task","a:b",true,true),"identity pairs cannot collide via delimiters");
  ledger.pause();
  check(ledger.claim("task:a","B",true,true),"token case is exact, never normalized");
  check(UpdateEmphasis.CYCLE_MS==2300L&&UpdateEmphasis.CYCLES==2,"exact bounded two-cycle timing");
  ledger.clear();
  ledger.observe("edited-task","canonical-token");
  check(!ledger.claim("edited-task","canonical-token",true,true),"own canonical snapshot token stays static without acknowledging");
  check(ledger.claim("edited-task","remote-next-token",true,true),"subsequent remote update still breathes");
  System.out.println("UpdateEmphasisTest: "+checks+" checks passed");
 }
}

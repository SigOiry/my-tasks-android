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
  check(ledger.claim("task-c","token-3",true,true),"unanimated overflow enters the next bounded batch rather than being lost");
  ledger.release("task-c","token-3");
  check(!ledger.claim("task-c","token-3",true,true),"completed overflow never replays on scroll");
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
  check(UpdateEmphasis.CYCLE_MS==1600L&&UpdateEmphasis.CYCLES==4,"four bounded 1.6-second breaths");
  ledger.clear();
  ledger.observe("edited-task","canonical-token");
  check(!ledger.claim("edited-task","canonical-token",true,true),"own canonical snapshot token stays static without acknowledging");
  check(ledger.claim("edited-task","remote-next-token",true,true),"subsequent remote update still breathes");
  UpdateEmphasis batches=new UpdateEmphasis();final int[] wake={0},started={0};
  batches.onSlotAvailable(()->{wake[0]++;if(batches.claim("third","v1",true,true))started[0]++;});
  check(batches.claim("first","v1",true,true)&&batches.claim("second","v1",true,true),"first bounded batch starts");
  check(!batches.claim("third","v1",true,true),"overflow waits while both slots active");
  batches.release("wrong","v1");check(wake[0]==0,"stale releases never schedule a batch");
  batches.release("first","v1");check(wake[0]==1&&started[0]==1&&batches.activeCount()==2,"release wakes next bounded visible batch");
  batches.release("third","v1");check(started[0]==1,"batch callback never replays an admitted token");
  System.out.println("UpdateEmphasisTest: "+checks+" checks passed");
 }
}

package com.simon.mytasks;
/** Main-thread state machine; generation is also observed by the dedicated worker. */
public final class UpdateState {
 public enum Stage {IDLE,CHECKING,OFFER,DOWNLOADING,READY,PERMISSION,VERIFYING,INSTALLING}
 public volatile Stage stage=Stage.IDLE;public volatile int generation;public boolean manual;
 public boolean beginCheck(boolean requested){if(stage==Stage.CHECKING){manual|=requested;return false;}if(stage!=Stage.IDLE)return false;manual=requested;generation++;stage=Stage.CHECKING;return true;}
 public void offer(){if(stage!=Stage.CHECKING)throw new IllegalStateException();stage=Stage.OFFER;}
 public boolean accept(){if(stage!=Stage.OFFER)return false;stage=Stage.DOWNLOADING;return true;}
 public void ready(){if(stage!=Stage.DOWNLOADING)throw new IllegalStateException();stage=Stage.READY;}
 public boolean permission(){if(stage!=Stage.READY)return false;stage=Stage.PERMISSION;return true;}
 public boolean verify(){if(stage!=Stage.READY&&stage!=Stage.PERMISSION)return false;stage=Stage.VERIFYING;return true;}
 public void installing(){if(stage!=Stage.VERIFYING)throw new IllegalStateException();stage=Stage.INSTALLING;}
 public boolean dropStaleOffer(String today,String checkedDay){if(stage!=Stage.OFFER||today.equals(checkedDay))return false;cancel();return true;}
 public void cancel(){generation++;stage=Stage.IDLE;manual=false;}
}

package com.simon.mytasks;
/** Duration/state gates used by attached native Views, not invented provider states. */
public final class NativeMotionPolicyTest {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Object call(String method,Class<?>[] types,Object...args)throws Exception{
  try{return Class.forName("com.simon.mytasks.NativeMotionPolicy").getDeclaredMethod(method,types).invoke(null,args);}catch(ClassNotFoundException|NoSuchMethodException e){throw new AssertionError("Missing native motion: "+method,e);}
 }
 public static void main(String[]args)throws Exception{
  String[] kinds={"navigation","tab","menu","insertion"};long[] times={280,210,280,200};
  for(int i=0;i<kinds.length;i++)check((Long)call("duration",new Class<?>[]{String.class},kinds[i])==times[i],"purposeful "+kinds[i]+" duration");
  Class<?>[] types={String.class,String.class,boolean.class,boolean.class,boolean.class};
  check((Boolean)call("pendingWave",types,"running","durable-id",true,true,true),"only actual running request waves");
  for(String status:new String[]{"queued","prepared","uncertain","complete","failed","interrupted",""})check(!(Boolean)call("pendingWave",types,status,"durable-id",true,true,true),status+" is static");
  check(!(Boolean)call("pendingWave",types,"running","",true,true,true),"missing durable identity static");
  check(!(Boolean)call("pendingWave",types,"running","id",false,true,true),"background static");
  check(!(Boolean)call("pendingWave",types,"running","id",true,false,true),"offscreen static");
  check(!(Boolean)call("pendingWave",types,"running","id",true,true,false),"reduced motion static");
  float a=(Float)call("dotLevel",new Class<?>[]{long.class,int.class},0L,0),b=(Float)call("dotLevel",new Class<?>[]{long.class,int.class},0L,1);
  check(a!=b,"dots wave out of phase");
  for(int i=0;i<3;i++)for(long t=0;t<1800;t+=37){float v=(Float)call("dotLevel",new Class<?>[]{long.class,int.class},t,i);check(v>=.35f&&v<=1,"bounded dot displacement");}
  System.out.println("NativeMotionPolicyTest: "+checks+" checks passed");
 }
}

package com.simon.mytasks;
import java.lang.reflect.*;
public final class UiReadGateTest {
 static int checks;static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception{
  Class<?> type;try{type=Class.forName("com.simon.mytasks.UiReadGate");}catch(ClassNotFoundException e){throw new AssertionError("Missing lifetime-bound foreground read gate",e);}
  Object gate=type.getConstructor().newInstance();Method begin=type.getMethod("begin",String.class),current=type.getMethod("current",long.class,String.class),invalidate=type.getMethod("invalidate");
  long first=(Long)begin.invoke(gate,"epoch-a");ok((Boolean)current.invoke(gate,first,"epoch-a"),"current foreground read can apply");invalidate.invoke(gate);ok(!(Boolean)current.invoke(gate,first,"epoch-a"),"pause or a mutation fences an older read");
  long second=(Long)begin.invoke(gate,"epoch-a");ok(!(Boolean)current.invoke(gate,first,"epoch-a"),"older response cannot override a later read");ok((Boolean)current.invoke(gate,second,"epoch-a"),"new request is not cleared by old completion");ok(!(Boolean)current.invoke(gate,second,"epoch-b"),"replacement session cannot receive a previous session's data");
  System.out.println("UiReadGateTest: "+checks+" assertions passed");
 }
}

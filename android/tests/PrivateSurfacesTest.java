package com.simon.mytasks;
public final class PrivateSurfacesTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("private surface "+checks);}
 public static void main(String[] args){PrivateSurfaces p=new PrivateSurfaces();long before=p.generation();int[] dismissed={0};for(String name:new String[]{"Summary","chat","rename","task","source"})p.register(()->dismissed[0]++);p.invalidate();ok(dismissed[0]==5);ok(!p.current(before));ok(p.current(p.generation()));p.invalidate();ok(dismissed[0]==5);Runnable done=()->dismissed[0]++;p.register(done);p.forget(done);p.invalidate();ok(dismissed[0]==5);p.register(()->{throw new IllegalStateException();});p.register(()->dismissed[0]++);p.invalidate();ok(dismissed[0]==6);System.out.println("PrivateSurfacesTest: "+checks+" checks passed");}
}

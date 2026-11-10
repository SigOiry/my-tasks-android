package com.simon.mytasks;

import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.lang.reflect.*;

/** Thin JVM bridge to real production methods. No replacement score/hash/comparator. */
public final class GoldenAdapter {
  static Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
    try { return PresentationPolicy.class.getMethod(method, types).invoke(null, args); }
    catch (NoSuchMethodException error) { throw new IllegalStateException("Missing production policy interface: " + method, error); }
  }
  static String hex(int color) { return String.format(Locale.ROOT,"#%06X", color & 0xffffff); }
  static int score(JSONObject task, LocalDate today, ZoneId zone) throws Exception {
    return (Integer)invoke("smartScore", new Class<?>[]{JSONObject.class,LocalDate.class,ZoneId.class},task,today,zone);
  }
  static List<String> ordered(List<JSONObject> tasks, LocalDate today, ZoneId zone) throws Exception {
    Method compare;
    try { compare=PresentationPolicy.class.getMethod("compare",JSONObject.class,JSONObject.class,String.class,boolean.class,LocalDate.class,ZoneId.class); }
    catch(NoSuchMethodException error){throw new IllegalStateException("Missing production policy interface: compare(task,task,mode,reverse,today,zone)",error);}
    tasks.sort((a,b)->{try{return (Integer)compare.invoke(null,a,b,"Smart",false,today,zone);}catch(Exception e){throw new RuntimeException(e);}});
    List<String> ids=new ArrayList<>();for(JSONObject t:tasks)ids.add(t.getString("id"));return ids;
  }
  public static void main(String[] args) throws Exception {
    JSONObject vectors=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
    JSONObject contexts=vectors.getJSONObject("contexts"),days=new JSONObject(),palette=new JSONObject(),scores=new JSONObject(),orders=new JSONObject();
    for(String name:contexts.keySet()){
      JSONObject context=contexts.getJSONObject(name);
      days.put(name,Instant.parse(context.getString("now")).atZone(ZoneId.of(context.getString("timezone"))).toLocalDate().toString());
    }
    JSONArray entries=vectors.getJSONArray("palette");
    for(int i=0;i<entries.length();i++){
      JSONObject item=entries.getJSONObject(i),task=item.getJSONObject("task");
      int slot=PresentationPolicy.projectSlot(task);
      JSONObject neutral=vectors.getJSONObject("neutral");
      int lightNeutral=(int)(0xff000000L|Long.parseLong(neutral.getString("light").substring(1),16));
      int darkNeutral=(int)(0xff000000L|Long.parseLong(neutral.getString("dark").substring(1),16));
      palette.put(item.getString("name"),new JSONObject().put("slot",slot).put("light",hex(PresentationPolicy.taskColor(task,"Project",false,lightNeutral))).put("dark",hex(PresentationPolicy.taskColor(task,"Project",true,darkNeutral))));
    }
    entries=vectors.getJSONArray("scores");
    for(int i=0;i<entries.length();i++){
      JSONObject item=entries.getJSONObject(i),context=contexts.getJSONObject(item.getString("context"));
      LocalDate today=LocalDate.parse(days.getString(item.getString("context")));ZoneId zone=ZoneId.of(context.getString("timezone"));
      scores.put(item.getString("name"),score(item.getJSONObject("task"),today,zone));
    }
    entries=vectors.getJSONArray("orders");
    for(int i=0;i<entries.length();i++){
      JSONObject item=entries.getJSONObject(i),context=contexts.getJSONObject(item.getString("context"));
      LocalDate today=LocalDate.parse(days.getString(item.getString("context")));ZoneId zone=ZoneId.of(context.getString("timezone"));
      String view=item.getString("view");view=view.substring(0,1).toUpperCase(Locale.ROOT)+view.substring(1);
      JSONArray input=item.getJSONArray("tasks");String before=input.toString();List<JSONObject> eligible=new ArrayList<>();
      for(int j=0;j<input.length();j++){JSONObject task=input.getJSONObject(j);if(PresentationPolicy.matches(task,view,"","",today))eligible.add(task);}
      List<String> ids=ordered(new ArrayList<>(eligible),today,zone);
      Collections.reverse(eligible);if(!ids.equals(ordered(eligible,today,zone)))throw new AssertionError("Arrival-order dependent result: "+item.getString("name"));
      if(!before.equals(input.toString()))throw new AssertionError("Policy mutated canonical input: "+item.getString("name"));
      orders.put(item.getString("name"),new JSONArray(ids));
    }
    System.out.println(new JSONObject().put("contexts",days).put("palette",palette).put("scores",scores).put("orders",orders));
  }
}

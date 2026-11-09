package com.simon.mytasks;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import org.json.*;

/** Pure presentation policy. Never rewrites the canonical task model. */
public final class PresentationPolicy {
 private PresentationPolicy(){}
 public static String value(JSONObject task,String key){return task.isNull(key)?"":task.optString(key,"");}
 public static boolean terminal(JSONObject task){return "completed".equals(value(task,"status"))||"cancelled".equals(value(task,"status"));}
 public static String project(JSONObject task){String p=value(task,"project");return p.isEmpty()?"Unsorted":p;}
 public static List<String> projectOptions(JSONArray tasks,String view,LocalDate today){
  TreeSet<String> names=new TreeSet<>((a,b)->{int c=a.compareToIgnoreCase(b);return c!=0?c:a.compareTo(b);});
  // Enumerate the current view, independent of the current project/search so
  // switching options never strands the user in an artificially narrowed menu.
  for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&matches(t,view,"","",today))names.add(project(t));}
  return new ArrayList<>(names);
 }
 public static boolean matches(JSONObject task,String view,String project,String query,LocalDate today){
  if(task.optBoolean("deleted")!=view.equals("Trash"))return false;
  if(!view.equals("All")&&!view.equals("Trash")&&terminal(task))return false;
  LocalDate due=parseDate(value(task,"due_date"));
  if(view.equals("Today")&&(due==null||due.isAfter(today)))return false;
  LocalDate end=today.plusDays(7-today.getDayOfWeek().getValue());
  if(view.equals("Week")&&(due==null||due.isAfter(end)))return false;
  if(!project.isEmpty()&&!project.equals(project(task)))return false;
  return (value(task,"title")+" "+value(task,"description")+" "+project(task)).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim());
 }
 public static String ackFailure(int code){
  if(code==409)return "A newer update is waiting on the server. The displayed summary and your draft are kept; nothing newer was marked read. Refresh the summary, review it, then acknowledge that displayed version.";
  if(code==401)return "Your update was not acknowledged. Check the server token in Settings, then refresh. The summary is kept.";
  return "The update could not be acknowledged. The summary is kept. Refresh with a connection to check its current read state before trying again.";
 }
 public static int compare(JSONObject a,JSONObject b,String mode){
  int c;
  if(mode.equals("Due date")){c=dueOrder(a).compareTo(dueOrder(b));if(c!=0)return c;}
  if(mode.equals("Priority")){c=Integer.compare(Core.rank(value(a,"priority")),Core.rank(value(b,"priority")));if(c!=0)return c;}
  c=project(a).compareToIgnoreCase(project(b));if(c!=0)return c;
  c=Integer.compare(Core.rank(value(a,"priority")),Core.rank(value(b,"priority")));if(c!=0)return c;
  c=dueOrder(a).compareTo(dueOrder(b));return c!=0?c:value(a,"id").compareTo(value(b,"id"));
 }
 private static String dueOrder(JSONObject t){String d=value(t,"due_date");return parseDate(d)==null?"9999-99-99":d;}
 public static String sourceSummary(JSONObject task){
  String source=plain(value(task,"source"));StringBuilder out=new StringBuilder(source.isEmpty()?"No source details provided":source);
  JSONArray accounts=task.optJSONArray("source_accounts");if(accounts!=null)for(int i=0;i<Math.min(accounts.length(),6);i++){String account=plain(accounts.optString(i));if(!account.isEmpty())out.append(" · ").append(account);}
  JSONArray refs=task.optJSONArray("source_references");if(refs!=null&&refs.length()>0)out.append("\n").append(refs.length()).append(refs.length()==1?" linked source record":" linked source records");
  return out.toString();
 }
 private static String plain(String s){s=s.trim();if(s.startsWith("{")||s.startsWith("[")||s.length()>180)return "";return s.replaceAll("[\\p{Cntrl}\\p{Cf}]","").trim();}
 public static boolean safeSourceUrl(String value){
  try{
   if(!value.equals(value.trim())||value.matches(".*[\\p{Cntrl}\\p{Cf}].*")||value.toLowerCase(Locale.ROOT).matches(".*%(0[0-9a-f]|1[0-9a-f]|7f|5c).*"))return false;
   java.net.URI u=new java.net.URI(value);String host=u.getHost();
   if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
   host=host.toLowerCase(Locale.ROOT);return host.contains(".")&&!host.endsWith(".local")&&!host.endsWith(".localhost")&&!host.matches("[0-9.]+")&&!host.contains(":");
  }catch(Exception e){return false;}
 }
 public static List<String> sourceLinks(JSONObject task){
  LinkedHashSet<String> links=new LinkedHashSet<>();
  for(String key:new String[]{"source_url","url","source_reference"})collect(task.opt(key),links);
  for(String key:new String[]{"source_references","source_links","evidence"}){JSONArray a=task.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++)collect(a.opt(i),links);}
  return new ArrayList<>(links);
 }
 private static void collect(Object entry,Set<String> links){
  if(entry instanceof String&&safeSourceUrl((String)entry))links.add((String)entry);
  if(entry instanceof JSONObject)for(String key:new String[]{"url","source_url","link"}){Object value=((JSONObject)entry).opt(key);if(value instanceof String&&safeSourceUrl((String)value))links.add((String)value);}
 }
 public static LocalDate parseDate(String value){try{return value.matches("\\d{4}-\\d{2}-\\d{2}")?LocalDate.parse(value):null;}catch(Exception e){return null;}}
 public static String dueLabel(String value,LocalDate today,Locale locale){
  if(value==null||value.isEmpty())return "No due date";
  LocalDate date=parseDate(value);if(date==null)return "Date unavailable";
  if(date.equals(today))return "Due today";if(date.equals(today.plusDays(1)))return "Due tomorrow";
  return (date.isBefore(today)?"Overdue · ":"")+date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
 }
}

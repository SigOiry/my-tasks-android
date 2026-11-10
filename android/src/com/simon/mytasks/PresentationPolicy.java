package com.simon.mytasks;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import org.json.*;

/** Pure presentation policy. Never rewrites the canonical task model. */
public final class PresentationPolicy {
 private PresentationPolicy(){}
 private static final int[] PROJECT_LIGHT={0xff527fc2,0xff3c8b83,0xff8972bb,0xffb26d87,0xffa57a35,0xff6c7e91};
 private static final int[] PROJECT_DARK={0xff8fb2e8,0xff82c8ba,0xffbba7e5,0xffdfa2b9,0xffd4b26f,0xffa6b7c9};
 /** UTF-8 FNV-1a unsigned32, exact canonical identity (never casefold/trim). */
 public static int projectSlot(JSONObject task){
  String identity=value(task,"project_id");if(identity.isEmpty())identity=value(task,"project");if(identity.isEmpty())return -1;
  int hash=0x811c9dc5;for(byte b:identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)){hash^=b&255;hash*=0x01000193;}
  return (int)(Integer.toUnsignedLong(hash)%6);
 }
 public static int unreadColor(boolean dark){return dark?0xff66aaff:0xff005bc8;}
 public static int importanceColor(String priority,boolean dark){return "high".equals(priority)?(dark?0xffe7a296:0xffb55746):"medium".equals(priority)?(dark?0xffd4b26f:0xff936b2b):(dark?0xff82c8ba:0xff367d75);}
 public static int taskColor(JSONObject task,String mode,boolean dark,int neutral){
  // Importance changes intensity, never the canonical project's hue.
  int slot=projectSlot(task);return !"Neutral".equals(mode)&&slot>=0?(dark?PROJECT_DARK:PROJECT_LIGHT)[slot]:neutral;
 }
 /** Opaque stable reading surface: priority strength within the same project hue. */
 public static int rowTint(JSONObject task,String mode,boolean dark,int surface){
  if("Neutral".equals(mode))return surface;
  String priority=value(task,"priority");
  float amount=("high".equals(priority)?(dark?20:14):"medium".equals(priority)?(dark?12:8):(dark?6:4))/100f;
  int hue=taskColor(task,mode,dark,dark?PROJECT_DARK[5]:PROJECT_LIGHT[5]);
  int result=0xff000000;
  for(int shift:new int[]{16,8,0})result|=Math.round(((surface>>shift)&255)*(1-amount)+((hue>>shift)&255)*amount)<<shift;
  return result;
 }
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
 public static String directionLabel(String mode,boolean reverse){
  if("Project".equals(mode))return reverse?"Z–A":"A–Z";
  if("Importance".equals(mode)||"Priority".equals(mode))return reverse?"Low–High":"High–Low";
  if("Due date".equals(mode))return reverse?"Latest":"Soonest";
  if("Recently updated".equals(mode))return reverse?"Oldest":"Newest";
  if("Created date".equals(mode))return reverse?"Newest":"Oldest";
  return reverse?"Lowest ranked":"Highest ranked";
 }
 public static String savedSort(String saved){return saved==null?"Smart":saved;}
 public static String[] sortModes(){return new String[]{"Smart","Project","Importance","Due date","Recently updated","Created date"};}
 /** Fixed presentation lanes in every mode, also preserving status order in Trash. */
 public static int smartLane(JSONObject t){return (t.optBoolean("deleted")?3:0)+(terminal(t)?2:"waiting".equals(value(t,"status"))?1:0);}
 public static int smartScore(JSONObject t,LocalDate today,java.time.ZoneId zone){
  LocalDate due=parseDate(value(t,"due_date"));long days=due==null?Long.MAX_VALUE:java.time.temporal.ChronoUnit.DAYS.between(today,due);
  int score=days<0?50:days==0?40:days==1?25:days<=7?10:0;
  score+="high".equals(value(t,"priority"))?30:"medium".equals(value(t,"priority"))?15:0;
  if("in_progress".equals(value(t,"status")))score+=6;
  JSONObject notification=t.optJSONObject("notification");if(notification!=null&&Boolean.TRUE.equals(notification.opt("unread")))score+=6;
  java.time.Instant created=timestamp(t,"created_at");if(created!=null)score+=(int)Math.min(4,Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(created.atZone(zone).toLocalDate(),today)/7));
  return score;
 }
 public static String whySmart(){return "Actionable tasks first. Deadline: overdue +50, today +40, tomorrow +25, next 7 days +10. Importance: high +30, medium +15. In progress +6, unread update +6, age +1 per whole week (max 4). Ties: earliest due, higher importance, oldest creation, task ID. Waiting and inactive tasks stay below actionable work.";}
 public static int compare(JSONObject a,JSONObject b,String mode){return compare(a,b,mode,false,LocalDate.now(),java.time.ZoneId.systemDefault());}
 public static int compare(JSONObject a,JSONObject b,String mode,boolean reverse,LocalDate today,java.time.ZoneId zone){
  int c=Integer.compare(smartLane(a),smartLane(b));if(c!=0)return c;
  int direction=reverse?-1:1;
  if("Smart".equals(mode)){
   c=Integer.compare(smartScore(b,today,zone),smartScore(a,today,zone));
  }else if("Project".equals(mode)){c=project(a).compareToIgnoreCase(project(b));if(c==0)c=project(a).compareTo(project(b));}
  else if("Priority".equals(mode)||"Importance".equals(mode))c=Integer.compare(Core.rank(value(a,"priority")),Core.rank(value(b,"priority")));
  else if("Due date".equals(mode)){c=optionalDate(a,b,reverse);if(c!=0)return c;}
  else if("Recently updated".equals(mode)||"Created date".equals(mode)){
   String key="Recently updated".equals(mode)?"updated_at":"created_at";java.time.Instant x=timestamp(a,key),y=timestamp(b,key);
   if(x==null||y==null){if(x!=y)return x==null?1:-1;}else c=x.compareTo(y)*("Recently updated".equals(mode)?-1:1);
  }
  if(c!=0)return c*direction;
  c=optionalDate(a,b,false);if(c!=0)return c;
  c=Integer.compare(Core.rank(value(a,"priority")),Core.rank(value(b,"priority")));if(c!=0)return c;
  java.time.Instant x=timestamp(a,"created_at"),y=timestamp(b,"created_at");if(x==null||y==null){if(x!=y)return x==null?1:-1;}else {c=x.compareTo(y);if(c!=0)return c;}
  return value(a,"id").compareTo(value(b,"id"));
 }
 private static int optionalDate(JSONObject a,JSONObject b,boolean reverse){LocalDate x=parseDate(value(a,"due_date")),y=parseDate(value(b,"due_date"));if(x==null||y==null)return x==y?0:x==null?1:-1;return x.compareTo(y)*(reverse?-1:1);}
 private static java.time.Instant timestamp(JSONObject t,String key){try{return java.time.OffsetDateTime.parse(value(t,key)).toInstant();}catch(Exception ignored){return null;}}
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

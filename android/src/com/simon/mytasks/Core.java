package com.simon.mytasks;
import java.util.*;
public final class Core {
 public static String server(String s){
  try{
   java.net.URI u=new java.net.URI(s.trim());
   if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getRawPath().isEmpty()||u.getRawPath().equals("/"))||u.getPort()==0||u.getPort()>65535)throw new IllegalArgumentException();
   String value=u.toASCIIString();return value.endsWith("/")?value.substring(0,value.length()-1):value;
  }catch(Exception e){throw new IllegalArgumentException("Use an HTTPS origin only, e.g. https://tasks.example (no path, credentials, query or fragment).");}
 }
 public static String token(String value){
  if(value==null)throw new IllegalArgumentException("Enter the server token.");
  int first=0,last=value.length();
  while(first<last&&tokenPadding(value.charAt(first)))first++;
  while(last>first&&tokenPadding(value.charAt(last-1)))last--;
  String token=value.substring(first,last);
  if(token.length()<32)throw new IllegalArgumentException("Paste the complete server token, without a Bearer prefix or other text.");
  for(int i=0;i<token.length();i++)if(token.charAt(i)<33||token.charAt(i)>126)throw new IllegalArgumentException("Paste only the server token. Spaces, line breaks or invisible characters inside it are not valid.");
  return token;
 }
 private static boolean tokenPadding(char c){return c==' '||c=='\t'||c=='\r'||c=='\n';}
 public static String credentialSaveFailure(Exception error){
  String code=error==null?"UnknownError":error.getClass().getSimpleName();
  return "Could not save credentials locally ("+code+"). No token was sent to the server. If this persists, report this diagnostic code.";
 }
 public static final class SortKey {public final String id,project,priority,due; public SortKey(String i,String p,String r,String d){id=i;project=p;priority=r;due=d;}}
 public static String path(String id){try{return java.net.URLEncoder.encode(id,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){throw new IllegalArgumentException("Invalid task ID");}}
 public static int rank(String p){return "high".equals(p)?0:"medium".equals(p)?1:2;}
 public static final Comparator<SortKey> ORDER=(a,b)->{
  int c=a.project.compareToIgnoreCase(b.project);if(c!=0)return c;
  c=Integer.compare(rank(a.priority),rank(b.priority));if(c!=0)return c;
  c=(a.due.isEmpty()?"9999-99-99":a.due).compareTo(b.due.isEmpty()?"9999-99-99":b.due);return c!=0?c:a.id.compareTo(b.id);
 };
 public static long advance(long cursor,long id,boolean handled){return handled?Math.max(cursor,id):cursor;}
 /** Build the exact wire body; evict whole oldest turns, never alter the current message. */
 public static String chatBody(String taskId,String message,String mode,List<String[]> history){
  int length=message.codePointCount(0,message.length());
  if(length<1||length>8000)throw new IllegalArgumentException("Message must contain 1 to 8,000 characters. It has not been shortened.");
  if(taskId.isEmpty()||taskId.codePointCount(0,taskId.length())>256)throw new IllegalArgumentException("Invalid task ID.");
  if(!"question".equals(mode)&&!"email_draft".equals(mode))throw new IllegalArgumentException("Invalid chat mode.");
  List<String[]> recent=new ArrayList<>();
  for(int i=Math.max(0,history.size()-12);i<history.size();i++){
   String[] turn=history.get(i);int n=turn[1].codePointCount(0,turn[1].length());
   // Oversized provider replies are omitted as whole turns, not silently rewritten.
   if(n>0&&n<=8000&&("user".equals(turn[0])||"assistant".equals(turn[0])))recent.add(turn);
  }
  for(;;){
   String json=chatJson(taskId,message,mode,recent);
   if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=60000)return json;
   if(recent.isEmpty())throw new IllegalArgumentException("Message body is too large. Shorten your message before sending.");
   recent.remove(0);
  }
 }
 private static String chatJson(String id,String message,String mode,List<String[]> turns){
  StringBuilder b=new StringBuilder("{\"task_id\":").append(quote(id)).append(",\"message\":").append(quote(message)).append(",\"mode\":").append(quote(mode)).append(",\"history\":[");
  for(int i=0;i<turns.size();i++){if(i>0)b.append(',');String[] t=turns.get(i);b.append("{\"role\":").append(quote(t[0])).append(",\"content\":").append(quote(t[1])).append('}');}
  return b.append("]}").toString();
 }
 private static String quote(String value){
  StringBuilder b=new StringBuilder("\"");
  for(int i=0;i<value.length();i++){
   char c=value.charAt(i);
   if(c=='"'||c=='\\')b.append('\\').append(c);
   else if(c=='\n')b.append("\\n");
   else if(c=='\r')b.append("\\r");
   else if(c=='\t')b.append("\\t");
   else if(c<32)b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
   else b.append(c);
  }
  return b.append('"').toString();
 }
 public static String chatFailure(int code){
  if(code==413)return "Request too large (HTTP 413). Your message is kept; shorten it or start a new conversation before sending.";
  if(code==422)return "The server rejected this chat input (HTTP 422). Your message is kept; check its length and mode before sending.";
  if(code==401)return "Authentication failed. Your message is kept; check your token in Settings.";
  return "Chat did not complete. The original provider request may still be running; it was not resent automatically. Your message is kept. Wait before deciding whether to send again; another send may duplicate provider work.";
 }
 public static boolean eventUnread(Boolean tracking,boolean unread,boolean deleted,String kind){
  return !deleted&&(unread||(Boolean.FALSE.equals(tracking)&&("created".equals(kind)||"updated".equals(kind)||"restored".equals(kind))));
 }
 public static boolean shouldNotify(boolean bootstrapped,boolean allowed,boolean unread){return bootstrapped&&allowed&&unread;}
}

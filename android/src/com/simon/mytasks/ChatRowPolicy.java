package com.simon.mytasks;
import org.json.*;
/** Row invalidation only. Never owns requests, drafts or durable identity. */
final class ChatRowPolicy {
 static String binding(JSONObject previous,JSONObject turn,JSONObject next){return new JSONArray().put(edge(previous)).put(turn.optLong("id")).put(turn.optString("role")).put(turn.optString("content")).put(turn.optString("created_at")).put(turn.optJSONObject("citation_metadata")).put(edge(next)).toString();}
 static String edge(JSONObject turn){return turn==null?"":turn.optString("role")+":"+turn.optString("created_at");}
 static boolean animate(boolean local,boolean nearEnd,boolean initial,boolean older,boolean motion){return local&&nearEnd&&!initial&&!older&&motion;}
 static boolean animateIncoming(boolean nearEnd,boolean initial,boolean older,boolean motion){return nearEnd&&!initial&&!older&&motion;}
 static boolean admitting(String status,boolean operation){return "prepared".equals(status)&&operation;}
 static String pendingLabel(JSONObject pending,boolean connected){
  String status=pending.optString("status");
  if(!connected)return "Last known: "+("queued".equals(status)?"queued":"reply in progress")+" · connect to check";
  if("queued".equals(status))return "Hermès · queued";
  JSONObject request=pending.optJSONObject("request");String phase=request==null?"":request.optString("web_phase");
  if("running".equals(status)&&"searching".equals(phase))return "Searching public web…";
  if("running".equals(status)&&"reading".equals(phase))return "Reading public sources…";
  return "Hermès · reply in progress";
 }
 /** Citation actions are separate from inert Markdown/update-summary rendering. */
 static java.util.List<String> sourceLinks(String prose){
  java.util.LinkedHashSet<String> links=new java.util.LinkedHashSet<>();
  java.util.regex.Matcher found=java.util.regex.Pattern.compile("https://[^\\s<>\\\"\\[\\]]+").matcher(prose);
  while(found.find()&&links.size()<12){String url=found.group();
   while(!url.isEmpty()&&".,;".indexOf(url.charAt(url.length()-1))>=0)url=url.substring(0,url.length()-1);
   int opens=0,closes=0;for(int i=0;i<url.length();i++){if(url.charAt(i)=='(')opens++;if(url.charAt(i)==')')closes++;}
   while(closes>opens&&url.endsWith(")")){url=url.substring(0,url.length()-1);closes--;}
   if(PresentationPolicy.safeSourceUrl(url)&&!java.net.URI.create(url).getHost().matches("(?i).*(\\.internal|\\.lan|\\.home|\\.test|\\.invalid)$"))links.add(url);
  }
  return new java.util.ArrayList<>(links);
 }
 static int masterWidth(int availableDp){return Math.max(280,Math.min(340,availableDp/3));}
 private ChatRowPolicy(){}
}

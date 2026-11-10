package com.simon.mytasks;

import org.json.*;
import java.time.*;

/** Pure presentation decisions only: never mutates admission or routing state. */
final class ChatPresentationPolicy {
 static boolean grouped(JSONObject previous,JSONObject next,ZoneId zone){
  if(previous==null||next==null||!previous.optString("role").equals(next.optString("role")))return false;
  Instant left=instant(previous.optString("created_at")),right=instant(next.optString("created_at"));
  if(left==null||right==null)return false;
  Duration elapsed=Duration.between(left,right);
  return !elapsed.isNegative()&&elapsed.compareTo(Duration.ofMinutes(5))<=0&&left.atZone(zone).toLocalDate().equals(right.atZone(zone).toLocalDate());
 }
 static Instant instant(String value){try{return OffsetDateTime.parse(value).toInstant();}catch(Exception ignored){return null;}}
 static boolean nearBottom(int contentHeight,int y,int viewportHeight,int threshold){return (long)y+viewportHeight>=contentHeight-Math.max(0,threshold);}
 static int restoreY(int anchorTop,int relativeOffset,int fallback){return Math.max(0,anchorTop<0?fallback:anchorTop-relativeOffset);}
 static String messageSignature(JSONArray messages){
  JSONArray values=new JSONArray();for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null)values.put(new JSONArray().put(m.optLong("id")).put(m.optString("role")).put(m.optString("content")).put(m.optString("created_at")).put(m.optJSONObject("citation_metadata")));}return values.toString();
 }
 static int countAfter(JSONArray messages,long seen){
  java.util.HashSet<Long> ids=new java.util.HashSet<>();for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&m.optLong("id")>seen)ids.add(m.optLong("id"));}return ids.size();
 }
 static boolean containsOwnSend(JSONArray messages,String request){
  if(request.isEmpty())return false;for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&m.optString("role").equals("user")&&m.optString("request_id").equals(request))return true;}return false;
 }
 private ChatPresentationPolicy(){}
 static boolean initialFollow(boolean hasSaved,boolean savedAtBottom,boolean ownSend){return ownSend||!hasSaved||savedAtBottom;}
 static boolean shouldFollow(boolean nearBottom,boolean ownSend,boolean loadingOlder){
  return !loadingOlder&&(nearBottom||ownSend);
 }
}

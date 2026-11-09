package com.simon.mytasks;
import org.json.*;
/** Row invalidation only. Never owns requests, drafts or durable identity. */
final class ChatRowPolicy {
 static String binding(JSONObject previous,JSONObject turn,JSONObject next){return new JSONArray().put(edge(previous)).put(turn.optLong("id")).put(turn.optString("role")).put(turn.optString("content")).put(turn.optString("created_at")).put(edge(next)).toString();}
 static String edge(JSONObject turn){return turn==null?"":turn.optString("role")+":"+turn.optString("created_at");}
 static boolean animate(boolean local,boolean nearEnd,boolean initial,boolean older,boolean motion){return local&&nearEnd&&!initial&&!older&&motion;}
 static int masterWidth(int availableDp){return Math.max(280,Math.min(340,availableDp/3));}
 private ChatRowPolicy(){}
}

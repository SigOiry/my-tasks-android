package com.simon.mytasks;
import org.json.*;
public class CitationTitleTest {
 public static void main(String[] args)throws Exception{
  ConversationState s=new ConversationState();JSONObject newer=new JSONObject().put("id","c").put("revision",8).put("title_revision",3).put("title","Manual").put("title_origin","manual");s.page("c",new JSONObject().put("conversation",newer),false);
  s.page("c",new JSONObject().put("conversation",new JSONObject().put("id","c").put("revision",7).put("title_revision",2).put("title","Old")),true);if(!s.conversation("c").optString("title").equals("Manual"))throw new AssertionError("older history overwrote title");
  String original="Saved citeturn9search0 and https://example.org/a_(b).";String rendered=CitationPolicy.display(original);if(rendered.contains("turn9search0")||!original.contains("turn9search0"))throw new AssertionError("opaque marker projection");
  JSONArray sources=CitationPolicy.sources(new JSONObject().put("content",original));if(sources.length()!=1||!sources.getJSONObject(0).optString("url").equals("https://example.org/a_(b)"))throw new AssertionError(sources.toString());
  JSONObject plain=new JSONObject().put("id",1).put("content","answer"),cited=new JSONObject(plain.toString()).put("citation_metadata",new JSONObject().put("version",1).put("sources",new JSONArray().put(new JSONObject().put("id","s"))));if(ChatPresentationPolicy.messageSignature(new JSONArray().put(plain)).equals(ChatPresentationPolicy.messageSignature(new JSONArray().put(cited))))throw new AssertionError("additive citations must invalidate visible rows");if(ChatRowPolicy.binding(null,plain,null).equals(ChatRowPolicy.binding(null,cited,null)))throw new AssertionError("citation chip binding stale");
  ConversationState turns=new ConversationState();turns.page("x",new JSONObject().put("messages",new JSONArray().put(cited)),false);turns.page("x",new JSONObject().put("messages",new JSONArray().put(plain)),true);if(turns.messages("x").getJSONObject(0).optJSONObject("citation_metadata")==null)throw new AssertionError("old reply erased canonical citation metadata");
  if(CitationPolicy.display("Unfinished citeturn123search4").contains("turn123search4"))throw new AssertionError("truncated provider marker escaped into rendered copy");
  if(!CitationPolicy.display(original).contains("[source unavailable]"))throw new AssertionError("unmapped marker pretends resolved");
  for(String url:new String[]{"http://example.org","https://localhost","https://127.0.0.1","https://[::1]","https://10.0.0.1","https://u@example.org","https://example.org:8443","https://foo.internal","https://foo.lan","https://foo.home","https://foo.test","https://foo.invalid","https://foo.local","https://foo.localhost","https://example.org./","https://2130706433","https://0x7f.0.0.1"})if(CitationPolicy.safe(url))throw new AssertionError("unsafe source: "+url);
  if(!CitationPolicy.safe("https://example.org/a?q=1#context")||!CitationPolicy.safe("https://www.nasa.gov:443/"))throw new AssertionError("public HTTPS rejected");
  System.out.println("CitationTitleTest passed");
 }
}

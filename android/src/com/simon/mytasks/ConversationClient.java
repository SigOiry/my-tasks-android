package com.simon.mytasks;
import org.json.*;
import java.net.URLEncoder;

/** Exact v1.3 API. No client history, tool/model selection or write retries. */
final class ConversationClient {
 interface Transport {JSONObject call(String method,String path,JSONObject body)throws Exception;}
 private final Transport transport;
 ConversationClient(Transport t){transport=t;}
 static String encode(String value){try{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException("Invalid identifier",e);}}
 private String route(String id){return "/api/conversations/"+encode(id);}
 JSONObject list(String scope,String task,boolean archived,String cursor)throws Exception{
  String path="/api/conversations?scope="+encode(scope)+"&limit=30&archived="+archived;
  if(scope.equals("task"))path+="&task_id="+encode(task);if(cursor!=null&&!cursor.isEmpty())path+="&cursor="+encode(cursor);return transport.call("GET",path,null);
 }
 JSONObject create(String task,String title)throws Exception{JSONObject b=new JSONObject().put("task_id",task==null?JSONObject.NULL:task);if(title!=null)b.put("title",title);return transport.call("POST","/api/conversations",b);}
 JSONObject get(String id,String before)throws Exception{return transport.call("GET",route(id)+"?limit=40"+(before==null||before.isEmpty()?"":"&before="+encode(before)),null);}
 JSONObject patch(String id,int revision,String title,Boolean archived)throws Exception{JSONObject b=new JSONObject().put("expected_revision",revision);if(title!=null)b.put("title",title);if(archived!=null)b.put("archived",archived);return transport.call("PATCH",route(id),b);}
 JSONObject send(String id,JSONObject body)throws Exception{return transport.call("POST",route(id)+"/messages",body);}
 JSONObject request(String id,String request)throws Exception{return transport.call("GET",route(id)+"/requests/"+encode(request),null);}
}

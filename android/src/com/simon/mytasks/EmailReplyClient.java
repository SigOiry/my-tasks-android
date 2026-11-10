package com.simon.mytasks;
import org.json.*;
final class EmailReplyClient {
 interface Transport{JSONObject call(String method,String path,JSONObject body)throws Exception;}
 final Transport t;EmailReplyClient(Transport t){this.t=t;}
 static String e(String s){return ConversationClient.encode(s);}String route(String id){return "/api/email/drafts/"+e(id);}
 JSONObject sources(String task,String token)throws Exception{return t.call("GET","/api/tasks/"+e(task)+"/email-sources"+(token==null?"":"?update_token="+e(token)),null);}
 JSONObject detail(String task,String source)throws Exception{return t.call("GET","/api/tasks/"+e(task)+"/email-sources/"+e(source),null);}
 JSONObject create(String request,String task,String source,String token)throws Exception{return t.call("POST","/api/email/drafts",new JSONObject().put("request_id",request).put("task_id",task).put("source_id",source).put("update_token",token==null?JSONObject.NULL:token).put("reply_all",false));}
 JSONObject get(String id)throws Exception{return t.call("GET",route(id),null);}
 JSONObject save(String id,JSONObject body)throws Exception{return t.call("PATCH",route(id),body);}
 JSONObject operation(String id,String action,JSONObject body)throws Exception{return t.call("POST",route(id)+"/"+action,body);}
 JSONObject job(String id,String request)throws Exception{return t.call("GET",route(id)+"/assist/"+e(request),null);}
 JSONObject discard(String id,int revision)throws Exception{return t.call("DELETE",route(id),new JSONObject().put("expected_revision",revision));}
}

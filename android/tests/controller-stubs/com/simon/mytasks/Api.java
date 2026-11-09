package com.simon.mytasks;
import java.util.*;
import org.json.*;
final class Api {
 static final QueueExecutor IO=new QueueExecutor();static ConversationClient.Transport transport;
 Api(Store.Account account){}
 JSONObject call(String method,String path,JSONObject body)throws Exception{return transport.call(method,path,body);}
 static String friendly(Exception e){return "Connection failed; read only.";}
 static final class Failure extends Exception {final int code;Failure(int c){code=c;}}
 static final class QueueExecutor {final ArrayDeque<Runnable> q=new ArrayDeque<>();void execute(Runnable r){q.add(r);}boolean hasWork(){return !q.isEmpty();}void drainOne(){if(!q.isEmpty())q.remove().run();}}
}

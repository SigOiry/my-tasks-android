package com.simon.mytasks;
import org.json.*;import java.util.*;
/** Executes actual production history binding against a minimal render sink. */
public class EmailAssistantHistoryHarness {
 static class View {static final int FOCUS_DOWN=130;}
 static class TextView {String value;TextView(String v){value=v;}void setTextIsSelectable(boolean v){}}
 static class LinearLayout {List<String> labels=new ArrayList<>();void removeAllViews(){labels.clear();}int getHeight(){return 600;}}
 static class ScrollView {int getScrollY(){return 0;}int getHeight(){return 300;}LinearLayout getChildAt(int i){return new LinearLayout();}void post(Runnable r){r.run();}void scrollTo(int x,int y){}void fullScroll(int v){}}
 static class Activity {int muted=0,ink=1;int dp(int v){return v;}TextView label(String v,int size,int color){return new TextView(v);}TextView heading(String v,int size){return new TextView(v);}void add(LinearLayout p,TextView t){p.labels.add(t.value);}}
 static class Controller {EmailReplyState state=new EmailReplyState(new JSONObject());boolean busy;}
 Activity a=new Activity();Controller c=new Controller();LinearLayout history=new LinearLayout();ScrollView assistantScroll=new ScrollView();String historySignature="";boolean assistantFollow;int proposalCount;
 void proposal(LinearLayout p,JSONObject value){proposalCount++;}
 // ACTUAL_METHODS
 public static void main(String[] args)throws Exception{
  EmailAssistantHistoryHarness ui=new EmailAssistantHistoryHarness();
  JSONObject p=new JSONObject().put("id","proposal-1").put("body","New writing").put("status","pending");
  JSONObject message=new JSONObject().put("id","message-1").put("role","assistant").put("content","One assistant result").put("proposal",p);
  JSONObject job=new JSONObject().put("id","request-1").put("status","succeeded").put("message",message).put("proposal",p);
  ui.c.state.data.put("draft",new JSONObject().put("id","draft-1").put("status","draft").put("revision",2).put("body","Original writing").put("ai_messages",new JSONArray().put(message)).put("latest_ai_request",job));ui.c.state.data.put("job",job);
  ui.refreshHistory();long count=ui.history.labels.stream().filter("One assistant result"::equals).count();
  if(count!=1||ui.proposalCount!=1)throw new AssertionError("one saved message/proposal must render once, got messages="+count+" proposals="+ui.proposalCount);
  System.out.println("EmailAssistantHistoryHarness passed: actual refreshHistory deduplicates saved job results");
 }
}

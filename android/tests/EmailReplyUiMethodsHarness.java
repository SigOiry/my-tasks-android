package com.simon.mytasks;
import org.json.*;
/** The runner injects production capture/recipients methods, not copies. */
public class EmailReplyUiMethodsHarness {
 boolean binding;String boundDraft="";static class Controller{EmailReplyState state=new EmailReplyState(new JSONObject());}Controller c=new Controller();Input body,instruction;Scroll editorScroll,assistantScroll;JSONObject data=new JSONObject();
 static class Input{String text;int start,end,y;boolean focus;Input(String t,int a,int b,int y,boolean f){text=t;start=a;end=b;this.y=y;focus=f;}int getSelectionStart(){return start;}int getSelectionEnd(){return end;}int getScrollY(){return y;}boolean hasFocus(){return focus;}String getText(){return text;}}
 static class Scroll{int y;Scroll(int y){this.y=y;}int getScrollY(){return y;}}
 void put(String k,Object v){ConversationState.put(data,k,v);}
 // ACTUAL_METHODS
 public static void main(String[] args)throws Exception{EmailReplyUiMethodsHarness ui=new EmailReplyUiMethodsHarness();ui.body=new Input("Body",1,3,17,true);ui.instruction=new Input("Instructions",2,6,0,false);ui.editorScroll=new Scroll(71);ui.assistantScroll=new Scroll(92);ui.put("preview",new JSONObject().put("confirmation_id","human-reviewed"));ui.capture();if(ui.data.getInt("selection_start")!=1||ui.data.getInt("selection_end")!=3||ui.data.getInt("instruction_end")!=6||ui.data.getInt("assistant_scroll")!=92||!ui.data.has("preview"))throw new AssertionError("capture lost selection, scroll, instructions or reviewed preview");if(!recipients(new JSONObject().put("to",new JSONArray().put("one@example.test").put("two@example.test")),"to").equals("one@example.test, two@example.test"))throw new AssertionError("exact server recipients");ui.boundDraft="outgoing";ui.c.state.data.put("draft",new JSONObject().put("id","incoming"));ui.data.put("instruction","Restored incoming instruction");ui.capture();if(!ui.data.getString("instruction").equals("Restored incoming instruction"))throw new AssertionError("outgoing widgets overwrote restored incoming draft instructions");System.out.println("EmailReplyUiMethodsHarness passed: actual capture + review recipients + draft-bound capture");}
}

package com.simon.mytasks;
import org.json.*;import java.util.*;
public class CitationSourceUiHarness {
 static class View {static final int ACCESSIBILITY_LIVE_REGION_POLITE=1;List<View> children=new ArrayList<>();String text="";void addView(View v){children.add(v);}void addView(View v,Object lp){children.add(v);}}
 static class LinearLayout extends View {static class LayoutParams {LayoutParams(int a,int b){}}}
 static class ScrollView extends View {ScrollView(MainActivity a){}}
 static class TextView extends View {void setAccessibilityLiveRegion(int v){}static final int ACCESSIBILITY_LIVE_REGION_POLITE=1;}
 static class Button extends TextView {Runnable click;void setEnabled(boolean b){}void setContentDescription(String s){}}
 static class MainActivity {int muted;MainActivity ui=this;LinearLayout vertical(){return new LinearLayout();}Button button(String s,Runnable r){Button b=new Button();b.text=s;b.click=r;return b;}TextView label(String s,int n,int color){TextView v=new TextView();v.text=s;return v;}TextView heading(String s,int n){return label(s,n,0);}void quiet(Button b){}void add(View p,View v){p.addView(v);}}
 static class Controller {EmailReplyState state=new EmailReplyState(new JSONObject());boolean busy;int chosen;void choose(String id){chosen++;}void open(String s,String t){}void reload(){}}
 MainActivity a=new MainActivity();Controller c=new Controller();LinearLayout content=new LinearLayout();void capture(){}void render(){}static void panel(MainActivity a,JSONObject s){}
 // ACTUAL_METHODS
 static String text(View v){String s=v.text;for(View child:v.children)s+="\n"+text(child);return s;}
 public static void main(String[] args)throws Exception {
 CitationSourceUiHarness h=new CitationSourceUiHarness();JSONObject message=new JSONObject().put("content","untouched").put("citation_metadata",new JSONObject().put("version",1).put("sources",new JSONArray()).put("warnings",new JSONArray().put("Source lookup incomplete")));String before=message.toString();chips(h.a,h.content,message);
 if(!text(h.content).contains("Source lookup incomplete"))throw new AssertionError("warning must render even without sources");if(!before.equals(message.toString()))throw new AssertionError("original metadata mutated");
 JSONObject s=new JSONObject().put("subject","Same").put("sender","Same").put("date","Same").put("available",true).put("replyable",true).put("excerpt","First unique context");JSONObject t=new JSONObject(s.toString()).put("excerpt","Second unique context");h.content=new LinearLayout();h.sourceChooser(new JSONObject().put("ambiguous",true).put("sources",new JSONArray().put(s).put(t)));
 String text=text(h.content);if(!text.contains("First unique context")||!text.contains("Second unique context"))throw new AssertionError("chooser must distinguish identical headers using excerpt");if(h.c.chosen!=0)throw new AssertionError("selection inferred");
 h.content=new LinearLayout();message.getJSONObject("citation_metadata").getJSONArray("sources").put(new JSONObject().put("label","Retained chip"));chips(h.a,h.content,message);if(!text(h.content).contains("[1] Retained chip"))throw new AssertionError("source chip lost");
 t.put("excerpt",new String(new char[1000]).replace('\0','z')+"HIDDEN_TAIL");h.content=new LinearLayout();h.sourceChooser(new JSONObject().put("sources",new JSONArray().put(t)));if(text(h.content).contains("HIDDEN_TAIL"))throw new AssertionError("excerpt unbounded");
 h.c.state.selectTask("first");h.c.state.accept(new JSONObject().put("id","receipt").put("status","sent").put("body","sent"));h.c.state.selectTask("second");h.content=new LinearLayout();h.sourceChooser(new JSONObject());if(text(h.content).contains("View saved delivery receipt"))throw new AssertionError("another task receipt action leaked");
 System.out.println("CitationSourceUiHarness passed: actual chips + sourceChooser");
 }
}

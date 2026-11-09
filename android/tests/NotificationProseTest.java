package com.simon.mytasks;
import org.json.*;
public final class NotificationProseTest {
 static int count;static void ok(boolean b){count++;if(!b)throw new AssertionError("prose check "+count);}
 public static void main(String[] args)throws Exception{
  JSONObject n=new JSONObject().put("summary",new JSONArray().put("OLD DIFF")).put("sections",new JSONObject().put("context","Earlier **context**").put("update","A *new* paragraph.\n\n- One\n- `two`").put("next_steps",""));
  String prose=NotificationProse.markdown(n);ok(prose.startsWith("**Context**\n\nEarlier"));ok(prose.contains("**New**"));ok(!prose.contains("OLD DIFF"));ok(!prose.contains("Suggested"));
  n.getJSONObject("sections").put("next_steps","Only real advice");ok(NotificationProse.markdown(n).contains("**Suggested next steps**"));
  n.getJSONObject("sections").put("context","");ok(!NotificationProse.markdown(n).contains("**Context**"));
  ok(NotificationProse.markdown(new JSONObject().put("summary",new JSONArray().put("Legacy prose"))).equals("Legacy prose"));
  ok(NotificationProse.markdown(new JSONObject().put("sections",new JSONObject())).isEmpty());
  SafeMarkdown.Document doc=SafeMarkdown.parse("**Bold** and *italic* with `code`\n\n- item\n1. next\n[label](https://secret.invalid) ![image](https://secret.invalid/x) <script>alert(1)</script>");
  ok(doc.text.contains("Bold and italic with code"));ok(doc.text.contains("• item"));ok(doc.text.contains("label"));ok(!doc.text.contains("https://secret"));ok(!doc.text.contains("image"));ok(!doc.text.contains("<script>"));
  ok(doc.styles.size()==3);ok(doc.styles.get(0).kind.equals("bold"));ok(doc.styles.get(1).kind.equals("italic"));ok(doc.styles.get(2).kind.equals("code"));
  ok(SafeMarkdown.parse("unclosed **bold and `code").text.equals("unclosed **bold and `code"));
  ok(SafeMarkdown.parse("[a](javascript:alert(1))").text.equals("a"));
  ok(SafeMarkdown.parse("````\n<b>literal</b>\n````").text.contains("<b>literal</b>"));
  String nested="label";for(int i=0;i<2000;i++)nested="["+nested+"](https://example.invalid)";ok(SafeMarkdown.parse(nested).text.length()>0);
  StringBuilder longText=new StringBuilder();for(int i=0;i<33000;i++)longText.append('x');ok(SafeMarkdown.parse(longText.toString()).text.contains("Display shortened"));
  System.out.println("NotificationProseTest: "+count+" checks passed");
 }
}

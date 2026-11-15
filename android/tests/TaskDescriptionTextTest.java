package com.simon.mytasks;
import java.lang.reflect.*;
public final class TaskDescriptionTextTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Class<?> policy;
  try{policy=Class.forName("com.simon.mytasks.TaskDescriptionText");}catch(ClassNotFoundException e){throw new AssertionError("Missing compact task-description presentation",e);}
  Method preview=policy.getDeclaredMethod("preview",String.class);preview.setAccessible(true);
  String original="**Send the report** by Friday.\n\n"+String.join(" ",java.util.Collections.nCopies(180,"Keep all original context."))+"\nFinal condition: do not send until approved.";
  SafeMarkdown.Document d=(SafeMarkdown.Document)preview.invoke(null,original);
  check(d.text.length()<=440,"Opening description must be bounded, not a full wall of text");
  check(d.text.startsWith("Send the report by Friday."),"Preview must retain original order and wording");
  check(d.text.contains("…"),"Omitted details must be explicit");
  check(d.styles.stream().anyMatch(s->s.kind.equals("bold")&&d.text.substring(s.start,s.end).equals("Send the report")),"Preview must preserve genuine Markdown bold");
  check(original.endsWith("Final condition: do not send until approved."),"Presentation never replaces original");
  SafeMarkdown.Document layout=TaskDescriptionText.formatted("# Context\n\nDeadline: **Friday**\n- Bring the report\n\n<script>literal</script> [label](javascript:alert(1)) ![remote](https://invalid/x)");
  check(layout.text.startsWith("Context\n\nDeadline: Friday\n• Bring the report"),"Task headings and lists must be readable without Markdown scaffolding");
  check(layout.styles.stream().anyMatch(s->s.kind.equals("bold")&&layout.text.substring(s.start,s.end).equals("Context")),"Heading gets safe bold emphasis");
  check(layout.styles.stream().anyMatch(s->s.kind.equals("bold")&&layout.text.substring(s.start,s.end).equals("Deadline:")),"Existing short labels get emphasis without invented facts");
  check(layout.styles.stream().noneMatch(s->s.kind.startsWith("web:")),"Task description links remain inert");
  check(!layout.text.contains("javascript:")&&!layout.text.contains("https://invalid"),"No active image/link target in formatted text");
  TaskDescriptionText.State state=new TaskDescriptionText.State();state.bind("a",original);
  check(!state.expanded&&!state.original&&state.shortened(),"Each task opens as a compact preview");
  state.toggleExpanded();check(state.document().text.endsWith("Final condition: do not send until approved."),"Show more includes final conditions in original order");
  state.toggleOriginal();check(state.document().text.equals(original),"Original text must be byte-for-byte source text");
  state.bind("a",original);check(state.original&&state.expanded,"Rebinding same task/source preserves reading mode");
  state.bind("b",original);check(!state.expanded&&!state.original,"Different task resets to compact");
  state.bind("b",original+" changed");check(!state.expanded,"Changed canonical source resets preview");
  String huge=String.join("",java.util.Collections.nCopies(4000,"**é😀** "))+"THE EXACT END";
  state.bind("large",huge);state.toggleExpanded();check(state.document().text.equals(huge),"Descriptions beyond parser bound expand as full original, never silently truncate");
  state.toggleExpanded();check(!state.expanded&&!state.original,"Show less restores compact safe rendering");
  String dense=String.join("",java.util.Collections.nCopies(2200,"Deadline: x\n"))+"FINAL DENSE CONDITION";
  state.bind("dense",dense);state.toggleExpanded();check(state.document().text.endsWith("FINAL DENSE CONDITION"),"Added emphasis must not make an originally in-bound description truncate");
  String cover="Opening action. "+String.join(" ",java.util.Collections.nCopies(80,"Background context."))+" Final condition: do not send until approved.";
  SafeMarkdown.Document fallback=TaskDescriptionText.preview(cover);
  check(fallback.text.contains("Opening action.")&&fallback.text.contains("Final condition: do not send until approved."),"Unavailable summary fallback must cover start/middle/end, not only truncate the intro");
  String summary="**Action:** revise the report.\n**Constraint:** wait for approval.";
  state.bind("summary",cover,summary);check(state.document().text.equals("Action: revise the report.\nConstraint: wait for approval."),"Optional backend summary is the default compact presentation");
  check(state.hasSummary(),"Server summary must be distinguished from local excerpts");
  state.toggleExpanded();check(state.document().text.endsWith("Final condition: do not send until approved."),"See more always reads original, never just the summary");
  state.bind("summary",cover,"Updated **summary**.");check(!state.expanded&&state.document().text.equals("Updated summary."),"Summary changes invalidate old reading state");
  state.bind("summary",cover,"   ");check(!state.hasSummary()&&state.document().text.contains("Final condition:"),"Blank summary falls back safely");
  String manyLines=String.join("\n",java.util.Collections.nCopies(100,"context"));
  check(TaskDescriptionText.preview(manyLines).text.split("\n",-1).length<=6,"Fallback must stay compact even for newline-heavy input");
  String emoji=String.join("",java.util.Collections.nCopies(600,"😀"));
  String unicodePreview=TaskDescriptionText.preview(emoji).text;
  for(int i=0;i<unicodePreview.length();i++){char c=unicodePreview.charAt(i);if(Character.isHighSurrogate(c))check(i+1<unicodePreview.length()&&Character.isLowSurrogate(unicodePreview.charAt(++i)),"Do not cut emoji surrogate pairs");else check(!Character.isLowSurrogate(c),"No orphan low surrogate");}
  SafeMarkdown.Document code=TaskDescriptionText.formatted("```\nDeadline: x\n# literal\n```");
  check(code.text.contains("Deadline: x")&&code.text.contains("# literal")&&code.styles.stream().noneMatch(s->s.kind.equals("bold")),"Code fences must not acquire label/heading emphasis");
  state.bind("valid-summary",cover,String.join("",java.util.Collections.nCopies(1400,"x")));check(state.hasSummary(),"Accept backend summaries up to 1600 Unicode characters");
  state.bind("invalid-summary",cover,String.join("",java.util.Collections.nCopies(1601,"x")));check(!state.hasSummary(),"Oversized optional summary cannot defeat compact default");
  state.bind("empty",null);check(state.document().text.isEmpty()&&!state.shortened(),"Null/empty is harmless");
  System.out.println("PASS compact task-description excerpt, safe layout, expansion/original and identity state");
 }
}

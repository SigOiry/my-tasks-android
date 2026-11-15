package com.simon.mytasks;
import java.util.*;
/** Android-only derived summary reader; local excerpts never impersonate a semantic summary. */
final class TaskDescriptionText {
 static final class State {
  private String owner="",source="",summary="";private SafeMarkdown.Document full=formatted(""),excerpt=preview("");
  boolean expanded,original;
  void bind(String key,String value){bind(key,value,"");}
  void bind(String key,String value,String compact){
   value=value==null?"":value;compact=compact==null?"":compact.trim();
   if(compact.codePointCount(0,compact.length())>1600)compact=""; // A malformed/unbounded summary is not a compact description.
   if(Objects.equals(owner,key)&&source.equals(value)&&summary.equals(compact))return;
   owner=key;source=value;summary=compact;full=formatted(value);excerpt=compact.isEmpty()?preview(value):formatted(compact);expanded=false;original=false;
  }
  boolean hasSummary(){return !summary.isEmpty();}
  boolean shortened(){return source.length()>32000||!excerpt.text.equals(full.text);}
  boolean plain(){return original||expanded&&source.length()>32000;}
  SafeMarkdown.Document document(){return plain()?new SafeMarkdown.Document(source,Collections.<SafeMarkdown.Style>emptyList()):expanded?full:excerpt;}
  void toggleExpanded(){expanded=!expanded;original=false;}
  void toggleOriginal(){original=!original;expanded=true;}
 }

 static SafeMarkdown.Document formatted(String source){
  StringBuilder layout=new StringBuilder();boolean fenced=false;
  // Match the existing safe renderer's bound before doing any layout work.
  String bounded=source==null?"":source;if(bounded.length()>32000)return new SafeMarkdown.Document(bounded,Collections.<SafeMarkdown.Style>emptyList());
  for(String row:bounded.replace("\r\n","\n").split("\n",-1)){
   if(layout.length()>0)layout.append('\n');
   if(row.trim().startsWith("```")){fenced=!fenced;layout.append(row);continue;}
   if(!fenced){
    java.util.regex.Matcher heading=java.util.regex.Pattern.compile("^ {0,3}#{1,6} +(.+)$").matcher(row);
    java.util.regex.Matcher label=java.util.regex.Pattern.compile("^([\\p{L}][\\p{L} \\-/]{0,36}:)([ \\t]+.*)$").matcher(row);
    if(heading.matches())row="**"+heading.group(1)+"**";
    else if(label.matches())row="**"+label.group(1)+"**"+label.group(2);
   }
   layout.append(row);
  }
  // Added Markdown markers must not consume the parser budget and hide a tail.
  if(layout.length()>32000)return new SafeMarkdown.Document(source,Collections.<SafeMarkdown.Style>emptyList());
  return SafeMarkdown.parse(layout.toString());
 }
 static SafeMarkdown.Document preview(String source){
  SafeMarkdown.Document full=formatted(source);String text=full.text;
  if(text.length()<=420&&text.split("\n",-1).length<=6)return full;
  // Fallback is labelled selected excerpts, not a semantic summary. Sample the
  // beginning, middle and end so a missing backend summary never means intro-only.
  java.text.BreakIterator sentences=java.text.BreakIterator.getSentenceInstance(Locale.ROOT);sentences.setText(text);
  ArrayList<int[]> ranges=new ArrayList<>();int start=sentences.first();
  for(int end=sentences.next();end!=java.text.BreakIterator.DONE;start=end,end=sentences.next()){
   int a=start,b=end;while(a<b&&Character.isWhitespace(text.charAt(a)))a++;while(b>a&&Character.isWhitespace(text.charAt(b-1)))b--;
   if(a<b)ranges.add(new int[]{a,b});
  }
  if(ranges.isEmpty())return new SafeMarkdown.Document("",Collections.<SafeMarkdown.Style>emptyList());
  if(ranges.size()==1&&text.length()>130){ranges.clear();ranges.add(new int[]{0,130});ranges.add(new int[]{(text.length()-130)/2,(text.length()+130)/2});ranges.add(new int[]{text.length()-130,text.length()});}
  LinkedHashSet<Integer> picks=new LinkedHashSet<>(Arrays.asList(0,ranges.size()/2,ranges.size()-1));
  StringBuilder out=new StringBuilder();ArrayList<SafeMarkdown.Style> styles=new ArrayList<>();
  for(int index:picks){int[] range=ranges.get(index);int a=range[0],b=range[1];
   if(b-a>130){if(index==ranges.size()-1)a=b-130;else b=a+130;}
   if(a>0&&Character.isLowSurrogate(text.charAt(a)))a++;
   if(b>0&&b<text.length()&&Character.isHighSurrogate(text.charAt(b-1)))b--;
   if(out.length()>0)out.append("\n…\n");int[] positions=new int[b-a+1];
   // Flatten only excerpt whitespace; preserve exact full/original text separately.
   for(int i=a;i<b;i++){positions[i-a]=out.length();char c=text.charAt(i);if(!Character.isWhitespace(c))out.append(c);else if(out.length()>0&&out.charAt(out.length()-1)!=' ')out.append(' ');}
   positions[b-a]=out.length();
   for(SafeMarkdown.Style s:full.styles)if(s.start<b&&s.end>a)styles.add(new SafeMarkdown.Style(positions[Math.max(a,s.start)-a],positions[Math.min(b,s.end)-a],s.kind));
  }
  return new SafeMarkdown.Document(out.toString(),styles);
 }
}

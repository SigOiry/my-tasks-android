package com.simon.mytasks;
import java.util.*;
/** Small bounded text parser. No HTML engine, URL objects, images or interactive spans. */
final class SafeMarkdown {
 static final class Style {final int start,end;final String kind;Style(int s,int e,String k){start=s;end=e;kind=k;}}
 static final class Document {final String text;final List<Style> styles;Document(String t,List<Style> s){text=t;styles=Collections.unmodifiableList(s);}}
 static Document parse(String source){String s=source==null?"":source;if(s.length()>32000)s=s.substring(0,32000)+"\n\n[Display shortened · view full message as plain text]";StringBuilder out=new StringBuilder();ArrayList<Style> styles=new ArrayList<>();boolean fenced=false;int fenceStart=0;
  for(String row:s.split("\n",-1)){
   if(row.trim().startsWith("```")){if(fenced)styles.add(new Style(fenceStart,out.length(),"code"));else fenceStart=out.length();fenced=!fenced;continue;}
   if(out.length()>0)out.append('\n');
   if(fenced){out.append(row);continue;}
   String line=row.replaceFirst("^\\s*[-*+]\\s+","• ");inline(line,out,styles,0);
  }
  if(fenced&&out.length()>fenceStart)styles.add(new Style(fenceStart,out.length(),"code"));return new Document(out.toString(),styles);
 }
 private static void inline(String s,StringBuilder out,List<Style> styles,int depth){
  for(int i=0;i<s.length();){char c=s.charAt(i);
   if(c=='\\'&&i+1<s.length()){out.append(s.charAt(i+1));i+=2;continue;}
   if(c=='<'){int end=s.indexOf('>',i+1);if(end>=0){i=end+1;continue;}}
   boolean image=c=='!'&&i+1<s.length()&&s.charAt(i+1)=='[';int open=image?i+1:i;
   if(s.charAt(open)=='['){int mid=s.indexOf("](",open+1);if(mid>=0){int end=mid+2,balance=1;while(end<s.length()&&balance>0){char p=s.charAt(end++);if(p=='(')balance++;if(p==')')balance--;}
    if(balance==0){if(!image)inline(s.substring(open+1,mid),out,styles,Math.min(depth+1,5));i=end;continue;}}}
   String mark=s.startsWith("**",i)?"**":s.startsWith("__",i)?"__":c=='*'?"*":c=='_'?"_":c=='`'?"`":"";
   if(!mark.isEmpty()&&depth<5){int end=s.indexOf(mark,i+mark.length());if(end>i+mark.length()){int start=out.length();String inner=s.substring(i+mark.length(),end);if(mark.equals("`"))out.append(inner);else inline(inner,out,styles,depth+1);styles.add(new Style(start,out.length(),mark.equals("`")?"code":mark.length()==2?"bold":"italic"));i=end+mark.length();continue;}}
   out.append(c);i++;
  }
 }
}

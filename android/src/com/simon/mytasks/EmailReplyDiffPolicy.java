package com.simon.mytasks;
import java.util.*;import java.util.regex.*;
final class EmailReplyDiffPolicy {
 static String[] tokens(String s){ArrayList<String> t=new ArrayList<>();Matcher m=Pattern.compile("\\s+|[^\\s]+").matcher(s);while(m.find())t.add(m.group());return t.toArray(new String[0]);}
 static String compare(String original,String proposed){if(original.equals(proposed))return "No text changes.";String[] a=tokens(original),b=tokens(proposed);if((long)a.length*b.length>250000)return "Original:\n"+original+"\n\nProposed:\n"+proposed;int[][] lcs=new int[a.length+1][b.length+1];for(int i=a.length-1;i>=0;i--)for(int j=b.length-1;j>=0;j--)lcs[i][j]=a[i].equals(b[j])?1+lcs[i+1][j+1]:Math.max(lcs[i+1][j],lcs[i][j+1]);StringBuilder out=new StringBuilder();int i=0,j=0;while(i<a.length||j<b.length){if(i<a.length&&j<b.length&&a[i].equals(b[j])){out.append(a[i++]);j++;}else if(i<a.length&&(j==b.length||lcs[i+1][j]>=lcs[i][j+1]))out.append("[-").append(a[i++]).append("-]");else out.append("{+").append(b[j++]).append("+}");}return out.toString();}
}

package com.simon.mytasks;
import org.json.*;import java.util.*;
public class CitationWarningsTest {
 public static void main(String[] args)throws Exception {
 JSONObject m=new JSONObject().put("content","immutable citex").put("citation_metadata",new JSONObject().put("version",1).put("sources",new JSONArray()).put("warnings",new JSONArray().put(4).put(JSONObject.NULL).put("  ").put(new JSONObject()).put("  Source unavailable  ").put(new String(new char[1200]).replace('\0','x'))));
 String original=m.toString();List<String> warnings=CitationPolicy.warnings(m);
 if(!original.equals(m.toString()))throw new AssertionError("warning projection mutated original");
 if(warnings.size()!=2||!warnings.get(0).equals("Source unavailable")||warnings.get(1).length()!=1000)throw new AssertionError("validated bounded warning projection");
 JSONArray rows=new JSONArray();for(int i=0;i<20;i++)rows.put("warning "+i);m.getJSONObject("citation_metadata").put("warnings",rows);if(CitationPolicy.warnings(m).size()!=8)throw new AssertionError("warning count bound");
 m.getJSONObject("citation_metadata").put("version",2);if(!CitationPolicy.warnings(m).isEmpty())throw new AssertionError("unknown version");
 if(!new JSONObject(original).optString("content").equals(m.optString("content")))throw new AssertionError("original changed");
 System.out.println("CitationWarningsTest passed");
 }
}

package com.simon.mytasks;
import org.json.*;
public class EmailReplyStateTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static JSONObject draft(int revision,String body)throws Exception{return new JSONObject().put("id","d").put("revision",revision).put("body",body).put("status","draft");}
 public static void main(String[] args)throws Exception{
  EmailReplyState s=new EmailReplyState(new JSONObject());s.accept(draft(1,"old"));s.edit("mine");JSONObject save=s.saveBody();s.edit("newer");s.saved(draft(2,"mine"),save);check(s.body().equals("newer")&&s.dirty(),"late save must preserve typing");
  s.conflict(draft(3,"remote"));check(s.body().equals("newer")&&s.conflicted(),"CAS conflict preserves local edits");
  EmailReplyState restored=new EmailReplyState(new JSONObject(s.json().toString()));check(restored.body().equals("newer"),"process restore");
  check(!restored.canApply(new JSONObject().put("base_revision",3)),"unsaved proposal cannot apply");
  check(!ComposerLayoutPolicy.split(720,1,false,0),"narrow width");check(ComposerLayoutPolicy.split(1100,1,false,0),"wide width");check(!ComposerLayoutPolicy.split(1100,2,false,0),"large text min widths");check(!ComposerLayoutPolicy.split(1100,1,true,0),"collapsed");
  EmailReplyState switcher=new EmailReplyState(new JSONObject());switcher.accept(draft(1,"old"));switcher.edit("private local");switcher.accept(new JSONObject().put("id","other").put("revision",1).put("body","other body").put("status","draft"));switcher.accept(draft(1,"old"));check(switcher.body().equals("private local"),"switching sources cannot lose offline writing");
  System.out.println("EmailReplyStateTest passed");
 }
}

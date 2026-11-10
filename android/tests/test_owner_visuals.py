"""Owner-brief regressions. Actual production JVM policies, not runtime visual proof."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ANDROID = Path(__file__).resolve().parents[1]
SRC = ANDROID / 'src/com/simon/mytasks'

class OwnerVisualsTest(unittest.TestCase):
    def test_project_hue_and_priority_composites(self):
        self.jvm('''
 JSONObject task=new JSONObject().put("project","Website").put("project_id","stable-project");
 int original=PresentationPolicy.taskColor(task,"Project",false,0xff63636b);
 task.put("project","Renamed project");
 check(original==PresentationPolicy.taskColor(task,"Project",false,0xff63636b),"canonical identity survives rename");
 for(boolean dark:new boolean[]{false,true})for(String mode:new String[]{"Project","Importance"}){
  int surface=dark?0xff1a1d24:0xffffffff, previous=0;
  for(String priority:new String[]{"low","medium","high"}){
   task.put("priority",priority);
   int hue=PresentationPolicy.taskColor(task,mode,dark,0xff63636b);
   check(hue==PresentationPolicy.taskColor(task,"Project",dark,0xff63636b),"importance must not replace project hue with global red/amber/teal");
   int tint=(Integer)PresentationPolicy.class.getMethod("rowTint",JSONObject.class,String.class,boolean.class,int.class).invoke(null,task,mode,dark,surface);
   int percent=priority.equals("high")?(dark?32:36):priority.equals("medium")?(dark?24:26):(dark?16:16);
   for(int shift:new int[]{16,8,0})check(((tint>>shift)&255)==Math.round(((surface>>shift)&255)*(1-percent/100f)+((hue>>shift)&255)*percent/100f),"actual opaque row tint must use pinned priority intensity");
   check(tint!=surface&&tint!=previous,"all three priority tiers are visibly distinct composites");previous=tint;
   int neutral=(Integer)PresentationPolicy.class.getMethod("rowTint",JSONObject.class,String.class,boolean.class,int.class).invoke(null,task,"Neutral",dark,surface);
   check(neutral==surface,"explicit Neutral choice remains untinted");
  }
 }
''')

    def test_sustained_ledger_can_resume_without_replaying_arrival(self):
        self.jvm('''
 UpdateEmphasis e=new UpdateEmphasis();
 check(e.claim("a","v1",true,true),"first token has arrival");e.release("a","v1");
 check(!e.claim("a","v1",true,true),"binding never replays arrival");
 java.lang.reflect.Method rest=UpdateEmphasis.class.getDeclaredMethod("acquireRest",String.class,String.class,boolean.class,boolean.class);
 rest.setAccessible(true);
 check((Boolean)rest.invoke(e,"a","v1",true,true),"seen unread token must sustain a resting halo");
 check(!(Boolean)rest.invoke(e,"a","v1",true,true),"one owner per exact identity");
 check(e.claim("b","v2",true,true),"arrival and rest share global cap");
 check(!(Boolean)rest.invoke(e,"c","v3",true,true),"unseen overflow cannot bypass arrival or cap");
 e.pause();check(e.activeCount()==0,"background releases slots");
 check((Boolean)rest.invoke(e,"a","v1",true,true),"rest resumes after navigation without arrival");
 e.release("a","v1");
 check(!(Boolean)rest.invoke(e,"a","v1",true,false),"reduced motion keeps static halo");
 check(!(Boolean)rest.invoke(e,"a","v1",false,true),"reviewed updates never breathe");
 check(e.claim("a","v1 ",true,true),"token remains exact including trailing whitespace");
''')

    def test_incoming_only_animates_new_tail_not_replayed_history(self):
        self.jvm('''
 java.lang.reflect.Method incoming=ChatRowPolicy.class.getDeclaredMethod("animateIncoming",boolean.class,boolean.class,boolean.class,boolean.class);incoming.setAccessible(true);
 check((Boolean)incoming.invoke(null,true,false,false,true),"new incoming at visible tail must animate");
 check(!(Boolean)incoming.invoke(null,false,false,false,true),"older reader must remain stationary");
 check(!(Boolean)incoming.invoke(null,true,true,false,true),"opening transcript must not replay");
 check(!(Boolean)incoming.invoke(null,true,false,true,true),"pagination must not replay");
 check(!(Boolean)incoming.invoke(null,true,false,false,false),"reduced motion has no incoming animation");
 java.lang.reflect.Method admitting=ChatRowPolicy.class.getDeclaredMethod("admitting",String.class,boolean.class);admitting.setAccessible(true);
 check((Boolean)admitting.invoke(null,"prepared",true),"Sending only while exact admission operation is active");
 check(!(Boolean)admitting.invoke(null,"prepared",false),"recovered prepared request is uncertain, not Sending forever");
 check(!(Boolean)admitting.invoke(null,"uncertain",true),"ambiguity never becomes fake Sending");
 check(!(Boolean)admitting.invoke(null,"running",true),"running is not admission");
''')

    def test_citations_allow_only_explicit_public_https(self):
        self.jvm('''
 java.lang.reflect.Method links=ChatRowPolicy.class.getDeclaredMethod("sourceLinks",String.class);links.setAccessible(true);
 String prose="Source [official](https://www.nasa.gov/news/) and https://example.com/evidence. https://example.com/evidence. "+
   "javascript:alert(1) intent://evil mailto:a@example.com http://example.com/no https://127.0.0.1/private https://localhost/private https://secret.internal/file https://user:pass@example.com/private";
 java.util.List<?> result=(java.util.List<?>)links.invoke(null,prose);
 check(result.size()==2&&result.get(0).equals("https://www.nasa.gov/news/")&&result.get(1).equals("https://example.com/evidence"),"only deduplicated explicit safe HTTPS citations launch");
''')

    def test_native_ime_and_global_capsule_are_wired(self):
        ui=(SRC/'WorkspaceUi.java').read_text()
        chat=(SRC/'ConversationsUi.java').read_text()
        main=(SRC/'MainActivity.java').read_text()
        self.assertIn('void dialogEnter(',ui)
        self.assertIn('ui.dialogEnter(window)',main)
        self.assertIn('window.setDimAmount',ui)
        self.assertIn('void coordinateIme(',ui)
        self.assertIn('WindowInsetsAnimation.Callback',ui)
        self.assertIn('getInterpolatedFraction()',ui)
        self.assertIn('a.ui.coordinateIme(composerGlass)',chat)
        self.assertIn('navigationOrigin',main)
        self.assertIn('ui.placeScopeCapsule(capsule,bottomTabs',main)
        tasks=(SRC/'TasksUi.java').read_text()
        self.assertIn('a.ui.enter(a.listPane,NativeMotionPolicy.duration("tab"))',tasks)
        self.assertIn('a.ui.placeScopeCapsule(scopePill,segments',chat)
        self.assertIn('discussionScopeOrigin',main)

    def test_actual_halo_draw_and_scheduling_methods(self):
        from test_tasks_anchor_methods import extract
        source=(SRC/'TaskRowView.java').read_text()
        methods='\n'.join(extract(source,p) for p in [r'^ void groupCorners\(',r'^ private void startEmphasis\(',r'^ void stopEmphasis\(',r'^ void pause\(',r'^ void resume\(',r'^ @Override protected void onDraw\('])
        template=(ANDROID/'tests/UnreadHaloMethodsHarness.java').read_text().replace('// METHODS',methods)
        javac=str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac: self.fail('JAVA_HOME/javac required')
        with tempfile.TemporaryDirectory(prefix='actual-halo-methods-') as tmp:
            path=Path(tmp)/'UnreadHaloMethodsHarness.java';path.write_text(template)
            subprocess.run([javac,'--release','8','-d',tmp,str(SRC/'UpdateEmphasis.java'),str(path)],check=True)
            result=subprocess.run([str(Path(javac).parent/'java'),'-cp',tmp,'com.simon.mytasks.UnreadHaloMethodsHarness'],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)

    def test_web_progress_uses_only_actual_optional_running_phase(self):
        self.jvm('''
 java.lang.reflect.Method label=ChatRowPolicy.class.getDeclaredMethod("pendingLabel",JSONObject.class,boolean.class);label.setAccessible(true);
 JSONObject p=new JSONObject().put("status","running").put("request",new JSONObject().put("web_phase","searching"));
 check(((String)label.invoke(null,p,true)).contains("Searching public web"),"actual server search phase is visible");
 p.getJSONObject("request").put("web_phase","reading");check(((String)label.invoke(null,p,true)).contains("Reading public sources"),"actual server read phase is visible");
 p.put("status","queued");check(!((String)label.invoke(null,p,true)).contains("Reading"),"stale phase never overrides queued");
 p.put("status","running");check(((String)label.invoke(null,p,false)).contains("Last known"),"offline state must not pretend active progress");
 p.getJSONObject("request").put("web_phase","invented");check(!((String)label.invoke(null,p,true)).contains("invented"),"unsupported phases never become fabricated status labels");
''')

    def test_final_row_composites_keep_text_and_indicators_legible(self):
        self.jvm('''
 for(String project:new String[]{"Website","website","é","p2"," ","p5"})for(boolean dark:new boolean[]{false,true})for(String mode:new String[]{"Project","Importance"})for(String priority:new String[]{"high","medium","low"}){
  JSONObject task=new JSONObject().put("project",project).put("priority",priority);
  int bg=PresentationPolicy.rowTint(task,mode,dark,dark?0xff1a1d24:0xffffffff);
  int ink=dark?0xfff5f5f7:0xff1c1c1e,secondary=PresentationPolicy.rowSecondary(dark),unread=PresentationPolicy.rowUnreadColor(dark);
  check(contrast(ink,bg)>=4.5&&contrast(secondary,bg)>=4.5,"neutral reading text on every actual composite "+project+priority+dark);
  check(contrast(unread,bg)>=4.5,"New update text contrast on every actual composite "+project+priority+dark+" "+contrast(unread,bg));
  check(contrast(PresentationPolicy.taskCueColor(PresentationPolicy.taskColor(task,mode,dark,secondary),dark),bg)>=3,"project indicator contrast on actual tint");
 }
''')

    def jvm(self, body):
        javac = str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac: self.fail('JAVA_HOME/javac required')
        jar=ANDROID/'build/test-lib/json.jar'
        self.assertTrue(jar.exists(), 'Run android/test.sh to resolve the pinned JSON dependency')
        source='''package com.simon.mytasks; import org.json.*;
public class OwnerVisualsHarness {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static double luminance(int c){double v=0;double[] w={.2126,.7152,.0722};for(int i=0;i<3;i++){double x=((c>>(16-8*i))&255)/255.0;v+=w[i]*(x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4));}return v;}
 static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 public static void main(String[] args)throws Exception {'''+body+'''System.out.println("Owner visual policy checks passed");}}
'''
        with tempfile.TemporaryDirectory(prefix='owner-visuals-') as tmp:
            f=Path(tmp)/'OwnerVisualsHarness.java';f.write_text(source)
            subprocess.run([javac,'--release','8','-cp',str(jar),'-d',tmp,*[str(SRC/(p+'.java')) for p in ['Core','PresentationPolicy','UpdateEmphasis','ChatRowPolicy']],str(f)],check=True)
            result=subprocess.run([str(Path(javac).parent/'java'),'-cp',tmp+os.pathsep+str(jar),'com.simon.mytasks.OwnerVisualsHarness'],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)

if __name__=='__main__': unittest.main()

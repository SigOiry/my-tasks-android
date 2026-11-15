#!/usr/bin/env python3
"""Real description policy + extracted Android binding; host UI shells, not native proof."""
from pathlib import Path
import os,re,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'src/com/simon/mytasks'
javac=Path(os.environ['JAVA_HOME'])/'bin/javac' if os.environ.get('JAVA_HOME') else shutil.which('javac')
if not javac:raise SystemExit('Set JAVA_HOME or put javac on PATH')
java=Path(javac).resolve().parent
source=(SRC/'MainActivity.java').read_text()
def production_method(name):
 match=re.search(r'^ (?:@Override public )?void '+re.escape(name)+r'\(',source,re.M)
 assert match,'Missing production method '+name
 start=source.index('{',match.start());depth=0
 for t in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]',source[start:]):
  if t.group()=='{':depth+=1
  elif t.group()=='}':
   depth-=1
   if depth==0:return source[match.start():start+t.end()]
 raise AssertionError('Unclosed real method '+name)
method=production_method('readingDescription')
harness='''package com.simon.mytasks;
import java.util.*;
public class DescriptionBindingHarness {
 String selected="a";int card,muted,ink;Object owner=this;
 TaskDescriptionText.State descriptionState=new TaskDescriptionText.State();
 LinearLayout editor=new LinearLayout();Ui ui=new Ui();
 int dp(int n){return n;}LinearLayout vertical(){return new LinearLayout();}
 TextView heading(String s,int n){return new TextView(s);}TextView label(String s,int n,int color){return new TextView(s);}
 Button button(String s,Runnable action){return new Button(s,action);}
 void add(LinearLayout p,View c){p.children.add(c);}void space(LinearLayout p,int n){}
 // METHOD
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static class View {static final int VISIBLE=0,GONE=8;int visibility=VISIBLE;void setVisibility(int v){visibility=v;}}
 static class TextView extends View {CharSequence text;boolean selectable,links;int autolink;TextView(String s){text=s;}void setText(CharSequence s){text=s;}void setTextColor(int c){}void setTextIsSelectable(boolean b){selectable=b;}void setAutoLinkMask(int n){autolink=n;}void setLinksClickable(boolean b){links=b;}void setMovementMethod(Object m){}void setContentDescription(String s){} }
 static class Button extends TextView {interface Listener{void click(View v);}Runnable action;Button(String s,Runnable r){super(s);action=r;}void setMinHeight(int n){}void setOnClickListener(Listener l){action=()->l.click(this);}void click(){action.run();}}
 static class LinearLayout extends View {List<View> children=new ArrayList<>();void setPadding(int a,int b,int c,int d){}void setBackground(Object s){} }
 static class Ui {Object shape(int c,int n,int r){return null;}void quiet(Button b){} }
 static class MarkdownText {static CharSequence render(SafeMarkdown.Document d){return d.text;}}
 public static void main(String[] args){
  DescriptionBindingHarness h=new DescriptionBindingHarness();String original="**First fact**\\n\\n"+String.join(" ",Collections.nCopies(100,"Original detail."))+" LAST CONDITION";
  h.readingDescription(original,"**Action:** do the work. **Constraint:** wait for approval.");LinearLayout g=(LinearLayout)h.editor.children.get(0);TextView body=(TextView)g.children.get(1);Button more=(Button)g.children.get(2),raw=(Button)g.children.get(3);
  check(body.text.length()<original.length()&&more.text.equals("See more"),"Compact native binding on open");
  check(body.text.toString().equals("Action: do the work. Constraint: wait for approval."),"Native reader consumes backend summary rather than original intro");
  check(body.selectable&&!body.links&&body.autolink==0,"Selectable but no active links");
  more.click();check(body.text.toString().endsWith("LAST CONDITION")&&more.text.equals("See less"),"Real See more callback displays full tail");
  raw.click();check(body.text.toString().equals(original),"Real original-text callback retains exact Markdown source");
  more.click();check(body.text.length()<original.length()&&!h.descriptionState.expanded,"Real See less callback collapses");
  h.editor=new LinearLayout();h.readingDescription(original,"");check(!h.descriptionState.expanded,"Rebind remains compact after collapse");
  check(original.endsWith("LAST CONDITION"),"No canonical write in reader callbacks");
  System.out.println("PASS actual Android description binding callbacks (host UI seams)");
 }
}'''.replace('// METHOD',method)
with tempfile.TemporaryDirectory(prefix='task-description-',dir=os.environ.get('TMPDIR','/opt/data/cache/scratch')) as d:
 p=Path(d);(p/'DescriptionBindingHarness.java').write_text(harness)
 subprocess.run([str(java/'javac'),'--release','8','-d',d,str(SRC/'SafeMarkdown.java'),str(SRC/'TaskDescriptionText.java'),str(ROOT/'tests/TaskDescriptionTextTest.java'),str(p/'DescriptionBindingHarness.java')],check=True)
 for name in ['TaskDescriptionTextTest','DescriptionBindingHarness']:
  subprocess.run([str(java/'java'),'-cp',d,'com.simon.mytasks.'+name],check=True)

# Execute whole production render/navigation/configuration methods. Only the layout
# tail of buildDetails/buildTaskReading is a host seam; their real early-return
# and description-dispatch lines are extracted so skipped readers remain skipped.
cp=ROOT/'build/test-lib/json.jar'
if not cp.exists():raise SystemExit('Run android/test.sh first to obtain its verified host JSON jar.')
details=production_method('buildDetails');detail_marker='  LinearLayout top='
assert details.count(detail_marker)==1,'Missing detail layout seam'
detail_entry=details[:details.index(detail_marker)]+'  editor=vertical();buildTaskReading();\n }'
gates=[line for line in production_method('buildTaskReading').splitlines() if 'if(!text(draft,"description").isEmpty())readingDescription(' in line]
assert len(gates)==1,'Missing real optional description dispatch'
lifecycle=(ROOT/'tests/DescriptionLifecycleHarness.java').read_text().replace('// METHODS','\n'.join(production_method(n) for n in ['render','renderPage','navigate','openTaskConversation','onConfigurationChanged','readingDescription'])).replace('// DETAIL_ENTRY',detail_entry).replace('// DESCRIPTION_GATE',gates[0])
with tempfile.TemporaryDirectory(prefix='description-lifecycle-',dir=os.environ.get('TMPDIR','/opt/data/cache/scratch')) as d:
 p=Path(d);(p/'DescriptionLifecycleHarness.java').write_text(lifecycle)
 (p/'Insets.java').write_text('package android.graphics;public class Insets {public int top,bottom,left,right;}')
 subprocess.run([str(java/'javac'),'--release','8','-cp',str(cp),'-d',d,str(SRC/'SafeMarkdown.java'),str(SRC/'TaskDescriptionText.java'),str(p/'DescriptionLifecycleHarness.java'),str(p/'Insets.java')],check=True)
 results=[subprocess.run([str(java/'java'),'-cp',d+os.pathsep+str(cp),'com.simon.mytasks.DescriptionLifecycleHarness',mode]).returncode for mode in ['empty','conversation']]
 assert results==[0,0],'Actual Activity description selection lifecycle regression(s) failed: '+str(results)
# These bindings keep preview text out of editing and mutation payloads.
assert 'description=field(editor,"Description",text(draft,"description"),true)' in source
assert source.count('readingDescription(text(draft,"description"),draft.opt("description_summary") instanceof String?text(draft,"description_summary"):"")')==2,'Normal and trash task details both use compact reader'
assert 'body.put(f,text(draft,f))' in source,'Save still serializes original editable fields'
print('PASS original editor/save and both detail entrypoints retained')

#!/usr/bin/env python3
"""Execute review callbacks/state and sizing extracted from real native sources.
Host UI shells are not Android runtime or render evidence.
"""
from pathlib import Path
import os,re,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]; SRC=ROOT/'src/com/simon/mytasks'
JAVA=Path(os.environ.get('JAVA_HOME','/opt/data/cache/android-build/jdk-17.0.2'))/'bin'
def method(source,name):
 m=re.search(r'^ (?:static )?[\w<>]+ '+name+r'\(',source,re.M)
 assert m, 'Missing production method '+name
 start=source.index('{',m.start());depth=0
 for t in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]',source[start:]):
  if t.group()=='{':depth+=1
  elif t.group()=='}':
   depth-=1
   if not depth:return source[m.start():start+t.end()]
 raise AssertionError('Unclosed production method '+name)
review=(SRC/'UpdateSheet.java').read_text()
def callback(label):
 m=re.search(r'a\.(?:primary|button)\("'+re.escape(label)+r'",\(\)->\{([^\n]*?)\}\)',review)
 assert m,'Missing honest review action: '+label
 return m.group(1)
callbacks=[callback(s) for s in ['Mark reviewed','Open task','Ask Hermes']]
# Execute the same callbacks and state gate that the Android controls bind.
harness='''
class ReviewNativeTest {
 static class View {static int VISIBLE=0,GONE=8;}
 static class Button {boolean enabled;String text;int visibility;void setEnabled(boolean v){enabled=v;}void setText(String s){text=s;}void setVisibility(int v){visibility=v;}}
 static class Label {String text;void setText(String s){text=s;}}
 static class Snapshot {String taskId(){return "task-7";}String token(){return "immutable-token";}}
 static class UpdateReviewPolicy {static boolean newer(Snapshot s,Object t){return Boolean.TRUE.equals(t);}}
 static class Activity {boolean online=true,ackNeedsRefresh,busy,newer;int ack,opens;String id,token;boolean chat;Object task(String id){return newer;}void markRead(String i,String t){ack++;id=i;token=t;}void openReviewedTask(String i,boolean c){opens++;id=i;chat=c;}}
 static class Dialog {boolean dismissed;void dismiss(){dismissed=true;}}
 Activity a=new Activity();Snapshot shown=new Snapshot();Dialog dialog=new Dialog();Button acknowledge=new Button(),latest=new Button();Label state=new Label();boolean waiting,failed,active=true;
 boolean current(){return active;}
 // STATE
 void reviewed(){// ACK
 }
 void open(){// OPEN
 }
 void ask(){// ASK
 }
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){ReviewNativeTest t=new ReviewNativeTest();t.updateState();check(t.acknowledge.enabled,"online displayed token is reviewable");check(t.acknowledge.text.equals("Mark reviewed"),"ack label");t.reviewed();check(t.a.ack==1&&t.a.token.equals("immutable-token")&&t.a.id.equals("task-7"),"exact snapshot acknowledgement");check(!t.dialog.dismissed,"ack confirmation owns dismissal");t.waiting=true;t.reviewed();check(t.a.ack==1,"pending duplicate blocked");t.waiting=false;t.active=false;t.reviewed();t.open();t.ask();check(t.a.ack==1&&t.a.opens==0,"stale surface callbacks ignored");t.active=true;t.open();check(t.a.opens==1&&!t.a.chat&&t.dialog.dismissed&&t.a.ack==1,"Open task never acknowledges");t.ask();check(t.a.opens==2&&t.a.chat&&t.a.ack==1,"Ask Hermes never acknowledges");t.a.newer=true;t.updateState();check(!t.acknowledge.enabled&&t.latest.visibility==0,"newer token requires another review");t.a.newer=false;t.a.online=false;t.updateState();check(!t.acknowledge.enabled,"offline cannot acknowledge");t.a.online=true;t.failed=true;t.updateState();check(!t.acknowledge.enabled,"failed confirmation requires refresh");System.out.println("Review native actions/state: 10 checks passed");}
}
'''
harness=harness.replace('// STATE',method(review,'updateState')).replace('// ACK',callbacks[0]).replace('// OPEN',callbacks[1]).replace('// ASK',callbacks[2])
with tempfile.TemporaryDirectory(prefix='details-v3-',dir=os.environ.get('TMPDIR','/opt/data/cache/scratch')) as d:
 p=Path(d);(p/'ReviewNativeTest.java').write_text(harness)
 subprocess.run([str(JAVA/'javac'),'--release','8','-d',d,str(p/'ReviewNativeTest.java')],check=True)
 subprocess.run([str(JAVA/'java'),'-cp',d,'ReviewNativeTest'],check=True)
# Geometry is deterministic production logic; test small and constrained windows.
sheets=(SRC/'NativeSheets.java').read_text()
anchor_test='''class AnchorTest {
 static class Rect {int height(){return 64;}}
 static class JSONObject {String id,title;JSONObject(String i,String t){id=i;title=t;}String optString(String k){return k.equals("id")?id:title;}}
 static class Tasks {JSONObject[] rows;Tasks(JSONObject...r){rows=r;}int length(){return rows.length;}JSONObject optJSONObject(int i){return rows[i];}}
 static class View {Object tag;boolean shown=true,visible=true;View parent;Object getTag(){return tag;}Object getParent(){return parent;}boolean isShown(){return shown;}boolean getGlobalVisibleRect(Rect r){return visible;}View findViewWithTag(Object id){return tag!=null&&tag.equals(id)?this:null;}}
 static class MainActivity {View listPane,focused;Tasks tasks;View getCurrentFocus(){return focused;}int dp(int n){return n;}JSONObject task(String id){for(JSONObject r:tasks.rows)if(r.id.equals(id))return r;return null;}}
 // ANCHOR
 public static void main(String[] args){MainActivity a=new MainActivity();View row=new View();row.tag="a";a.listPane=row;a.focused=row;a.tasks=new Tasks(new JSONObject("a","Task"));if(findAnchor(a,"Task")!=row)throw new AssertionError("unique visible tagged row anchors");a.tasks=new Tasks(new JSONObject("a","Task"),new JSONObject("b","Task"));if(findAnchor(a,"Task")!=null)throw new AssertionError("ambiguous title cannot anchor a stale focused row");a.tasks=new Tasks(new JSONObject("a","Task"));row.visible=false;if(findAnchor(a,"Task")!=null)throw new AssertionError("offscreen row falls back");row.visible=true;row.shown=false;if(findAnchor(a,"Task")!=null)throw new AssertionError("hidden row falls back");System.out.println("Native menu identity/visibility: 4 checks passed");}
}'''.replace('// ANCHOR',method(sheets,'findAnchor'))
with tempfile.TemporaryDirectory(prefix='menu-v3-',dir=os.environ.get('TMPDIR','/opt/data/cache/scratch')) as d:
 p=Path(d);(p/'AnchorTest.java').write_text(anchor_test)
 subprocess.run([str(JAVA/'javac'),'--release','8','-d',d,str(p/'AnchorTest.java')],check=True)
 subprocess.run([str(JAVA/'java'),'-cp',d,'AnchorTest'],check=True)
geometry='class SheetGeometryTest {\n'+method(sheets,'boundedHeight')+'''\nstatic void check(boolean b){if(!b)throw new AssertionError("bounded content height");}
public static void main(String[] args){check(boundedHeight(210,800)==210);check(boundedHeight(1500,800)==688);check(boundedHeight(900,320)==275);check(boundedHeight(0,800)==1);System.out.println("Native sheet bounds: 4 checks passed");}}
'''
with tempfile.TemporaryDirectory(prefix='sheet-v3-',dir=os.environ.get('TMPDIR','/opt/data/cache/scratch')) as d:
 p=Path(d);(p/'SheetGeometryTest.java').write_text(geometry)
 subprocess.run([str(JAVA/'javac'),'--release','8','-d',d,str(p/'SheetGeometryTest.java')],check=True)
 subprocess.run([str(JAVA/'java'),'-cp',d,'SheetGeometryTest'],check=True)
main=(SRC/'MainActivity.java').read_text();details=method(main,'buildDetails')
assert details.index('conversationUi.buildTask(detailPane,selected)')<details.index('LinearLayout top='),'Chat owns its only header'
assert 'Task workspace' not in details and 'new String[]{"Details","Hermès"}' not in details,'No duplicate workspace title/tabs'
assert 'JSONObject current=task(selected);if(draft==null)' in details,'Draft hydration seam survives'
assert 'Runnable reloadLatest=()->confirm(' in details and 'reloadLatest.run()' in details and 'refresh(true);' in details,'Confirmed reload remains reachable'
edit=method(main,'buildTaskEditor')
assert 'detailPane.addView' in edit and 'ui.glassSurface()' in edit,'Save and Cancel must sit outside scrolling fields'
assert all(field+'=' in edit for field in ['title','description','priority','status','due','project','notes']),'Retain all bound editable fields'
assert 'findAnchor' in sheets and 'requestFocus' in sheets and 'a.forgetDialog(dialog)' in sheets,'Anchored menu focus and private dismissal lifecycle'
assert 'ui.glassSurface()' in review and 'setAutoLinkMask(0)' in review and 'MarkdownText.render(value)' in review,'Glass rim, stable restricted prose'
assert 'setBackgroundBlurRadius' in sheets and 'isCrossWindowBlurEnabled' in sheets and 'reduce_transparency' in sheets,'Separate dialog windows need supported cross-window blur or readable fallback'
print('Details v3 review, sizing and integration contracts passed')

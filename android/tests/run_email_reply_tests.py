#!/usr/bin/env python3
"""Actual native email client/controller/state + extracted UI method regressions.
Host-only, no Android runtime or real mail claim. test.sh owner can invoke this.
"""
from pathlib import Path
import os,re,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'src/com/simon/mytasks'
JAVAC=str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
if not JAVAC:raise SystemExit('JAVA_HOME or javac is required')
JAVA=Path(JAVAC).parent/'java';JAR=ROOT/'build/test-lib/json.jar'
def method(file,name,signature=None):
 s=(SRC/file).read_text();m=re.search(signature or r'^ (?:static )?[\w<>\[\]]+ '+re.escape(name)+r'\(',s,re.M)
 if not m:raise RuntimeError('Missing actual method '+name)
 start=s.index('{',m.start());depth=0
 for t in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]',s[start:]):
  if t.group()=='{':depth+=1
  elif t.group()=='}':
   depth-=1
   if not depth:return s[m.start():start+t.end()]
 raise RuntimeError('Unclosed production method')
with tempfile.TemporaryDirectory(prefix='email-native-tests-') as tmp:
 p=Path(tmp);sources=['Core','ConversationState','ConversationClient','EmailReplyState','EmailReplyClient','EmailReplyController','ComposerLayoutPolicy','CitationPolicy','ChatPresentationPolicy','ChatRowPolicy','PresentationPolicy','EmailReplyDiffPolicy','ComposerWindowObserver']
 tests=['EmailReplyStateTest','EmailReplyClientTest','EmailReplyControllerTest','EmailReplyRaceTest','CitationTitleTest','CitationWarningsTest','EmailReplyDiffTest','ComposerHingeTest','ComposerWindowObserverTest']
 template=(ROOT/'tests/EmailReplyUiMethodsHarness.java').read_text();actual='\n'.join(method('EmailReplyUi.java',n) for n in ['capture','recipients'])
 template=template.replace('// ACTUAL_METHODS',actual);harness=p/'EmailReplyUiMethodsHarness.java';harness.write_text(template)
 template2=(ROOT/'tests/CitationSourceUiHarness.java').read_text().replace('// ACTUAL_METHODS',method('CitationUi.java','chips')+'\n'+method('EmailReplyUi.java','sourceChooser'))
 harness2=p/'CitationSourceUiHarness.java';harness2.write_text(template2)
 harness3=p/'EmailAssistantHistoryHarness.java';harness3.write_text((ROOT/'tests/EmailAssistantHistoryHarness.java').read_text().replace('// ACTUAL_METHODS',method('EmailReplyUi.java','refreshHistory')))
 hinge_methods=['fitPane','fitPaneForMode','split','reflow','capture','put','showEditor','openAssistant']
 harness4=p/'EmailReplyHingeUiHarness.java';harness4.write_text((ROOT/'tests/EmailReplyHingeUiHarness.java').read_text().replace('// ACTUAL_METHODS','\n'.join(method('EmailReplyUi.java',n) for n in hinge_methods)))
 view_group=p/'android/view/ViewGroup.java';view_group.parent.mkdir(parents=True);view_group.write_text('package android.view;public class ViewGroup {public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}}')
 files=[harness2,harness3,harness4,view_group]+[SRC/(s+'.java') for s in sources]+[ROOT/'tests'/(t+'.java') for t in tests]+list((ROOT/'tests/email-stubs').rglob('*.java'))+[harness]
 subprocess.run([JAVAC,'--release','8','-cp',str(JAR),'-d',str(p),*map(str,files)],check=True,timeout=30)
 for t in tests+['EmailReplyUiMethodsHarness','EmailAssistantHistoryHarness','CitationSourceUiHarness','EmailReplyHingeUiHarness']:subprocess.run([str(JAVA),'-cp',str(p)+os.pathsep+str(JAR),'com.simon.mytasks.'+t],check=True,timeout=15)
 # Same production observer against the OEM vendor Consumer signature.
 vendor=p/'vendor';vendor.mkdir();provider=vendor/'androidx/window/extensions/WindowExtensionsProvider.java';provider.parent.mkdir(parents=True);provider.write_text((ROOT/'tests/email-stubs/androidx/window/extensions/WindowExtensionsProvider.java').read_text().replace('java.util.function.Consumer','androidx.window.extensions.core.util.function.Consumer'))
 consumer=vendor/'androidx/window/extensions/core/util/function/Consumer.java';consumer.parent.mkdir(parents=True);consumer.write_text('package androidx.window.extensions.core.util.function;public interface Consumer<T>{void accept(T value);}')
 subprocess.run([JAVAC,'--release','8','-d',str(vendor),str(SRC/'ComposerWindowObserver.java'),str(ROOT/'tests/ComposerWindowObserverTest.java'),str(ROOT/'tests/email-stubs/android/app/Activity.java'),str(ROOT/'tests/email-stubs/android/graphics/Rect.java'),str(provider),str(consumer)],check=True,timeout=30)
 subprocess.run([str(JAVA),'-cp',str(vendor),'com.simon.mytasks.ComposerWindowObserverTest'],check=True,timeout=15)
 print('OEM vendor Consumer proxy variant passed; no physical OEM runtime claimed.')

"""Actual chat citation methods under host UI seams, not device proof."""
import os, pathlib, re, shutil, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
def method(file,name):
    text=(ROOT/'src/com/simon/mytasks'/file).read_text()
    m=re.search(r'^ (?:static )?[\w<>]+ '+name+r'\(',text,re.M)
    if not m: raise AssertionError('Missing production citation method '+name)
    start=text.index('{',m.start()); depth=0
    for token in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]',text[start:]):
        if token.group()=='{': depth+=1
        elif token.group()=='}':
            depth-=1
            if not depth:return text[m.start():start+token.end()]
    raise AssertionError('Unclosed method')
class InlineCitationsTest(unittest.TestCase):
    def test_actual_span_sources_task_and_fences(self):
        java_home=os.environ.get('JAVA_HOME')
        javac=str(pathlib.Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
        if not javac: self.fail('Set JAVA_HOME')
        with tempfile.TemporaryDirectory(prefix='inline-citation-') as tmp:
            p=pathlib.Path(tmp); harness=p/'InlineCitationHarness.java'
            template=(ROOT/'tests/InlineCitationHarness.java').read_text()
            actual='\n'.join(method('CitationUi.java',n) for n in ['render','sources','taskSource','bindLinks','linkAt'])
            render_rows=method('ConversationsUi.java','renderMessages')
            selection=next(line for line in render_rows.splitlines() if 'body.setCustomSelectionActionModeCallback' in line)
            accessibility=next(line for line in render_rows.splitlines() if 'body.setAccessibilityDelegate' in line)
            actual+='\n static void bindActions(MainActivity a,TextView body,JSONObject turn){final boolean outgoing=turn.optString("role").equals("user");final String original=turn.optString("content");final Store.Account rowOwner=a.account;final long rowGeneration=a.surfaces.generation();'+selection+accessibility+'}'
            harness.write_text(template.replace('// ACTUAL_METHODS',actual).replace('// ACTUAL_NAVIGATE',method('MainActivity.java','navigate')))
            jar=ROOT/'build/test-lib/json.jar'
            files=[ROOT/'src/com/simon/mytasks'/(n+'.java') for n in ['CitationPolicy','SafeMarkdown','ConversationState','PresentationPolicy','Core']]
            subprocess.run([javac,'--release','8','-cp',str(jar),'-d',tmp,str(harness),*map(str,files)],check=True)
            subprocess.run([str(pathlib.Path(javac).parent/'java'),'-cp',tmp+os.pathsep+str(jar),'com.simon.mytasks.InlineCitationHarness'],check=True)
    def test_selection_menu_and_accessibility_wiring(self):
        source=(ROOT/'src/com/simon/mytasks/ConversationsUi.java').read_text()
        for token in ['CitationUi.render(a,turn)','CitationUi.sources(a,turn)','7343','"Related sources"','messageMenu(turn)','ACTION_LONG_CLICK','CitationUi.bindLinks(body)']:
            self.assertIn(token,source)
        self.assertIn('copyOriginal(original)',source)
        self.assertIn('binding.equals(rowBindings.get(id))',source)
        self.assertNotIn('LinkMovementMethod.getInstance()',source)

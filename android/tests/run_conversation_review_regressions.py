#!/usr/bin/env python3
"""Host-only production policy/callback tests. No Android runtime or device claims."""
from pathlib import Path
import os, re, shutil, subprocess, tempfile, sys
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/com/simon/mytasks'

def method(file, name):
    source = (SRC/file).read_text()
    match = re.search(r'^ (?:static )?\w+(?:<[^>]+>)? '+re.escape(name)+r'\(', source, re.M)
    if not match:
        raise AssertionError('Missing production method '+name)
    start = source.index('{', match.start()); depth = 0
    for token in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]', source[start:]):
        if token.group() == '{': depth += 1
        elif token.group() == '}':
            depth -= 1
            if not depth: return source[match.start():start+token.end()]
    raise AssertionError('Unclosed method '+name)

javac = str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
assert javac, 'JDK required'
java = str(Path(javac).resolve().parent/'java')
cp = ROOT/'build/test-lib/json.jar'
assert cp.exists(), 'Run android/test.sh for verified JSON jar first'
mode = sys.argv[1] if len(sys.argv)>1 else 'all'
policy = SRC/'ConversationActionPolicy.java'
assert policy.exists(), 'Missing production ConversationActionPolicy: free threads still expose task-only actions'
template = (ROOT/'tests/ConversationReviewHarness.java').read_text()
if mode != 'menu':
    template = template.replace('// REVIEW_METHOD', method('MainActivity.java','reviewConversationAsTask'))
    template = template.replace('// EMPTY_METHOD', method('MainActivity.java','empty'))
    template = template.replace('// MESSAGE_METHOD', method('ConversationsUi.java','messageMenu'))
else:
    template = template.replace('// REVIEW_METHOD', 'void reviewConversationAsTask(String text){}').replace('// EMPTY_METHOD','').replace('// MESSAGE_METHOD','void messageMenu(String role,String original){}')
if mode != 'menu':
    editor = method('MainActivity.java', 'buildTaskEditor')
    assert 'if(selected.equals("NEW"))' in editor and 'DRAFT' in editor and 'Review' in editor, 'NEW editor must visibly label the unsaved proposal DRAFT and request review'
    assert 'Create task' in editor and '()->save(false)' in editor, 'Canonical reviewed Create route retained'
    assert 'messageMenu(turn.optString("role"),original)' in method('ConversationsUi.java', 'renderMessages'), 'Actual longpress must route role and original text'
thread_menu = method('ConversationsUi.java', 'threadMenu')
assert 'ConversationActionPolicy.threadActions(task,' in thread_menu and 'String action=keys[i]' in thread_menu, 'Production menu must use keyed policy'
assert not re.search(r'if\(i==', thread_menu), 'Hidden entries must not shift numeric action routes'
with tempfile.TemporaryDirectory(prefix='conversation-review-') as tmp:
    work = Path(tmp); harness=work/'ConversationReviewHarness.java'; harness.write_text(template)
    subprocess.run([javac,'--release','8','-cp',str(cp),'-d',str(work),str(harness),str(policy)],check=True)
    subprocess.run([java,'-cp',str(work)+os.pathsep+str(cp),'com.simon.mytasks.ConversationReviewHarness',mode],check=True)

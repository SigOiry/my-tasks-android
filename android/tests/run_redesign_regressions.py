#!/usr/bin/env python3
"""Host regressions executing extracted production methods, not Android runtime proof.
Synthetic transport, UI scheduling/render, Store, and notification manager seams live
only under tests. Requires JDK + android/build/test-lib/json.jar (from test.sh), no SDK.
Run from any directory; JAVA_HOME defaults to PATH javac for CI.
"""
from pathlib import Path
import os, re, shutil, subprocess, tempfile, sys
ANDROID = Path(__file__).resolve().parents[1]
SRC = ANDROID / 'src/com/simon/mytasks'
javac = str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
if not javac:
    raise SystemExit('Set JAVA_HOME to a JDK, or put javac/java on PATH.')
JAVA = Path(javac).resolve().parent

def method(file, name, signature=None):
    source = (SRC/file).read_text()
    match = re.search(signature or r'^ (?:static )?\w+(?:<[^>]+>)? '+re.escape(name)+r'\(', source, re.M)
    if not match:
        raise RuntimeError('Missing production method '+name)
    start = source.index('{', match.start()); depth = 0
    # Ignore braces in string/character literals and comments.
    tokens = re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]', source[start:])
    for token in tokens:
        if token.group() == '{': depth += 1
        elif token.group() == '}':
            depth -= 1
            if depth == 0: return source[match.start():start+token.end()]
    raise RuntimeError('Unclosed method '+name)

scratch = Path(os.environ.get('TMPDIR', tempfile.gettempdir()))
scratch.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='redesign-regressions-', dir=scratch) as work:
    work = Path(work)
    template = (ANDROID/'tests/RedesignRegressionHarness.java').read_text()
    names = ['refresh','quickMutation','mutation','markRead','dirty','task','copy','empty','text','openTaskConversation','renderKeepingConversation','captureDraft']
    extracted = '\n'.join(method('MainActivity.java', n) for n in names)
    source = (SRC/'MainActivity.java').read_text()
    if 'void refresh(boolean ' in source:
        extracted += '\n'+method('MainActivity.java','refresh',r'^ void refresh\(boolean ')
    # Execute the actual confirmed reload action, not a separately invented policy.
    action = re.search(r'"Reload latest task",\(\)->confirm\([^\n]+?,\(\)->\{(.*?)\}\)\)',source)
    if not action: raise RuntimeError('Cannot extract confirmed reload action')
    extracted += '\n void confirmedReload(){'+action.group(1)+'}'
    template = template.replace('// MAIN_METHODS', extracted)
    hydration = next(line.strip() for line in method('MainActivity.java','buildDetails').splitlines() if 'JSONObject current=task(selected);if(draft==null)' in line)
    template = template.replace('// HYDRATE_DRAFT', hydration)
    template = template.replace('// OWN_CHANGE_METHOD', method('TasksUi.java','acceptOwnChange'))
    template = template.replace('// THREAD_ACTION_METHOD',method('ConversationsUi.java','taskThreadAction'))
    # Real poll loop with Android API shells: synthetic events and OS manager only.
    template = template.replace('// POLL_METHOD',method('PollService.java','poll'))
    (work/'RedesignRegressionHarness.java').write_text(template)
    sources = [SRC/(n+'.java') for n in ['UiReadGate','UpdateReviewPolicy','UpdateEmphasis','Core']]
    if (SRC/'NotificationIdentity.java').exists(): sources.append(SRC/'NotificationIdentity.java')
    cp = ANDROID/'build/test-lib/json.jar'
    if not cp.exists():
        raise SystemExit('Run android/test.sh first to obtain its verified host JSON jar.')
    allowed = {'all','reload','quick','ack','own','route'}
    if len(sys.argv)>2 or (len(sys.argv)==2 and sys.argv[1] not in allowed):
        raise SystemExit('Expected one of: '+', '.join(sorted(allowed)))
    subprocess.run([str(JAVA/'javac'),'--release','8','-cp',str(cp),'-d',str(work),str(work/'RedesignRegressionHarness.java'),*map(str,sources)],check=True)
    subprocess.run([str(JAVA/'java'),'-cp',str(work)+os.pathsep+str(cp),'com.simon.mytasks.RedesignRegressionHarness',*sys.argv[1:]],check=True)

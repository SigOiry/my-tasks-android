#!/usr/bin/env python3
"""Current ConversationsUi methods with synthetic View/Layout geometry; not native proof."""
from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys, tempfile
from run_m5_native import method, SRC, ROOT

def main():
    names=['viewport','nearBottom','latestId','captureViewport','restoreViewport','jumpToLatest']
    source=(SRC/'ConversationsUi.java').read_text()
    # Optional collaborators are extracted verbatim once production introduces them.
    for name in ['readingDigest','readingBodyTop','readingGeometry','captureReading','readingRestoreY','readingInteraction']:
        if ' '+name+'(' in source:
            names.append(name)
    blocks=[method('ConversationsUi.java',n) for n in names]
    template=(ROOT/'android/tests/ReadingContinuityHarness.java').read_text()
    template=template.replace('// PRODUCTION_METHODS','\n'.join(blocks))
    build=method('ConversationsUi.java','buildThread')
    binding=build[build.index('  scroll=new ScrollView(a){'):build.index('  timeline=a.vertical();')]
    listener=next(line for line in build.splitlines() if line.strip().startswith('scroll.setOnScrollChangeListener('))
    template=template.replace('// SCROLL_BINDING',binding+'\n'+listener)
    render=method('ConversationsUi.java','renderMessages')
    assert 'wrapper.setTag(body);' in render,'Missing production body-only binding'
    template=template.replace('// BODY_BINDING','wrapper.setTag(body);')
    print('Scroll binding SHA-256 '+hashlib.sha256((binding+listener).encode()).hexdigest(),flush=True)
    print('Actual method SHA-256 '+json.dumps({n:hashlib.sha256(b.encode()).hexdigest() for n,b in zip(names,blocks)},sort_keys=True),flush=True)
    print('Source SHA-256 '+hashlib.sha256(source.encode()).hexdigest(),flush=True)
    javac=shutil.which('javac')
    if not os.environ.get('JAVA_HOME') and not javac:raise SystemExit('Set JAVA_HOME or put javac on PATH')
    java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(javac or '').resolve().parent
    cp=ROOT/'android/build/test-lib/json.jar'
    if not cp.exists():raise SystemExit('Run android/test.sh to bootstrap verified JSON dependency')
    with tempfile.TemporaryDirectory(prefix='reading-continuity-',dir=os.environ.get('TMPDIR')) as d:
        p=Path(d)/'ReadingContinuityHarness.java';p.write_text(template)
        subprocess.run([str(java/'javac'),'--release','8','-cp',str(cp),'-d',d,str(p),str(SRC/'ChatPresentationPolicy.java')],check=True)
        subprocess.run([str(java/'java'),'-cp',d+os.pathsep+str(cp),'com.simon.mytasks.ReadingContinuityHarness',*(sys.argv[1:] or ['all'])],check=True)
if __name__=='__main__':main()

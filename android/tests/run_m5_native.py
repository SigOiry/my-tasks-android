#!/usr/bin/env python3
"""Execute current M5 production methods with deterministic host Views, not native runtime evidence."""
from pathlib import Path
import os, re, shutil, subprocess, tempfile, sys, hashlib, json
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'android/src/com/simon/mytasks'
def method(file,name):
    source=(SRC/file).read_text()
    match=re.search(r'^ (?:@Override )?(?:public |protected |private )?(?:static )?[\w<>\[\].]+ '+re.escape(name)+r'\(',source,re.M)
    if not match: raise AssertionError('Missing production method '+name)
    start=source.index('{',match.start());depth=0
    for token in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]',source[start:]):
        if token.group()=='{':depth+=1
        elif token.group()=='}':
            depth-=1
            if not depth:return source[match.start():start+token.end()]
    raise AssertionError('Unclosed '+name)

def main():
    stage=sys.argv[1] if len(sys.argv)>1 else 'all'
    template=(ROOT/'android/tests/M5NativeHarness.java').read_text()
    if stage not in ('nav','task','chat','shell','all','repair-r1','repair-r2','repair-matrix','repair-outer-fallback','repair-outer-enablement','repair-outer-boundaries'):raise SystemExit('Unknown M5 stage: '+stage)
    groups={
        'NAV':['buildPrimaryTabs','buildRail','navigationClearance'],
        'TASK':['editorPresentation','untouchedEditor','capturedDetailY','restoreDetailScroll','captureDraft','captureTaskPresentation','restoreTaskPresentation','saveTaskPresentation','loadTaskPresentation','onConfigurationChanged','onSaveInstanceState','stateDraft'],
        'CHAT':['capture','viewport','captureComposerPresentation','restoreComposerPresentation','captureListPresentation','restoreListPresentation','listViewport'],
        'SHELL':['applyShellPolicy','readShellPolicy','updateShellGeometry'],
        'NAV_ROLE':['navigation'],
    }
    bindings=[('TASK_BINDING','MainActivity.java','buildTaskEditor','restoreTaskPresentation();'),('CHAT_BINDING','ConversationsUi.java','buildThread','restoreComposerPresentation();'),('LIST_BINDING','ConversationsUi.java','updateRows','restoreListPresentation();')]
    for marker,file,name,call in bindings:
        assert call in method(file,name),'Missing actual binding '+call
        template=template.replace('// '+marker,call)
    render=method('MainActivity.java','render')
    outer=next(line.strip() for line in method('MainActivity.java','buildDetails').splitlines() if line.strip().startswith('if(!taskPresentationPending)'))
    template=template.replace('// TASK_OUTER_BINDING',outer)
    outer_fields=next(line for line in (SRC/'MainActivity.java').read_text().splitlines() if line.startswith(' ScrollView pendingDetailScroll;'))
    template=template.replace('// OUTER_FIELDS',outer_fields)
    field_enablement=re.search(r'e\.setEnabled\(([^;]+)\);',method('MainActivity.java','field'))
    value_enablement=re.search(r'e\.setEnabled\(([^;]+)\);',method('MainActivity.java','valueField'))
    assert field_enablement and value_enablement,'Missing production field enablement'
    enablement=field_enablement.group(1)
    assert enablement==value_enablement.group(1),'Different field/valueField enablement requires separate host bindings'
    template=template.replace('// FIELD_ENABLEMENT','for(EditText f:new EditText[]{title,description,notes,project,due})f.setEnabled('+enablement+');')
    decision=next(line.strip() for line in render.splitlines() if 'applyShellPolicy(readShellPolicy(rootFrame))' in line)
    template=template.replace('// SHELL_DECISION',decision)
    # Execute exact lifecycle presentation seams, with field construction/rendering
    # replaced by the labelled host View binder. No copied retention algorithm.
    prefix=render[render.index('{')+1:render.index('  // A selected task')]
    template=template.replace('// RENDER_PRESENTATION',prefix)
    create=method('MainActivity.java','onCreate')
    hydration=next(line for line in create.splitlines() if 'if(saved!=null){selected=saved.getString(' in line)
    assert 'loadTaskPresentation(saved);' in create
    template=template.replace('// RECREATE_PRESENTATION',hydration+'\n loadTaskPresentation(saved);')
    hashes={'MainActivity.java:buildDetails:outer-binding':hashlib.sha256(outer.encode()).hexdigest(), 'MainActivity.java:field:enablement':hashlib.sha256(enablement.encode()).hexdigest()}
    for marker,names in groups.items():
        file='ConversationsUi.java' if marker=='CHAT' else 'WorkspaceUi.java' if marker=='NAV_ROLE' else 'MainActivity.java'
        blocks=[method(file,n) for n in names]
        for n,b in zip(names,blocks):hashes[file+':'+n]=hashlib.sha256(b.encode()).hexdigest()
        template=template.replace('// '+marker+'_METHODS','\n'.join(blocks))
    # Later stages' host assertions are added only with their corresponding tracer bullet.
    template=template.replace('STAGE_VALUE',stage)
    javac=shutil.which('javac')
    if not os.environ.get('JAVA_HOME') and not javac:raise SystemExit('Set JAVA_HOME or put javac on PATH')
    java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(javac or '').resolve().parent
    cp=ROOT/'android/build/test-lib/json.jar'
    if not cp.exists():raise SystemExit('Run android/test.sh to bootstrap its verified JSON dependency.')
    print('Actual method SHA-256 '+json.dumps(hashes,sort_keys=True),flush=True)
    with tempfile.TemporaryDirectory(prefix='m5-native-',dir=os.environ.get('TMPDIR')) as d:
        work=Path(d);p=work/'M5NativeHarness.java';p.write_text(template)
        helpers=[SRC/'CompactPolicy.java']
        if (SRC/'ShellLayoutPolicy.java').exists():helpers.append(SRC/'ShellLayoutPolicy.java')
        subprocess.run([str(java/'javac'),'--release','8','-cp',str(cp),'-d',d,str(p),*map(str,helpers)],check=True)
        subprocess.run([str(java/'java'),'-cp',d+os.pathsep+str(cp),'com.simon.mytasks.M5NativeHarness'],check=True)
if __name__=='__main__':main()

"""Prepare private copies only; does not compile, sign, install, or launch apps."""
import hashlib
import json
from pathlib import Path
import shutil

REPO = Path(__file__).resolve().parents[2]
ROOT = Path('/opt/data/cache/scratch/chat-search-20261114')
PACKAGE = 'com.simon.mytasks.qachat20261114'
RUNNER = 'com.simon.mytasks.uiqachat20261114'
REMOTE = '/home/simon/hermes-apks/qa-chat-search-20261114'


def main():
    ROOT.mkdir(mode=0o700, exist_ok=True)
    toolkit = ROOT/'toolkit/qa/liquid-glass'
    toolkit.mkdir(mode=0o700, parents=True)
    (toolkit/'native').mkdir(mode=0o700)
    for name in ['overlay.py', 'build-qa.sh', 'build-native-tests.sh', 'device.py', 'run_native.py', 'native/Acceptance.java']:
        text = (REPO/'qa/liquid-glass'/name).read_text()
        text = text.replace('com.simon.mytasks.uiqa', RUNNER).replace('com.simon.mytasks.qa', PACKAGE)
        if name == 'build-qa.sh':
            text = text.replace('ANDROID="$(cd "$HERE/../../android" && pwd)"', 'ANDROID="'+str(REPO/'android')+'"')
        if name == 'run_native.py':
            text = text.replace('/opt/data/cache/scratch/liquid-glass-runtime-20261109', str(ROOT/'runtime'))
            text = text.replace('/home/simon/hermes-apks/qa-runtime', REMOTE)
            text = text.replace('/home/simon/hermes-apks/ui-acceptance.apk', REMOTE+'/ui-acceptance.apk')
            text = text.replace("'native-ui-tests-'", "'chat-search-native-ui-tests-'")
        if name == 'device.py':
            text = text.replace("EVIDENCE=ROOT/'evidence'", "EVIDENCE=Path("+repr(str(REPO/'qa/chat-search-20261114/evidence'))+")")
            text = text.replace("REMOTE='/home/simon/hermes-apks/evidence'", "REMOTE='"+REMOTE+"/evidence'")
            text = text.replace("[ADB,*map(str,args)]", "[ADB,'-s','emulator-5554',*map(str,args)]")
        target = toolkit/name
        if target.exists():
            raise RuntimeError('Refusing to overwrite prepared tooling: '+str(target))
        target.write_text(text)
        target.chmod(0o600)
    source = toolkit/'overlay.py'
    text = source.read_text().replace("default=Path(__file__).resolve().parents[2]/'android'", "default=Path("+repr(str(REPO/'android'))+")")
    source.write_text(text)
    (ROOT/'runtime').mkdir(mode=0o700, exist_ok=True)
    (REPO/'qa/chat-search-20261114/evidence').mkdir(mode=0o700, exist_ok=True)
    print(json.dumps({'toolkit': str(toolkit), 'runtime': str(ROOT/'runtime'),
                      'package': PACKAGE, 'runner': RUNNER,
                      'prepared_files': {str(p.relative_to(toolkit)):hashlib.sha256(p.read_bytes()).hexdigest()
                                         for p in toolkit.rglob('*') if p.is_file()},
                      'built': False, 'installed': False}, indent=2))


if __name__ == '__main__':
    main()

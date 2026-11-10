"""Dependency-free reproducible web bundle. Run after source changes."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ('index.html','app.css','app.js','policy.mjs','conversations.mjs','email-workspace.mjs','citations.mjs','icon.svg','inter-variable.woff2','INTER-LICENSE.txt')

def build():
    output = ROOT/'web_dist'
    output.mkdir(exist_ok=True)
    digest = hashlib.sha256()
    for name in ASSETS:
        content = (ROOT/'web'/name).read_bytes()
        digest.update(name.encode()+b'\0'+content)
        shutil.copyfile(ROOT/'web'/name,output/name)
    version = 'web-'+digest.hexdigest()[:12]
    (output/'manifest.json').write_text(json.dumps({'version':version,'assets':list(ASSETS)},sort_keys=True)+'\n')
    print(version)

if __name__ == '__main__':
    build()

#!/usr/bin/env python3
"""Generate public release metadata only from a verified, already-signed APK.
No signing key/password is read. Requires Android SDK aapt2/apksigner and a JDK.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

MAX_APK=100*1024*1024

def derive(badging, certificates, data):
    match=re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",badging,re.M)
    fingerprints=re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$',certificates,re.M)
    if not match or len(fingerprints)!=1: raise ValueError('Expected one signed APK with readable package/version')
    package, code, name=match.groups()
    if package!='com.simon.mytasks' or not 0<int(code)<=2147483647 or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,79}',name): raise ValueError('Invalid My Tasks package/version')
    if not 0<len(data)<=MAX_APK: raise ValueError('APK must contain 1..100 MiB')
    return dict(schema_version=1,version_code=int(code),version_name=name,package_name=package,apk_asset='my-tasks.apk',sha256=hashlib.sha256(data).hexdigest(),size=len(data),certificate_sha256=fingerprints[0].lower())

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--out-dir',type=Path,help='Defaults to APK directory')
    parser.add_argument('--build-tools',type=Path,help='Android build-tools directory (default SDK build-tools/35.0.0)')
    parser.add_argument('--expected-certificate',help='Optional known 64-hex release certificate fingerprint; fail on mismatch')
    args=parser.parse_args()
    sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home()/'Android'/'Sdk')
    tools=args.build_tools or Path(os.environ.get('ANDROID_BUILD_TOOLS',sdk/'build-tools'/'35.0.0'))
    apk=args.apk.resolve()
    if apk.name!='my-tasks.apk': parser.error('APK asset must be named exactly my-tasks.apk')
    if not apk.is_file() or not 0<apk.stat().st_size<=MAX_APK: parser.error('APK must contain 1..100 MiB')
    cert=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
    badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(apk)],text=True)
    metadata=derive(badging,cert,apk.read_bytes())
    if args.expected_certificate is not None:
        if not re.fullmatch(r'[0-9a-fA-F]{64}',args.expected_certificate) or args.expected_certificate.lower()!=metadata['certificate_sha256']: parser.error('Signing certificate does not match expected identity')
    out=(args.out_dir or apk.parent).resolve()
    out.mkdir(parents=True,exist_ok=True)
    payload=(json.dumps(metadata,indent=2,sort_keys=True)+'\n').encode()
    (out/'update.json').write_bytes(payload)
    (out/'SHA256SUMS').write_text(f"{metadata['sha256']}  my-tasks.apk\n{hashlib.sha256(payload).hexdigest()}  update.json\n")
    print(json.dumps(metadata,indent=2,sort_keys=True))
    print(f'Wrote {out / "update.json"} and SHA256SUMS')

if __name__=='__main__': main()

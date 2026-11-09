"""Generate scratch-only QA manifest and loopback TLS trust; never alter app code."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

SCRATCH = Path('/opt/data/cache/scratch')
A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',A[1:-1])


def generate(android, output, certificate):
    android, output, certificate = Path(android).resolve(), Path(output).resolve(), Path(certificate).resolve()
    if not output.is_relative_to(SCRATCH) or output == SCRATCH or output.exists():
        raise ValueError('Output must be a new scratch child; no overwrite permitted')
    if not certificate.is_relative_to(SCRATCH):
        raise ValueError('Certificate must belong to synthetic scratch runtime')
    cert = certificate.read_bytes()
    if not cert.startswith(b'-----BEGIN CERTIFICATE-----') or b'PRIVATE KEY' in cert:
        raise ValueError('Expected public PEM certificate only')
    manifest = ET.parse(android/'AndroidManifest.xml')
    root = manifest.getroot()
    if root.get('package') != 'com.simon.mytasks':
        raise ValueError('Unexpected production namespace')
    root.set('package','com.simon.mytasks.qa')
    app = root.find('application')
    assert app is not None
    app.set(A+'label','My Tasks · Synthetic QA')
    app.set(A+'usesCleartextTraffic','false')
    app.set(A+'networkSecurityConfig','@xml/qa_network')
    for node in app:
        name = node.get(A+'name','')
        if name.startswith('.'): node.set(A+'name','com.simon.mytasks'+name)
        if node.tag == 'provider':
            authority = node.get(A+'authorities','')
            if authority.startswith('com.simon.mytasks.'):
                node.set(A+'authorities',authority.replace('com.simon.mytasks.','com.simon.mytasks.qa.',1))
    output.mkdir(mode=0o700,parents=True)
    (output/'res/xml').mkdir(parents=True)
    (output/'res/raw').mkdir(parents=True)
    manifest.write(output/'AndroidManifest.xml',encoding='unicode')
    (output/'res/raw/qa_certificate.pem').write_bytes(cert)
    (output/'res/xml/qa_network.xml').write_text('''<network-security-config>
 <base-config cleartextTrafficPermitted="false"/>
 <domain-config cleartextTrafficPermitted="false">
  <domain includeSubdomains="false">127.0.0.1</domain>
  <trust-anchors><certificates src="@raw/qa_certificate"/></trust-anchors>
 </domain-config>
</network-security-config>
''')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android',type=Path,default=Path(__file__).resolve().parents[2]/'android')
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--certificate',type=Path,required=True)
    args = parser.parse_args()
    generate(args.android,args.output,args.certificate)
    print('QA TLS overlay generated; no source mutation, signing or installation performed.')

if __name__ == '__main__': main()

"""Generated-only Android QA trust; release HTTPS validator remains byte-identical."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ANDROID = HERE.parents[1]/'android'
A = '{http://schemas.android.com/apk/res/android}'

class OverlayTests(unittest.TestCase):
    def test_generated_overlay_has_separate_identity_and_private_tls_trust(self):
        spec = importlib.util.spec_from_file_location('qa_overlay',HERE/'overlay.py')
        assert spec and spec.loader
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        original = (ANDROID/'src/com/simon/mytasks/Core.java').read_bytes()
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as root:
            output = Path(root)/'overlay'
            cert = Path(root)/'certificate.pem'
            cert.write_text('-----BEGIN CERTIFICATE-----\nSYNTHETIC TEST INPUT\n-----END CERTIFICATE-----\n')
            module.generate(ANDROID,output,cert)
            manifest = ET.parse(output/'AndroidManifest.xml').getroot()
            self.assertEqual(manifest.get('package'),'com.simon.mytasks.qa')
            app = manifest.find('application')
            assert app is not None
            self.assertEqual(app.get(A+'usesCleartextTraffic'),'false')
            activity,provider = app.find('activity'),app.find('provider')
            assert activity is not None and provider is not None
            self.assertEqual(activity.get(A+'name'),'com.simon.mytasks.MainActivity')
            self.assertEqual(provider.get(A+'authorities'),'com.simon.mytasks.qa.updates')
            policy = ET.parse(output/'res/xml/qa_network.xml').getroot()
            base = policy.find('base-config')
            assert base is not None
            self.assertEqual(base.get('cleartextTrafficPermitted'),'false')
            self.assertEqual([d.text for d in policy.findall('domain-config/domain')],['127.0.0.1'])
            self.assertEqual(policy.find('domain-config/trust-anchors/certificates').get('src'),'@raw/qa_certificate')
            self.assertEqual((output/'res/raw/qa_certificate.pem').read_bytes(),cert.read_bytes())
            self.assertFalse((output/'src').exists(),'must not override HTTPS/auth source')
            with self.assertRaises(ValueError): module.generate(ANDROID,ANDROID,cert)
            with self.assertRaises(ValueError): module.generate(ANDROID,output,cert)
        self.assertEqual((ANDROID/'src/com/simon/mytasks/Core.java').read_bytes(),original)

if __name__ == '__main__': unittest.main()

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent

class BuildTests(unittest.TestCase):
    def test_real_qa_apk_has_isolated_identity_and_tls_manifest(self):
        self.assertTrue((HERE/'build-qa.sh').exists(),'QA build script not implemented')
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as name:
            root = Path(name)
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-subj','/CN=Synthetic QA',
                '-addext','subjectAltName=IP:127.0.0.1','-keyout',str(root/'qa-key.pem'),'-out',str(root/'qa-cert.pem')],capture_output=True,check=True)
            build = root/'build'
            result = subprocess.run(['bash',str(HERE/'build-qa.sh'),str(root),str(build)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertTrue((build/'my-tasks-qa.apk').exists())
            second = root/'build-second'
            result = subprocess.run(['bash',str(HERE/'build-qa.sh'),str(root),str(second)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            tools = Path(os.environ.get('ANDROID_HOME','/opt/data/cache/android-build/sdk'))/'build-tools/35.0.0'
            badging = subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(build/'my-tasks-qa.apk')],text=True)
            self.assertIn("package: name='com.simon.mytasks.qa'",badging)
            self.assertIn("launchable-activity: name='com.simon.mytasks.MainActivity'",badging)
            env = {**os.environ,'JAVA_HOME':'/opt/data/cache/android-build/jdk-17.0.2'}
            certificate_reports = [subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(p/'my-tasks-qa.apk')],text=True,env=env) for p in (build,second)]
            digests = [[line for line in report.splitlines() if 'certificate SHA-256 digest' in line] for report in certificate_reports]
            self.assertEqual(digests[0],digests[1],'QA rebuild must preserve its own isolated signing identity')

if __name__ == '__main__': unittest.main()

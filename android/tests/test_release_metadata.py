import importlib.util
import pathlib
import unittest
spec=importlib.util.spec_from_file_location('release_metadata', pathlib.Path(__file__).parents[1]/'release_metadata.py')
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
class MetadataTests(unittest.TestCase):
    def test_exact_fields_derived_from_verified_apk(self):
        cert='a'*64
        info=module.derive("package: name='com.simon.mytasks' versionCode='2' versionName='1.1.0' compileSdkVersion='35'", 'Signer #1 certificate SHA-256 digest: '+cert, b'abc')
        self.assertEqual(info,{'schema_version':1,'version_code':2,'version_name':'1.1.0','package_name':'com.simon.mytasks','apk_asset':'my-tasks.apk','sha256':'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad','size':3,'certificate_sha256':cert})
    def test_rejects_different_package(self):
        with self.assertRaises(ValueError): module.derive("package: name='evil.app' versionCode='2' versionName='1.1.0'",'Signer #1 certificate SHA-256 digest: '+'a'*64,b'abc')
    def test_rejects_multiple_signers(self):
        with self.assertRaises(ValueError): module.derive("package: name='com.simon.mytasks' versionCode='2' versionName='1.1.0'",'Signer #1 certificate SHA-256 digest: '+'a'*64+'\nSigner #2 certificate SHA-256 digest: '+'b'*64,b'abc')
    def test_rejects_empty_apk(self):
        with self.assertRaises(ValueError): module.derive("package: name='com.simon.mytasks' versionCode='2' versionName='1.1.0'",'Signer #1 certificate SHA-256 digest: '+'a'*64,b'')
if __name__=='__main__': unittest.main()

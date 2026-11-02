import unittest
from verify_apk import check_manifest, certificate_digest


class ApkContractTest(unittest.TestCase):
    def manifest(self, package="com.danishbears.bulgariantrainer", code="2", debug=False):
        return f"package: name='{package}' versionCode='{code}' versionName='0.2.0'\nsdkVersion:'26'\n" + ("application-debuggable\n" if debug else "")

    def test_accepts_expected_release_and_debug_manifests(self):
        for debug in (False, True):
            check_manifest(self.manifest(debug=debug), "", 2, "0.2.0", debug)

    def test_rejects_another_application_or_stale_version(self):
        for manifest in (self.manifest(package="wrong.app"), self.manifest(code="1")):
            with self.assertRaises(ValueError):
                check_manifest(manifest, "", 2, "0.2.0", False)

    def test_rejects_debuggable_release(self):
        with self.assertRaises(ValueError):
            check_manifest(self.manifest(debug=True), "", 2, "0.2.0", False)

    def test_rejects_network_and_broad_storage_permissions(self):
        for permission in ("INTERNET", "MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"):
            with self.assertRaises(ValueError):
                check_manifest(self.manifest(), f"uses-permission: name='android.permission.{permission}'", 2, "0.2.0", False)

    def test_certificate_parser_requires_one_real_sha256_digest(self):
        digest = "a1" * 32
        self.assertEqual(digest, certificate_digest(f"Signer #1 certificate SHA-256 digest: {digest}\n"))
        for text in ("", "Signer #1 certificate SHA-256 digest: 123\n",
                     f"Signer #1 certificate SHA-256 digest: {digest}\nSigner #2 certificate SHA-256 digest: {digest}\n"):
            with self.assertRaises(ValueError):
                certificate_digest(text)


if __name__ == "__main__":
    unittest.main()

import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_release_signing import OFFICIAL_CERTIFICATE, signing_certificates, verify


class VerifyReleaseSigningTest(unittest.TestCase):
    def test_sdk_35_and_36_signer_output(self):
        self.assertEqual(signing_certificates(f"Signer #1 certificate SHA-256 digest: {OFFICIAL_CERTIFICATE}\n"), {OFFICIAL_CERTIFICATE})

    def test_sdk_37_and_rotated_signer_labels(self):
        for label in ("V2 Signer:", "V3 Signer:", "V3.1 Signer:", "Signer (minSdkVersion=24, maxSdkVersion=35)"):
            with self.subTest(label=label):
                self.assertEqual(signing_certificates(f"{label} certificate SHA-256 digest: {OFFICIAL_CERTIFICATE.upper()}\n"), {OFFICIAL_CERTIFICATE})

    def test_source_stamp_is_not_an_application_signer(self):
        text = f"V2 Signer: certificate SHA-256 digest: {OFFICIAL_CERTIFICATE}\nSource Stamp Signer certificate SHA-256 digest: {'0' * 64}\n"
        self.assertEqual(signing_certificates(text), {OFFICIAL_CERTIFICATE})

    def test_unparseable_output_and_wrong_key_are_distinct_failures(self):
        for output, message in (("unrecognized output", "Cannot read"), (f"V2 Signer: certificate SHA-256 digest: {'0' * 64}\n", "mismatch")):
            with self.subTest(message=message), patch("verify_release_signing.subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout=output)):
                with self.assertRaisesRegex(ValueError, message):
                    verify([Path("release.apk")], Path("apksigner"))


if __name__ == "__main__":
    unittest.main()

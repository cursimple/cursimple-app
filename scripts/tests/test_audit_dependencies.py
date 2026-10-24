import importlib.util
import hashlib
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("audit", Path(__file__).parents[1] / "audit_dependencies.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
COORDINATE = {"package": {"ecosystem": "Maven", "name": "example:library"}, "version": "1"}


class AuditTests(unittest.TestCase):
    def test_vendored_npm_inventory_is_audited(self):
        result = audit.audit({"queries": [{"package": {"ecosystem": "npm", "name": "jquery"}, "version": "3.7.1"}]},
                             lambda *_: {"results": [{}]})
        self.assertEqual(1, result["package_count"])
        self.assertEqual([], result["findings"])

    def test_vendored_checksum_and_containment_fail_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "library.js").write_bytes(b"safe")
            item = {"path": "library.js", "sha256": hashlib.sha256(b"safe").hexdigest()}
            audit.verify_artifacts({"artifacts": [item]}, root)
            (root / "library.js").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                audit.verify_artifacts({"artifacts": [item]}, root)
            with self.assertRaisesRegex(ValueError, "artifact path"):
                audit.verify_artifacts({"artifacts": [{**item, "path": "../outside.js"}]}, root)

    def test_empty_inventory_and_incomplete_response_fail_closed(self):
        with self.assertRaises(ValueError):
            audit.audit({"queries": []})
        with self.assertRaises(ValueError):
            audit.audit({"queries": [COORDINATE]}, lambda *_: {"results": []})

    def test_findings_retain_coordinate_and_skip_withdrawn_advisories(self):
        def api(path, payload=None):
            if path == "querybatch":
                return {"results": [{"vulns": [{"id": "live"}, {"id": "withdrawn"}]}]}
            return {"id": path.split("/")[1], "withdrawn": "2026-01-01" if path.endswith("withdrawn") else None}
        result = audit.audit({"queries": [COORDINATE]}, api)
        self.assertEqual(1, result["package_count"])
        self.assertEqual(["live"], [x["id"] for x in result["findings"]])
        self.assertEqual(COORDINATE, result["findings"][0]["coordinate"])

    def test_network_failure_propagates(self):
        def unavailable(*_):
            raise OSError("API unavailable")
        with self.assertRaises(OSError):
            audit.audit({"queries": [COORDINATE]}, unavailable)

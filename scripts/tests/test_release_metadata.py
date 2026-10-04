import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import release_metadata as release


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.properties = self.root / "gradle.properties"
        self.apks = self.root / "release"
        self.apks.mkdir()
        self.feed = self.root / "feed"
        self.feed.mkdir()

    def properties_for(self, channel="beta", version="0.7.5", code="31"):
        content = f"app.versionCode={code}\napp.versionName={version}\n"
        if channel is not None:
            content += f"app.releaseChannel={channel}\n"
        self.properties.write_text(content, encoding="utf-8")

    def manifest_for(self, prerelease=True, code=31, version="0.7.5"):
        return {
            "schemaVersion": 1,
            "versionCode": code,
            "versionName": version,
            "tagName": f"v{version}",
            "prerelease": prerelease,
            "assets": [],
        }

    def run_cli(self, *args):
        return subprocess.run(
            [sys.executable, "-B", str(SCRIPTS / "release_metadata.py"), *map(str, args)],
            capture_output=True, text=True, check=False,
        )

    def test_channel_matrix(self):
        cases = (
            ("0.7.5", "beta", True),
            ("0.7.5", "stable", False),
            ("0.7.5", None, False),
            ("0.7.5-beta.1", None, True),
            ("0.7.5-rc.1", None, True),
            ("0.7.5-beta.1", "stable", False),
            ("0.7.4", "beta", True),
        )
        for version, channel, prerelease in cases:
            with self.subTest(version=version, channel=channel):
                self.properties_for(channel, version)
                metadata = release.resolve_metadata(self.properties, f"v{version}")
                self.assertEqual(metadata["prerelease"], prerelease)
                self.assertEqual(metadata["channel"], "beta" if prerelease else "stable")
                self.assertEqual(metadata["versionName"], version)
                self.assertEqual(metadata["versionCode"], 31)

    def test_invalid_channel_never_falls_back(self):
        for channel in ("", "Beta", "release", "alpha", "beta # comment"):
            for version in ("0.7.5", "0.7.5-beta.1"):
                with self.subTest(channel=channel, version=version):
                    self.properties_for(channel, version)
                    with self.assertRaisesRegex(ValueError, "app.releaseChannel"):
                        release.resolve_metadata(self.properties, f"v{version}")

    def test_property_spacing_comments_and_bom(self):
        self.properties.write_text(
            "\ufeff# ignored\n! ignored\napp.versionCode : 31\n"
            "app.versionName = 0.7.5\napp.releaseChannel beta\n",
            encoding="utf-8",
        )
        self.assertTrue(release.resolve_metadata(self.properties, "v0.7.5")["prerelease"])

    def test_tag_must_match_version_name_exactly(self):
        self.properties_for()
        for tag in ("v0.7.4", "0.7.5", "v0.7.5-beta.1"):
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "does not match"):
                release.resolve_metadata(self.properties, tag)

    def test_missing_or_invalid_version(self):
        for code in ("", "abc", "0", "-1"):
            with self.subTest(code=code):
                self.properties_for(code=code)
                with self.assertRaisesRegex(ValueError, "versionCode"):
                    release.resolve_metadata(self.properties, "v0.7.5")
        self.properties.write_text("app.versionName=0.7.5\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "versionCode"):
            release.resolve_metadata(self.properties, "v0.7.5")
        self.properties_for(version="")
        with self.assertRaisesRegex(ValueError, "versionName"):
            release.resolve_metadata(self.properties, "v0.7.5")

    def test_resolve_cli_outputs_and_latest_matrix(self):
        for channel, expected, latest in (("beta", "true", "false"), ("stable", "false", "legacy")):
            with self.subTest(channel=channel):
                self.properties_for(channel)
                output = self.root / f"{channel}-output"
                output.write_text("previous=value\n", encoding="utf-8")
                result = self.run_cli("resolve", "--properties", self.properties, "--tag", "v0.7.5", "--github-output", output)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, f"channel={channel}\nprerelease={expected}\nmake_latest={latest}\n")
                self.assertEqual(output.read_text(), "previous=value\n" + result.stdout)

    def test_invalid_cli_does_not_emit_outputs(self):
        self.properties_for("invalid")
        output = self.root / "output"
        output.write_text("previous=value\n", encoding="utf-8")
        result = self.run_cli("resolve", "--properties", self.properties, "--tag", "v0.7.5", "--github-output", output)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("app.releaseChannel", result.stderr)
        self.assertEqual(output.read_text(), "previous=value\n")

    def test_manifest_cli_hashes_all_abis_and_is_deterministic(self):
        self.properties_for()
        for abi in release.ABIS:
            (self.apks / f"CurSimple-{abi}.apk").write_bytes(f"fixture-{abi}".encode())
        args = ("manifest", "--properties", self.properties, "--tag", "v0.7.5",
                "--repository", "example/cursimple", "--release-dir", self.apks)
        result = self.run_cli(*args)
        self.assertEqual(result.returncode, 0, result.stderr)
        target = self.apks / "update.json"
        original = target.read_bytes()
        mtime = target.stat().st_mtime_ns
        manifest = json.loads(original)
        self.assertEqual(manifest["versionCode"], 31)
        self.assertEqual(manifest["tagName"], "v0.7.5")
        self.assertIs(manifest["prerelease"], True)
        self.assertEqual([asset["fileName"] for asset in manifest["assets"]], sorted(f"CurSimple-{abi}.apk" for abi in release.ABIS))
        for asset in manifest["assets"]:
            self.assertEqual(asset["sha256"], hashlib.sha256((self.apks / asset["fileName"]).read_bytes()).hexdigest())
            self.assertEqual(asset["downloadUrl"], f"https://github.com/example/cursimple/releases/download/v0.7.5/{asset['fileName']}")
        result = self.run_cli(*args)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(target.read_bytes(), original)
        self.assertEqual(target.stat().st_mtime_ns, mtime)

    def test_manifest_rejects_missing_and_unexpected_apks(self):
        self.properties_for()
        metadata = release.resolve_metadata(self.properties, "v0.7.5")
        with self.assertRaisesRegex(ValueError, "five release APKs"):
            release.generate_manifest(metadata, "example/repo", self.apks)
        for abi in release.ABIS:
            (self.apks / f"CurSimple-{abi}.apk").write_bytes(b"fixture")
        (self.apks / "CurSimple-unknown.apk").write_bytes(b"fixture")
        with self.assertRaisesRegex(ValueError, "unexpected"):
            release.generate_manifest(metadata, "example/repo", self.apks)

    def test_beta_only_updates_beta_and_preserves_repaired_stable(self):
        stable = self.feed / "stable.json"
        for previous in ({"schemaVersion": 1, "noRelease": True}, self.manifest_for(False, 20, "0.6.0")):
            with self.subTest(previous=previous):
                release.write_json(stable, previous)
                before = stable.read_bytes()
                release.write_json(self.feed / "beta.json", self.manifest_for(False, 30, "0.7.4"))
                self.assertEqual(release.update_feed(self.manifest_for(), self.feed, True), ["beta.json"])
                self.assertEqual(stable.read_bytes(), before)
                beta = json.loads((self.feed / "beta.json").read_bytes())
                self.assertEqual(beta["tagName"], "v0.7.5")
                self.assertIs(beta["prerelease"], True)

    def test_stable_updates_both_feeds(self):
        manifest = self.manifest_for(False)
        self.assertEqual(release.update_feed(manifest, self.feed, False), ["beta.json", "stable.json"])
        for name in ("beta.json", "stable.json"):
            self.assertEqual(json.loads((self.feed / name).read_bytes()), manifest)

    def test_first_beta_creates_empty_stable_sentinel(self):
        self.assertEqual(release.update_feed(self.manifest_for(), self.feed, True), ["beta.json", "stable.json"])
        self.assertEqual(json.loads((self.feed / "stable.json").read_bytes()), {"schemaVersion": 1, "noRelease": True})

    def test_feed_retries_do_not_rewrite_or_report_changes(self):
        manifest = self.manifest_for()
        release.update_feed(manifest, self.feed, True)
        before = {path.name: (path.read_bytes(), path.stat().st_mtime_ns) for path in self.feed.iterdir()}
        self.assertEqual(release.update_feed(manifest, self.feed, True), [])
        self.assertEqual({path.name: (path.read_bytes(), path.stat().st_mtime_ns) for path in self.feed.iterdir()}, before)

    def test_old_tag_rerun_never_overwrites_newer_feeds(self):
        newer = self.manifest_for(False, 32, "0.7.6")
        release.update_feed(newer, self.feed, False)
        self.assertEqual(release.update_feed(self.manifest_for(), self.feed, True), [])
        self.assertEqual(release.update_feed(self.manifest_for(False), self.feed, False), [])
        for name in ("beta.json", "stable.json"):
            self.assertEqual(json.loads((self.feed / name).read_bytes()), newer)

    def test_stable_does_not_replace_a_newer_beta(self):
        release.update_feed(self.manifest_for(True, 32, "0.7.6-beta.1"), self.feed, True)
        self.assertEqual(release.update_feed(self.manifest_for(False), self.feed, False), ["stable.json"])
        self.assertEqual(json.loads((self.feed / "beta.json").read_bytes())["versionCode"], 32)
        self.assertEqual(json.loads((self.feed / "stable.json").read_bytes())["versionCode"], 31)

    def test_same_code_can_correct_beta_classification(self):
        release.write_json(self.feed / "beta.json", self.manifest_for(False, 30, "0.7.4"))
        release.write_json(self.feed / "stable.json", {"schemaVersion": 1, "noRelease": True})
        self.assertEqual(release.update_feed(self.manifest_for(True, 30, "0.7.4"), self.feed, True), ["beta.json"])
        self.assertIs(json.loads((self.feed / "beta.json").read_bytes())["prerelease"], True)

    def test_feed_rejects_mismatched_channel_and_invalid_codes_before_writing(self):
        cases = ((self.manifest_for(True), False), (self.manifest_for(False), True),
                 (self.manifest_for(True, 0), True), (self.manifest_for(True, "31"), True),
                 (self.manifest_for(True, True), True), (self.manifest_for("true"), True))
        for manifest, prerelease in cases:
            with self.subTest(manifest=manifest, prerelease=prerelease):
                with self.assertRaises(ValueError):
                    release.update_feed(manifest, self.feed, prerelease)
                self.assertEqual(list(self.feed.iterdir()), [])

    def test_corrupt_stable_prevents_partial_stable_publication(self):
        (self.feed / "stable.json").write_text('{"versionCode":"bad"}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "stable.json"):
            release.update_feed(self.manifest_for(False), self.feed, False)
        self.assertFalse((self.feed / "beta.json").exists())

    def test_feed_cli_reports_only_changed_names_and_rejects_bad_boolean(self):
        manifest = self.apks / "update.json"
        release.write_json(manifest, self.manifest_for())
        args = ("feed", "--manifest", manifest, "--feed-dir", self.feed, "--prerelease")
        result = self.run_cli(*args, "True")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.feed.iterdir()), [])
        result = self.run_cli(*args, "true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "beta.json\nstable.json\n")
        result = self.run_cli(*args, "true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()

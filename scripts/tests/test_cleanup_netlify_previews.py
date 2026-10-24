import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("cleanup", Path(__file__).parents[1] / "cleanup_netlify_previews.py")
cleanup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cleanup)
SITE = "test-site"
CURRENT = "a" * 24
OLD = "b" * 24
URL = f"https://{CURRENT}--cursimple.netlify.app"


def deploy(key=OLD, **changes):
    result = dict(id=key, site_id=SITE, branch="pre", context="deploy-preview", state="ready",
                  created_at="2026-10-22T00:00:00Z", deploy_ssl_url=URL)
    result.update(changes)
    return result


class CleanupTests(unittest.TestCase):
    def api(self, records, current=None, published=None, fail_delete=False):
        self.deleted = []
        self.pages = []
        def request(method, path):
            if method == "DELETE":
                if fail_delete:
                    raise OSError("API unavailable")
                self.deleted.append(path.rsplit("/", 1)[1])
                return None
            if "?" in path:
                page = int(path.rsplit("=", 1)[1])
                self.pages.append(page)
                return records[(page-1)*100:page*100]
            if path == f"/sites/{SITE}":
                return {"id": SITE, "published_deploy": {"id": published}}
            key = path.rsplit("/", 1)[1]
            if key == CURRENT:
                value = deploy(CURRENT)
                value["created_at"] = "2026-10-23T00:00:00Z"
                value.update(current or {})
                return value
            return next(item for item in records if item["id"] == key)
        return request

    def test_deletes_only_old_pre_and_preserves_production_other_site_branch_newer_and_locked(self):
        records = [deploy(), deploy("prod", context="production"), deploy("main", branch="main"),
                   deploy("other", site_id="other-site"), deploy("locked", locked=True),
                   deploy("published", published_at="2026-10-22T00:00:00Z")]
        newer = deploy("newer"); newer["created_at"] = "2026-10-24T00:00:00Z"; records.append(newer)
        self.assertEqual(1, cleanup.cleanup(SITE, URL, self.api(records)))
        self.assertEqual([OLD], self.deleted)

    def test_paginates_before_deleting(self):
        records = [deploy(f"old-{i}") for i in range(101)]
        self.assertEqual(101, cleanup.cleanup(SITE, URL, self.api(records)))
        self.assertEqual([1, 2], self.pages)

    def test_rejects_production_or_unready_current_before_deleting(self):
        for state in [{"context": "production"}, {"state": "building"}, {"site_id": "other"}, {"branch": "main"}]:
            with self.subTest(state=state), self.assertRaises(ValueError):
                cleanup.cleanup(SITE, URL, self.api([deploy()], current=state))
            self.assertEqual([], self.deleted)

    def test_published_deployment_is_never_deleted(self):
        with self.assertRaises(ValueError):
            cleanup.cleanup(SITE, URL, self.api([deploy()], published=OLD))
        self.assertEqual([], self.deleted)

    def test_api_failure_is_not_reported_as_success(self):
        with self.assertRaises(OSError):
            cleanup.cleanup(SITE, URL, self.api([deploy()], fail_delete=True))

    def test_missing_production_metadata_or_changed_preview_is_never_deleted(self):
        for replacement in [{}, {"id": "other-site", "published_deploy": None},
                            {"id": SITE}]:
            base = self.api([deploy()])
            def api(method, path):
                return replacement if path == f"/sites/{SITE}" else base(method, path)
            with self.assertRaises(ValueError):
                cleanup.cleanup(SITE, URL, api)
            self.assertEqual([], self.deleted)
        base = self.api([deploy()])
        def changed(method, path):
            value = base(method, path)
            if method == "GET" and path.endswith(f"/deploys/{OLD}"):
                return dict(value, context="production")
            return value
        with self.assertRaises(ValueError):
            cleanup.cleanup(SITE, URL, changed)
        self.assertEqual([], self.deleted)

    def test_incomplete_or_invalid_listing_stops_before_any_delete(self):
        base = self.api([deploy()])
        def invalid(method, path):
            return {} if "?" in path else base(method, path)
        with self.assertRaises(ValueError):
            cleanup.cleanup(SITE, URL, invalid)
        self.assertEqual([], self.deleted)

    def test_invalid_urls_and_ids_fail_closed(self):
        for url in ["https://pre--site.netlify.app", "https://evil.example", URL + "?bad=1", URL.replace("https:", "http:")]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                cleanup.cleanup(SITE, url, self.api([]))
        with self.assertRaises(ValueError):
            cleanup.cleanup("../another-site", URL, self.api([]))

    def test_legacy_preview_marker_is_supported_but_arbitrary_title_is_protected(self):
        legacy = deploy(); legacy.update(branch=None, title="Preview from pre (" + "c"*40 + ")")
        self.assertTrue(cleanup.is_preview(legacy, SITE))
        legacy["title"] = "Production"
        self.assertFalse(cleanup.is_preview(legacy, SITE))

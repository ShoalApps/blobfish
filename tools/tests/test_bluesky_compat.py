"""Synthetic schemas only; no network, SDK, Gradle or service access."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("checker", Path(__file__).parents[1] / "check-bluesky-compat.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class CheckerTest(unittest.TestCase):
    def setUp(self):
        self.support = {"lexicons": {"app.bsky.test": True}, "variants": {"app.bsky.test#old": True},
                        "records": {"app.bsky.test#main": ["text"]}}
        self.old = ({"app.bsky.test": "Test"}, {"app.bsky.test#old": set()}, {"app.bsky.test#main": {"text"}})

    def test_report_headings_and_new_items_are_not_failure(self):
        current = ({"app.bsky.test": "Test", "app.bsky.new": "New"},
                   {"app.bsky.test#old": set(), "app.bsky.test#new": set()}, {"app.bsky.test#main": {"text", "added"}})
        report, failed, sections = checker.compare(current, self.support, self.old)
        self.assertFalse(failed)
        for title in ["New lexicons", "New union variants", "New fields on records read", "Removed things"]:
            self.assertIn("## " + title + "\n", report)
        self.assertIn("- `app.bsky.test#main.added`", report)
        self.assertEqual(["app.bsky.new"], sections["New lexicons"])

    def test_supported_removal_fails(self):
        _, failed, _ = checker.compare(({}, {}, {}), self.support, self.old)
        self.assertTrue(failed)

    def test_unsupported_removal_is_reported_without_failure(self):
        previous = ({**self.old[0], "app.bsky.extra": "Extra"}, self.old[1], self.old[2])
        report, failed, _ = checker.compare(self.old, self.support, previous)
        self.assertFalse(failed)
        self.assertIn("- `app.bsky.extra`", report)

    def test_inventory_resolves_local_union_references_and_record_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "test.json").write_text(json.dumps({"lexicon": 1, "id": "app.bsky.test", "defs": {
                "main": {"type": "record", "record": {"type": "object", "properties": {
                    "text": {"type": "string"}, "embed": {"type": "union", "refs": ["#view", "app.bsky.other#view"]}}}}}}))
            _, variants, records = checker.inventory(directory)
            self.assertIn("app.bsky.test#view", variants)
            self.assertEqual({"text", "embed"}, records["app.bsky.test#main.record"])

    def test_empty_snapshot_and_malformed_support_fail_loudly(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                checker.inventory(directory)
        with self.assertRaises(ValueError):
            checker.compare(self.old, {"lexicons": []})

    def test_asset_lists_what_a_person_could_see_with_a_verified_commit(self):
        current = ({"app.bsky.embed.images": "Pictures."},
                   {"app.bsky.embed.images#view": {"x"}, "app.bsky.actor.defs#savedFeedsPref": {"x"}}, {})
        support = {"lexicons": {}, "variants": {"app.bsky.embed.images#view": True}, "records": {}}
        result = checker.asset(current, support, {"date": "2026-11-05", "atproto": {"commit": "a" * 40}})
        self.assertEqual("a" * 40, result["upstreamCommit"])
        self.assertEqual(["app.bsky.embed.images#view"], [f["id"] for f in result["features"]])
        self.assertTrue(result["features"][0]["rendered"])
        self.assertEqual("Pictures.", result["features"][0]["description"])
        self.assertEqual("Gallery image", checker.plain("app.bsky.embed.external#viewGalleryImage"))
        self.assertEqual("", checker.plain("app.bsky.embed.images#view"))

if __name__ == "__main__":
    unittest.main()

import base64
import contextlib
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error
from urllib.parse import urlparse, parse_qs

import yaml


class Response(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()


class LabelMaintenanceTest(unittest.TestCase):
    def run_workflow(self, config, labels=None, race=False, error=None, reject_create=False):
        self.labels = list(labels or [])
        self.created = []
        self.requested_pages = []
        workflow = yaml.safe_load((Path(__file__).parents[1] / "workflows/dependabot-labels.yml").read_text(encoding="utf-8"))
        script = workflow["jobs"]["labels"]["steps"][-1]["run"]

        def urlopen(request, timeout):
            self.assertEqual(timeout, 30)
            parsed = urlparse(request.full_url)
            path = parsed.path.removeprefix("/repos/roflsunriz/test")
            if error:
                failure = urllib.error.HTTPError(request.full_url, error, "denied", {}, io.BytesIO())
                failure.close()
                raise failure
            if path == "/contents/.github":
                result = [{"name": "dependabot.yml", "path": ".github/dependabot.yml"}]
            elif path == "/contents/.github/dependabot.yml":
                result = {"content": base64.b64encode(yaml.safe_dump(config).encode()).decode()}
            elif path == "/labels" and request.data is not None:
                payload = json.loads(request.data)
                self.created.append(payload)
                if reject_create:
                    failure = urllib.error.HTTPError(request.full_url, 422, "invalid", {}, io.BytesIO())
                    failure.close()
                    raise failure
                self.labels.append(payload)
                if race:
                    failure = urllib.error.HTTPError(request.full_url, 422, "already exists", {}, io.BytesIO())
                    failure.close()
                    raise failure
                result = payload
            elif path == "/labels":
                page = int(parse_qs(parsed.query)["page"][0])
                self.requested_pages.append(page)
                result = self.labels[(page - 1) * 100:page * 100]
            else:
                raise AssertionError("Unexpected API mutation or path: " + path)
            return Response(json.dumps(result).encode())

        with patch.dict(os.environ, {"GITHUB_API_URL": "https://api.github.com", "GITHUB_REPOSITORY": "roflsunriz/test", "GH_TOKEN": "test"}), patch("urllib.request.urlopen", side_effect=urlopen), contextlib.redirect_stdout(io.StringIO()):
            exec(compile(script, "dependabot-labels.yml", "exec"), {})

    def test_missing_labels_created_once_with_standard_metadata(self):
        config = {"updates": [{"labels": ["dependencies", "github-actions"]}, {"labels": ["dependencies"]}]}
        self.run_workflow(config)
        self.assertEqual([label["name"] for label in self.created], ["dependencies", "github-actions"])
        self.assertEqual(self.created[0]["color"], "0366d6")
        self.assertEqual(self.created[1]["color"], "2088FF")
        self.run_workflow(config, self.labels)
        self.assertEqual(self.created, [])

    def test_existing_labels_preserved_case_insensitively(self):
        existing = [{"name": "Dependencies", "color": "abcdef", "description": "custom"}]
        self.run_workflow({"updates": [{"labels": ["dependencies"]}]}, existing)
        self.assertEqual(self.created, [])
        self.assertEqual(self.labels, existing)

    def test_all_pages_read(self):
        labels = [{"name": f"label-{index}"} for index in range(100)] + [{"name": "dependencies"}]
        self.run_workflow({"updates": [{"labels": ["dependencies"]}]}, labels)
        self.assertIn(2, self.requested_pages)
        self.assertEqual(self.created, [])

    def test_no_explicit_labels_creates_nothing(self):
        self.run_workflow({"updates": [{}, {"labels": []}]})
        self.assertEqual(self.created, [])

    def test_multi_ecosystem_group_labels(self):
        self.run_workflow({"updates": [], "multi-ecosystem-groups": {"group": {"labels": ["dependencies"]}}})
        self.assertEqual(len(self.created), 1)

    def test_create_race_is_verified(self):
        self.run_workflow({"updates": [{"labels": ["dependencies"]}]}, race=True)
        self.assertEqual(len(self.labels), 1)

    def test_permission_error_is_not_ignored(self):
        with self.assertRaises(urllib.error.HTTPError):
            self.run_workflow({"updates": [{"labels": ["dependencies"]}]}, error=403)

    def test_rejected_creation_is_not_treated_as_a_race(self):
        with self.assertRaises(urllib.error.HTTPError):
            self.run_workflow({"updates": [{"labels": ["dependencies"]}]}, reject_create=True)

    def test_archived_required_label_is_not_silently_accepted(self):
        with self.assertRaises(urllib.error.HTTPError):
            self.run_workflow({"updates": [{"labels": ["dependencies"]}]}, [{"name": "dependencies", "archived_at": "2026-10-05"}], reject_create=True)

    def test_invalid_labels_fail_before_mutation(self):
        for labels in ("dependencies", [None], [""]):
            with self.subTest(labels=labels), self.assertRaises(RuntimeError):
                self.run_workflow({"updates": [{"labels": labels}]})
            self.assertEqual(self.created, [])

    def test_bun_lockfile_remains_supported_by_dependabot(self):
        root = Path(__file__).parents[2]
        lockfile = root / "bun.lock"
        if not lockfile.exists():
            self.skipTest("This repository does not use Bun")
        lock = yaml.safe_load(lockfile.read_text(encoding="utf-8"))
        self.assertLessEqual(lock["lockfileVersion"], 1, "Dependabot's Bun updater currently supports lockfile versions up to 1")
        self.assertEqual(lock["configVersion"], 1)
        package = json.loads((root / "package.json").read_text(encoding="utf-8"))
        self.assertEqual(package["scripts"]["deps:lock"], "npx --yes bun@1.3.14 install --lockfile-only --ignore-scripts")
        self.assertEqual(package["engines"]["bun"], ">=1.4")
        automation = yaml.safe_load((root / ".github/workflows/dependabot-automation.yml").read_text(encoding="utf-8"))
        self.assertEqual(automation["jobs"]["dependabot"]["with"]["autofix_command"], package["scripts"]["deps:lock"])


if __name__ == "__main__":
    unittest.main()

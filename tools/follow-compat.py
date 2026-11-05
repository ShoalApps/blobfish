#!/usr/bin/env python3
"""GitHub-only fetch and single-issue maintenance. Never fetch X or Bluesky services or copy X code."""
import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import posixpath
import re
import shutil
import tempfile
import urllib.request
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
LEXICONS = "packages/api/lexicons/"


def api(path, data=None, method=None):
    token = os.environ["GH_TOKEN"]
    request = urllib.request.Request("https://api.github.com/" + path,
        data=json.dumps(data).encode() if data is not None else None, method=method,
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "Butterflyfish-compat"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def head(repo):
    branch = api(f"repos/{repo}")["default_branch"]
    value = api(f"repos/{repo}/commits/{quote(branch, safe='')}")["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError(f"Invalid commit for {repo}")
    return value


def bluesky():
    atproto, social = head("bluesky-social/atproto"), head("bluesky-social/social-app")
    # Verify paths using GitHub's actual tree. Missing/moved/truncated trees are failures, not empty snapshots.
    tree = api(f"repos/bluesky-social/atproto/git/trees/{atproto}?recursive=1")
    if tree.get("truncated"):
        raise ValueError("Upstream tree truncated; cannot safely snapshot lexicons")
    blobs = {f["path"]: f["sha"] for f in tree["tree"] if f["type"] == "blob"}
    # Bluesky's client package carries the schemas it builds against.
    files = [f for f in tree["tree"] if f["path"].startswith(LEXICONS + "app/bsky/") and f["path"].endswith(".json") and f["type"] == "blob"]
    if not any(f["path"].endswith("app/bsky/feed/post.json") for f in files):
        raise ValueError("Expected app/bsky/feed/post.json under " + LEXICONS + " upstream")
    compat = ROOT / "compat/bluesky"
    # Download to a staging directory in the repo; leave the prior snapshot intact if any fetch fails.
    with tempfile.TemporaryDirectory(dir=compat) as staging:
        target = Path(staging)
        ids = set()
        for f in files:
            def fetch(sha):
                blob = api(f"repos/bluesky-social/atproto/git/blobs/{sha}")
                if blob.get("encoding") != "base64":
                    raise ValueError(f"Unexpected encoding for {f['path']}")
                return base64.b64decode(blob["content"], validate=False).decode("utf-8")
            text = fetch(f["sha"])
            if f["mode"] == "120000":
                # Some schemas are links into Bluesky's overrides folder: read what they point at.
                linked = posixpath.normpath(posixpath.join(posixpath.dirname(f["path"]), text.strip()))
                if linked not in blobs:
                    raise ValueError(f"Broken link upstream: {f['path']} -> {linked}")
                text = fetch(blobs[linked])
            doc = json.loads(text)
            if not re.fullmatch(r"app\.bsky\.[A-Za-z0-9.-]+", doc.get("id", "")) or doc["id"] in ids or doc.get("lexicon") != 1:
                raise ValueError(f"Invalid lexicon: {f['path']}")
            ids.add(doc["id"])
            write(target / (doc["id"] + ".json"), doc)
        destination = compat / "lexicons"
        def contents(folder):
            return {p.name: p.read_bytes() for p in folder.glob("*.json")}
        # Both repositories change constantly. Only a real schema change may produce a commit and a
        # release, or the app would rebuild every six hours with nothing new in it.
        if contents(target) == contents(destination):
            return
        shutil.rmtree(destination)
        shutil.copytree(target, destination)
    write(compat / "upstream.json", {"date": datetime.now(timezone.utc).date().isoformat(),
        "atproto": {"repo": "bluesky-social/atproto", "commit": atproto},
        "social-app": {"repo": "bluesky-social/social-app", "commit": social}})


def issue(title, body):
    repo = os.environ["GITHUB_REPOSITORY"]
    matches = []
    page = 1
    while True:
        issues = api(f"repos/{repo}/issues?state=all&per_page=100&page={page}")
        matches.extend(i for i in issues if "pull_request" not in i and i["title"] == title)
        if len(issues) < 100:
            break
        page += 1
    if matches:
        existing = min(matches, key=lambda i: i["number"])
        api(f"repos/{repo}/issues/{existing['number']}", {"body": body, "state": "open"}, "PATCH")
    else:
        api(f"repos/{repo}/issues", {"title": title, "body": body}, "POST")


def bluesky_issue():
    report = (ROOT / "compat/bluesky/REPORT.md").read_text()
    issue("Bluesky changed: features to add", "These schema differences need review. New features require implementation; they do not appear automatically.\n\n" + report)


def x():
    watched = json.loads((ROOT / "compat/x/watched.json").read_text())
    path = ROOT / "compat/x/upstream.json"
    previous = json.loads(path.read_text())
    current, changes = {}, []
    for repo in watched:
        if not isinstance(repo, str) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
            raise ValueError("watched.json must contain owner/repo strings")
        commit = head(repo)  # Also verifies the repository exists and is accessible.
        current[repo] = commit
        if previous.get(repo) != commit:
            changes.append(f"- [{repo}](https://github.com/{repo}/commit/{commit}) (previous: {previous.get(repo, 'first observation')})")
    if current != previous:
        write(path, current)
    if changes:
        issue("X changes to check", "X has no public client source. These public projects may offer clues to website API changes. Review their changes; no code is copied and the app is never auto-edited.\n\nLatest observed changes:\n\n" + "\n".join(changes))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["bluesky", "bluesky-issue", "x"])
    action = parser.parse_args().action
    {"bluesky": bluesky, "bluesky-issue": bluesky_issue, "x": x}[action]()

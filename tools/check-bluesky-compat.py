#!/usr/bin/env python3
"""Offline compatibility inventory, report and asset generation. Python 3 standard library only.

Output headings and +/- lines are stable for CI and fixtures. A new feature is informational.
Only malformed input or removal of something declared supported fails the check.
"""
import argparse
import json
import re
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


DESCRIPTIONS = {}


def plain(key):
    """A name a person can read: app.bsky.embed.external#viewGalleryImage becomes "Gallery image"."""
    name = key.split("#")[-1] if "#" in key else ""
    name = re.sub(r"^(view|reason)(?=[A-Z])", "", name)
    words = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", name).lower()
    return words.capitalize() if words and words != "view" else ""


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def ref_id(lexicon, ref):
    if not isinstance(ref, str) or not ref:
        raise ValueError("Invalid union reference")
    return lexicon + ref if ref.startswith("#") else ref


def inventory(directory):
    lexicons, variants, records = {}, {}, {}
    paths = list(Path(directory).rglob("*.json"))
    if not paths:
        raise ValueError(f"No lexicons in {directory}; fetch a verified snapshot first")
    for path in sorted(paths):
        doc = read(path)
        nsid = doc["id"]
        if not isinstance(nsid, str) or not re.fullmatch(r"app\.bsky\.[A-Za-z0-9.-]+", nsid) or doc.get("lexicon") != 1:
            raise ValueError(f"Not an app.bsky lexicon: {path}")
        if nsid in lexicons or not isinstance(doc.get("defs"), dict):
            raise ValueError(f"Duplicate id or invalid defs: {path}")
        lexicons[nsid] = doc.get("description") or nsid.removeprefix("app.bsky.").replace(".", " ")
        def walk(node, location):
            if not isinstance(node, dict):
                raise ValueError(f"Invalid schema at {location}")
            if node.get("type") == "union":
                if not isinstance(node.get("refs"), list):
                    raise ValueError(f"Invalid union at {location}")
                for ref in node["refs"]:
                    full = ref_id(nsid, ref)
                    variants.setdefault(full, set()).add(location)
            if node.get("type") in ("object", "params"):
                properties = node.get("properties", {})
                if not isinstance(properties, dict):
                    raise ValueError(f"Invalid properties at {location}")
                records[location] = set(properties)
            for key, value in node.items():
                if key in ("properties",):
                    for name, schema in value.items():
                        walk(schema, location + "." + name)
                elif key in ("record", "items", "schema", "parameters") and isinstance(value, dict):
                    walk(value, location + "." + key)
                elif key in ("input", "output") and isinstance(value, dict) and "schema" in value:
                    walk(value["schema"], location + "." + key)
        for name, definition in doc["defs"].items():
            walk(definition, nsid + "#" + name)
            if definition.get("description"):
                DESCRIPTIONS[nsid + "#" + name] = definition["description"]
            if name == "main" and definition.get("description"):
                lexicons[nsid] = definition["description"]
    return lexicons, variants, records


def compare(current, support, previous=None):
    lexicons, variants, records = current
    if not isinstance(support.get("lexicons"), dict) or not isinstance(support.get("variants"), dict) or not isinstance(support.get("records"), dict):
        raise ValueError("supported.json needs lexicons, variants and records maps")
    for section in ("lexicons", "variants"):
        if any(not isinstance(value, bool) for value in support[section].values()):
            raise ValueError(f"{section} flags must be booleans")
    for name, fields in support["records"].items():
        if not isinstance(fields, list) or any(not isinstance(f, str) for f in fields):
            raise ValueError(f"Invalid supported fields: {name}")
    old_lex, old_var, old_records = previous or ({}, {}, {})
    new_lex = sorted(set(lexicons) - set(support["lexicons"]))
    new_var = sorted(set(variants) - set(support["variants"]))
    new_fields = sorted(f"{name}.{field}" for name, fields in support["records"].items()
                        for field in records.get(name, set()) - set(fields))
    removed_supported = sorted(
        [name for name in support["records"] if name not in records] +
        [key for key, yes in support["lexicons"].items() if yes and key not in lexicons] +
        [key for key, yes in support["variants"].items() if yes and key not in variants] +
        [f"{name}.{field}" for name, fields in support["records"].items() for field in fields
         if field not in records.get(name, set())])
    removed = sorted(set(removed_supported) | (set(old_lex) - set(lexicons)) | (set(old_var) - set(variants)) |
                     {f"{name}.{field}" for name, fields in old_records.items() for field in fields - records.get(name, set())})
    sections = [("New lexicons", new_lex), ("New union variants", new_var),
                ("New fields on records read", new_fields), ("Removed things", removed)]
    lines = ["# Bluesky compatibility", "", "New data can flow automatically; new screens need building.", ""]
    for title, values in sections:
        lines += ["## " + title, ""] + ([f"- `{v}`" for v in values] or ["None."]) + [""]
    lines += ["## Removed supported things", ""] + ([f"- `{v}`" for v in removed_supported] or ["None."])
    return "\n".join(lines) + "\n", bool(removed_supported), dict(sections)


def asset(current, support, upstream):
    lexicons, variants, _ = current
    commit = upstream["atproto"]["commit"]
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Missing verified atproto commit")
    # The screen lists what a person could see in a post, not every request and setting in the schema.
    def visible(key):
        return re.fullmatch(r"app\.bsky\.embed\.[A-Za-z]+#view[A-Za-z]*|app\.bsky\.feed\.defs#reason[A-Za-z]+|app\.bsky\.richtext\.facet#[A-Za-z]+", key)
    features = [{"id": key, "description": DESCRIPTIONS.get(key) or plain(key) or lexicons.get(key.split('#')[0], key),
                 "rendered": support["variants"].get(key, False)} for key in sorted(variants) if visible(key)]
    return {"date": upstream["date"], "upstreamCommit": commit, "features": features,
            "supportedVariants": sorted(k for k, yes in support["variants"].items() if yes)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, default=ROOT / "compat/bluesky/lexicons")
    parser.add_argument("--supported", type=Path, default=ROOT / "compat/bluesky/supported.json")
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--asset", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--report-json", type=Path)
    args = parser.parse_args()
    try:
        current, supported = inventory(args.snapshot), read(args.supported)
        baseline = inventory(args.baseline) if args.baseline and list(args.baseline.rglob("*.json")) else None
        report, failed, sections = compare(current, supported, baseline)
        print(report, end="")
        if args.report:
            args.report.write_text(report, encoding="utf-8")
        if args.report_json:
            args.report_json.write_text(json.dumps(sections, indent=2) + "\n", encoding="utf-8")
        if args.asset:
            args.asset.write_text(json.dumps(asset(current, supported, read(ROOT / "compat/bluesky/upstream.json")), indent=2) + "\n", encoding="utf-8")
        return 1 if failed else 0
    except (ValueError, KeyError, TypeError, OSError) as error:
        print(f"Malformed compatibility input: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Resolve release metadata and generate local/CI update manifests and feeds."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import quote


ABIS = ("armeabi-v7a", "arm64-v8a", "x86", "x86_64", "universal")


def resolve_metadata(properties_path, tag):
    properties = {}
    for raw_line in Path(properties_path).read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        parts = re.split(r"\s*[=:]\s*|\s+", line, maxsplit=1)
        properties[parts[0]] = parts[1].strip() if len(parts) == 2 else ""

    version_name = properties.get("app.versionName", "")
    if not version_name:
        raise ValueError("app.versionName is missing or empty")
    try:
        version_code = int(properties["app.versionCode"])
    except (KeyError, ValueError):
        raise ValueError("app.versionCode must be a positive integer") from None
    if version_code <= 0:
        raise ValueError("app.versionCode must be a positive integer")
    if tag != f"v{version_name}":
        raise ValueError(f"Tag {tag} does not match app.versionName (v{version_name})")

    channel = properties.get("app.releaseChannel")
    if channel is None:
        channel = "beta" if "-" in tag else "stable"
    elif channel not in ("beta", "stable"):
        raise ValueError("app.releaseChannel must be beta or stable; only a missing property permits tag fallback")
    prerelease = channel == "beta"
    return {
        "versionCode": version_code,
        "versionName": version_name,
        "tagName": tag,
        "channel": channel,
        "prerelease": prerelease,
    }


def generate_manifest(metadata, repository, release_dir):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("repository must be OWNER/REPO")
    release_dir = Path(release_dir)
    expected = {f"CurSimple-{abi}.apk" for abi in ABIS}
    actual = {path.name for path in release_dir.glob("CurSimple-*.apk") if path.is_file()}
    if actual != expected:
        raise ValueError(f"Expected exactly the five release APKs; missing={sorted(expected - actual)}, unexpected={sorted(actual - expected)}")
    assets = []
    for name in sorted(expected):
        with (release_dir / name).open("rb") as apk:
            checksum = hashlib.file_digest(apk, "sha256").hexdigest()
        assets.append({
            "abi": name[len("CurSimple-"):-len(".apk")],
            "fileName": name,
            "sha256": checksum,
            "downloadUrl": f"https://github.com/{repository}/releases/download/{quote(metadata['tagName'], safe='')}/{name}",
        })
    return {
        "schemaVersion": 1,
        "versionCode": metadata["versionCode"],
        "versionName": metadata["versionName"],
        "tagName": metadata["tagName"],
        "prerelease": metadata["prerelease"],
        "assets": assets,
    }


def write_json(path, value):
    content = (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    path = Path(path)
    if path.exists() and path.read_bytes() == content:
        return False
    path.write_bytes(content)
    return True


def update_feed(manifest, feed_dir, prerelease):
    code = manifest.get("versionCode")
    if type(code) is not int or code <= 0:
        raise ValueError("manifest versionCode must be a positive integer")
    if type(manifest.get("prerelease")) is not bool or manifest["prerelease"] != prerelease:
        raise ValueError("manifest prerelease does not match the resolved release channel")
    feed_dir = Path(feed_dir)
    pending = {}
    names = ("beta.json",) if prerelease else ("beta.json", "stable.json")
    for name in names:
        target = feed_dir / name
        old_code = -1
        if target.exists():
            old = json.loads(target.read_text(encoding="utf-8-sig"))
            old_code = old.get("versionCode", -1)
            if type(old_code) is not int:
                raise ValueError(f"{name} versionCode must be an integer")
        if code >= old_code:
            pending[name] = manifest
        else:
            print(f"Skip {name}: existing versionCode {old_code} is newer than {code}", file=sys.stderr)
    # Preserve the existing empty-stable sentinel on the first ever beta release.
    if "stable.json" not in pending and not (feed_dir / "stable.json").exists():
        pending["stable.json"] = {"schemaVersion": 1, "noRelease": True}
    feed_dir.mkdir(parents=True, exist_ok=True)
    return [name for name, value in pending.items() if write_json(feed_dir / name, value)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    resolve = subparsers.add_parser("resolve", help="validate properties/tag and emit Actions outputs")
    manifest = subparsers.add_parser("manifest", help="hash the five APKs and write update.json")
    for command in (resolve, manifest):
        command.add_argument("--properties", type=Path, default=Path("gradle.properties"))
        command.add_argument("--tag", required=True)
    resolve.add_argument("--github-output", type=Path)
    manifest.add_argument("--repository", required=True)
    manifest.add_argument("--release-dir", type=Path, required=True)
    feed = subparsers.add_parser("feed", help="update local feed files; print only changed file names")
    feed.add_argument("--manifest", type=Path, required=True)
    feed.add_argument("--feed-dir", type=Path, required=True)
    feed.add_argument("--prerelease", choices=("true", "false"), required=True)
    args = parser.parse_args()
    try:
        if args.command in ("resolve", "manifest"):
            metadata = resolve_metadata(args.properties, args.tag)
        if args.command == "resolve":
            outputs = (
                f"channel={metadata['channel']}\n"
                f"prerelease={str(metadata['prerelease']).lower()}\n"
                f"make_latest={'false' if metadata['prerelease'] else 'legacy'}\n"
            )
            if args.github_output:
                with args.github_output.open("a", encoding="utf-8") as output:
                    output.write(outputs)
            print(outputs, end="")
        elif args.command == "manifest":
            value = generate_manifest(metadata, args.repository, args.release_dir)
            write_json(args.release_dir / "update.json", value)
        else:
            value = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
            for name in update_feed(value, args.feed_dir, args.prerelease == "true"):
                print(name)
    except (OSError, ValueError, AttributeError) as error:
        parser.exit(1, f"release metadata: {error}\n")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Query OSV for resolved/vendored dependencies. Network/schema errors fail closed."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen


def query(path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = Request("https://api.osv.dev/v1/" + path, data=data,
                      headers={"Content-Type": "application/json", "User-Agent": "CurSimple-dependency-audit"})
    with urlopen(request, timeout=45) as response:
        body = response.read(16 * 1024 * 1024 + 1)
        if len(body) > 16 * 1024 * 1024:
            raise ValueError("OSV response exceeds limit")
        return json.loads(body)


def audit(inventory, api=query):
    queries = inventory["queries"]
    if not isinstance(queries, list) or not queries:
        raise ValueError("Empty or invalid dependency inventory")
    for coordinate in queries:
        if (coordinate.get("package", {}).get("ecosystem") not in {"Maven", "npm"}
                or not coordinate["package"].get("name") or not coordinate.get("version")):
            raise ValueError("Invalid dependency coordinate")
    findings = []
    for offset in range(0, len(queries), 100):
        batch = queries[offset:offset + 100]
        results = api("querybatch", {"queries": batch})["results"]
        if len(results) != len(batch):
            raise ValueError("Incomplete OSV response")
        for coordinate, result in zip(batch, results):
            for vulnerability in result.get("vulns", []):
                detail = api("vulns/" + vulnerability["id"])
                if not detail.get("withdrawn"):
                    findings.append({"coordinate": coordinate, "id": detail["id"],
                                     "aliases": detail.get("aliases", []), "summary": detail.get("summary", ""),
                                     "references": detail.get("references", [])})
    return {"package_count": len(queries), "findings": findings}


def verify_artifacts(inventory, root):
    root = root.resolve()
    for artifact in inventory.get("artifacts", []):
        path = (root / artifact["path"]).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            raise ValueError("Invalid vendored artifact path")
        digest = hashlib.sha256()
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(65536), b""):
                digest.update(chunk)
        if digest.hexdigest() != artifact["sha256"]:
            raise ValueError("Vendored artifact checksum mismatch: " + artifact["path"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    inventory = json.loads(args.inventory.read_text())
    verify_artifacts(inventory, Path.cwd())
    report = audit(inventory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"OSV checked {report['package_count']} resolved coordinates; {len(report['findings'])} findings")
    for finding in report["findings"]:
        print(f"{finding['id']}: {finding['coordinate']['package']['name']}:{finding['coordinate']['version']}")
    return bool(report["findings"])


if __name__ == "__main__":
    raise SystemExit(main())

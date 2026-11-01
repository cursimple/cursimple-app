#!/usr/bin/env python3
"""Physically delete only older pre previews after validating the new ready deploy.

No deletion happens if metadata is missing, the current URL is ambiguous, or any
API request fails. Never use a branch alias as CURRENT_DEPLOY_URL.
"""
import json
import os
import re
from datetime import datetime
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

API = "https://api.netlify.com/api/v1"
MARKER = re.compile(r"(?:CurSimple pre preview [0-9a-f]{40}|Preview from pre \([0-9a-f]{40}\))\Z")


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9-]{1,128}", value):
        raise ValueError("Invalid Netlify identifier")
    return value


def request(method, path):
    req = Request(API + path, method=method, headers={
        "Authorization": "Bearer " + os.environ["NETLIFY_AUTH_TOKEN"],
        "Accept": "application/json",
    })
    with urlopen(req, timeout=30) as response:
        body = response.read(8 * 1024 * 1024 + 1)
        if len(body) > 8 * 1024 * 1024:
            raise ValueError("Oversized Netlify response")
        return json.loads(body) if body else None


def instant(value):
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if result.tzinfo is None:
        raise ValueError("Missing deploy timestamp timezone")
    return result


def is_preview(deploy, site_id):
    return (deploy.get("site_id") == site_id
            and deploy.get("context") in {"deploy-preview", "branch-deploy", "dev"}
            and not deploy.get("published_at") and not deploy.get("locked")
            and (deploy.get("branch") == "pre" or
                 (not deploy.get("branch") and bool(MARKER.fullmatch(deploy.get("title") or "")))))


def cleanup(site_id, current_url, api=request):
    identifier(site_id)
    uri = urlsplit(current_url)
    match = re.fullmatch(r"([0-9a-f]{24})--[a-z0-9-]+\.netlify\.app", uri.netloc)
    if uri.scheme != "https" or not match or uri.path not in {"", "/"} or uri.query or uri.fragment:
        raise ValueError("Expected immutable HTTPS Netlify deploy URL")
    current_id = match[1]
    base = f"/sites/{site_id}"
    current = api("GET", f"{base}/deploys/{current_id}")
    if (current.get("id") != current_id or current.get("state") != "ready"
            or not is_preview(current, site_id)
            or current.get("deploy_ssl_url", "").rstrip("/") != current_url.rstrip("/")):
        raise ValueError("Current deployment is not a ready pre preview on this site")
    created = instant(current["created_at"])
    # Read every page before mutating, otherwise pagination can skip deleted rows.
    candidates = {}
    for page in range(1, 1001):
        batch = api("GET", f"{base}/deploys?per_page=100&page={page}")
        if not isinstance(batch, list):
            raise ValueError("Invalid Netlify deploy list")
        for deploy in batch:
            if (deploy.get("id") != current_id and is_preview(deploy, site_id)
                    and instant(deploy["created_at"]) < created):
                candidates[identifier(deploy["id"])] = deploy
        if len(batch) < 100:
            break
    else:
        raise ValueError("Netlify deploy list exceeds safety limit")
    deleted = 0
    for deploy_id in candidates:
        # Recheck production protection and metadata immediately before deletion.
        site = api("GET", base)
        if (not isinstance(site, dict) or site.get("id") != site_id
                or "published_deploy" not in site):
            raise ValueError("Missing site production metadata; refusing deletion")
        protected = {site.get("deploy_id"), (site.get("published_deploy") or {}).get("id"), current_id}
        deploy = api("GET", f"{base}/deploys/{deploy_id}")
        if (deploy_id in protected or deploy.get("id") != deploy_id or not is_preview(deploy, site_id)
                or instant(deploy["created_at"]) >= created):
            raise ValueError("Preview metadata changed; refusing deletion")
        api("DELETE", f"{base}/deploys/{deploy_id}")
        deleted += 1
    return deleted


if __name__ == "__main__":
    count = cleanup(os.environ["NETLIFY_SITE_ID"], os.environ["CURRENT_DEPLOY_URL"])
    print(f"Physically deleted {count} older pre previews; production/other branches protected.")
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"\n已通过 Netlify API 删除 {count} 个旧 pre 预览部署。生产部署及其他分支受保护。\n")

#!/usr/bin/env python3
"""Prevent publishing APKs that cannot update the existing official application."""

import argparse
import os
import re
import subprocess
from pathlib import Path


# Public SHA-256 fingerprint of the certificate on the published v0.7.4 APK.
OFFICIAL_CERTIFICATE = "630163f497cbb26d8d3a99dabacfc023c23c869cb55008d39a9763bd2da063bb"


def signing_certificates(output):
    # Build Tools 37 uses "V2 Signer:"; older releases use "Signer #1".
    # Source Stamp certificates identify a distributor and are not app signers.
    return set(value.lower() for value in re.findall(
        r"^(?:Signer #\d+(?: in lineage)?|Signer \([^\n]*\)|V\d+(?:\.\d+)? Signer:)"
        r"\s+certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$",
        output, re.MULTILINE,
    ))


def verify(apks, signer):
    for apk in apks:
        result = subprocess.run(
            [str(signer), "verify", "--print-certs", str(apk)],
            capture_output=True, text=True, check=True,
        )
        certificates = signing_certificates(result.stdout)
        if not certificates:
            raise ValueError(f"Cannot read {apk.name} signing certificate from apksigner output")
        if certificates != {OFFICIAL_CERTIFICATE}:
            raise ValueError(f"{apk.name} certificate mismatch: {sorted(certificates)}; publication stopped")
        print(f"Verified official signing certificate: {apk.name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-dir", type=Path, required=True)
    parser.add_argument("--android-sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    args = parser.parse_args()
    try:
        apks = sorted(args.apk_dir.glob("*.apk"))
        if len(apks) != 5:
            raise ValueError("Expected the five release APKs")
        signers = list(args.android_sdk.glob("build-tools/*/apksigner")) if args.android_sdk else []
        if not signers:
            raise ValueError("Android SDK apksigner was not found")
        signer = max(signers, key=lambda path: tuple(int(n) for n in re.findall(r"\d+", path.parent.name)))
        verify(apks, signer)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"release signing: {error}\n")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Prevent publishing APKs that cannot update the existing official application."""

import argparse
import os
import re
import subprocess
from pathlib import Path


# Public SHA-256 fingerprint of the certificate on the published v0.7.4 APK.
OFFICIAL_CERTIFICATE = "630163f497cbb26d8d3a99dabacfc023c23c869cb55008d39a9763bd2da063bb"


def verify(apks, signer):
    for apk in apks:
        result = subprocess.run(
            [str(signer), "verify", "--print-certs", str(apk)],
            capture_output=True, text=True, check=True,
        )
        certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", result.stdout)
        if not certificates or set(value.lower() for value in certificates) != {OFFICIAL_CERTIFICATE}:
            raise ValueError(f"{apk.name} does not use the official signing certificate; publication stopped")
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

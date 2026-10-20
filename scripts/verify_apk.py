#!/usr/bin/env python3
"""Check the APK actually built, independently of Gradle's source configuration."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile

APPLICATION_ID = "com.danishbears.bulgariantrainer"
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET", "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
}


def version_properties(path):
    values = dict(line.split("=", 1) for line in Path(path).read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith("#"))
    code = int(values["VERSION_CODE"])
    if not 1 <= code <= 2100000000:
        raise ValueError("Invalid VERSION_CODE")
    return code, values["VERSION_NAME"]


def check_manifest(badging, permissions, code, name, debug):
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != (APPLICATION_ID, str(code), name):
        raise ValueError("APK applicationId/version does not match the committed version")
    if ("application-debuggable" in badging) != debug:
        raise ValueError("APK debuggable flag does not match its variant")
    used = set(re.findall(r"name='([^']+)'", permissions))
    if used & FORBIDDEN_PERMISSIONS:
        raise ValueError("APK unexpectedly requests network or broad storage access")
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    if not minimum or int(minimum[1]) != 26:
        raise ValueError("Unexpected minimum Android version")


def certificate_digest(output):
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", output, re.M)
    if len(digests) != 1 or len(digests[0]) != 64:
        raise ValueError("Expected exactly one APK signing certificate")
    return digests[0].lower()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--signing", choices=["debug", "release", "unsigned"], required=True)
    parser.add_argument("--version-file", default="version.properties")
    args = parser.parse_args()
    sdk = os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"]
    tools = Path(sdk) / "build-tools" / "35.0.0"
    with zipfile.ZipFile(args.apk) as archive:
        if archive.testzip() is not None or "AndroidManifest.xml" not in archive.namelist():
            raise ValueError("Invalid APK ZIP")
    code, name = version_properties(args.version_file)
    badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(args.apk)], text=True)
    permissions = subprocess.check_output([str(tools / "aapt"), "dump", "permissions", str(args.apk)], text=True)
    check_manifest(badging, permissions, code, name, args.signing == "debug")
    signature = subprocess.run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(args.apk)],
                               text=True, capture_output=True)
    if args.signing == "unsigned":
        if signature.returncode == 0:
            raise ValueError("Unsigned validation unexpectedly received a signed APK")
        signing = "UNSIGNED: validation only; cannot be installed"
    else:
        if signature.returncode != 0:
            raise ValueError("APK signature verification failed")
        digest = certificate_digest(signature.stdout)
        if args.signing == "release":
            expected = os.environ.get("ANDROID_SIGNING_CERT_SHA256", "").replace(":", "").strip().lower()
            if not re.fullmatch(r"[0-9a-f]{64}", expected) or expected != digest:
                raise ValueError("Release signing certificate does not match ANDROID_SIGNING_CERT_SHA256")
        signing = f"{args.signing}: verified certificate SHA-256 {digest}"
    print(f"APK_CHECK: {APPLICATION_ID} versionCode={code} versionName={name} minSdk=26")
    print(f"APK_SIGNING: {signing}")
    print(f"APK_SHA256: {hashlib.sha256(args.apk.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()

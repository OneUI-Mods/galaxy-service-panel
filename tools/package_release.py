#!/usr/bin/env python3
"""Build and verify a signed release; private signing material never enters Git or output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CERTIFICATE = "4cda6775c402ba4cbc2591ffb3490b825d53a0aa7fafd98d67e85b337e8df0b0"


def run(*args, capture=False):
    return subprocess.run(
        [str(a) for a in args], cwd=ROOT, check=True, text=True, capture_output=capture
    )


def validate(apk, tools):
    badging = run(tools / "aapt2", "dump", "badging", apk, capture=True).stdout
    if (
        "name='dev.ryuya.galaxyservicepanel'" not in badging
        or "targetSdkVersion:'37'" not in badging
    ):
        raise RuntimeError("Unexpected package or SDK")
    if "application-debuggable" in badging or "name='android.permission.INTERNET'" in badging:
        raise RuntimeError("Debuggable or network-enabled APK")
    manifest = run(
        tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml", capture=True
    ).stdout
    if any(
        word in manifest.lower()
        for word in ["testactivity", "probeactivity", "fixtureactivity", "instrumentation"]
    ):
        raise RuntimeError("Test component in release")
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
        prefix = "META-INF/xposed/"
        properties = archive.read(prefix + "module.prop").decode()
        if "minApiVersion=102" not in properties or "targetApiVersion=102" not in properties:
            raise RuntimeError("Expected API 102 module")
        if (
            archive.read(prefix + "java_init.list").decode().strip()
            != "dev.ryuya.galaxyservicepanel.HookEntry"
        ):
            raise RuntimeError("Unexpected module entry")
        if (
            archive.read(prefix + "scope.list").decode().strip()
            != "com.sec.android.RilServiceModeApp"
        ):
            raise RuntimeError("Unexpected hook scope")
        if "assets/xposed_init" in names or any(
            b"Lde/robv/android/xposed/" in archive.read(n) for n in names if n.endswith(".dex")
        ):
            raise RuntimeError("Legacy Xposed entry or API reference")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-ref", default="HEAD")
    args = parser.parse_args()
    run("git", "diff", "--exit-code", args.source_ref, "--", ".")
    if run("git", "ls-files", "--others", "--exclude-standard", capture=True).stdout:
        raise RuntimeError("Commit all release sources first")
    signing = ROOT / ".signing"
    if not all((signing / f).is_file() for f in ["release.keystore", "password"]):
        raise RuntimeError("Missing private distribution key; see docs/releasing.md")
    tools = (
        Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
        / "build-tools/37.0.0"
    )
    run(
        ROOT / "gradlew",
        ":app:assembleRelease",
        ":app:lintRelease",
        ":app:testDebugUnitTest",
        ":app:writeRuntimeDependencyList",
    )
    run(sys.executable, ROOT / "tools/check_license_catalog.py")
    output = ROOT / "app/build/outputs/apk/release"
    metadata = json.loads((output / "output-metadata.json").read_text())
    if metadata["variantName"] != "release" or len(metadata["elements"]) != 1:
        raise RuntimeError("Expected a single release variant")
    item = metadata["elements"][0]
    version = item["versionName"]
    unsigned = output / item["outputFile"]
    validate(unsigned, tools)
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    target = dist / f"GalaxyServicePanel-{version}-release.apk"
    with tempfile.TemporaryDirectory(prefix="galaxy-service-panel-") as folder:
        aligned = Path(folder) / "aligned.apk"
        run(tools / "zipalign", "-P", "16", "-f", "4", unsigned, aligned)
        run(
            tools / "apksigner",
            "sign",
            "--ks",
            signing / "release.keystore",
            "--ks-key-alias",
            "galaxyservicepanel-release",
            "--ks-pass",
            "file:" + str(signing / "password"),
            "--debuggable-apk-permitted",
            "false",
            "--out",
            target,
            aligned,
        )
    run(tools / "zipalign", "-c", "-P", "16", "4", target)
    signature = run(
        tools / "apksigner", "verify", "--verbose", "--print-certs", target, capture=True
    ).stdout
    if CERTIFICATE not in signature:
        raise RuntimeError("Distribution certificate mismatch")
    validate(target, tools)
    (dist / f"GalaxyServicePanel-{version}-signature.txt").write_text(signature)
    source = dist / f"GalaxyServicePanel-{version}-source.zip"
    run(
        "git",
        "archive",
        "--format=zip",
        "--prefix=GalaxyServicePanel-" + version + "/",
        "--output=" + str(source),
        args.source_ref,
    )
    (dist / f"SHA256SUMS-{version}.txt").write_text(
        "".join(
            hashlib.sha256(f.read_bytes()).hexdigest() + "  " + f.name + "\n"
            for f in [target, source]
        )
    )
    print("Signed and verified release:", target.name)


if __name__ == "__main__":
    main()

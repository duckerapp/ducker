#!/usr/bin/env python3
"""Validate local release artifacts before any publication or device installation."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
ABIS = ("arm64-v8a", "armeabi-v7a")
PACKAGE = "app.ducker.android"


def run(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd or ROOT, text=True, stderr=subprocess.PIPE).strip()


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def tool(name):
    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    local = ROOT / "local.properties"
    if local.exists():
        roots += re.findall(r"^sdk.dir=(.+)$", local.read_text(), re.M)
    roots += [str(Path.home() / "Android/Sdk")]
    for root in filter(None, roots):
        candidates = list((Path(root) / "build-tools").glob(f"*/{name}"))
        if candidates:
            return str(max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name))))
    raise ValueError(f"Android SDK tool required: {name}")


def expected_certificate():
    value = (ROOT / "scripts/release-certificate.sha256").read_text().strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Invalid pinned release certificate SHA-256")
    return value


def inspect_apk(path, abi, version, code, min_sdk):
    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)",
                       run(tool("apksigner"), "verify", "--print-certs", str(path)))
    if [c.lower() for c in certs] != [expected_certificate()]:
        raise ValueError(f"{path.name}: unexpected release signing certificate")
    badging = run(tool("aapt2"), "dump", "badging", str(path))
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    sdk = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging, re.M)
    if not package or package.groups() != (PACKAGE, str(code), version) or not sdk or int(sdk[1]) != min_sdk:
        raise ValueError(f"{path.name}: wrong package, version, versionCode or minSdk")
    with zipfile.ZipFile(path) as archive:
        native = {n.split('/')[1] for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')}
        if native != {abi} or f"lib/{abi}/libclash.so" not in archive.namelist():
            raise ValueError(f"{path.name}: wrong native ABI")
        if len([n for n in archive.namelist() if n.startswith("assets/licenses/") and not n.endswith('/')]) < 7:
            raise ValueError(f"{path.name}: missing bundled licences")


def source_fingerprint(revision=None):
    """Git content identity, excluding the feed which publication itself changes."""
    if revision:
        raw = run("git", "ls-tree", "-rz", revision)
        entries = [line.split("\t", 1) for line in raw.split("\0") if line]
        entries = [(meta.split()[0], meta.split()[2], name) for meta, name in entries]
    else:
        raw = run("git", "ls-files", "--stage", "-z")
        entries = []
        for line in filter(None, raw.split("\0")):
            meta, name = line.split("\t", 1)
            mode, obj, stage = meta.split()
            if stage != "0":
                raise ValueError("Unmerged source tree")
            if mode == "160000":
                if run("git", "status", "--porcelain", cwd=ROOT / name):
                    raise ValueError(f"Dirty core submodule: {name}")
                obj = run("git", "rev-parse", "HEAD", cwd=ROOT / name)
            else:
                path = ROOT / name
                if path.is_symlink():
                    data = os.readlink(path).encode()
                    obj = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
                else:
                    obj = run("git", "hash-object", "--", name)
            entries.append((mode, obj, name))
    data = "\n".join(f"{mode} {obj}\t{name}" for mode, obj, name in sorted(entries, key=lambda e: e[2]) if name != "update.json")
    return hashlib.sha256(data.encode()).hexdigest()


def config():
    text = (ROOT / "build.gradle.kts").read_text()
    return (re.search(r'versionName = "([^"]+)"', text)[1],
            int(re.search(r'versionCode = (\d+)', text)[1]),
            int(re.search(r'minSdk = (\d+)', text)[1]))


def inventory(out, version, code, min_sdk, abis=ABIS):
    expected = {f"ducker-{version}-{abi}.apk" for abi in abis}
    if {p.name for p in out.glob("*.apk")} != expected:
        raise ValueError("Release requires exactly the two ARM APKs; no missing or extra APKs")
    records = {}
    for abi in abis:
        path = out / f"ducker-{version}-{abi}.apk"
        inspect_apk(path, abi, version, code, min_sdk)
        records[path.name] = {"sha256": sha(path), "size": path.stat().st_size}
    sums = {}
    for line in (out / "SHA256SUMS").read_text().splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (?:\./)?(ducker-[^/]+\.apk)", line)
        if not match or match[2] in sums:
            raise ValueError("Malformed or duplicate SHA256SUMS entry")
        sums[match[2]] = match[1]
    if sums != {name: info["sha256"] for name, info in records.items()}:
        raise ValueError("SHA256SUMS does not match both APKs")
    for name in ("SHA256SUMS", "release-notes.md"):
        records[name] = {"sha256": sha(out / name), "size": (out / name).stat().st_size}
    return records


def seal(out, version, smoke_passed=False, fixture=False):
    name, code, sdk = config()
    if name != version:
        raise ValueError("Build version differs from release version")
    abis = ABIS
    if fixture:
        abis = tuple(sorted(p.name[len(f"ducker-{version}-"):-4] for p in out.glob(f"ducker-{version}-*.apk")))
        if not abis or not set(abis) <= {*ABIS, "x86", "x86_64"}:
            raise ValueError("Unsupported local fixture ABIs")
    manifest = {"schema": 1, "version": name, "code": code, "minSdk": sdk, "smokePassed": smoke_passed,
                "fixture": fixture, "abis": list(abis),
                "certificateSha256": expected_certificate(), "sourceFingerprint": source_fingerprint(),
                "files": inventory(out, version, code, sdk, abis)}
    target = out / "release-manifest.json"
    if target.exists() and json.loads(target.read_text()) != manifest:
        raise ValueError("Existing release manifest differs; use a new version/output directory")
    target.write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def verify(out, version, revision=None, allow_fixture=False):
    manifest = json.loads((out / "release-manifest.json").read_text())
    if manifest.get("schema") != 1 or manifest["version"] != version or manifest["certificateSha256"] != expected_certificate():
        raise ValueError("Wrong release manifest")
    if manifest.get("fixture") and not allow_fixture:
        raise ValueError("Local fixtures cannot be published")
    abis = manifest["abis"] if manifest.get("fixture") else ABIS
    if manifest["files"] != inventory(out, version, manifest["code"], manifest["minSdk"], abis):
        raise ValueError("Release artifacts changed after sealing")
    if manifest["sourceFingerprint"] != source_fingerprint(revision):
        raise ValueError("Release source differs from the sealed build; rebuild under a new version")
    return manifest


def notes(path, version):
    text = Path(path).read_text()
    matches = list(re.finditer(rf"^## {re.escape(version)}(?:\s[^\n]*)?$", text, re.M))
    if len(matches) != 1:
        raise ValueError(f"{path}: expected exactly one changelog entry")
    text = text[matches[0].end():].split("\n## ", 1)[0]
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S).strip()
    if not text or not re.search(r"^- ", text, re.M):
        raise ValueError(f"{path}: empty changelog entry")
    return text


def release_inputs(version):
    for path in (ROOT / "CHANGELOG.md", ROOT / "docs/CHANGELOG.ru.md"):
        text = path.read_text()
        heading = re.search(rf"^## {re.escape(version)}(?:\s[^\n]*)?$", text, re.M)
        if not heading or re.search(r"draft|черновик", heading[0], re.I):
            raise ValueError("Finalize and date both changelog entries before building")
        notes(path, version)
    if version == "1.0.0":
        support = ROOT / "app/src/main/java/com/github/kr328/clash/ui/settings/ProjectSupport.kt"
        networks = re.search(r"enum class DonationNetwork\b.*?\{(.*?)^\}", support.read_text(), re.S | re.M)
        wallets = re.findall(r'^\s*\w+\("[^"\n]*",\s*"[^"\n]*",\s*"([^"\n]*)"\),?$', networks[1] if networks else "", re.M)
        if not wallets or not all(value.strip() for value in wallets):
            raise ValueError("Add all agreed wallet addresses before building 1.0.0")


def candidate(feed, manifest, out, bases, stable):
    version = manifest["version"]
    beta = feed.get("beta")
    abis = manifest["abis"] if manifest.get("fixture") else ABIS
    if stable:
        if not beta or beta["version"] != version:
            raise ValueError("Only the current beta can be promoted")
        for abi in abis:
            info = manifest["files"][f"ducker-{version}-{abi}.apk"]
            if any(beta["apk"][abi][k] != info[k] for k in ("sha256", "size")):
                raise ValueError("Beta bytes differ from local artifacts")
        if beta["code"] != manifest["code"] or beta["minSdk"] != manifest["minSdk"]:
            raise ValueError("Beta metadata differs from sealed build")
        if feed.get("stable") and feed["stable"]["code"] > beta["code"]:
            raise ValueError("Refusing to downgrade stable")
        return dict(feed, stable=beta)
    apk = {}
    for abi in abis:
        filename = f"ducker-{version}-{abi}.apk"
        apk[abi] = dict(manifest["files"][filename], urls=[base.rstrip('/') + '/' + filename for base in bases])
    changes = {}
    for lang, path in (("en", "CHANGELOG.md"), ("ru", "docs/CHANGELOG.ru.md")):
        text = notes(ROOT / path, version)
        text = re.sub(r"(?m)^- ", "• ", text)
        text = re.sub(r"\n[ \t]+", " ", text)
        text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
        changes[lang] = re.sub(r"[`*]", "", text)
    entry = {"version": version, "code": manifest["code"], "minSdk": manifest["minSdk"], "notes": changes, "apk": apk}
    for previous in (feed.get("stable"), beta):
        if not previous:
            continue
        if previous["code"] >= entry["code"] and previous != entry:
            raise ValueError("Version code must increase; an existing release is immutable")
        if previous["version"] == version and previous != entry:
            raise ValueError("Version already exists with different metadata")
    return dict(feed, beta=entry)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("seal", "verify", "apk", "feed", "inputs"))
    parser.add_argument("version")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--revision")
    parser.add_argument("--abi", choices=ABIS)
    parser.add_argument("--file", type=Path)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--target", type=Path)
    parser.add_argument("--bases", nargs="+")
    parser.add_argument("--stable", action="store_true")
    parser.add_argument("--smoke-passed", action="store_true", help="Attest the exact artifact passed the agreed device smoke")
    parser.add_argument("--fixture", action="store_true", help="Local-only ABI fixture, never eligible for publication")
    args = parser.parse_args()
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", args.version):
        raise ValueError("Expected a version such as 1.0.0")
    if args.action == "inputs":
        release_inputs(args.version)
    elif args.action == "apk":
        name, code, sdk = config()
        if name != args.version:
            raise ValueError("Wrong build version")
        inspect_apk(args.file, args.abi, name, code, sdk)
    elif args.action == "seal":
        seal(args.out, args.version, args.smoke_passed, args.fixture)
    else:
        manifest = verify(args.out, args.version, args.revision, args.fixture)
        if args.action == "feed":
            feed = json.loads(args.source.read_text()) if args.source.exists() else {"stable": None, "beta": None, "mirrors": []}
            args.target.write_text(json.dumps(candidate(feed, manifest, args.out, args.bases, args.stable), ensure_ascii=False, indent=2) + "\n")
    print("Release artifact checks passed")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"Release check failed: {error}", file=sys.stderr)
        sys.exit(1)

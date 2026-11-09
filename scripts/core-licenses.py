#!/usr/bin/env python3
"""Writes app/src/main/assets/licenses/core.txt: the licences of everything linked into libclash.so.

    python3 scripts/core-licenses.py          # write the file
    python3 scripts/core-licenses.py --check  # exit 1 if the file is stale (release.sh runs this)

The list is `go list -deps` of the core with the build's GOOS, tags and toolchain, which names the
same modules the built library records (checked against 0.4.47's libclash.so). Each module's
LICENSE/COPYING and NOTICE files are copied verbatim from its directory; a module without one stops
the script, so a new dependency is never shipped without its licence.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GOLANG = ROOT / "core/src/foss/golang"
GO = ROOT / "core/toolchain/go.sh"
OUT = ROOT / "app/src/main/assets/licenses/core.txt"
# The same as goTags in core/build.gradle.kts.
TAGS = "foss,with_gvisor,cmfa"
LICENCE_FILE = re.compile(r"(licen[cs]e|copying|notice)([-._].*)?", re.I)

HEADER = """\
Licences of the code built into ducker's core (lib/*/libclash.so): the Go standard library and
runtime, and every Go module the core links. Written by scripts/core-licenses.py; do not edit.
ducker itself is GPL-3.0: https://github.com/duckerapp/ducker
"""

# Modules whose release carries no licence file; the text is the one the project added later.
MISSING = {
    # https://github.com/metacubex/chacha/blob/7b981d42c3/LICENSE, added after v0.1.5.
    "github.com/metacubex/chacha": """\
MIT License

Copyright (c) 2016 Andreas Auernhammer
Copyright (c) 2025 MetaCubex

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.""",
}


def go(*args):
    env = dict(os.environ, GOOS="android", GOARCH="arm64", CGO_ENABLED="1")
    return subprocess.run([str(GO), *args], cwd=GOLANG, env=env, check=True,
                          capture_output=True, text=True).stdout


def texts(directory):
    files = sorted(f for f in Path(directory).iterdir() if f.is_file() and f.suffix != ".go" and LICENCE_FILE.fullmatch(f.name))
    return [(f.name, f.read_text(errors="replace").strip()) for f in files]


def entry(title, directory, path=None):
    found = texts(directory) or ([("LICENSE (from the project's repository)", MISSING[path])] if path in MISSING else [])
    if not found:
        sys.exit(f"core-licenses: no licence file in {directory} ({title})")
    parts = [f"{'=' * 100}\n{title}\n{'=' * 100}"]
    for name, text in found:
        parts.append(f"--- {name}\n\n{text}")
    return "\n\n".join(parts)


def generate():
    goroot = go("env", "GOROOT").strip()
    version = go("env", "GOVERSION").strip()
    listing = go("list", "-deps", "-tags", TAGS, "-f",
                 "{{with .Module}}{{if not .Main}}{{.Path}}\t{{.Version}}\t"
                 "{{with .Replace}}{{.Path}} {{.Version}}{{end}}\t{{.Dir}}{{end}}{{end}}",
                 "cfa/native")
    modules = sorted({tuple(line.split("\t")) for line in listing.splitlines() if line})
    entries = [entry(f"Go {version.removeprefix('go')} (standard library and runtime)", goroot)]
    for path, ver, replace, directory in modules:
        # cfa is ducker's own Go code (core/src/main/golang).
        if path == "cfa":
            continue
        title = f"{path} {ver}".strip()
        if replace.startswith("./"):
            # The fork's go.mod version is a placeholder; the submodule's commit is the real one.
            title = f"{path} (ducker's fork, core/src/foss/golang/{replace.strip().removeprefix('./')})"
        elif replace:
            title += f" (replaced by {replace.strip()})"
        entries.append(entry(title, directory, path))
    return HEADER + "\n" + "\n\n\n".join(entries) + "\n"


if __name__ == "__main__":
    text = generate()
    if "--check" in sys.argv[1:]:
        if not OUT.is_file() or OUT.read_text() != text:
            sys.exit(f"{OUT.relative_to(ROOT)} is stale: run python3 scripts/core-licenses.py and commit it")
        print(f"{OUT.relative_to(ROOT)} is up to date")
    else:
        OUT.write_text(text)
        print(f"{OUT.relative_to(ROOT)}: {text.count('=' * 100) // 2} entries, {len(text) // 1024} KB")

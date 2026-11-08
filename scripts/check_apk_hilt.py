#!/usr/bin/env python3
"""Checks that a built APK contains every class declared in the app's sources, can
create every Hilt ViewModel and worker, and has the Hilt base class of every entry point.

A flaky build once dropped some of Hilt's generated code: the APK still compiled, but
opening a screen whose ViewModel was missing from Hilt's map crashed the app. Dagger
creates each ViewModel / worker with `new-instance`, so a class that Dagger never
instantiates in the APK is missing from the graph.

Usage: check_apk_hilt.py <apk> [dexdump]
"""
import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile

apk = sys.argv[1]
dexdump = sys.argv[2] if len(sys.argv) > 2 else None
if not dexdump:
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    tools = sorted(glob.glob(os.path.join(home, "build-tools", "*", "dexdump")))
    if not tools:
        sys.exit("dexdump not found (set ANDROID_HOME)")
    dexdump = tools[-1]

# What the sources say Hilt must provide.
need_new = {}      # descriptor -> "ViewModel" / "worker"
need_class = {}    # descriptor -> "entry point" / "class"
declared = re.compile(
    r"^(?:public |internal |private )?(?:(?:data|sealed|enum|abstract|open|annotation|value|fun) )*(?:class|interface|object) (\w+)",
    re.M,
)
for path in glob.glob("app/src/main/kotlin/**/*.kt", recursive=True) + glob.glob("core/*/src/main/kotlin/**/*.kt", recursive=True) + glob.glob("feature/*/src/main/kotlin/**/*.kt", recursive=True):
    text = open(path, encoding="utf-8").read()
    pkg = re.search(r"^package ([\w.]+)", text, re.M)
    if not pkg:
        continue
    base = "L" + pkg.group(1).replace(".", "/") + "/"
    for kind, pattern in (
        ("ViewModel", r"@HiltViewModel\s+(?:internal\s+)?class\s+(\w+)"),
        ("worker", r"@HiltWorker\s+(?:internal\s+)?class\s+(\w+)"),
    ):
        for name in re.findall(pattern, text):
            need_new[base + name + ";"] = kind
    for name in declared.findall(text):
        need_class.setdefault(base + name + ";", "class")
    for name in re.findall(r"@AndroidEntryPoint\s+(?:internal\s+)?(?:open\s+)?class\s+(\w+)", text):
        need_class[base + "Hilt_" + name + ";"] = "entry point"

created = set()
defined = set()
new_re = re.compile(r"new-instance v\d+, (L[^;]+;)")
class_re = re.compile(r"Class descriptor\s+:\s+'(L[^']+;)'")
with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(apk) as z:
        dexes = [n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
        for name in dexes:
            z.extract(name, tmp)
    for name in dexes:
        proc = subprocess.Popen([dexdump, "-d", os.path.join(tmp, name)], stdout=subprocess.PIPE, text=True, errors="replace")
        for line in proc.stdout:
            m = new_re.search(line)
            if m:
                created.add(m.group(1))
                continue
            m = class_re.search(line)
            if m:
                defined.add(m.group(1))
        proc.wait()

missing = [f"{d} ({k} never created by Hilt)" for d, k in need_new.items() if d not in created]
missing += [f"{d} ({k} missing)" for d, k in need_class.items() if d not in defined]
print(f"Checked {len(need_new)} ViewModels / workers and {len(need_class)} classes in {os.path.basename(apk)}.")
if missing:
    print("MISSING from the APK:")
    print("\n".join("  " + m for m in sorted(missing)))
    sys.exit(1)
print("All present.")

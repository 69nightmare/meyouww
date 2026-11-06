#!/usr/bin/env python3
"""Fails on Android string resources that aapt would reject: bare apostrophes or
quotes, and unbalanced format arguments. Fast, so CI runs it before Gradle."""
import glob
import re
import sys

bad = []
for path in glob.glob("**/src/main/res/values*/strings.xml", recursive=True):
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        m = re.search(r'<(string|item)[^>]*>(.*)</(string|item)>', line)
        if not m:
            continue
        body = m.group(2)
        if re.search(r"(?<!\\)'", body):
            bad.append(f"{path}:{n}: unescaped apostrophe")
        if re.search(r'(?<!\\)"', body):
            bad.append(f"{path}:{n}: unescaped double quote")
        if body.startswith("@") or body.startswith("?"):
            bad.append(f"{path}:{n}: starts with @ or ? (escape it)")
print("\n".join(bad) or "strings ok")
sys.exit(1 if bad else 0)

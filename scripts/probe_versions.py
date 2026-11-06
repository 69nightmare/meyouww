#!/usr/bin/env python3
"""Print the latest stable version of every build dependency.

Runs in CI (which has normal internet access) so the version catalog can be
checked against what is actually published today. Usage:

    python3 scripts/probe_versions.py            # print a table
    python3 scripts/probe_versions.py --check    # also compare with gradle/libs.versions.toml
"""
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

GOOGLE = "https://dl.google.com/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"
PORTAL = "https://plugins.gradle.org/m2"

# catalog key -> (repository, group, artifact)
ARTIFACTS = {
    "agp": (GOOGLE, "com.android.tools.build", "gradle"),
    "kotlin": (CENTRAL, "org.jetbrains.kotlin", "kotlin-gradle-plugin"),
    "ksp": (CENTRAL, "com.google.devtools.ksp", "symbol-processing-gradle-plugin"),
    "hilt": (CENTRAL, "com.google.dagger", "hilt-android"),
    "androidxHilt": (GOOGLE, "androidx.hilt", "hilt-work"),
    "composeBom": (GOOGLE, "androidx.compose", "compose-bom"),
    "activity": (GOOGLE, "androidx.activity", "activity-compose"),
    "lifecycle": (GOOGLE, "androidx.lifecycle", "lifecycle-runtime-compose"),
    "navigation": (GOOGLE, "androidx.navigation", "navigation-compose"),
    "coreKtx": (GOOGLE, "androidx.core", "core-ktx"),
    "splashscreen": (GOOGLE, "androidx.core", "core-splashscreen"),
    "work": (GOOGLE, "androidx.work", "work-runtime-ktx"),
    "datastore": (GOOGLE, "androidx.datastore", "datastore"),
    "room": (GOOGLE, "androidx.room", "room-runtime"),
    "firebaseBom": (GOOGLE, "com.google.firebase", "firebase-bom"),
    "playServicesBase": (GOOGLE, "com.google.android.gms", "play-services-base"),
    "tink": (CENTRAL, "com.google.crypto.tink", "tink-android"),
    "supabase": (CENTRAL, "io.github.jan-tennert.supabase", "bom"),
    "ktor": (CENTRAL, "io.ktor", "ktor-client-okhttp"),
    "coroutines": (CENTRAL, "org.jetbrains.kotlinx", "kotlinx-coroutines-core"),
    "serialization": (CENTRAL, "org.jetbrains.kotlinx", "kotlinx-serialization-json"),
    "coil": (CENTRAL, "io.coil-kt.coil3", "coil-compose"),
    "credentials": (GOOGLE, "androidx.credentials", "credentials"),
    "googleId": (GOOGLE, "com.google.android.libraries.identity.googleid", "googleid"),
    "junit": (CENTRAL, "junit", "junit"),
    "truth": (CENTRAL, "com.google.truth", "truth"),
    "robolectric": (CENTRAL, "org.robolectric", "robolectric"),
    "androidxTestExt": (GOOGLE, "androidx.test.ext", "junit"),
    "espresso": (GOOGLE, "androidx.test.espresso", "espresso-core"),
}

UNSTABLE = re.compile(r"(alpha|beta|rc|dev|eap|snapshot|-m\d|preview|wasm)", re.I)


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "meyouww-version-probe"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def vkey(v: str):
    return [int(p) if p.isdigit() else p for p in re.split(r"[.\-]", v)]


def latest(repo: str, group: str, artifact: str) -> str:
    url = f"{repo}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    root = ET.fromstring(fetch(url))
    versions = [v.text for v in root.iter("version") if v.text and not UNSTABLE.search(v.text)]
    try:
        versions.sort(key=vkey)
    except TypeError:
        versions.sort()
    return versions[-1] if versions else "?"


def gradle_current() -> str:
    import json
    return json.loads(fetch("https://services.gradle.org/versions/current"))["version"]


def android_platforms() -> list:
    xml = fetch("https://dl.google.com/android/repository/repository2-3.xml").decode()
    return sorted(set(re.findall(r'path="(platforms;android-[0-9.]+)"', xml)), key=lambda s: vkey(s.split("-")[-1]))[-4:]


def main() -> int:
    catalog = {}
    if "--check" in sys.argv:
        text = open("gradle/libs.versions.toml", encoding="utf-8").read()
        section = text.split("[versions]", 1)[1].split("[", 1)[0]
        catalog = dict(re.findall(r'^(\w+)\s*=\s*"([^"]+)"', section, re.M))
    print(f"{'key':<20}{'latest stable':<20}{'catalog':<20}")
    for key, (repo, group, artifact) in ARTIFACTS.items():
        try:
            v = latest(repo, group, artifact)
        except Exception as exc:  # report and keep going
            v = f"ERR {exc.__class__.__name__}"
        mark = "" if not catalog or catalog.get(key) in (None, v) else "  <-- differs"
        print(f"{key:<20}{v:<20}{catalog.get(key, ''):<20}{mark}")
    try:
        print(f"{'gradle':<20}{gradle_current()}")
    except Exception as exc:
        print("gradle ERR", exc)
    try:
        print("android platforms:", ", ".join(android_platforms()))
    except Exception as exc:
        print("platforms ERR", exc)
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Gate (plan-r9 Task 17): the public APK carries no secret, the personal APK does (proves the scan works).

    python tools/check_secrets.py      -> builds both debug APKs, exit 1 on a leak or a scan that finds nothing

Builds :standby:assemblePublicDebug + :standby:assemblePersonalDebug (JAVA_HOME must point at JDK 17), then scans
every entry of each APK (classes*.dex, resources.arsc, res/, assets/ …) for each value of
../../../../status-widget/secrets.properties, as UTF-8 and UTF-16LE bytes. Prints key names and counts only, never a
value. The APKs stay in the build folder; do not send them anywhere.
"""
import os
import subprocess
import sys
import zipfile
from pathlib import Path

APP = Path(__file__).resolve().parent.parent
# same file standby/build.gradle.kts reads for the personal flavour (outside git)
SECRETS = (APP / "../../../../status-widget/secrets.properties").resolve()
KEYS = ("statusBotToken", "statusChatId", "lanToken")


def load() -> dict[str, str]:
    """key -> value for KEYS in secrets.properties ({} when the file is missing). For byte matching only: never print."""
    if not SECRETS.exists():
        return {}
    out: dict[str, str] = {}
    for line in SECRETS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and line[0] not in "#!" and "=" in line:
            k, v = (p.strip() for p in line.split("=", 1))
            if k in KEYS and v:
                out[k] = v
    return out


def apk(flavour: str) -> Path:
    root = Path(os.environ.get("SB_BUILD_DIR") or Path(os.environ["LOCALAPPDATA"]) / "standby-build")
    return root / "standby" / "outputs" / "apk" / flavour / "debug" / f"standby-{flavour}-debug.apk"


def hits(path: Path, secrets: dict[str, str]) -> dict[str, int]:
    """Entries of [path] that contain each secret (UTF-8 or UTF-16LE)."""
    pats = {k: (v.encode("utf-8"), v.encode("utf-16-le")) for k, v in secrets.items()}
    found = dict.fromkeys(secrets, 0)
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            data = z.read(info)
            for k, (a, b) in pats.items():
                if a in data or b in data:
                    found[k] += 1
    return found


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    secrets = load()
    missing = [k for k in KEYS if k not in secrets]
    if missing:
        print(f"check_secrets: FAIL - secrets.properties lacks {', '.join(missing)}: the scan could not prove anything")
        return 1
    gradle = APP / ("gradlew.bat" if os.name == "nt" else "gradlew")
    r = subprocess.run([str(gradle), ":standby:assemblePublicDebug", ":standby:assemblePersonalDebug", "-q"], cwd=APP)
    if r.returncode:
        print("check_secrets: FAIL - APK build failed")
        return 1
    bad = []
    for flavour, want in (("public", False), ("personal", True)):
        found = hits(apk(flavour), secrets)
        print(f"{flavour}: " + ", ".join(f"{k} in {n} entries" for k, n in found.items()))
        bad += [f"{flavour}: {k} {'missing' if want else 'LEAKED'}" for k, n in found.items() if (n > 0) != want]
    print("\n".join(bad) if bad else "check_secrets: ok")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())

"""Gate (plan-r9 Task 6): colours come from the theme, not from literals.

    python tools/check_literals.py      -> exit 1 and a list of offending lines

Fails on `Color.Black` / `Color.White` in StandBy code, except
  - settings/ (settings chrome stays un-themed, SK.*),
  - token definitions (ui/theme/ThemeTokens.kt, Primitives.kt, Tokens.kt),
  - lines that name a scrim or carry `literal-ok` (text over pictures is white + scrim in every theme),
  - files listed in tools/legacy.txt (family code; each card task deletes its entry, D1).
Also fails on `copy(theme` outside ui/theme/Theme.kt: every theme change goes through AppConfig.withTheme (D4).
"""
import re
import sys
from pathlib import Path

APP = Path(__file__).resolve().parent.parent
SRC = APP / "standby/src/main/java/kr/dot/sb"
SKIP = {"ui/theme/ThemeTokens.kt", "ui/theme/Primitives.kt", "ui/theme/Tokens.kt"}
LIT = re.compile(r"\bColor\.(Black|White)\b")
OK = re.compile(r"literal-ok|scrim|overPicture", re.I)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    legacy = {l.split("#")[0].strip() for l in (APP / "tools/legacy.txt").read_text(encoding="utf-8").splitlines()} - {""}
    bad: list[str] = []
    for f in sorted(SRC.rglob("*.kt")):
        rel = f.relative_to(SRC).as_posix()
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if "copy(theme" in line and rel != "ui/theme/Theme.kt":
                bad.append(f"{rel}:{n}: theme change outside withTheme: {line.strip()}")
            if rel.startswith("settings/") or rel in SKIP or rel in legacy:
                continue
            if LIT.search(line) and not OK.search(line):
                bad.append(f"{rel}:{n}: {line.strip()}")
    stale = sorted(p for p in legacy if not (SRC / p).exists())
    for p in stale:
        bad.append(f"tools/legacy.txt: {p} no longer exists, drop it")
    print("\n".join(bad) if bad else "check_literals: ok")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())

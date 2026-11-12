"""Gate (plan-r9 Task 6): colours come from the theme, not from literals.

    python tools/check_literals.py      -> exit 1 and a list of offending lines

Fails on `Color.Black` / `Color.White` in StandBy code, except
  - settings/ (settings chrome stays un-themed, SK.*),
  - token definitions (ui/theme/ThemeTokens.kt, Primitives.kt, Tokens.kt),
  - lines that name a scrim or carry `literal-ok` (text over pictures is white + scrim in every theme),
  - files listed in tools/legacy.txt (family code; each card task deletes its entry, D1).
Also fails on `copy(theme` outside ui/theme/Theme.kt: every theme change goes through AppConfig.withTheme (D4),
and on "아이폰" / "iPhone" inside a string literal (r10: no Apple name in anything the user sees; comments may).
"""
import re
import sys
from pathlib import Path

APP = Path(__file__).resolve().parent.parent
SRC = APP / "standby/src/main/java/kr/dot/sb"
# the flavour source sets (personal usage card, public stubs) obey the same rule
SRCS = [APP / f"standby/src/{s}/java/kr/dot/sb" for s in ("main", "personal", "public")]
SKIP = {"ui/theme/ThemeTokens.kt", "ui/theme/Primitives.kt", "ui/theme/Tokens.kt"}
LIT = re.compile(r"\bColor\.(Black|White)\b")
OK = re.compile(r"literal-ok|scrim|overPicture", re.I)
APPLE = ("아이폰", "iPhone")


def literals(line: str) -> list[str]:
    """String literals on one Kotlin line ("..." with escapes); text after a // outside a literal is a comment."""
    out, i, n = [], 0, len(line)
    while i < n:
        if line.startswith("//", i):
            break
        if line[i] == '"':
            j, buf = i + 1, []
            while j < n and line[j] != '"':
                if line[j] == "\\" and j + 1 < n:
                    buf.append(line[j:j + 2]); j += 2; continue
                buf.append(line[j]); j += 1
            out.append("".join(buf)); i = j + 1; continue
        i += 1
    return out


assert literals('val a = "x // 아이폰" // 아이폰') == ["x // 아이폰"]
assert literals("// 아이폰 in a comment") == []
assert literals('f("a\\"b", "c")') == ['a\\"b', "c"]


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    legacy = {l.split("#")[0].strip() for l in (APP / "tools/legacy.txt").read_text(encoding="utf-8").splitlines()} - {""}
    bad: list[str] = []
    for f in sorted(f for root in SRCS for f in root.rglob("*.kt")):
        rel = next(f.relative_to(r) for r in SRCS if f.is_relative_to(r)).as_posix()
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if "copy(theme" in line and rel != "ui/theme/Theme.kt":
                bad.append(f"{rel}:{n}: theme change outside withTheme: {line.strip()}")
            if any(a in lit for lit in literals(line) for a in APPLE):
                bad.append(f"{rel}:{n}: Apple name in a string: {line.strip()}")
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

"""Gate (plan-r9 Task 17): the status.json fixture (a trimmed ai-hub snapshot) carries no token or chat id.

    python tools/check_fixture.py      -> exit 1 and "file:line: rule" for each suspect (never the matched text)

Fails on token-shaped strings (Telegram bot token, any 32+ char opaque run), chat-id-shaped values (a string of 6+
digits, a negative number of 6+ digits, any key naming a chat/token/secret) and on any value of secrets.properties.
"""
import re
import sys
from pathlib import Path

from check_secrets import APP, load

FIXTURES = sorted((APP / "standby/src").glob("test*/resources/**/*.json"))
RULES = [
    ("bot-token", re.compile(r"\d{6,12}:[A-Za-z0-9_-]{30,}")),
    ("opaque-32", re.compile(r"[A-Za-z0-9_\-+/=]{32,}")),
    ("chat-id-string", re.compile(r'"-?\d{6,}"')),
    ("negative-id", re.compile(r"(?<![\w.])-\d{6,}")),
    ("secret-key", re.compile(r'"[^"]*(chat|token|secret|password)[^"]*"\s*:', re.I)),
]


def scan(text: str, secrets: dict[str, str]) -> list[tuple[int, str]]:
    out = []
    for n, line in enumerate(text.splitlines(), 1):
        out += [(n, name) for name, rx in RULES if rx.search(line)]
        out += [(n, f"value of {k}") for k, v in secrets.items() if v in line]
    return out


def _selftest() -> None:
    fake_token = "1234567:" + "A" * 35
    assert [r for _, r in scan(f'{{"x": "{fake_token}"}}', {})][0] == "bot-token"
    assert scan('{"chat": 5}', {})[0][1] == "secret-key"
    assert scan('{"id": "987654321"}', {})[0][1] == "chat-id-string"
    assert scan('{"id": -1001234567890}', {})[0][1] == "negative-id"
    assert scan('{"x": "abc"}', {"k": "abc"}) == [(1, "value of k")]
    assert scan('{"at": 1794428400, "pct": -3, "reset": 1794913200}', {}) == []  # timestamps pass


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    _selftest()
    if not FIXTURES:
        print("check_fixture: FAIL - no fixture found")
        return 1
    secrets = load()
    bad = [f"{f.relative_to(APP).as_posix()}:{n}: {rule}" for f in FIXTURES for n, rule in scan(f.read_text(encoding="utf-8"), secrets)]
    print("\n".join(bad) if bad else f"check_fixture: ok ({len(FIXTURES)} file(s), {len(secrets)} secret values checked)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())

"""Count 같이 네컷 visits from the R2 bucket (Worker /v keys).

Usage (from this folder, needs `npx wrangler login` once):
    python visits.py            # last 14 days
    python visits.py 30
Keys look like v/<KST day>/<event>/<platform>.<in-app browser>.<visitor hash>; one key = one visitor that day.
"""
import datetime
import json
import os
import subprocess
import sys
import urllib.request
from collections import Counter

ACCOUNT = "acb4b1bf50d311deb3427b94bdf32f08"
BASE = f"https://api.cloudflare.com/client/v4/accounts/{ACCOUNT}/r2/buckets/dot-lab-booth/objects"
EVENTS = ["open", "join", "shoot", "save"]


def token() -> str:
    if os.environ.get("CF_TOKEN"):
        return os.environ["CF_TOKEN"].strip()
    out = subprocess.run("npx wrangler auth token", shell=True, capture_output=True, text=True, check=True).stdout
    return out.strip().splitlines()[-1].strip()


def keys(tok: str, prefix: str) -> list[str]:
    found: list[str] = []
    cursor = None
    while True:
        url = f"{BASE}?per_page=1000&prefix={prefix}" + (f"&cursor={cursor}" if cursor else "")
        req = urllib.request.Request(url, headers={"Authorization": "Bearer " + tok, "User-Agent": "visits.py"})
        d = json.load(urllib.request.urlopen(req))
        found += [o["key"] for o in d["result"]]
        info = d.get("result_info") or {}
        if not info.get("is_truncated"):
            return found
        cursor = info["cursor"]


def main() -> None:
    days = int(sys.argv[1]) if len(sys.argv) > 1 else 14
    since = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=9) - datetime.timedelta(days=days)).date().isoformat()
    rows: Counter[tuple[str, str]] = Counter()
    via: Counter[str] = Counter()
    for k in keys(token(), "v/"):
        _, day, event, rest = k.split("/")
        if day < since:
            continue
        rows[(day, event)] += 1
        if event in ("open", "join"):
            platform, browser, _ = rest.split(".")
            via[f"{platform}/{browser}"] += 1
    print("day         " + "".join(f"{e:>7}" for e in EVENTS))
    for day in sorted({d for d, _ in rows}):
        print(f"{day}  " + "".join(f"{rows[(day, e)]:>7}" for e in EVENTS))
    if not rows:
        print("(no visits yet)")
    print("\nopen+join by platform/browser:")
    for k, n in via.most_common():
        print(f"  {k:<24}{n:>5}")


if __name__ == "__main__":
    main()

"""Apply additive Worker v2 D1 changes, skipping columns already present."""

import argparse
import json
import shutil
import subprocess
from pathlib import Path


MIGRATION = Path(__file__).resolve().parents[1] / "migrations" / "2026-11-19-v2.sql"


def execute(database, location, statement):
    result = subprocess.run(
        [shutil.which("npx") or "npx", "wrangler@4", "d1", "execute", database, location, "--command", statement, "--json"],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout)


def columns(database, location, table):
    data = execute(database, location, f"PRAGMA table_info({table})")
    return {row["name"] for batch in data for row in batch.get("results", [])}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--remote", action="store_true")
    parser.add_argument("--local", action="store_true")
    parser.add_argument("--db", default="dot-lab-parking")
    args = parser.parse_args()
    if args.remote == args.local:
        parser.error("choose exactly one of --remote or --local")
    location = "--remote" if args.remote else "--local"
    for statement in MIGRATION.read_text(encoding="utf-8").split(";"):
        statement = statement.strip()
        if not statement:
            continue
        if statement.startswith("ALTER TABLE members") and "last_seen" in columns(args.db, location, "members"):
            continue
        if statement.startswith("ALTER TABLE groups") and "name" in columns(args.db, location, "groups"):
            continue
        execute(args.db, location, statement)


if __name__ == "__main__":
    main()

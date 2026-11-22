"""One-shot v3 migration: one family -> one group (spec-v3-groups.md section 3).

Only CREATE IF NOT EXISTS / INSERT OR IGNORE keyed by car_map, so a failed run is simply run again
and a finished run re-run changes 0 rows. Never deletes or rewrites old rows or R2 objects.
"""

import argparse
import json
import os
import secrets
import shutil
import subprocess
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Callable

MIGRATION = Path(__file__).resolve().parents[1] / "migrations" / "2026-11-22-v3.sql"
MAX_CARS = 4


def new_id() -> str:
    return "c_" + secrets.token_hex(8)


def lit(v) -> str:
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return str(int(v))
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def insert(table: str, row: dict) -> str:
    return f"INSERT OR IGNORE INTO {table} ({','.join(row)}) VALUES ({','.join(lit(v) for v in row.values())});"


def dumps(v) -> str:
    return json.dumps(v, ensure_ascii=False, separators=(",", ":"))


def oldest(members: list[dict]) -> dict | None:
    return min(members, key=lambda m: (m["created"], m["id"]), default=None)


def plan(rows: dict[str, list[dict]], car_map: dict[tuple[str, str], str],
         new_id: Callable[[], str]) -> tuple[list[str], list[str], dict]:
    now = int(time.time() * 1000)
    by_group: dict[str, list[dict]] = defaultdict(list)
    for m in rows["members"]:
        by_group[m["group_id"]].append(m)
    parks = {(p["group_id"], p["car_id"]): p for p in rows["parks2"]}
    cars = [c for c in rows["cars"] if c["group_id"] in by_group]
    skipped = sorted({g["id"] for g in rows["groups"]} - set(by_group))

    # car_map: stored mapping wins; else the copy with the newest park (else car updated) keeps the id.
    def recency(c: dict) -> int:
        p = parks.get((c["group_id"], c["id"]))
        return json.loads(p["json"]).get("atMs", 0) if p else c["updated"]

    mapping = dict(car_map)
    used = set(mapping.values())
    map_sql, rekeyed = [], []
    copies: dict[str, list[dict]] = defaultdict(list)
    for c in cars:
        copies[c["id"]].append(c)
    for old_id, group in copies.items():
        for c in sorted(group, key=lambda c: (-recency(c), c["group_id"])):
            key = (c["group_id"], old_id)
            if key not in mapping:
                mapping[key] = old_id if old_id not in used else new_id()
                used.add(mapping[key])
                map_sql.append(insert("car_map", {"old_group": key[0], "old_id": old_id, "new_id": mapping[key]}))
            if mapping[key] != old_id:
                rekeyed.append((key[0], old_id, mapping[key]))

    copy_sql: list[str] = []
    for m in rows["members"]:
        copy_sql.append(insert("devices", {"id": m["id"], "secret_hash": m["secret_hash"], "name": m["name"],
                                           "fcm_token": m["fcm_token"], "created": m["created"],
                                           "last_seen": m["last_seen"]}))
        copy_sql.append(insert("memberships", {"group_id": m["group_id"], "device_id": m["id"],
                                               "owner": int(bool(m["owner"])), "joined": m["created"]}))

    def named(g: str, name: str | None) -> str | None:
        hit = oldest([m for m in by_group[g] if name is not None and m["name"] == name])
        return hit["id"] if hit else None

    owners, report_cars = Counter(), []
    for c in cars:
        g, old_id = c["group_id"], c["id"]
        nid = mapping[(g, old_id)]
        members = by_group[g]
        park = parks.get((g, old_id))
        parker = park and park["by_id"]
        if parker and any(m["id"] == parker for m in members):
            owner, why = parker, "last-parker"
        elif boss := oldest([m for m in members if m["owner"]]):
            owner, why = boss["id"], "방장"
        else:
            owner, why = oldest(members)["id"], "oldest"
        owners[owner] += 1
        report_cars.append((g, old_id, nid, owner, why))
        car_json = json.loads(c["json"]) | {"id": nid}
        copy_sql.append(insert("cars3", {"id": nid, "owner_id": owner,
                                         "json": dumps(car_json),
                                         "ord": c["ord"], "updated": c["updated"],
                                         "legacy_group": g, "legacy_car_id": old_id}))
        copy_sql.append(insert("car_groups", {"car_id": nid, "group_id": g, "added": now}))
        if not park:
            continue
        pj = json.loads(park["json"]) | {"carId": nid}
        prev = json.loads(park["prev_json"]) if park["prev_json"] else None
        if prev is not None:
            prev["drivingById"] = named(g, prev.get("drivingBy"))
            if isinstance(prev.get("park"), dict):
                prev["park"]["carId"] = nid
        copy_sql.append(insert("parks3", {"car_id": nid, "json": dumps(pj),
                                          "prev_json": dumps(prev) if prev is not None else None,
                                          "by_name": park["by_name"], "by_id": park["by_id"],
                                          "driving_by": park["driving_by"],
                                          "driving_by_id": named(g, park["driving_by"]),
                                          "updated": park["updated"]}))

    over = {o: n for o, n in owners.items() if n > MAX_CARS}
    assert not over, f"owners over {MAX_CARS} cars: {over}"
    orphans = [(p["group_id"], p["car_id"]) for p in rows["parks2"]
               if p["group_id"] in by_group and (p["group_id"], p["car_id"]) not in mapping]
    report = {"groups": len(by_group), "devices": len(rows["members"]), "cars": len(cars),
              "parks": sum(1 for k in parks if k in mapping and k[0] in by_group),
              "cars_by_owner": report_cars, "rekeyed": rekeyed, "skipped_groups": skipped,
              "orphan_parks": orphans}
    return map_sql, copy_sql, report


def print_report(report: dict) -> None:
    print(f"groups {report['groups']} · devices {report['devices']} · cars {report['cars']} · parks {report['parks']}")
    for g, old_id, nid, owner, why in report["cars_by_owner"]:
        print(f"  {g} · {old_id}{' -> ' + nid if nid != old_id else ''} · {owner} · {why}")
    print(f"re-keyed {len(report['rekeyed'])}")
    for g, old_id, nid in report["rekeyed"]:
        print(f"  {g} · {old_id} -> {nid}")
    print(f"groups without members (skipped) {len(report['skipped_groups'])}: {', '.join(report['skipped_groups'])}")
    if report["orphan_parks"]:
        print(f"parks without a car (skipped) {len(report['orphan_parks'])}: {report['orphan_parks']}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--remote", action="store_true")
    parser.add_argument("--local", action="store_true")
    parser.add_argument("--db", default="dot-lab-parking")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--out", default="./migrate_v3.sql")
    parser.add_argument("--persist-to")
    args = parser.parse_args()
    if args.remote == args.local:
        parser.error("choose exactly one of --remote or --local")
    if args.persist_to and args.remote:
        parser.error("--persist-to is local only")
    target = ["--remote"] if args.remote else ["--local"] + (["--persist-to", args.persist_to] if args.persist_to else [])
    out = Path(args.out)

    def d1(*extra: str) -> list:
        # WRANGLER = a wrangler.cmd path: avoids npx re-resolving wrangler@4 on every call (slow, EPERM on a busy cache)
        base = [os.environ["WRANGLER"]] if os.environ.get("WRANGLER") else [shutil.which("npx") or "npx", "wrangler@4"]
        cmd = [*base, "d1", "execute", args.db, *target, *extra, "--json"]
        result = subprocess.run(cmd, check=True, capture_output=True, text=True, encoding="utf-8")
        out = result.stdout  # --file prints upload progress lines before the JSON
        return json.loads(out[out.index("["):])

    def select(sql: str) -> list[dict]:
        return [r for batch in d1("--command", sql) for r in batch.get("results", [])]

    def read() -> tuple[dict, dict]:
        rows = {t: select(f"SELECT * FROM {t}") for t in ("groups", "members", "cars", "parks2")}
        try:
            stored = {(r["old_group"], r["old_id"]): r["new_id"] for r in select("SELECT * FROM car_map")}
        except subprocess.CalledProcessError:
            if not args.dry_run:
                raise
            stored = {}  # dry-run before the CREATE file was applied
        return rows, stored

    def run_file(statements: list[str]) -> None:
        out.write_text("\n".join(statements) + "\n", encoding="utf-8")
        if statements:
            d1("--file", str(out))

    if args.dry_run:
        rows, stored = read()
        map_sql, copy_sql, report = plan(rows, stored, new_id)
        out.write_text("\n".join(map_sql + copy_sql) + "\n", encoding="utf-8")
        print_report(report)
        print(f"dry-run: {len(map_sql)} car_map + {len(copy_sql)} copy statements -> {out}")
        return

    d1("--file", str(MIGRATION))
    rows, stored = read()
    map_sql, _, _ = plan(rows, stored, new_id)
    run_file(map_sql)
    rows, stored = read()
    map_sql, copy_sql, report = plan(rows, stored, new_id)
    assert not map_sql, "car_map still incomplete after insert"
    run_file(copy_sql)
    print_report(report)
    print(f"applied {len(copy_sql)} copy statements (INSERT OR IGNORE) -> {out}")


if __name__ == "__main__":
    main()

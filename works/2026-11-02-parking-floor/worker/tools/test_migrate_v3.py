"""v3 migration on synthetic families, with wrangler replaced by an in-memory sqlite3 executor."""

import contextlib
import importlib.util
import io
import json
import sqlite3
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("migrate_v3", Path(__file__).with_name("migrate_v3.py"))
migrate_v3 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate_v3)

SCHEMA = (Path(__file__).resolve().parents[1] / "schema.sql").read_text(encoding="utf-8")
OLD_SCHEMA = SCHEMA.split("-- v3 tables")[0]
V3_TABLES = ("devices", "memberships", "cars3", "car_groups", "parks3", "car_map")


def park(car, at, by_id, driving_by=None, prev=None):
    return {"car_id": car, "json": json.dumps({"id": f"p{at}", "carId": car, "atMs": at, "floor": "B2", "photo": False}),
            "prev_json": json.dumps(prev) if prev else None, "by_name": "x", "by_id": by_id,
            "driving_by": driving_by, "updated": at}


def seed(conn):
    groups = [("gA", 1), ("gB", 2), ("gC", 3)]
    members = [  # id, group, name, owner, created
        ("a1", "gA", "엄마", 1, 100), ("a2", "gA", "아빠", 0, 200),
        ("b1", "gB", "아빠", 0, 50), ("b2", "gB", "아빠", 0, 60),
    ]
    cars = [  # group, id, updated
        ("gA", "c_1", 10), ("gA", "c_2", 10), ("gA", "c_shared", 10),
        ("gB", "c_shared", 5000),  # gA copy has a newer park (9000), so gB is re-keyed
        ("gC", "c_9", 10),  # gC has no members -> skipped
    ]
    parks = [
        ("gA", park("c_1", 1000, "a2", "아빠", {"park": {"id": "p1", "carId": "c_1", "atMs": 1}, "by": "엄마",
                                                "byId": "a1", "drivingBy": "엄마"})),
        ("gA", park("c_2", 2000, "x9", "누구")),  # x9 left the family -> 방장 a1; no 누구 -> NULL
        ("gA", park("c_shared", 9000, "a1")),
    ]
    conn.executemany("INSERT INTO groups (id, created) VALUES (?,?)", groups)
    conn.executemany("INSERT INTO members (id,group_id,name,secret_hash,fcm_token,owner,created,last_seen) "
                     "VALUES (?,?,?,'h_'||?1,NULL,?,?,?5)", members)
    conn.executemany("INSERT INTO cars (group_id,id,json,ord,updated) VALUES (?,?,json_object('id',?2,'name','차'),0,?)", cars)
    for g, p in parks:
        conn.execute("INSERT INTO parks2 (group_id,car_id,json,prev_json,by_name,by_id,driving_by,updated) "
                     "VALUES (?,?,?,?,?,?,?,?)", (g, *p.values()))
    conn.commit()


def db(schema=SCHEMA):
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(schema)
    seed(conn)
    return conn


def fake_wrangler(conn, calls):
    def run(args, **kwargs):
        if "--command" in args:
            sql = args[args.index("--command") + 1]
            calls.append(sql)
            try:
                results = [dict(r) for r in conn.execute(sql).fetchall()]
            except sqlite3.OperationalError as e:  # wrangler exits non-zero on a SQL error
                raise subprocess.CalledProcessError(1, args, stderr=str(e))
        else:
            path = args[args.index("--file") + 1]
            calls.append(f"FILE {path}")
            conn.executescript(Path(path).read_text(encoding="utf-8"))
            results = []
        return type("Result", (), {"stdout": json.dumps([{"results": results}])})()
    return run


def one_id():
    ids = iter(["c_new"])
    return lambda: next(ids)  # StopIteration on a second call = the mapping was re-decided


class MigrateV3Test(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.out = str(Path(self.tmp.name) / "migrate_v3.sql")

    def tearDown(self):
        self.tmp.cleanup()

    def migrate(self, conn, *flags, new_id=None, calls=None):
        argv = ["migrate_v3.py", "--local", "--out", self.out, *flags]
        with patch.object(migrate_v3.subprocess, "run", side_effect=fake_wrangler(conn, [] if calls is None else calls)), \
                patch.object(migrate_v3, "new_id", new_id or one_id()), patch("sys.argv", argv), \
                contextlib.redirect_stdout(io.StringIO()) as out:
            migrate_v3.main()
        return out.getvalue()

    def cars(self, conn):
        return {(r["legacy_group"], r["legacy_car_id"]): dict(r) for r in conn.execute("SELECT * FROM cars3")}

    def test_owner_rules_and_collision(self):
        conn = db()
        report = self.migrate(conn)
        cars = self.cars(conn)
        self.assertEqual(cars[("gA", "c_1")]["owner_id"], "a2")  # last parker
        self.assertEqual(cars[("gA", "c_2")]["owner_id"], "a1")  # parker left -> 방장
        self.assertEqual(cars[("gB", "c_shared")]["owner_id"], "b1")  # no parks, no 방장 -> oldest
        self.assertIn("last-parker", report)
        self.assertIn("방장", report)
        self.assertIn("oldest", report)
        self.assertEqual(cars[("gA", "c_shared")]["id"], "c_shared")
        moved = cars[("gB", "c_shared")]
        self.assertEqual((moved["id"], moved["legacy_car_id"]), ("c_new", "c_shared"))
        self.assertEqual(json.loads(moved["json"])["id"], "c_new")
        self.assertNotIn(("gC", "c_9"), cars)
        self.assertIn("gC", report)
        self.assertEqual({r["car_id"]: r["group_id"] for r in conn.execute("SELECT * FROM car_groups")},
                         {"c_1": "gA", "c_2": "gA", "c_shared": "gA", "c_new": "gB"})
        self.assertEqual(conn.execute("SELECT count(*) FROM devices").fetchone()[0], 4)
        self.assertEqual(conn.execute("SELECT owner FROM memberships WHERE device_id='a1'").fetchone()[0], 1)

    def test_driving_by_id_match_and_null(self):
        conn = db()
        self.migrate(conn)
        parks = {r["car_id"]: r for r in conn.execute("SELECT * FROM parks3")}
        self.assertEqual(parks["c_1"]["driving_by_id"], "a2")
        self.assertEqual(json.loads(parks["c_1"]["prev_json"])["drivingById"], "a1")
        self.assertEqual(json.loads(parks["c_1"]["json"])["carId"], "c_1")
        self.assertIsNone(parks["c_2"]["driving_by_id"])
        self.assertEqual(set(parks), {"c_1", "c_2", "c_shared"})

    def test_rerun_changes_nothing_and_keeps_mapping(self):
        conn = db()
        self.migrate(conn)
        before = conn.total_changes
        self.migrate(conn, new_id=lambda: self.fail("mapping re-decided"))
        self.assertEqual(conn.total_changes, before)

    def test_half_copy_then_full_run_equals_clean_run(self):
        clean, partial = db(), db()
        self.migrate(clean)
        partial.executescript(migrate_v3.MIGRATION.read_text(encoding="utf-8"))
        rows = {t: [dict(r) for r in partial.execute(f"SELECT * FROM {t}")] for t in ("groups", "members", "cars", "parks2")}
        map_sql, copy_sql, _ = migrate_v3.plan(rows, {}, one_id())
        partial.executescript("\n".join(map_sql + copy_sql[: len(copy_sql) // 2]))
        self.migrate(partial, new_id=lambda: self.fail("mapping re-decided"))
        for t in V3_TABLES:
            dump = lambda c: sorted(tuple(r) for r in c.execute(f"SELECT * FROM {t}"))
            if t == "car_groups":
                dump = lambda c: sorted(tuple(r) for r in c.execute("SELECT car_id, group_id FROM car_groups"))
            self.assertEqual(dump(partial), dump(clean), t)

    def test_dry_run_only_selects_and_writes_sql(self):
        conn = db(OLD_SCHEMA)  # v3 tables not created yet: car_map missing = empty
        calls = []
        report = self.migrate(conn, "--dry-run", calls=calls)
        self.assertTrue(calls)
        self.assertTrue(all(c.startswith("SELECT") for c in calls), calls)
        self.assertIsNone(conn.execute("SELECT name FROM sqlite_master WHERE name='cars3'").fetchone())
        sql = Path(self.out).read_text(encoding="utf-8")
        self.assertIn("INSERT OR IGNORE INTO car_map", sql)
        self.assertIn("INSERT OR IGNORE INTO parks3", sql)
        self.assertIn("re-keyed 1", report)

    def test_owner_cap_asserted(self):
        rows = {"groups": [{"id": "g"}], "parks2": [],
                "members": [{"id": "m", "group_id": "g", "name": "n", "secret_hash": "h", "fcm_token": None,
                             "owner": 1, "created": 1, "last_seen": 1}],
                "cars": [{"group_id": "g", "id": f"c_{i}", "json": "{}", "ord": i, "updated": 1} for i in range(5)]}
        with self.assertRaises(AssertionError):
            migrate_v3.plan(rows, {}, one_id())


if __name__ == "__main__":
    unittest.main()

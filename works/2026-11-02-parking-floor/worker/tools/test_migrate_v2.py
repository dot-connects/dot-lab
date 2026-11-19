"""Check that the additive migration can be applied twice without D1 access."""

import contextlib
import importlib.util
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("migrate_v2", Path(__file__).with_name("migrate_v2.py"))
migrate_v2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate_v2)


class MigrationTest(unittest.TestCase):
    def test_two_runs_skip_existing_columns(self):
        columns = {"members": set(), "groups": set()}
        calls = []

        def fake_run(args, **kwargs):
            statement = args[args.index("--command") + 1]
            calls.append(statement)
            if statement.startswith("PRAGMA table_info"):
                table = statement.removeprefix("PRAGMA table_info(").removesuffix(")")
                results = [{"name": name} for name in columns[table]]
            else:
                results = []
                if statement.startswith("ALTER TABLE members"):
                    if "last_seen" in columns["members"]:
                        raise AssertionError("duplicate last_seen ALTER")
                    columns["members"].add("last_seen")
                if statement.startswith("ALTER TABLE groups"):
                    if "name" in columns["groups"]:
                        raise AssertionError("duplicate name ALTER")
                    columns["groups"].add("name")
            return type("Result", (), {"stdout": json.dumps([{"results": results}])})()

        with patch.object(migrate_v2.subprocess, "run", side_effect=fake_run), patch("sys.argv", ["migrate_v2.py", "--local"]):
            with contextlib.redirect_stdout(io.StringIO()):
                migrate_v2.main()
                migrate_v2.main()
        self.assertEqual(sum(s.startswith("ALTER TABLE") for s in calls), 2)
        self.assertEqual(sum(s.startswith("CREATE TABLE") for s in calls), 4)


if __name__ == "__main__":
    unittest.main()

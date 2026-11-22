"""Live smoke test for the v3 cutover (spec-v3-groups.md section 9, steps 2 and 5).

  smoke_v3.py seed  [base]   maintenance build (v2 routes): throwaway family + car + park -> ~/.wherepark/smoke-v3-seed.json
  smoke_v3.py check [base]   v3 build, maintenance still on: migrated seed + two-device, two-group flow; exit 1 on any FAIL

Every call carries header x-smoke from env WP_SMOKE_KEY (the maintenance gate bypass). Never run it
without the gate being on: it creates and deletes throwaway devices.
"""
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_BASE = "https://dot-lab-parking.dotconnects.workers.dev"
SEED = Path.home() / ".wherepark" / "smoke-v3-seed.json"
BASE = DEFAULT_BASE
SMOKE_KEY = os.environ.get("WP_SMOKE_KEY", "")


def call(method: str, path: str, body=None, auth: str | None = None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("user-agent", "wherepark-smoke")
    req.add_header("x-smoke", SMOKE_KEY)
    if data is not None:
        req.add_header("content-type", "application/json")
        req.add_header("content-length", str(len(data)))
    if auth:
        req.add_header("authorization", f"Bearer {auth}")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            txt = r.read()
            return r.status, (json.loads(txt) if r.headers.get("content-type", "").startswith("application/json") else txt)
    except urllib.error.HTTPError as e:
        txt = e.read()
        try:
            return e.code, json.loads(txt)
        except Exception:
            return e.code, txt


def check(name: str, ok: bool, detail="") -> bool:
    print(("PASS " if ok else "FAIL ") + name + (f"  {detail}" if detail and not ok else ""))
    return bool(ok)


def new_car(now: int, name: str) -> dict:
    return {"id": "c_" + secrets.token_hex(4), "name": name, "color": -723724, "plate": None, "order": 0, "updated": now}


def new_park(car_id: str, now: int, floor: str) -> dict:
    return {"id": now, "carId": car_id, "atMs": now, "floor": floor, "zone": "C", "number": 37, "state": "PARKED"}


def seed() -> int:
    now = int(time.time() * 1000)
    s, g = call("POST", "/v2/groups", {"name": "스모크", "groupName": "스모크 가족"})
    if s != 200 or "secret" not in g:
        print(f"FAIL seed: create family  {(s, g)}")
        return 1
    token = f"{g['memberId']}.{g['secret']}"
    car = new_car(now, "시드 차")
    s1, b1 = call("PUT", f"/v2/cars/{car['id']}", {"car": car}, token)
    s2, b2 = call("PUT", f"/v2/cars/{car['id']}/park", {"park": new_park(car["id"], now, "B2")}, token)
    if s1 != 200 or s2 != 200:
        print(f"FAIL seed: car/park  {(s1, b1, s2, b2)}")
        call("DELETE", "/v2/me", None, token)
        return 1
    SEED.parent.mkdir(parents=True, exist_ok=True)
    SEED.write_text(json.dumps({"base": BASE, "token": token, "memberId": g["memberId"], "groupId": g["groupId"],
                                "carId": car["id"], "floor": "B2"}), encoding="utf-8")
    print(f"PASS seed written -> {SEED}")
    return 0


def run_check() -> int:
    if not SEED.exists():
        print(f"FAIL no seed file {SEED} (run `seed` first)")
        return 1
    sd = json.loads(SEED.read_text(encoding="utf-8"))
    results: list[bool] = []
    held = {"seed": sd["token"]}  # every credential we hold, deleted at the end

    def ok(name, cond, detail=""):
        results.append(check(name, cond, detail))
        return cond

    def flow() -> None:
        # 1. the migrated seed credential works on both state routes, with its car and park
        s, st2 = call("GET", "/v2/state", auth=sd["token"])
        ok("seed /v2/state has car + park", s == 200 and any(c["id"] == sd["carId"] for c in st2.get("cars", []))
           and (st2.get("parks", {}).get(sd["carId"]) or {}).get("floor") == sd["floor"], (s, st2))
        s, st3 = call("GET", "/v3/state", auth=sd["token"])
        ok("seed /v3/state has group + car + park", s == 200 and st3["me"]["id"] == sd["memberId"]
           and [g["id"] for g in st3["groups"]] == [sd["groupId"]]
           and any(c["id"] == sd["carId"] and c["mine"] for c in st3["cars"])
           and (st3["parks"].get(sd["carId"]) or {}).get("floor") == sd["floor"], (s, st3))

        # 2. devices A (G1, G2) and B (G1)
        s, a = call("POST", "/v3/groups", {"name": "에이", "groupName": "스모크1"})
        if not ok("A creates G1", s == 200 and "token" in a, (s, a)):
            return
        held["A"] = a["token"]
        g1 = a["groupId"]
        s, g2r = call("POST", "/v3/groups", {"groupName": "스모크2"}, a["token"])
        ok("A creates G2", s == 200 and g2r["groupId"] != g1, (s, g2r))
        s, inv = call("POST", f"/v3/groups/{g1}/invites", None, a["token"])
        if not ok("A invites to G1", s == 200 and "code" in inv, (s, inv)):
            return
        s, b = call("POST", "/v3/join", {"code": inv["code"], "name": "비"})
        if not ok("B joins G1", s == 200 and b.get("groupId") == g1 and "token" in b, (s, b)):
            return
        held["B"] = b["token"]

        # 3. A's car into G1, B parks it, A excludes it, B loses it, B leaves, B is gone
        now = int(time.time() * 1000)
        car = new_car(now, "스모크 차")
        cid = car["id"]
        s, r = call("PUT", f"/v3/cars/{cid}", {"car": car}, a["token"])
        ok("A creates car", s == 200, (s, r))
        s, r = call("PUT", f"/v3/cars/{cid}/groups/{g1}", None, a["token"])
        ok("A includes car in G1", s == 200, (s, r))
        s, r = call("PUT", f"/v3/cars/{cid}/park", {"park": new_park(cid, now, "B3")}, b["token"])
        ok("B parks A's car = 200", s == 200, (s, r))
        s, r = call("DELETE", f"/v3/cars/{cid}/groups/{g1}", None, a["token"])
        ok("A excludes car from G1", s == 200, (s, r))
        s, r = call("PUT", f"/v3/cars/{cid}/park", {"park": new_park(cid, now + 1, "B4")}, b["token"])
        ok("B parks excluded car = 404 car", s == 404 and r.get("error") == "car", (s, r))
        s, r = call("DELETE", f"/v3/groups/{g1}/me", None, b["token"])
        ok("B leaves G1", s == 200, (s, r))
        s, r = call("GET", "/v3/state", auth=b["token"])
        ok("B after leaving = 401", s == 401, (s, r))
    try:
        flow()
    finally:
        # DELETE /v3/me for A and the seed; B already left, so its 401 is expected and not a result
        for who, token in reversed(held.items()):
            s, r = call("DELETE", "/v3/me", None, token)
            if who != "B":
                ok(f"cleanup: delete {who}", s == 200, (s, r))
        if all(results):
            SEED.unlink(missing_ok=True)
    print(f"{sum(results)}/{len(results)} passed")
    return 0 if all(results) else 1


def main() -> int:
    global BASE
    if len(sys.argv) < 2 or sys.argv[1] not in ("seed", "check"):
        print(__doc__)
        return 2
    if not SMOKE_KEY:
        print("FAIL env WP_SMOKE_KEY is not set")
        return 1
    BASE = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_BASE
    return seed() if sys.argv[1] == "seed" else run_check()


if __name__ == "__main__":
    sys.exit(main())

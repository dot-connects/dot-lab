"""Live smoke test for Worker v2: makes a throwaway family, exercises the main routes, then deletes it."""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://dot-lab-parking.dotconnects.workers.dev"


def call(method, path, body=None, auth=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("user-agent", "wherepark-smoke")
    if data is not None:
        req.add_header("content-type", ctype)
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


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (f"  {detail}" if detail and not ok else ""))
    return ok


def main():
    results = []
    s, b = call("GET", "/v1/park")
    results.append(check("v1 /park without auth = 401", s == 401, s))
    s, b = call("GET", "/j")
    results.append(check("/j page 200 utf-8", s == 200 and "초대".encode() in b, s))
    s, b = call("GET", "/.well-known/assetlinks.json")
    results.append(check("assetlinks json array", s == 200 and isinstance(b, list), s))
    s, g = call("POST", "/v2/groups", {"name": "스모크", "groupName": "스모크네 가족"})
    if not check("create group", s == 200 and "secret" in g, (s, g)):
        return 1
    auth = f"{g['memberId']}.{g['secret']}"
    now = int(time.time() * 1000)
    car = {"id": "c_5e0c0001", "name": "흰 차", "color": -723724, "plate": None, "order": 0, "updated": now}
    s, b = call("PUT", "/v2/cars/c_5e0c0001", {"car": car}, auth)
    results.append(check("put car (plate null)", s == 200, (s, b)))
    p1 = {"id": now, "carId": "c_5e0c0001", "atMs": now, "floor": "B2", "zone": "C", "number": 37, "state": "PARKED"}
    s, b = call("PUT", "/v2/cars/c_5e0c0001/park", {"park": p1}, auth)
    results.append(check("put park (missing pillar/memo)", s == 200, (s, b)))
    p2 = dict(p1, id=now + 1, atMs=now + 1, floor="B4")
    s, b = call("PUT", "/v2/cars/c_5e0c0001/park", {"park": p2}, auth)
    results.append(check("put newer park", s == 200, (s, b)))
    s, b = call("PUT", "/v2/cars/c_5e0c0001/park", {"park": p1}, auth)
    results.append(check("older park = 409 stale", s == 409, (s, b)))
    jpeg = b"\xff\xd8\xff" + b"0" * 100_000
    s, b = call("PUT", f"/v2/cars/c_5e0c0001/photo?park={now + 1}", raw=jpeg, auth=auth, ctype="image/jpeg")
    results.append(check("photo 100 KB", s == 200, (s, b)))
    s, b = call("GET", f"/v2/cars/c_5e0c0001/photo?park={now + 1}", auth=auth)
    results.append(check("photo get", s == 200 and len(b) == len(jpeg), s))
    s, b = call("POST", "/v2/cars/c_5e0c0001/state", {"state": "DRIVING"}, auth)
    results.append(check("state driving", s == 200, (s, b)))
    s, b = call("POST", "/v2/cars/c_5e0c0001/undo", {"parkId": now + 1}, auth)
    results.append(check("undo", s == 200, (s, b)))
    s, st = call("GET", "/v2/state", auth=auth)
    park = (st.get("parks") or {}).get("c_5e0c0001") if isinstance(st, dict) else None
    results.append(check("state after undo = B2", s == 200 and park and park.get("floor") == "B2", (s, st)))
    s, b = call("POST", "/v2/invites", None, auth)
    results.append(check("invite link", s == 200 and str(b.get("link", "")).endswith("#" + b.get("code", "?")), (s, b)))
    s, b = call("DELETE", "/v2/me", None, auth)
    results.append(check("delete me (cleanup)", s == 200, (s, b)))
    print(f"{sum(results)}/{len(results)} passed")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())

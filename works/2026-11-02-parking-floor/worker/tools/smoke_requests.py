"""Live check of the join-request routes: a throwaway family accepts a request, the requester polls, peeks, joins; then cleanup."""
import hashlib
import secrets
import sys

from smoke_v2 import call, check


def main() -> int:
    r = []
    s, g = call("POST", "/v2/groups", {"name": "요청스모크", "groupName": "요청스모크네 가족"})
    if not check("create group", s == 200, (s, g)):
        return 1
    owner = f"{g['memberId']}.{g['secret']}"
    s, inv = call("POST", "/v2/invites", None, owner)
    r.append(check("normal invite", s == 200, (s, inv)))
    sec = secrets.token_hex(16)
    h = hashlib.sha256(sec.encode()).hexdigest()
    s, b = call("POST", "/v2/requests/poll", {"secret": sec})
    r.append(check("poll before accept = 404", s == 404, (s, b)))
    s, b = call("PUT", "/v2/requests", {"req": h}, owner)
    r.append(check("accept", s == 200, (s, b)))
    s, b = call("PUT", "/v2/requests", {"req": h}, owner)
    r.append(check("second accept = 409", s == 409, (s, b)))
    s, b = call("POST", "/v2/requests/poll", {"secret": h[:32]})
    r.append(check("poll with link hash = 404", s == 404, (s, b)))
    s, b = call("POST", "/v2/requests/poll", {"secret": sec})
    ok = check("poll returns code", s == 200 and "code" in b, (s, b)); r.append(ok)
    r.append(check("poll says who accepted", s == 200 and b.get("byName") == "요청스모크", (s, b)))
    code = b.get("code") if ok else ""
    s, b = call("POST", "/v2/peek", {"code": code})
    r.append(check("peek shows family", s == 200 and b.get("groupName") == "요청스모크네 가족" and b.get("count") == 1, (s, b)))
    s, b = call("POST", "/v2/peek", {"code": inv.get("code", "")})
    r.append(check("normal invite still alive after accept", s == 200, (s, b)))
    s, j = call("POST", "/v2/join", {"code": code, "name": "새사람"})
    r.append(check("join with request code", s == 200 and j.get("groupId") == g.get("groupId"), (s, j)))
    s, b = call("POST", "/v2/requests/poll", {"secret": sec})
    r.append(check("request gone after join = 404", s == 404, (s, b)))
    # cancel path
    sec2 = secrets.token_hex(16)
    h2 = hashlib.sha256(sec2.encode()).hexdigest()
    call("PUT", "/v2/requests", {"req": h2}, owner)
    s, b = call("POST", "/v2/requests/poll", {"secret": sec2})
    code2 = b.get("code", "") if s == 200 else ""
    s, b = call("POST", "/v2/requests/cancel", {"secret": sec2})
    r.append(check("cancel", s == 200, (s, b)))
    s, b = call("POST", "/v2/peek", {"code": code2})
    r.append(check("cancelled request's code is dead", s == 404, (s, b)))
    if s == 200 or j.get("secret"):
        call("DELETE", "/v2/me", None, f"{j['memberId']}.{j['secret']}") if j.get("secret") else None
    s, b = call("DELETE", "/v2/me", None, owner)
    r.append(check("cleanup", s == 200, (s, b)))
    print(f"{sum(r)}/{len(r)} passed")
    return 0 if all(r) else 1


if __name__ == "__main__":
    sys.exit(main())

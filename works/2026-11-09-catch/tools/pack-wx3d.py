"""Pack out/wx3d/<critter>/ frames into wx3d/<critter>.zip + wx3d/manifest.json for the app (phase C).

The app downloads one critter's zip on first need and checks size + sha256 from the manifest.
Zips are stored (webp is already compressed) with a fixed timestamp, so a rerun on the same frames gives the same hash.
Run from anywhere: python tools/pack-wx3d.py
"""
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "out" / "wx3d"
DST = ROOT / "wx3d"
STATES = ["sun", "hot", "cold", "snow", "rain", "dust"]
NAME = re.compile(r"^([a-z]+)-([a-z]+)-(act|loop)-(\d\d)\.webp$")


def pack(critter: Path) -> dict:
    counts: dict[str, dict[str, int]] = {}
    files = sorted(p for p in critter.iterdir() if NAME.match(p.name))
    for p in files:
        c, s, kind, _ = NAME.match(p.name).groups()
        assert c == critter.name, p
        counts.setdefault(s, {"act": 0, "loop": 0})[kind] += 1
    missing = [s for s in STATES if not counts.get(s, {}).get("act")]
    assert not missing, f"{critter.name}: no act frames for {missing}"
    out = DST / f"{critter.name}.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as z:
        for p in files:
            z.writestr(zipfile.ZipInfo(p.name, date_time=(2026, 1, 1, 0, 0, 0)), p.read_bytes())
    data = out.read_bytes()
    return {"zip": out.name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "states": {s: counts[s] for s in STATES}}


def main() -> None:
    DST.mkdir(exist_ok=True)
    critters = {d.name: pack(d) for d in sorted(SRC.iterdir()) if d.is_dir() and d.name != "sheets"}
    (DST / "manifest.json").write_text(json.dumps({"v": 1, "fps": 12, "critters": critters}, indent=1) + "\n", encoding="utf-8")
    for k, v in critters.items():
        print(f"{k:10} {v['size'] / 1e6:.2f} MB")


if __name__ == "__main__":
    main()

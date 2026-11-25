"""Seed an emulator gallery with a sample trip: EXIF-dated, geotagged JPEGs plus deliberately bad copies.

Usage: python seed_emulator.py <photo folder of .webp/.jpg> [--date 2026-06-12] [--days 3]
Writes to a temp folder, pushes to /sdcard/DCIM/Camera (and Screenshots for the fake screenshot), then asks the media
scanner to index them. Sample photos are never committed (see plan.md Task 6).
"""
import io
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

from PIL import Image, ImageFilter

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
# rough Jeju spots for the sample keys (so places and the map have something real to work with)
GPS = {"hyeopjae": (33.394, 126.239), "road": (33.345, 126.180), "udo": (33.506, 126.953), "haenyeo": (33.524, 126.894),
       "doldam": (33.556, 126.796), "woljeongri": (33.556, 126.796), "seongsan": (33.458, 126.942),
       "hallasan": (33.392, 126.495), "oreum": (33.373, 126.360), "tangerine": (33.300, 126.400)}


def _rational(x: float):
    d = int(x); m = int((x - d) * 60); s = ((x - d) * 60 - m) * 60
    return (float(d), float(m), round(s, 2))


def save(img: Image.Image, path: Path, when: datetime, gps: tuple[float, float] | None) -> None:
    ex = Image.Exif()
    stamp = when.strftime("%Y:%m:%d %H:%M:%S")
    ex[0x0132] = stamp
    # DateTimeOriginal/OffsetTimeOriginal live in the Exif sub-IFD; in IFD0 MediaStore leaves datetaken NULL (2026-11-26).
    sub = ex.get_ifd(0x8769)
    sub[0x9003] = stamp
    sub[0x9011] = "+09:00"
    if gps:
        g = ex.get_ifd(0x8825)
        g.update({1: "N", 2: _rational(gps[0]), 3: "E", 4: _rational(gps[1])})
    img.convert("RGB").save(path, "JPEG", quality=88, exif=ex)


def adb(*args: str) -> None:
    subprocess.run([ADB, *args], check=True)


def main() -> None:
    src = Path(sys.argv[1])
    start = datetime.fromisoformat(next((a[7:] for a in sys.argv if a.startswith("--date=")), "2026-06-12"))
    days = int(next((a[7:] for a in sys.argv if a.startswith("--days=")), "3"))
    files = sorted([*src.glob("*.webp"), *src.glob("*.jpg")])
    out = Path(tempfile.mkdtemp(prefix="seed-"))
    cam = out / "Camera"; cam.mkdir()
    shots = out / "Screenshots"; shots.mkdir()
    for i, f in enumerate(files):
        when = start + timedelta(days=i * days // max(1, len(files)), hours=9 + (i % 9), minutes=(i * 7) % 60)
        key = f.stem.split("_")[0]
        img = Image.open(f)
        save(img, cam / f"IMG_{when:%Y%m%d_%H%M%S}.jpg", when, GPS.get(key))
        if i == 0:  # a burst: two near-identical frames a second apart
            save(img, cam / f"IMG_{when + timedelta(seconds=1):%Y%m%d_%H%M%S}.jpg", when + timedelta(seconds=1), GPS.get(key))
        if i == 1:  # blurred and dark copies
            save(img.filter(ImageFilter.GaussianBlur(8)), cam / f"IMG_{when + timedelta(minutes=1):%Y%m%d_%H%M%S}.jpg",
                 when + timedelta(minutes=1), GPS.get(key))
            save(img.point(lambda p: p * 0.06), cam / f"IMG_{when + timedelta(minutes=2):%Y%m%d_%H%M%S}.jpg",
                 when + timedelta(minutes=2), GPS.get(key))
        if i == 2:
            save(img, shots / f"Screenshot_{when:%Y%m%d_%H%M%S}.jpg", when, None)
    adb("shell", "rm", "-rf", "/sdcard/DCIM/Camera", "/sdcard/Pictures/Screenshots")  # re-seed from scratch
    adb("shell", "mkdir", "-p", "/sdcard/DCIM/Camera", "/sdcard/Pictures/Screenshots")
    for f in cam.iterdir():
        adb("push", str(f), f"/sdcard/DCIM/Camera/{f.name}")
    for f in shots.iterdir():
        adb("push", str(f), f"/sdcard/Pictures/Screenshots/{f.name}")
    adb("shell", "content", "call", "--method", "scan_volume", "--uri", "content://media", "--arg", "external_primary")
    print(f"pushed {len(list(cam.iterdir()))} camera + {len(list(shots.iterdir()))} screenshot files")


if __name__ == "__main__":
    main()

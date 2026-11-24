"""One headless-Chrome render at a time for this session (workspace heavy-job rule: renders share 2 slots).

Also: BelowNormal priority + affinity 0xC0 (inherited by Chromium children), and no renders in the
US-close protected window (winter Tue-Sat 06:00-06:40 local).
"""
import contextlib
import ctypes
import datetime
import os
import time
from pathlib import Path

LOCK = Path(__file__).resolve().parent / ".render.lock"
STALE_S = 600


def _throttle() -> None:
    k = ctypes.windll.kernel32
    h = k.GetCurrentProcess()
    k.SetPriorityClass(h, 0x4000)  # BELOW_NORMAL_PRIORITY_CLASS
    k.SetProcessAffinityMask(h, 0xC0)


def _in_protected_window() -> bool:
    n = datetime.datetime.now()
    return n.weekday() in (1, 2, 3, 4, 5) and datetime.time(5, 58) <= n.time() < datetime.time(6, 42)


@contextlib.contextmanager
def render_slot():
    _throttle()
    while True:
        while _in_protected_window():
            time.sleep(30)
        try:
            fd = os.open(LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode())
            os.close(fd)
            break
        except FileExistsError:
            try:
                if time.time() - LOCK.stat().st_mtime > STALE_S:
                    LOCK.unlink(missing_ok=True)
                    continue
            except FileNotFoundError:
                continue
            time.sleep(2)
    try:
        yield
    finally:
        LOCK.unlink(missing_ok=True)

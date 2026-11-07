"""Sound track (audio.html, offline Web Audio) + frames -> out/reel.mp4 (1080x1920, 30 fps, H.264 + AAC).

Run after render.py: uv run --with playwright python compose.py
"""
import asyncio, base64, pathlib, subprocess, sys
from playwright.async_api import async_playwright

if sys.platform == "win32":   # idle priority, inherited by Chrome: the trading tick shares this machine
    import ctypes; ctypes.windll.kernel32.SetPriorityClass(ctypes.windll.kernel32.GetCurrentProcess(), 0x40)
    # and only the last two cores (children inherit the mask), so the scheduled tick keeps the other six
    ctypes.windll.kernel32.SetProcessAffinityMask(ctypes.windll.kernel32.GetCurrentProcess(), 0xC0)

HERE = pathlib.Path(__file__).parent
OUT = HERE / "out"

async def sound():
    srv = subprocess.Popen([sys.executable, "-m", "http.server", "8765", "--bind", "127.0.0.1"], cwd=str(HERE.parents[2]),
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        async with async_playwright() as p:
            b = await p.chromium.launch(channel="chrome")
            pg = await b.new_page(); pg.on("pageerror", lambda e: print("ERR", e)); pg.on("console", lambda m: print("CON", m.type, m.text))
            await pg.goto("http://127.0.0.1:8765/works/2026-11-04-dalgona/reel/audio.html")
            await pg.wait_for_function("window.__wav", timeout=900000, polling=1000)
            (OUT / "sound.wav").write_bytes(base64.b64decode(await pg.evaluate("window.__wav")))
            await b.close()
    finally:
        srv.terminate()

asyncio.run(sound())
if "--sound-only" in sys.argv: sys.exit(0)
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "30", "-i", str(OUT / "frames" / "%04d.png"), "-i", str(OUT / "sound.wav"),
                "-vf", "scale=1080:1920:flags=lanczos,format=yuv420p", "-c:v", "libx264", "-preset", "slow", "-crf", "18",
                "-af", "afade=t=in:d=0.08,loudnorm=I=-15:TP=-1.5:LRA=11", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", str(OUT / "reel.mp4")], check=True)
print("wrote", OUT / "reel.mp4")

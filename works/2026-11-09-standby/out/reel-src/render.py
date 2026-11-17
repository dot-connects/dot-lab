"""Render v9.html frame by frame at 1080x1920/30fps into final/frames/*.jpg.

usage: render.py ROOT [gpu|swiftshader] [start_frame] [end_frame]
Pins itself (and so every Chromium child) to logical CPUs 6-7 (0xC0) at BelowNormal priority.
"""
import ctypes, subprocess, functools, http.server, os, socketserver, sys, threading, time
from playwright.sync_api import sync_playwright

k32 = ctypes.windll.kernel32
h = k32.GetCurrentProcess()
k32.SetProcessAffinityMask(h, 0xC0)
k32.SetPriorityClass(h, 0x4000)  # BELOW_NORMAL_PRIORITY_CLASS, inherited by children

ROOT = sys.argv[1]
MODE = sys.argv[2] if len(sys.argv) > 2 else "gpu"
F0 = int(sys.argv[3]) if len(sys.argv) > 3 else 0
F1 = int(sys.argv[4]) if len(sys.argv) > 4 else 960
PAGE = sys.argv[5] if len(sys.argv) > 5 else "v9.html"
TOFF = float(sys.argv[6]) if len(sys.argv) > 6 else 0.0
OUT = os.path.join(ROOT, "final", sys.argv[7] if len(sys.argv) > 7 else "frames")
os.makedirs(OUT, exist_ok=True)
SCALE = 2.7  # 400 css px stage * 2.7 = 1080 device px; keeps the page's own px sizes for captions

H = functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT)
H.log_message = lambda *a: None
srv = socketserver.ThreadingTCPServer(("127.0.0.1", 0), H)
srv.daemon_threads = True
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()

CAPTURE_CSS = """<style>
html,body{margin:0;background:#000;overflow:hidden}
.wrap{padding:0!important;max-width:none!important;margin:0!important}
.wrap>h1,.wrap>.lead,.ui,.grid>div:nth-child(2){display:none!important}
.grid{display:block!important}
.stage{width:400px!important;max-width:none!important;height:711.1111px!important;aspect-ratio:auto!important;border-radius:0!important}
*{transition:none!important;animation:none!important}
</style>"""

# CSS 'ease' transitions, emulated per frame from the class / opacity state renderAt leaves behind.
EMU_JS = """
(t)=>{
  const ease=x=>{ // cubic-bezier(.25,.1,.25,1)
    if(x<=0)return 0;if(x>=1)return 1;
    let lo=0,hi=1,u=.5;for(let i=0;i<30;i++){u=(lo+hi)/2;const bx=3*.25*u*(1-u)**2+3*.25*u*u*(1-u)+u**3;if(bx<x)lo=u;else hi=u}
    return 3*.1*u*(1-u)**2+3*1*u*u*(1-u)+u**3};
  const S=window.__emu??={};
  const els=[['cap',.4,true],['endA',.7,true],['endB',.7,true],['scrim',.5,false]];
  for(const [id,dur,cls] of els){
    const el=document.getElementById(id);
    const target=cls?(el.classList.contains('on')?1:0):parseFloat(el.style.opacity||0);
    let s=S[id];
    if(!s){s=S[id]={target,from:target,t0:-99}}
    const cur=s.from+(s.target-s.from)*ease((t-s.t0)/dur);
    if(target!==s.target){s.from=cur;s.target=target;s.t0=t}
    const v=s.from+(s.target-s.from)*ease((t-s.t0)/dur);
    el.style.opacity=v;
    if(cls)el.style.transform=`translateY(${6*(1-v)}px)`;
  }
}
"""

def pin():
    try:
        _pin()
    except OSError as e:  # a refused powershell spawn must not kill a 15-min render
        print("pin failed:", e, flush=True)


def _pin():
    """Pin every descendant of this process (Chromium, node driver) to 0xC0 / BelowNormal; affinity is not inherited."""
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "Get-CimInstance Win32_Process | % { \"$($_.ProcessId) $($_.ParentProcessId)\" }"],
                         capture_output=True, text=True).stdout.split()
    kids: dict[int, list[int]] = {}
    for a, b in zip(out[::2], out[1::2]):
        kids.setdefault(int(b), []).append(int(a))
    ids, q = [], [os.getpid()]
    while q:
        c = q.pop()
        for k in kids.get(c, []):
            if k not in ids:
                ids.append(k); q.append(k)
    if ids:
        cmd = ";".join(f"try{{$p=Get-Process -Id {i};$p.ProcessorAffinity=0xC0;$p.PriorityClass='BelowNormal'}}catch{{}}" for i in ids)
        subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True)
    print("pinned", len(ids), flush=True)


ARGS = {
    "gpu": ["--use-angle=d3d11", "--enable-gpu-rasterization", "--ignore-gpu-blocklist", "--enable-gpu"],
    "swiftshader": ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"],
}[MODE]


def patch(route):
    r = route.fetch()
    body = r.text().replace("Math.min(2,devicePixelRatio)", "devicePixelRatio").replace("</style>", "</style>" + CAPTURE_CSS, 1)
    route.fulfill(response=r, body=body, headers={**r.headers, "content-type": "text/html; charset=utf-8"})


with sync_playwright() as p:
    b = p.chromium.launch(channel="chromium", args=ARGS)  # channel=chromium = new headless
    pg = b.new_page(viewport={"width": 400, "height": 712}, device_scale_factor=SCALE)
    pg.on("console", lambda m: print("console:", m.text, flush=True) if m.type in ("error", "warning") else None)
    pg.on("pageerror", lambda e: print("pageerror:", e, flush=True))
    pg.route("**/" + PAGE, patch)
    pg.goto(f"http://127.0.0.1:{port}/{PAGE}")
    pin()
    pg.wait_for_function("window.__ready===true", timeout=180000)
    pg.evaluate("document.fonts.ready")
    print("gl:", pg.evaluate("""()=>{const g=document.createElement('canvas').getContext('webgl2');
        const d=g.getExtension('WEBGL_debug_renderer_info');return d?g.getParameter(d.UNMASKED_RENDERER_WEBGL):'?'}"""), flush=True)
    print("canvas:", pg.evaluate("[document.getElementById('gl').width,document.getElementById('gl').height]"), flush=True)
    # replay emulation state from a bit before F0 so a resumed run starts with the right fades
    for i in range(max(0, F0 - 30), F0):
        pg.evaluate(f"renderAt({i/30+TOFF})"); pg.evaluate(EMU_JS, i / 30 + TOFF)
    stage = pg.locator("#stage")
    t_start = time.time()
    for i in ([int(x) for x in os.environ['FRAMES'].split(',')] if os.environ.get('FRAMES') else range(F0, F1)):
        t = i / 30 + TOFF
        if i % 60 == 0:
            pin()
        pg.evaluate(f"renderAt({t})")
        pg.evaluate(EMU_JS, t)
        pg.evaluate("new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))")
        stage.screenshot(path=os.path.join(OUT, f"f{i:04d}.jpg"), type="jpeg", quality=95, animations="disabled", caret="hide")
        if i % 30 == 0:
            el = time.time() - t_start
            print(f"frame {i} {el:.0f}s elapsed, {(el/(i-F0+1)):.2f}s/frame", flush=True)
    b.close()
srv.shutdown()
print("done", time.time() - t_start)

import asyncio, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "mock" / "r5"))
from renderlock import render_slot

async def main(scheme: str) -> None:
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        b = await p.chromium.launch(channel="chrome")
        pg = await b.new_page(viewport={"width": 430, "height": 900}, color_scheme=scheme, device_scale_factor=2)
        errs = []
        pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
        pg.on("pageerror", lambda e: errs.append(str(e)))
        await pg.goto((HERE / "edit-ui.html").as_uri())
        await pg.wait_for_timeout(2500)
        print("scrollWidth", await pg.evaluate("document.documentElement.scrollWidth"), "errors", errs)
        phones = await pg.query_selector_all(".phone")
        for i, ph in enumerate(phones):
            if scheme == "dark" and i not in (2, 9, 13):
                continue
            await ph.screenshot(path=str(HERE / f"s-{scheme}-{i}.png"))
        if scheme == "light":
            await pg.screenshot(path=str(HERE / "full.png"), full_page=True, scale="css")
        await b.close()

with render_slot():
    for s in sys.argv[1:] or ["light"]:
        asyncio.run(main(s))

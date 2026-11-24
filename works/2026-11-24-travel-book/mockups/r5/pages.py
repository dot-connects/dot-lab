import asyncio,sys
from pathlib import Path
from playwright.async_api import async_playwright
from PIL import Image
R=Path(__file__).parent
async def go():
  src=next((a[6:] for a in sys.argv[1:] if a.startswith('--src=')),'travel-book-themes-r5.html');h=(R/src).read_text(encoding='utf-8')
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={'width':430,'height':900},device_scale_factor=1)
    await pg.set_content('<!doctype html><meta charset=utf-8>'+h);await pg.wait_for_timeout(3500)
    for k in ([a for a in sys.argv[1:] if not a.startswith('--')] or ['a','b','c','diary','mag','album']):
      if not await pg.query_selector(f'#theme-{k}'): continue
      els=await pg.query_selector_all(f'#theme-{k} .pg');ims=[]
      for i,e in enumerate(els):
        await e.scroll_into_view_if_needed();f=R/f'pp-{k}-{i}.png';await e.screenshot(path=str(f));ims.append(Image.open(f).convert('RGB'))
      w,hh=ims[0].size;cols=4;rows=(len(ims)+cols-1)//cols
      S=Image.new('RGB',(cols*(w+10),rows*(hh+10)),'#888')
      for i,im in enumerate(ims):S.paste(im,((i%cols)*(w+10),(i//cols)*(hh+10)))
      S.save(R/f'sheet-{k}.jpg',quality=80);print(k,len(ims),S.size)
    await b.close()
asyncio.run(go())

"""Check pv-max.html (every text slot at its limit): text stays on the page, above the folio line, off photos and off other text;
reports the smallest font size in cqw. Run pv.py --max --no-shot first."""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent.parent / "r10"))
from renderlock import render_slot
from playwright.sync_api import sync_playwright
ONPHOTO = ("c-mast", "op-bleed", "back")
JS = """(on)=>{const bad=[];let min=99,minAt='';const rr=e=>{const g=document.createRange();g.selectNodeContents(e);return g.getBoundingClientRect()};const hit=(a,b)=>a.left<b.right-1&&a.right>b.left+1&&a.top<b.bottom-1&&a.bottom>b.top+1;
for(const fig of document.querySelectorAll('figure.cell')){const id=fig.querySelector('figcaption').textContent.split(' ')[0];
 const pg=fig.querySelector('.pg');const P=pg.getBoundingClientRect();const u=P.width/100;
 const phs=[...pg.querySelectorAll('.ph')].map(e=>e.getBoundingClientRect());const ts=[...pg.querySelectorAll('[data-text]')];
 const photoPage=on.some(c=>pg.classList.contains(c));
 for(const t of pg.querySelectorAll('p,h3,span,b,li')){if(!t.textContent.trim())continue;const f=parseFloat(getComputedStyle(t).fontSize)/u;if(f<min){min=f;minAt=id+' '+(t.dataset.text||t.className)}}
 for(const t of ts){const r=rr(t);const lim=photoPage||pg.classList.contains('op-spread')?P.bottom-1.5*u:P.bottom-8.6*u;
  if(r.right>P.right-1.5*u+.5||r.bottom>lim||r.left<P.left+1||r.top<P.top+1)bad.push(id+' '+t.dataset.text+' off  bottom='+((r.bottom-P.top)/u).toFixed(1));
  if(!photoPage)for(const b of phs)if(hit(r,b))bad.push(id+' '+t.dataset.text+' on photo');
  for(const o of ts)if(o!==t&&!o.contains(t)&&!t.contains(o)&&hit(r,rr(o))&&t.dataset.text<o.dataset.text)bad.push(id+' '+t.dataset.text+' x '+o.dataset.text);}}
 return [bad,min.toFixed(2)+' at '+minAt]}"""
with render_slot(), sync_playwright() as pw:
    b = pw.chromium.launch(channel="chrome")
    pg = b.new_page(viewport={"width": 1500, "height": 900})
    pg.goto((HERE / "pv-max.html").as_uri()); pg.wait_for_timeout(900)
    bad, mn = pg.evaluate(JS, list(ONPHOTO))
    b.close()
print("\n".join(bad) or "all text fits"); print("smallest font cqw:", mn)

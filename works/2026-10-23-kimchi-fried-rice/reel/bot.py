import sys

BOT = """window.__bot=()=>{const G=KFR.Game,g=G.state,E=KFR.Egg;
 if(g.ph==='intro'&&!window.__went){window.__went=1;document.getElementById('go').click();return}
 if(g.ph==='rice'&&g.started&&g.clock>0&&!g.finaleDone&&g.win){const b=g.t-g.lastFlick;window.__n=window.__n||0;
   if(window.__tg===undefined)window.__tg=g.win.peak+(Math.random()-0.5)*0.16;
   if(b>=window.__tg){window.__n++;const side=window.__n===4?0.7:0;const fin=g.darkPh==='back'||g.darkPh==='over';
     G.flick({strength:fin?1.35:1.15,dirU:side,dirV:Math.sqrt(1-side*side)});window.__tg=undefined;}}
 if(g.ph==='egg'){const half=g.order==='half';
   if(E.ph==='pan'&&E.cook>=(half?0.66:0.98)&&!window.__e1){window.__e1=1;G.flick({strength:1.5,dirU:0,dirV:1});}
   if(E.ph==='landed'&&g.flip2Open&&!g.flip2Done&&E.cook2>=(half?0.3:0.65)){G.flick({strength:1.0,dirU:0,dirV:1});}
   if(E.ph==='air'){const t=E.target();if(t)KFR.Input.demoTilt(Math.max(-1,Math.min(1,t.x/0.9)),Math.max(-1,Math.min(1,-t.z/0.65)));}else KFR.Input.demoTilt(0,0);}
};"""

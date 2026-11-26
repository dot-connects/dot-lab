"""Writes layouts.html from compact specs (kept so the library can be regenerated)."""
from pathlib import Path

def ph(i, cls=""):
    return f'<div class="ph{(" " + cls) if cls else ""}"><img data-slot="{i}" alt=""></div>'

def head(nm="nm", memo=False, mcls="mm"):
    m = f'<p class="{mcls}" data-text="memo"></p>' if memo else ""
    return f'<div class="col"><p class="no" data-text="no"></p><h3 class="{nm}" data-text="place"></h3><p class="mt" data-text="meta"></p>{m}</div>'

def cap(key="cap", cls="cp", place=True):
    b = '<b data-text="place"></b>' if place else ""
    return f'<p class="{cls}">{b}<span data-text="{key}"></span></p>'

T = {
 "c-window": '<h3 class="ttl" data-text="title"></h3><p class="sub" data-text="period"></p>' + ph(0) + '<p class="cnt" data-text="count"></p>',
 "c-bleed": ph(0) + '<div class="sh"></div><h3 class="ttl" data-text="title"></h3><p class="sub" data-text="period"></p>',
 "c-index": '<h3 class="ttl" data-text="title"></h3><p class="sub" data-text="period"></p>' + ph(0) + '<p class="toc pre" data-text="toc"></p>',
 "op-times": ph(0) + '<div class="col"><h3 class="dh" data-text="dayline"></h3><p class="mt" data-text="day"></p></div><p class="tm" data-text="times"></p>',
 "op-split": ph(0) + '<div class="col"><h3 class="dh" data-text="dayline"></h3><p class="mt" data-text="day"></p></div><p class="tm" data-text="times"></p>',
 "e-wide": head() + ph(0) + '<p class="mm" data-text="memo"></p>',
 "e-tall": ph(0) + head("nm s") + '<p class="mm" data-text="memo"></p><p class="cp" data-text="cap"></p>',
 "e-two": ph(0, "p0") + ph(1, "p1") + head("nm s", True),
 "e-three": head("nm s", True) + ph(0, "p0") + ph(1, "p1") + ph(2, "p2") + '<p class="cp l c1" data-text="capl"></p><p class="cp r c2" data-text="capr"></p>',
 "e-specimen": head("nm s") + "".join(ph(i, f"x{i%3} y{i//3}") + f'<p class="cp lb x{i%3} y{i//3}" data-text="l{i}"></p>' for i in range(6)),
 "e-pair": ph(0, "p0") + ph(1, "p1") + head("nm s") + '<p class="mm" data-text="memo"></p>',
 "e-plate": ph(0) + cap(),
 "e-bleed": ph(0),
 "e-drop": ph(0) + cap(),
 "e-essay": head() + ph(0) + '<p class="mm" data-text="memo"></p>',
 "e-stagger": ph(0, "p0") + ph(1, "p1") + '<p class="cp u c1" data-text="capu"></p><p class="cp d c2" data-text="capd"></p>',
 "e-grid4": "".join(ph(i, f"p{i}") for i in range(4)) + cap(),
 "e-strip": "".join(ph(i, f"p{i}") for i in range(3)) + cap(),
 "e-detail": ph(0, "p0") + head("nm s", True) + ph(1, "p1"),
 "e-quote": ph(0) + '<div class="col"><p class="no" data-text="no"></p><h3 class="nm s" data-text="place"></h3></div><div class="qt"><p class="q" data-text="quote"></p><p class="mt" data-text="meta"></p></div>',
 "e-list": head("nm s") + "".join(ph(i, f"y{i}") + f'<div class="it y{i}"><p class="n" data-text="n{i}"></p><p class="mm" data-text="t{i}"></p></div>' for i in range(3)),
 "text": '<div class="col"><p class="mt" data-text="place"></p></div><p class="bd" data-text="body"></p>',
 "index": '<h3 class="dh" data-text="hd"></h3><p class="ix" data-text="index"></p><p class="cp colo" data-text="colophon"></p>',
 "end-photo": ph(0) + '<p class="last" data-text="last"></p><p class="cp colo" data-text="colophon"></p>',
}
NOFO = {"c-window", "c-bleed", "c-index", "op-times", "op-split", "e-bleed", "e-detail", "e-quote", "e-grid4", "e-strip", "e-drop", "e-stagger"}
out = ["<!-- theme b 「기록 카드」 r11d. One entry per place: number / name / meta / memo set as type. No rules or boxes. -->\n"]
for k, body in T.items():
    fo = "" if k in NOFO else " fo"
    out.append(f'<template id="b/{k}"><div class="pg {k}{fo}">{body}</div></template>\n')
Path(__file__).with_name("layouts.html").write_text("\n".join(out), encoding="utf-8")
print(len(T))

import time, json, sys, urllib.request, urllib.parse, os, re
UA = {"User-Agent": "dot-lab-research/1.0"}
def get(u):
    for k in range(6):
        try:
            time.sleep(3); return urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=30).read()
        except urllib.error.HTTPError as e:
            if e.code!=429: raise
            w=int(e.headers.get("Retry-After") or 10); print("429 wait",w,flush=True); time.sleep(min(w,40))
    raise RuntimeError("429")
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
Q = {
 "hamster": ["hamster cage", "hamster hand", "pet hamster", "Syrian hamster", "dwarf hamster"],
 "rabbit": ["pet rabbit floor", "pet rabbit indoor", "rabbit house pet", "dwarf rabbit"],
 "turtle": ["pet turtle", "red-eared slider tank", "pet tortoise", "tortoise garden"],
 "fish": ["aquarium goldfish", "home aquarium fish", "betta fish aquarium", "guppy aquarium", "neon tetra aquarium"],
 "guineapig": ["pet guinea pig", "guinea pig cage"],
 "lizard": ["leopard gecko pet", "bearded dragon pet"],
 "neg": ["empty living room", "sofa living room", "portrait man indoor", "dog living room", "plush toy"],
}
meta = []
for cls, qs in Q.items():
    for q in qs:
        p = {"action":"query","format":"json","generator":"search","gsrsearch":f"filetype:bitmap {q}","gsrnamespace":6,"gsrlimit":6,
             "prop":"imageinfo","iiprop":"url|extmetadata|mime","iiurlwidth":500}
        u = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(p)
        d = json.loads(get(u))
        for pg in (d.get("query",{}).get("pages",{}) or {}).values():
            ii = pg["imageinfo"][0]
            if ii.get("mime") != "image/jpeg": continue
            em = ii.get("extmetadata", {})
            meta.append({"cls":cls,"q":q,"title":pg["title"],"thumb":ii["thumburl"],"page":ii["descriptionurl"],
                         "license":em.get("LicenseShortName",{}).get("value",""),
                         "artist":re.sub("<[^>]+>","",em.get("Artist",{}).get("value",""))[:80]})
seen=set(); m2=[]
for i,m in enumerate(meta):
    if m["title"] in seen: continue
    seen.add(m["title"]); m["id"]=f"{m['cls']}_{len([x for x in m2 if x['cls']==m['cls']]):02d}"; m2.append(m)
json.dump(m2, open(os.path.join(out,"meta.json"),"w", encoding="utf-8"), indent=1, ensure_ascii=False)
for m in m2:
    fn=os.path.join(out, m["id"]+".jpg")
    if os.path.exists(fn): continue
    try:
        b = get(m["thumb"])
        open(os.path.join(out, m["id"]+".jpg"),"wb").write(b)
    except Exception as e: print("fail", m["title"], e)
json.dump(m2, open(os.path.join(out,"meta.json"),"w", encoding="utf-8"), indent=1, ensure_ascii=False)
print(len(m2))

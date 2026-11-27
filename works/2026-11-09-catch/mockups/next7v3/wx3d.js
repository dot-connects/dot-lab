// 3D-baked weather actions (tools/bake-weather.html): per critter, an action then its loop
const wxSeq = (id, name, n) => Array.from({ length: n }, (_, i) => `img/wx3d/${id}-${name}-${String(i).padStart(2, "0")}.webp`);
const WX3D_ALL = Object.fromEntries(["monggeul", "nyangsom"].map(id => [id, {
  rain: wxSeq(id, "rain-open", 16), rainLoop: wxSeq(id, "rain-hold", 4),
  dust: wxSeq(id, "mask-on", 14), dustLoop: wxSeq(id, "mask-idle", 4),
  snow: [], snowLoop: wxSeq(id, "scarf", 4),
}]));
window.WX3D = WX3D_ALL.monggeul;

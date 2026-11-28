// 다닌 길 page (operator 11-28: only with a real map, drawn by us from OpenStreetMap). From the photos' positions in
// time order: the line, the stops, the box to ask the map for, and the few places worth a close-up (style C).
// Coordinates stay [lat, lng]; book.js projects them onto the page with the map the Worker sends for [box].
import 'dart:math' as math;

import 'models.dart';

double km((double, double) a, (double, double) b) {
  const r = 6371.0, d = math.pi / 180;
  final h = math.pow(math.sin((b.$1 - a.$1) * d / 2), 2) +
      math.cos(a.$1 * d) * math.cos(b.$1 * d) * math.pow(math.sin((b.$2 - a.$2) * d / 2), 2);
  return 2 * r * math.asin(math.sqrt(h));
}

/// A box around [pts] that any page shape can show: square in kilometres, [pad] × the longer side, ≥ [minKm] across.
List<double> boxAround(List<(double, double)> pts, {double pad = 2.0, double minKm = 2}) {
  final la = pts.map((p) => p.$1), lo = pts.map((p) => p.$2);
  final cLat = (la.reduce(math.min) + la.reduce(math.max)) / 2, cLng = (lo.reduce(math.min) + lo.reduce(math.max)) / 2;
  final cos = math.max(0.2, math.cos(cLat * math.pi / 180));
  final spanLat = la.reduce(math.max) - la.reduce(math.min), spanLng = (lo.reduce(math.max) - lo.reduce(math.min)) * cos;
  final half = math.max(math.max(spanLat, spanLng) * pad / 2, minKm / 111 / 2);
  double r(double v) => (v * 1e4).roundToDouble() / 1e4;
  return [r(cLat - half), r(cLng - half / cos), r(cLat + half), r(cLng + half / cos)];
}

/// {pts, stops: [{lat, lng, n, label, sub}], box, km, legs: [{n, label, sub, box, km, pts}]}, or null when there is
/// no route worth a page: fewer than 2 places, or a trip under ~8 km across. Positions far from the trip (home,
/// the departure airport) are left out, so the map shows the destination, not the flight.
Map<String, Object>? routeOf(List<Candidate> pool) {
  var xs = [for (final c in pool) if (c.shot.lat != null && c.shot.lng != null) c]..sort((a, b) => a.local.compareTo(b.local));
  if (xs.length < 3) return null;
  (double, double) at(Candidate c) => (c.shot.lat!, c.shot.lng!);
  // the trip's middle (median) and how far its 80% reach: anything 3x further (and > 50 km) is the way there
  final mid = (_median([for (final c in xs) c.shot.lat!]), _median([for (final c in xs) c.shot.lng!]));
  final dist = [for (final c in xs) km(at(c), mid)]..sort();
  final reach = math.max(50.0, dist[((dist.length - 1) * 0.75).floor()] * 3);
  // where the trip started from (the first photo far away, before the trip): the locator inset's other end
  final firstNear = xs.indexWhere((c) => km(at(c), mid) <= reach);
  final from = firstNear > 0 ? at(xs[firstNear - 1]) : null;
  xs = [for (final c in xs) if (km(at(c), mid) <= reach) c];
  if (xs.length < 3) return null;
  final all = [for (final c in xs) at(c)];
  final b = boxAround(all, pad: 1, minKm: 0);
  if (km((b[0], b[1]), (b[2], b[1])) < 2) return null; // a walk round one neighbourhood is no route
  // stops: places by position, not by the geocoder's names (11-28 Bali: "Kecamatan Kuta", "Jalan Tanpa Nama"): a photo
  // joins the first stop within a radius of its centre (a few % of the trip, >= 2 km), else starts one; stops with
  // under 3% of the photos (the airport, a stop on the road) are left off. The map's place names label them later.
  final tripKm = km((b[0], b[1]), (b[2], b[3]));
  final radius = math.max(2.0, tripKm * 0.04), least = math.max(1, (all.length * 0.03).ceil());
  final groups = <({List<(double, double)> pts, Set<String> days, Map<String, int> names})>[];
  final groupOf = <int>[]; // each photo's group, in time order
  for (final c in xs) {
    final p = at(c);
    var g = groups.where((g) => km(_centre(g.pts), p) <= radius).firstOrNull;
    if (g == null) groups.add(g = (pts: [], days: {}, names: {}));
    groupOf.add(groups.indexOf(g));
    g.pts.add(p);
    g.days.add(ymd(c.local));
    final name = c.place;
    if (name != null && name.isNotEmpty) g.names[name] = (g.names[name] ?? 0) + 1;
  }
  final stops = [
    for (final g in groups)
      if (g.pts.length >= least)
        (label: g.names.isEmpty ? '' : (g.names.entries.toList()..sort((x, y) => y.value.compareTo(x.value))).first.key, days: g.days, pts: g.pts),
  ];
  if (stops.length < 2 || tripKm < 8) return null;
  var total = 0.0;
  for (var i = 1; i < all.length; i++) {
    final d = km(all[i - 1], all[i]);
    if (d < 300) total += d; // a flight inside the trip is not "다닌 길"
  }
  final step = math.max(1, (all.length / 300).ceil());
  List<List<double>> thin(List<(double, double)> ps, int max) {
    final st = math.max(1, (ps.length / max).ceil());
    return [for (var i = 0; i < ps.length; i += st) [_r5(ps[i].$1), _r5(ps[i].$2)]];
  }

  final named = [for (final (n, s) in stops.take(10).indexed) (n: n + 1, s: s)];
  // the order the stops were visited (back to one counts again): the overview draws stop to stop, not every photo
  final numberOf = {for (final x in named) groups.indexWhere((g) => identical(g.pts, x.s.pts)): x.n};
  final visits = <int>[];
  for (final g in groupOf) {
    final n = numberOf[g];
    if (n != null && (visits.isEmpty || visits.last != n)) visits.add(n);
  }
  // close-ups: the (up to 3) places with the most photos, in trip order, each with its own walk
  final big = [...named]..sort((a, b) => b.s.pts.length.compareTo(a.s.pts.length));
  final legs = [for (final x in big.take(3)) if (x.s.pts.length >= 8) x]..sort((a, b) => a.n.compareTo(b.n));
  double walk(List<(double, double)> ps) {
    var t = 0.0;
    for (var i = 1; i < ps.length; i++) {
      final d = km(ps[i - 1], ps[i]);
      if (d < 30) t += d;
    }
    return (t * 10).roundToDouble() / 10;
  }

  return {
    'pts': [for (var i = 0; i < all.length; i += step) [_r5(all[i].$1), _r5(all[i].$2)]],
    'stops': [
      for (final x in named)
        {'lat': _r5(_centre(x.s.pts).$1), 'lng': _r5(_centre(x.s.pts).$2), 'n': x.n, 'label': x.s.label, 'sub': _days(x.s.days)},
    ],
    'visits': visits,
    if (from != null && km(from, mid) > 300) 'from': [_r5(from.$1), _r5(from.$2)],
    'box': boxAround(all),
    'km': total.round(),
    'legs': [
      for (final x in legs)
        {'n': x.n, 'label': x.s.label, 'sub': _days(x.s.days), 'box': boxAround(x.s.pts, pad: 1.6, minKm: 1.5), 'km': walk(x.s.pts), 'pts': thin(x.s.pts, 200)},
    ],
  };
}

/// "5월 2–5일" / "5월 2일" / "5월 1일 · 8일" (left and came back) / "5월 30일–6월 2일" from yyyy-mm-dd days.
String _days(Set<String> days) {
  final ds = (days.toList()..sort()).map(DateTime.parse).toList();
  final runs = <(DateTime, DateTime)>[];
  for (final d in ds) {
    if (runs.isNotEmpty && d.difference(runs.last.$2).inDays <= 1) {
      runs.last = (runs.last.$1, d);
    } else {
      runs.add((d, d));
    }
  }
  final out = <String>[];
  int? month;
  for (final (a, z) in runs) {
    final head = a.month == month ? '${a.day}일' : '${a.month}월 ${a.day}일';
    month = z.month;
    out.add(a == z ? head : (a.month == z.month ? '${head.substring(0, head.length - 1)}–${z.day}일' : '$head–${z.month}월 ${z.day}일'));
  }
  return out.join(' · ');
}

(double, double) _centre(List<(double, double)> ps) =>
    (ps.map((p) => p.$1).reduce((a, b) => a + b) / ps.length, ps.map((p) => p.$2).reduce((a, b) => a + b) / ps.length);

double _r5(double v) => (v * 1e5).roundToDouble() / 1e5;

double _median(List<double> v) {
  final s = [...v]..sort();
  return s[s.length ~/ 2];
}

/// After the map came back, each stop takes a name from it: the small island it is on (렘봉안, not the regency the
/// phone's geocoder says), else the nearest town or village (a city counts from further away), else the phone's
/// name. Stops that end up with the same name in a row become one; close-ups follow their stop's name and number.
void nameStops(Map<String, Object?> route, Map<String, dynamic> map) {
  double d(Object? v) => (v as num).toDouble();
  String? name(double la, double lo) {
    String? best;
    var bestSpan = 0.35; // degrees: Bali (1.4°) or Jeju (0.7°) are too big to name a stop
    for (final i in (map['islands'] as List? ?? const [])) {
      if (i is! Map || i['n'] is! String || i['b'] is! List || (i['b'] as List).length != 4) continue;
      final b = [for (final v in i['b'] as List) d(v)];
      final span = math.max(b[2] - b[0], b[3] - b[1]);
      if (la >= b[0] && la <= b[2] && lo >= b[1] && lo <= b[3] && span < bestSpan) {
        best = i['n'] as String;
        bestSpan = span;
      }
    }
    if (best != null) return best;
    var score = 6.0; // km for a village; a town counts 2.5x, a city 4x as far
    for (final p in (map['places'] as List? ?? const [])) {
      if (p is! Map || p['n'] is! String || p['lat'] is! num || p['lng'] is! num) continue;
      final s = km((la, lo), (d(p['lat']), d(p['lng']))) / (p['r'] is num ? d(p['r']) : 1);
      if (s < score) {
        score = s;
        best = p['n'] as String;
      }
    }
    return best;
  }

  final stops = (route['stops'] as List).cast<Map<String, Object?>>();
  final legs = (route['legs'] as List? ?? const []).cast<Map<String, Object?>>();
  final byOld = <int, Map<String, Object?>>{for (final s in stops) s['n'] as int: s};
  for (final s in stops) {
    final n = name(d(s['lat']), d(s['lng']));
    if (n != null) s['label'] = n;
  }
  // a close-up keeps a link to its stop (by the old number) through the merges below
  for (final l in legs) {
    final s = byOld[l['n']];
    if (s != null) l['label'] = s['label'];
  }
  stops.removeWhere((s) => (s['label'] as String).isEmpty);
  for (var i = stops.length - 1; i > 0; i--) {
    if (stops[i]['label'] == stops[i - 1]['label']) stops.removeAt(i);
  }
  final newOf = <int, int>{};
  for (final (i, s) in stops.indexed) {
    s['n'] = i + 1;
  }
  for (final MapEntry(key: o, value: s) in byOld.entries) {
    final i = stops.indexWhere((x) => x['label'] == s['label']);
    if (i >= 0) newOf[o] = i + 1;
  }
  final vs = <int>[];
  for (final v in (route['visits'] as List? ?? const []).cast<int>()) {
    final n = newOf[v];
    if (n != null && (vs.isEmpty || vs.last != n)) vs.add(n);
  }
  route['visits'] = vs;
  final seen = <Object?>{};
  legs.retainWhere((l) => (l['label'] as String).isNotEmpty && seen.add(l['label']));
  for (final l in legs) {
    l['n'] = stops.indexWhere((s) => s['label'] == l['label']) + 1;
  }
}

// 다닌 길 page (operator 11-27: only when the trip has one): the trip's positions in time order, drawn by the book
// runtime as a line with its stops. No map tiles - just the shape of where the person went, in 0..100 page units.
import 'dart:math' as math;

import 'models.dart';

/// {pts: [[x, y], …] ≤ 200, stops: [{x, y, n, label}] ≤ 10} in a 100 x 100 box (north up, aspect kept), or null when
/// there is no route worth a page: fewer than 3 named places, or everything within ~2 km.
Map<String, Object>? routeOf(List<Candidate> pool) {
  final xs = [for (final c in pool) if (c.shot.lat != null && c.shot.lng != null) c]..sort((a, b) => a.local.compareTo(b.local));
  if (xs.length < 3) return null;
  final lat0 = xs.map((c) => c.shot.lat!).reduce((a, b) => a + b) / xs.length;
  final k = math.cos(lat0 * math.pi / 180);
  final ps = [for (final c in xs) (c.shot.lng! * k, -c.shot.lat!)]; // x east, y south (screen)
  final minX = ps.map((p) => p.$1).reduce(math.min), maxX = ps.map((p) => p.$1).reduce(math.max);
  final minY = ps.map((p) => p.$2).reduce(math.min), maxY = ps.map((p) => p.$2).reduce(math.max);
  final span = math.max(maxX - minX, maxY - minY);
  if (span * 111 < 2) return null; // degrees -> km: a walk round one neighbourhood is no route
  // stops: each new place name in time order (the first point there)
  final stops = <(int, String)>[];
  for (final (i, c) in xs.indexed) {
    final p = c.place;
    if (p == null || p.isEmpty || (stops.isNotEmpty && stops.last.$2 == p)) continue;
    if (stops.any((s) => s.$2 == p)) continue; // back to a place already named: the line shows it
    stops.add((i, p));
  }
  if (stops.length < 3) return null;
  (double, double) fit((double, double) p) {
    // centred in 8..92, the longer side fills it
    final s = 84 / span;
    return (8 + (p.$1 - minX) * s + (84 - (maxX - minX) * s) / 2, 8 + (p.$2 - minY) * s + (84 - (maxY - minY) * s) / 2);
  }

  final step = math.max(1, (ps.length / 200).ceil());
  double r(double v) => (v * 10).roundToDouble() / 10;
  return {
    'pts': [
      for (var i = 0; i < ps.length; i += step) [r(fit(ps[i]).$1), r(fit(ps[i]).$2)],
    ],
    'stops': [
      for (final (n, (i, label)) in stops.take(10).indexed) {'x': r(fit(ps[i]).$1), 'y': r(fit(ps[i]).$2), 'n': n + 1, 'label': label},
    ],
  };
}

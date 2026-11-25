// Google Maps Timeline exports -> visits and paths. Three shapes are accepted (see test/timeline_test.dart).
import 'dart:convert';

class Visit {
  final DateTime start, end; // UTC
  final double lat, lng;
  final String? name; // only the legacy Takeout shape names places; others are geocoded by the caller
  const Visit(this.start, this.end, this.lat, this.lng, [this.name]);
}

class Timeline {
  final List<Visit> visits;
  final List<List<(double, double)>> paths;
  const Timeline(this.visits, this.paths);
}

Timeline parseTimeline(String raw) {
  final Object? j;
  try {
    j = jsonDecode(raw);
  } on FormatException {
    throw const FormatException('timeline: not JSON');
  }
  if (j is Map && j['semanticSegments'] is List) return _segments(j['semanticSegments'] as List);
  if (j is List) return _segments(j);
  if (j is Map && j['timelineObjects'] is List) return _takeout(j['timelineObjects'] as List);
  throw const FormatException('timeline: unknown shape');
}

final _num = RegExp(r'-?\d+(?:\.\d+)?');

/// "33.394°, 126.239°" or "geo:33.394,126.239"
(double, double)? _ll(Object? s) {
  if (s is! String) return null;
  final m = _num.allMatches(s).map((x) => double.parse(x.group(0)!)).toList();
  return m.length >= 2 ? (m[0], m[1]) : null;
}

DateTime? _t(Object? s) => s is String ? DateTime.tryParse(s)?.toUtc() : null;

/// Older Takeout files carry epoch-millisecond strings (startTimestampMs) instead of ISO times.
DateTime? _tAny(Map? d, String key) {
  final iso = _t(d?[key]);
  if (iso != null) return iso;
  final ms = int.tryParse('${d?['${key}Ms']}');
  return ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms, isUtc: true);
}

Timeline _segments(List xs) {
  final visits = <Visit>[];
  final paths = <List<(double, double)>>[];
  for (final o in xs) {
    if (o is! Map) continue;
    final st = _t(o['startTime']), en = _t(o['endTime']);
    final v = o['visit'];
    if (v is Map && st != null && en != null) {
      final pl = (v['topCandidate'] as Map?)?['placeLocation'];
      final ll = _ll(pl is Map ? pl['latLng'] : pl);
      if (ll != null) visits.add(Visit(st, en, ll.$1, ll.$2));
    }
    final p = o['timelinePath'];
    if (p is List) {
      final pts = [for (final q in p) if (q is Map) _ll(q['point'])].whereType<(double, double)>().toList();
      if (pts.length >= 2) paths.add(pts);
    }
  }
  if (visits.isEmpty && paths.isEmpty) throw const FormatException('timeline: no visits or paths');
  return Timeline(visits, paths);
}

Timeline _takeout(List xs) {
  final visits = <Visit>[];
  final paths = <List<(double, double)>>[];
  double e7(Object? v) => (v as num) / 1e7;
  for (final o in xs) {
    if (o is! Map) continue;
    final pv = o['placeVisit'];
    if (pv is Map) {
      final loc = pv['location'] as Map?, d = pv['duration'] as Map?;
      final st = _tAny(d, 'startTimestamp'), en = _tAny(d, 'endTimestamp');
      if (loc?['latitudeE7'] != null && st != null && en != null) {
        visits.add(Visit(st, en, e7(loc!['latitudeE7']), e7(loc['longitudeE7']), loc['name'] as String?));
      }
    }
    final seg = o['activitySegment'];
    if (seg is Map) {
      final pts = (seg['simplifiedRawPath'] as Map?)?['points'] ?? (seg['waypointPath'] as Map?)?['waypoints'];
      if (pts is List) {
        final l = [for (final q in pts) if (q is Map && q['latE7'] != null) (e7(q['latE7']), e7(q['lngE7']))];
        if (l.length >= 2) paths.add(l);
      }
    }
  }
  if (visits.isEmpty && paths.isEmpty) throw const FormatException('timeline: no visits or paths');
  return Timeline(visits, paths);
}

/// The visit covering [utc] within ±15 minutes (for shots without GPS).
Visit? visitAt(Timeline t, DateTime utc) {
  const slack = Duration(minutes: 15);
  for (final v in t.visits) {
    if (!utc.isBefore(v.start.subtract(slack)) && !utc.isAfter(v.end.add(slack))) return v;
  }
  return null;
}

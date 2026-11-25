// Local time of each shot and how shots are grouped into chapters.
import 'models.dart';

/// Photos: the camera wall clock (EXIF DateTimeOriginal) is the trip's local time. Without it, UTC + EXIF offset,
/// else the device zone. Videos carry no wall clock: they borrow the offset of the photo closest in time.
DateTime localOf(Shot s, List<Shot> photos) {
  if (s.wall != null) return _naive(s.wall!);
  if (s.exifOffset != null) return _naive(s.utc.toUtc().add(s.exifOffset!));
  if (s.isVideo) {
    Shot? best;
    for (final p in photos) {
      if (p.wall == null) continue;
      if (best == null || (p.utc.difference(s.utc).abs() < best.utc.difference(s.utc).abs())) best = p;
    }
    if (best != null) {
      final off = _naive(best.wall!).difference(_naive(best.utc.toUtc()));
      return _naive(s.utc.toUtc().add(off));
    }
  }
  return _naive(s.utc.toLocal());
}

DateTime _naive(DateTime d) => DateTime(d.year, d.month, d.day, d.hour, d.minute, d.second, d.millisecond);

/// The trip day of a local time: a day starts at 04:00, so late-night shots stay with the evening before.
String tripDay(DateTime local) => ymd(local.subtract(const Duration(hours: 4)));

List<Chapter> byDay(List<Candidate> xs) {
  final sorted = [...xs]..sort((a, b) => a.local.compareTo(b.local));
  final out = <Chapter>[];
  for (final c in sorted) {
    final d = tripDay(c.local);
    if (out.isEmpty || out.last.day != d) {
      out.add(Chapter(d, c.place, [c]));
    } else {
      out.last.items.add(c);
    }
  }
  return [for (final ch in out) Chapter(ch.day, _mostCommon(ch.items), ch.items)];
}

List<Chapter> byPlace(List<Candidate> xs) {
  final sorted = [...xs]..sort((a, b) => a.local.compareTo(b.local));
  final out = <Chapter>[];
  for (final c in sorted) {
    final d = tripDay(c.local);
    final last = out.isEmpty ? null : out.last;
    if (last != null && last.day == d && (c.place == null || c.place == last.place || last.place == null)) {
      if (last.place == null && c.place != null) {
        out[out.length - 1] = Chapter(d, c.place, last.items..add(c));
      } else {
        last.items.add(c);
      }
    } else {
      out.add(Chapter(d, c.place, [c]));
    }
  }
  return out;
}

String? _mostCommon(List<Candidate> xs) {
  final n = <String, int>{};
  for (final c in xs) {
    if (c.place != null) n[c.place!] = (n[c.place!] ?? 0) + 1;
  }
  if (n.isEmpty) return null;
  return (n.entries.toList()..sort((a, b) => b.value.compareTo(a.value))).first.key;
}

/// Before any work: too few photos stops with the spec's message; photos without GPS are counted for the cover line.
({bool ok, String? message, int noGps}) gate(int photos, int noGps) => photos < 5
    ? (ok: false, message: '사진이 $photos장뿐이에요, 기간을 넓혀 볼까요?', noGps: noGps)
    : (ok: true, message: null, noGps: noGps);

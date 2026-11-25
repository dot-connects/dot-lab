// On-device first pass: drop blurred, dark, blown-out frames, screenshots and burst duplicates for free,
// before anything is sent to the AI. Works on a 256 px thumbnail.
import 'package:image/image.dart' as img;

import 'chapters.dart' show tripDay;
import 'models.dart';

/// Laplacian variance on a 256 px grey thumbnail below this = blurred.
/// Emulator seed 2026-11-26: a Gaussian-8 blurred copy measured 11.2, but sharp low-texture shots measured 12.3 (fog
/// over a peak) and 50 (sky over sea) - 120 dropped both. Only near-blank frames go here; the AI pick judges the rest.
const double kSharpMin = 8;

({double sharp, double luma, int dhash}) measure(img.Image thumb) {
  final g = img.grayscale(thumb.clone());
  final w = g.width, h = g.height;
  final lum = List<double>.filled(w * h, 0);
  var sum = 0.0;
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      final v = g.getPixel(x, y).r.toDouble();
      lum[y * w + x] = v;
      sum += v;
    }
  }
  // Laplacian (4-neighbour) variance
  var n = 0, m = 0.0, m2 = 0.0;
  for (var y = 1; y < h - 1; y++) {
    for (var x = 1; x < w - 1; x++) {
      final i = y * w + x;
      final l = lum[i - 1] + lum[i + 1] + lum[i - w] + lum[i + w] - 4 * lum[i];
      n++;
      final d = l - m;
      m += d / n;
      m2 += d * (l - m);
    }
  }
  return (sharp: n > 1 ? m2 / (n - 1) : 0, luma: sum / (w * h), dhash: _dhash(g));
}

int _dhash(img.Image grey) {
  final s = img.copyResize(grey, width: 9, height: 8, interpolation: img.Interpolation.average);
  var bits = 0;
  for (var y = 0; y < 8; y++) {
    for (var x = 0; x < 8; x++) {
      bits = (bits << 1) | (s.getPixel(x, y).r < s.getPixel(x + 1, y).r ? 1 : 0);
    }
  }
  return bits;
}

int _hamming(int a, int b) {
  var v = a ^ b, c = 0;
  while (v != 0) {
    v &= v - 1;
    c++;
  }
  return c;
}

bool isScreenshot(Shot s) {
  final p = s.relPath.toLowerCase();
  return p.contains('screenshot') || p.contains('스크린샷') || p.contains('screen recording') || p.contains('screenrecord');
}

bool isBad(Candidate c) => c.sharp < kSharpMin || c.luma < 28 || c.luma > 245;

/// Frames <= 4 s apart that look alike (dHash distance <= 10) form one burst; its sharpest frame stays.
List<Candidate> dedupeBursts(List<Candidate> xs) {
  final sorted = [...xs]..sort((a, b) => a.local.compareTo(b.local));
  final out = <Candidate>[];
  for (final c in sorted) {
    final last = out.isEmpty ? null : out.last;
    if (last != null &&
        !c.shot.isVideo &&
        !last.shot.isVideo &&
        c.local.difference(last.local).inMilliseconds.abs() <= 4000 &&
        _hamming(c.dhash, last.dhash) <= 10) {
      if (c.sharp > last.sharp) out[out.length - 1] = c;
      continue;
    }
    out.add(c);
  }
  return out;
}

/// Keep [cap] candidates: each local day gets a share in proportion to its count (at least 3, or all it has),
/// the sharpest first. Result in time order.
List<Candidate> capEvenly(List<Candidate> xs, int cap) {
  if (xs.length <= cap) return [...xs]..sort((a, b) => a.local.compareTo(b.local));
  final byDay = <String, List<Candidate>>{};
  for (final c in xs) {
    byDay.putIfAbsent(tripDay(c.local), () => []).add(c);
  }
  for (final l in byDay.values) {
    l.sort((a, b) => b.sharp.compareTo(a.sharp));
  }
  final quota = {for (final e in byDay.entries) e.key: e.value.length < 3 ? e.value.length : 3};
  var left = cap - quota.values.fold(0, (a, b) => a + b);
  // hand out the rest one by one to the day furthest below its fair share
  while (left > 0) {
    String? best;
    var bestGap = double.negativeInfinity;
    for (final e in byDay.entries) {
      if (quota[e.key]! >= e.value.length) continue;
      final gap = e.value.length / xs.length * cap - quota[e.key]!;
      if (gap > bestGap) {
        bestGap = gap;
        best = e.key;
      }
    }
    if (best == null) break;
    quota[best] = quota[best]! + 1;
    left--;
  }
  final out = [for (final e in byDay.entries) ...e.value.take(quota[e.key]!)];
  return out..sort((a, b) => a.local.compareTo(b.local));
}

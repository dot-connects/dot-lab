import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/prefilter.dart';

img.Image checker({int cell = 8, int shift = 0}) {
  final im = img.Image(width: 256, height: 256);
  for (var y = 0; y < 256; y++) {
    for (var x = 0; x < 256; x++) {
      final on = (((x + shift) ~/ cell) + (y ~/ cell)) % 2 == 0;
      im.setPixelRgb(x, y, on ? 230 : 30, on ? 230 : 30, on ? 230 : 30);
    }
  }
  return im;
}

Candidate cand(String id, DateTime t, img.Image im) {
  final m = measure(im);
  return Candidate(Shot(id: id, utc: t), sharp: m.sharp, luma: m.luma, dhash: m.dhash, local: t);
}

void main() {
  test('blur lowers sharpness below the threshold, a crisp image stays above', () {
    final crisp = measure(checker());
    final blurred = measure(img.gaussianBlur(checker(), radius: 10)); // a smeared frame, not soft focus
    expect(crisp.sharp, greaterThan(kSharpMin));
    expect(blurred.sharp, lessThan(kSharpMin));
  });

  test('black frame is bad, mid-grey crisp frame is not', () {
    final black = img.Image(width: 256, height: 256);
    final b = measure(black);
    expect(isBad(Candidate(Shot(id: 'b', utc: DateTime(2026)), sharp: b.sharp, luma: b.luma, dhash: b.dhash, local: DateTime(2026))), isTrue);
    expect(isBad(cand('c', DateTime(2026), checker())), isFalse);
  });

  test('a burst keeps only its sharpest frame', () {
    final t = DateTime.utc(2026, 6, 12, 4);
    final xs = [
      cand('b1', t, img.gaussianBlur(checker(), radius: 2)),
      cand('b2', t.add(const Duration(seconds: 1)), checker()),
      cand('b3', t.add(const Duration(seconds: 2)), img.gaussianBlur(checker(), radius: 1)),
      cand('other', t.add(const Duration(seconds: 3)), checker(cell: 32, shift: 16)),
    ];
    expect(dedupeBursts(xs).map((c) => c.id), ['b2', 'other']);
  });

  test('same picture 10 s apart is not a burst', () {
    final t = DateTime.utc(2026, 6, 12, 4);
    final xs = [cand('a', t, checker()), cand('b', t.add(const Duration(seconds: 10)), checker())];
    expect(dedupeBursts(xs).length, 2);
  });

  test('capEvenly keeps every day represented', () {
    final m = measure(checker());
    final xs = <Candidate>[];
    for (var d = 0; d < 3; d++) {
      final n = d == 0 ? 60 : 15; // day 1 dominates
      for (var i = 0; i < n; i++) {
        final t = DateTime(2026, 6, 12 + d, 9, i);
        xs.add(Candidate(Shot(id: '$d-$i', utc: t), sharp: m.sharp + i, luma: 120, dhash: i, local: t));
      }
    }
    final kept = capEvenly(xs, 36);
    expect(kept.length, 36);
    for (var d = 0; d < 3; d++) {
      expect(kept.where((c) => c.local.day == 12 + d).length, greaterThanOrEqualTo(6));
    }
    // time order
    for (var i = 1; i < kept.length; i++) {
      expect(!kept[i].local.isBefore(kept[i - 1].local), isTrue);
    }
  });

  test('screenshots are recognised by folder', () {
    final t = DateTime(2026);
    expect(isScreenshot(Shot(id: 's', utc: t, relPath: 'Pictures/Screenshots/')), isTrue);
    expect(isScreenshot(Shot(id: 'c', utc: t, relPath: 'DCIM/Camera/')), isFalse);
  });
}

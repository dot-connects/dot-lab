// Shapes: Android on-device export (semanticSegments, "lat°, lng°" strings), iOS export (top-level array,
// "geo:lat,lng"), legacy Takeout Semantic Location History (timelineObjects, E7 integers).
// Google help answer/6258979: timeline moved to the device, export = this JSON.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/timeline.dart';

String fx(String n) => File('test/fixtures/$n').readAsStringSync();

void main() {
  for (final n in ['timeline_android.json', 'timeline_ios.json', 'timeline_takeout.json']) {
    test('$n: two visits and one path', () {
      final t = parseTimeline(fx(n));
      expect(t.visits.length, 2);
      expect(t.paths.length, 1);
      expect(t.visits.first.lat, closeTo(33.394, 1e-6));
      expect(t.visits.first.lng, closeTo(126.239, 1e-6));
      expect(t.visits.first.start, DateTime.utc(2026, 6, 12, 4, 10));
      expect(t.paths.first.length, 2);
    });
  }

  test('takeout keeps place names', () {
    expect(parseTimeline(fx('timeline_takeout.json')).visits.first.name, '협재해수욕장');
  });

  test('unknown shape is a FormatException', () {
    expect(() => parseTimeline('{"foo":1}'), throwsFormatException);
    expect(() => parseTimeline('not json'), throwsFormatException);
  });

  test('visitAt covers ±15 minutes around a visit', () {
    final t = parseTimeline(fx('timeline_android.json'));
    expect(visitAt(t, DateTime.utc(2026, 6, 12, 5, 40))?.lat, closeTo(33.394, 1e-6)); // 10 min after end
    expect(visitAt(t, DateTime.utc(2026, 6, 12, 6, 0)), isNull); // 30 min after end
  });
}

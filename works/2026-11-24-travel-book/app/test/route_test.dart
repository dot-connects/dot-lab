import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/route.dart';

Candidate at(String id, int min, double lat, double lng, String? place) => Candidate(
    Shot(id: id, utc: DateTime.utc(2026, 6, 12, 1).add(Duration(minutes: min)), lat: lat, lng: lng),
    sharp: 50, luma: 120, dhash: 0, place: place, local: DateTime(2026, 6, 12, 10).add(Duration(minutes: min)));

void main() {
  test('a route: points in 0..100, one numbered stop per new place in time order', () {
    final r = routeOf([
      at('a', 0, 33.25, 126.40, '중문'), at('b', 30, 33.26, 126.41, '중문'),
      at('c', 90, 33.46, 126.93, '성산'), at('d', 200, 33.51, 126.52, '제주시'), at('e', 260, 33.25, 126.41, '중문'),
    ])!;
    final pts = r['pts'] as List;
    expect(pts.length, 5);
    for (final p in pts.cast<List>()) {
      expect(p[0], inInclusiveRange(0, 100));
      expect(p[1], inInclusiveRange(0, 100));
    }
    expect([for (final s in (r['stops'] as List).cast<Map>()) '${s['n']}${s['label']}'], ['1중문', '2성산', '3제주시']);
  });

  test('no route page: one neighbourhood, or fewer than 3 named places', () {
    expect(routeOf([at('a', 0, 33.25, 126.40, 'x'), at('b', 1, 33.251, 126.401, 'y'), at('c', 2, 33.252, 126.40, 'z')]), isNull);
    expect(routeOf([at('a', 0, 33.25, 126.40, 'x'), at('b', 1, 33.5, 126.9, 'y'), at('c', 2, 33.3, 126.5, null)]), isNull);
  });
}

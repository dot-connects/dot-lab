import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/route.dart';

Candidate at(String id, int min, double lat, double lng, String? place) => Candidate(
    Shot(id: id, utc: DateTime.utc(2026, 6, 12, 1).add(Duration(minutes: min)), lat: lat, lng: lng),
    sharp: 50, luma: 120, dhash: 0, place: place, local: DateTime(2026, 6, 12, 10).add(Duration(minutes: min)));

void main() {
  test('a route: one numbered stop per new place in time order, a box around it all', () {
    final r = routeOf([
      at('a', 0, 33.25, 126.40, '중문'), at('b', 30, 33.26, 126.41, '중문'),
      at('c', 90, 33.46, 126.93, '성산'), at('d', 200, 33.51, 126.52, '제주시'), at('e', 260, 33.25, 126.41, '중문'),
    ])!;
    expect((r['pts'] as List).length, 5);
    expect([for (final s in (r['stops'] as List).cast<Map>()) '${s['n']}${s['label']}'], ['1중문', '2성산', '3제주시']);
    final b = (r['box'] as List).cast<double>();
    expect(b[0] < 33.25 && b[2] > 33.51 && b[1] < 126.40 && b[3] > 126.93, isTrue);
    expect(r['km'] as int, greaterThan(80));
    expect(r['visits'], [1, 2, 3, 1]);
  });

  test('no route page: one neighbourhood, or one place and a stray photo', () {
    expect(routeOf([at('a', 0, 33.25, 126.40, 'x'), at('b', 1, 33.251, 126.401, 'y'), at('c', 2, 33.252, 126.40, 'z')]), isNull);
    expect(routeOf([for (var i = 0; i < 40; i++) at('a$i', i, 33.25, 126.40, 'x'), at('b', 50, 33.5, 126.9, 'y')]), isNull);
  });

  test('the departure airport at home is not part of the map', () {
    final r = routeOf([
      at('home', 0, 37.46, 126.44, '인천'),
      at('a', 600, -8.70, 115.26, '사누르'), at('b', 700, -8.68, 115.45, '렘봉안'), at('c', 900, -8.35, 116.04, '길리'),
      at('d', 1000, -8.69, 115.26, '사누르'),
    ])!;
    expect([for (final s in (r['stops'] as List).cast<Map>()) s['label']], ['사누르', '렘봉안', '길리']);
    expect((r['box'] as List).cast<double>()[2], lessThan(0));
  });

  test('stops take names from the map: a small island, else the nearest town; merged names merge stops and close-ups', () {
    final r = <String, Object?>{
      'stops': [
        {'lat': -8.69, 'lng': 115.26, 'n': 1, 'label': 'Kecamatan Denpasar Selatan'},
        {'lat': -8.68, 'lng': 115.45, 'n': 2, 'label': 'Nusa Penida'},
        {'lat': -8.35, 'lng': 116.04, 'n': 3, 'label': 'Gili Indah'},
        {'lat': -8.352, 'lng': 116.042, 'n': 4, 'label': 'Jalan Tanpa Nama'},
      ],
      'legs': [
        {'n': 2, 'label': 'Nusa Penida'},
        {'n': 4, 'label': 'Jalan Tanpa Nama'},
      ],
      'visits': [1, 2, 3, 4, 1],
    };
    nameStops(r, {
      'islands': [
        {'n': '발리 섬', 'b': [-8.85, 114.43, -8.06, 115.71]},
        {'n': 'Nusa Lembongan', 'b': [-8.70, 115.43, -8.66, 115.47]},
        {'n': 'Gili Trawangan', 'b': [-8.37, 116.02, -8.34, 116.05]},
      ],
      'places': [
        {'n': '사누르', 'lat': -8.68, 'lng': 115.263, 'r': 1},
        {'n': 'Denpasar', 'lat': -8.65, 'lng': 115.22, 'r': 4},
      ],
    });
    expect([for (final s in (r['stops'] as List).cast<Map>()) '${s['n']}${s['label']}'], ['1사누르', '2Nusa Lembongan', '3Gili Trawangan']);
    expect([for (final l in (r['legs'] as List).cast<Map>()) '${l['n']}${l['label']}'], ['2Nusa Lembongan', '3Gili Trawangan']);
    expect(r['visits'], [1, 2, 3, 1]);
  });
}

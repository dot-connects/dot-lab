import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/library.dart';

ShelfBook b(String id, DateTime s, DateTime e, {int places = 0, int photos = 0, String stage = 'written'}) =>
    ShelfBook(id: id, title: id, start: s, end: e, pages: 20, places: places, photos: photos, stage: stage);

void main() {
  test('groupByYear: newest year first, newest trip first, year-boundary trip counts in its start year', () {
    final g = groupByYear([
      b('osaka', DateTime(2025, 4, 2), DateTime(2025, 4, 5)),
      b('nye', DateTime(2025, 12, 31), DateTime(2026, 1, 1)),
      b('jeju', DateTime(2026, 6, 12), DateTime(2026, 6, 14)),
      b('busan', DateTime(2026, 2, 3), DateTime(2026, 2, 5)),
    ]);
    expect(g.map((e) => e.$1), [2026, 2025]);
    expect(g[0].$2.map((x) => x.id), ['jeju', 'busan']);
    expect(g[1].$2.map((x) => x.id), ['nye', 'osaka']);
  });

  test('groupByYear: empty', () => expect(groupByYear([]), isEmpty));

  test('days is inclusive and crosses year and month ends', () {
    expect(b('x', DateTime(2026, 6, 12), DateTime(2026, 6, 14)).days, 3);
    expect(b('x', DateTime(2025, 12, 31), DateTime(2026, 1, 1)).days, 2);
    expect(b('x', DateTime(2026, 3, 1), DateTime(2026, 3, 1)).days, 1);
  });

  test('yearTotals sums places, photos and days', () {
    final t = yearTotals([
      b('jeju', DateTime(2026, 6, 12), DateTime(2026, 6, 14), places: 9, photos: 38),
      b('busan', DateTime(2026, 2, 3), DateTime(2026, 2, 5), places: 5, photos: 27),
    ]);
    expect(t, (books: 2, places: 14, photos: 65, days: 6));
  });

  test('freeLeft counts only written books and floors at 0', () {
    final d = DateTime(2026, 1, 1);
    expect(freeLeft([]), 2);
    expect(freeLeft([b('a', d, d, stage: 'picked'), b('b', d, d, stage: 'picked')]), 2);
    expect(freeLeft([b('a', d, d), b('b', d, d, stage: 'picked')]), 1);
    expect(freeLeft([b('a', d, d), b('b', d, d), b('c', d, d)]), 0);
  });
}

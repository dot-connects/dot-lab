import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/book/store.dart';
import 'package:tripbook/core/book.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/plan_check.dart';

void main() {
  late Directory tmp;
  setUp(() async => tmp = await Directory.systemTemp.createTemp('books'));
  tearDown(() async => tmp.delete(recursive: true));

  Book sample() {
    final t = DateTime(2026, 6, 12, 13, 20);
    final c = Candidate(Shot(id: 'p0', utc: t.toUtc(), wall: t, exifOffset: const Duration(hours: 9), lat: 33.4, lng: 126.2, w: 4, h: 3),
        sharp: 512.5, luma: 120, dhash: 77, place: '협재', local: t);
    return Book(
      id: 'b1', from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), opts: BookOptions(), theme: 'a',
      pool: [c], picks: const [Pick('p0', '', 90)], clips: const [Clip('v1', 0, 3000)], mustInclude: {'p0'},
      texts: {'short': const WriteResult(['제주', '', ''], {'p0': '물이 차갑다'}, {'2026-06-12': '서쪽'}, '좋았다')},
      pages: [PageSpec('a/c1', ['p0'], text: {'title': '제주'})],
    );
  }

  test('a saved book loads back with its pool, picks and texts (resume after restart)', () async {
    final s = BookStore(tmp);
    await s.save(sample());
    final b = (await s.load('b1'))!;
    expect(b.stage, 'picked');
    expect(b.pool.single.shot.exifOffset, const Duration(hours: 9));
    expect(b.pool.single.place, '협재');
    expect(b.texts['short']!.captions['p0'], '물이 차갑다');
    expect(b.mustInclude, {'p0'});
    expect(b.pages.single.text['title'], '제주');
  });

  test('a broken book.json is skipped, not a crash; delete removes the folder', () async {
    final s = BookStore(tmp);
    await s.save(sample());
    await Directory('${tmp.path}/bad').create();
    await File('${tmp.path}/bad/book.json').writeAsString('{oops');
    expect((await s.all()).map((b) => b.id), ['b1']);
    await s.delete('b1');
    expect(await s.all(), isEmpty);
  });
}

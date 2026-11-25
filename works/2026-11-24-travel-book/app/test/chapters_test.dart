import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/chapters.dart';
import 'package:tripbook/core/models.dart';

Candidate c(String id, DateTime local, {String? place}) =>
    Candidate(Shot(id: id, utc: local.toUtc()), sharp: 500, luma: 120, dhash: 0, place: place, local: local);

void main() {
  test('a photo uses the camera wall clock, not the UTC date', () {
    // Hawaii 23:30 on 06-03 is 06-04 in UTC
    final s = Shot(id: 'h', utc: DateTime.utc(2026, 6, 4, 9, 30), wall: DateTime(2026, 6, 3, 23, 30));
    expect(localOf(s, const []), DateTime(2026, 6, 3, 23, 30));
  });

  test('without wall clock, the EXIF offset is applied to UTC', () {
    final s = Shot(id: 'o', utc: DateTime.utc(2026, 6, 4, 0, 30), exifOffset: const Duration(hours: 9));
    expect(localOf(s, const []), DateTime(2026, 6, 4, 9, 30));
  });

  test('a video takes the offset of the nearest photo', () {
    final photo = Shot(id: 'p', utc: DateTime.utc(2026, 6, 3, 20), wall: DateTime(2026, 6, 3, 10)); // -10 h
    final far = Shot(id: 'f', utc: DateTime.utc(2026, 5, 1), wall: DateTime(2026, 5, 1, 9)); // +9 h, far away in time
    final video = Shot(id: 'v', utc: DateTime.utc(2026, 6, 3, 21), isVideo: true);
    expect(localOf(video, [far, photo]), DateTime(2026, 6, 3, 11));
  });

  test('a day starts at 04:00 local: 01:30 belongs to the previous day', () {
    final ch = byDay([c('a', DateTime(2026, 6, 12, 22)), c('b', DateTime(2026, 6, 13, 1, 30)), c('c', DateTime(2026, 6, 13, 9))]);
    expect(ch.map((x) => x.day), ['2026-06-12', '2026-06-13']);
    expect(ch.first.items.map((x) => x.id), ['a', 'b']);
  });

  test('byPlace: unknown place joins the run before it, a run never crosses a day', () {
    final ch = byPlace([
      c('1', DateTime(2026, 6, 12, 10), place: '협재'),
      c('2', DateTime(2026, 6, 12, 11)),
      c('3', DateTime(2026, 6, 12, 15), place: '신창'),
      c('4', DateTime(2026, 6, 13, 9), place: '신창'),
    ]);
    expect(ch.map((x) => '${x.day} ${x.place} ${x.items.length}'), ['2026-06-12 협재 2', '2026-06-12 신창 1', '2026-06-13 신창 1']);
  });

  test('gate: fewer than 5 photos stops with the spec message', () {
    final g = gate(4, 0);
    expect(g.ok, isFalse);
    expect(g.message, '사진이 4장뿐이에요, 기간을 넓혀 볼까요?');
    expect(gate(10, 3).ok, isTrue);
    expect(gate(10, 3).noGps, 3);
  });
}

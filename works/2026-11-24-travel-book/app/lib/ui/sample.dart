// Fake data with the mockup's numbers, so the screens can be seen before the gallery is wired.
import '../core/library.dart';
import 'making/pairs.dart';
import 'making/swap_sheet.dart';
import 'new_book/draft.dart';

const _s = 'assets/sample/';

final sampleBooks = <ShelfBook>[
  ShelfBook(id: 'jeju', title: '제주 2박 3일', start: DateTime(2026, 6, 12), end: DateTime(2026, 6, 14), pages: 24, places: 9, photos: 38, km: 214, cover: 'assets/covers/a.jpg', theme: 'a'),
  ShelfBook(id: 'busan', title: '부산, 겨울', start: DateTime(2026, 2, 3), end: DateTime(2026, 2, 5), pages: 18, places: 5, photos: 27, km: 31, cover: 'assets/covers/b.jpg', theme: 'b'),
  ShelfBook(id: 'osaka', title: '오사카 4일', start: DateTime(2025, 4, 2), end: DateTime(2025, 4, 5), pages: 32, places: 6, photos: 72, km: 48, cover: 'assets/covers/mag.jpg', theme: 'mag'),
  ShelfBook(id: 'gangneung', title: '강릉 일기', start: DateTime(2025, 8, 15), end: DateTime(2025, 8, 16), pages: 14, places: 4, photos: 19, cover: 'assets/covers/diary.jpg', theme: 'diary'),
  ShelfBook(id: 'gyeongju', title: '경주 하루', start: DateTime(2025, 10, 4), end: DateTime(2025, 10, 4), pages: 12, places: 3, photos: 58, km: 12, cover: '${_s}hallasan.jpg', theme: 'album'),
];

final sampleMonth = DateTime(2026, 6);

final samplePhotosPerDay = <DateTime, int>{
  DateTime(2026, 6, 2): 6,
  DateTime(2026, 6, 6): 14,
  DateTime(2026, 6, 11): 3,
  DateTime(2026, 6, 12): 131,
  DateTime(2026, 6, 13): 178,
  DateTime(2026, 6, 14): 103,
  DateTime(2026, 6, 20): 9,
};

final sampleVideosPerDay = <DateTime, int>{DateTime(2026, 6, 12): 3, DateTime(2026, 6, 13): 4, DateTime(2026, 6, 14): 2};

const sampleAlbums = [
  Album('camera', '카메라', inRange: 164, total: 9842, thumb: '${_s}hyeopjae.jpg'),
  Album('kakao', 'KakaoTalk', inRange: 148, total: 2310, thumb: '${_s}d5.jpg'),
  Album('shots', '스크린샷', inRange: 78, total: 1204, thumb: '${_s}d8.jpg'),
  Album('jeju', '제주 여행', inRange: 22, total: 22, thumb: '${_s}tangerine.jpg'),
  Album('dl', '다운로드', inRange: 0, total: 31, thumb: '${_s}d1.jpg'),
];

const _pool = [
  'd9', 'd4', 'd2', 'd7', 'road_2', 'road', 'd3', 'udo', 'd10', 'udo_2', 'd5', 'haenyeo', 'haenyeo_2', 'doldam', 'doldam_2', //
  'woljeongri', 'woljeongri_2', 'seongsan', 'seongsan_2', 'hyeopjae', 'hyeopjae_2', 'hallasan', 'hallasan_2', 'oreum', 'oreum_2',
  'tangerine', 'tangerine_2', 'd1', 'd6', 'd8',
];

/// The 186 photos of the two kept albums, by day (58 / 81 / 47), photo files reused round-robin.
final sampleMustDays = <(DateTime, List<PhotoRef>)>[
  for (final (day, n, off) in [(12, 58, 0), (13, 81, 6), (14, 47, 15)])
    (DateTime(2026, 6, day), [for (var i = 0; i < n; i++) PhotoRef('$day-$i', '$_s${_pool[(off + i) % _pool.length]}.jpg')]),
];

String samplePath(String id) {
  for (final (_, ps) in sampleMustDays) {
    for (final p in ps) {
      if (p.id == id) return p.path;
    }
  }
  return '${_s}road.jpg';
}

const sampleNotes = [
  NoteItem('제주 메모.txt', '1,240자 · 6월 12일, 13일 글을 찾았어요'),
  NoteItem('손글씨 사진 1장', '글 쓸 때 읽어요 · 날짜를 못 찾으면 책 전체에 써요', thumb: '${_s}d10.jpg'),
];

// ----- D4 making -----

/// What the picker kept, by day (9 / 13 / 16 = 38), with the mockup's must-include and cover photos.
final samplePicks = <(DateTime, List<PhotoRef>)>[
  (DateTime(2026, 6, 12), [for (final k in ['d8', 'hyeopjae', 'd9', 'd4', 'd2', 'd6', 'd7', 'road_2', 'road']) PhotoRef('p-$k', '$_s$k.jpg')]),
  (DateTime(2026, 6, 13), [
    for (final k in ['d3', 'udo', 'd10', 'udo_2', 'd5', 'haenyeo', 'haenyeo_2', 'doldam', 'doldam_2', 'woljeongri', 'woljeongri_2', 'seongsan', 'seongsan_2'])
      PhotoRef('p-$k', '$_s$k.jpg'),
  ]),
  (DateTime(2026, 6, 14), [
    for (final (i, k) in ['hallasan', 'hallasan_2', 'oreum', 'oreum_2', 'tangerine', 'tangerine_2', 'd1', 'hyeopjae_2', 'seongsan', 'udo', 'road', 'doldam', 'd9', 'd5', 'd2', 'd8'].indexed)
      PhotoRef('p14-$i', '$_s$k.jpg'),
  ]),
];
const sampleMustPicks = {'p-road', 'p-haenyeo', 'p-doldam_2'};
const sampleCoverId = 'p-udo';

/// The 6x2 strip on the picking screen: kept / dropped.
const samplePickStrip = [
  ('${_s}hyeopjae.jpg', true), ('${_s}d3.jpg', false), ('${_s}road.jpg', true), ('${_s}hyeopjae_2.jpg', false), ('${_s}udo.jpg', true), ('${_s}d5.jpg', false), //
  ('${_s}road_2.jpg', false), ('${_s}haenyeo.jpg', true), ('${_s}d7.jpg', false), ('${_s}doldam_2.jpg', true), ('${_s}d10.jpg', false), ('${_s}seongsan.jpg', true),
];

PairAsk _pair(String a, String b, String ta, String tb, String label) =>
    PairAsk(PhotoRef('p-$a', '$_s$a.jpg'), PhotoRef('x-$b', '$_s$b.jpg'), aTime: ta, bTime: tb, label: label);

final samplePairs = [
  _pair('hyeopjae', 'hyeopjae_2', '13:20', '13:24', '협재 해변 · 6월 12일'),
  _pair('udo', 'udo_2', '10:05', '10:07', '우도 · 6월 13일'),
  _pair('haenyeo', 'haenyeo_2', '14:10', '14:12', '하도 해녀 탈의장 · 6월 13일'),
  _pair('oreum', 'oreum_2', '09:40', '09:52', '오름 · 6월 14일'),
];

/// Same-place alternatives for the swap sheet (the pair's loser first).
final sampleAlts = [
  for (final (k, l, t) in const [('hyeopjae_2', '아까 둘 중', '13:24'), ('d9', '같은 곳', '13:31'), ('d4', '같은 곳', '13:47')])
    SwapAlt(PhotoRef('a-$k', '$_s$k.jpg'), l, t),
];

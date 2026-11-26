// The pipeline with a fake phone, a fake AI and a real store in a temp folder: order, caps and failure paths.
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:tripbook/ai/client.dart';
import 'package:tripbook/book/shelf_of.dart';
import 'package:tripbook/book/store.dart';
import 'package:tripbook/core/library.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/device/gallery.dart' as g;
import 'package:tripbook/pipeline.dart';

Uint8List _jpeg(int seed, {bool flat = false}) {
  final r = math.Random(seed);
  final im = img.Image(width: 64, height: 48);
  for (final p in im) {
    final v = flat ? 128 : r.nextInt(256);
    p
      ..r = v
      ..g = v
      ..b = v;
  }
  return img.encodeJpg(im, quality: 95);
}

Shot _shot(String id, int day, int minute, {bool video = false, String rel = 'DCIM/Camera/', double? lat}) {
  final wall = DateTime(2026, 6, day, 10, minute);
  return Shot(
    id: id,
    utc: wall.subtract(const Duration(hours: 9)).toUtc(),
    wall: video ? null : wall,
    isVideo: video,
    durationMs: video ? 10000 : 0,
    relPath: rel,
    lat: lat,
    lng: lat == null ? null : 126.5,
    w: 4000,
    h: 3000,
  );
}

class FakeSource implements Source {
  final Set<String> flat; // blurred photos
  final Map<String, int> sameAs; // burst: same pixels as another id
  final calls = <String>[];
  FakeSource({this.flat = const {}, this.sameAs = const {}});

  @override
  Future<Uint8List?> jpeg(String id, int long, {int quality = 80}) async {
    calls.add('jpeg $id $long');
    return _jpeg(sameAs[id] ?? id.hashCode, flat: flat.contains(id));
  }

  @override
  Future<List<Uint8List>> videoFrames(String id) async => [for (var i = 0; i < 4; i++) _jpeg(i)];

  @override
  Future<String?> trim(String id, int startMs, int endMs, String out) async {
    await File(out).writeAsBytes([1, 2, 3]);
    return out;
  }

  @override
  Future<String?> placeOf(double lat, double lng) async => '협재';

  @override
  Future<Map<DateTime, (int, int)>> monthCounts(DateTime month) async => {};

  @override
  Future<List<g.Album>> scan(DateTime from, DateTime to) async => [];
}

class FakeAi implements AiClient {
  AiResult Function(List<AiImage> images)? onPick;
  AiResult Function(List<AiImage> images, String shape)? onWrite;
  AiResult Function(List<AiImage> images)? onLayout;
  final laidOut = <Map<String, Object?>>[];
  AiResult onRewrite = const AiOk({'caption': '물이 차갑다.'});
  final picked = <List<AiImage>>[];
  final written = <List<AiImage>>[];

  @override
  Future<AiResult> pick(List<AiImage> images, List<AiShot> shots,
      {List<Map<String, String?>> notes = const [], Map<String, Object?> opts = const {}, List<String> mustInclude = const []}) async {
    picked.add(images);
    if (onPick != null) return onPick!(images);
    final ids = {for (final i in images) if (!i.id.startsWith('ref')) i.id}.toList();
    return AiOk({
      'picks': [for (final (k, id) in ids.indexed) {'id': id, 'score': 90 - k}],
      'ask': [if (ids.length > 2) {'a': ids[0], 'b': ids[1]}],
      'theme': 'a',
      'chapters': [{'day': '2026-06-12', 'line': '바다'}],
      'clips': [for (final id in ids) if (id.startsWith('v')) {'id': id, 'startMs': 1000, 'endMs': 4000}],
    });
  }

  @override
  Future<AiResult> write(List<AiImage> images, List<AiShot> shots, String shape,
      {List<Map<String, String?>> notes = const [], Map<String, Object?> opts = const {}}) async {
    written.add(images);
    if (onWrite != null) return onWrite!(images, shape);
    return AiOk({
      'titles': ['제주 바다', '', ''],
      'captions': [for (final i in images) {'id': i.id, 'text': '캡션 ${i.id}'}],
      'chapters': [{'day': '2026-06-12', 'line': '첫날'}],
      'essay': '좋았다.',
    });
  }

  /// A plain editor: cover, then one page a photo (a captioned single-slot layout, the video layout for clips).
  @override
  Future<AiResult> layout(List<AiImage> images, List<AiShot> shots,
      {required String theme,
      required List<Map<String, Object>> catalog,
      required Map<String, Object> rules,
      List<Map<String, String?>> notes = const [],
      Map<String, Object?> opts = const {},
      List<String>? fix,
      Map<String, dynamic>? prev}) async {
    laidOut.add({'theme': theme, 'fix': fix, 'opts': opts, 'n': images.length});
    if (onLayout != null) return onLayout!(images);
    Map<String, Object> first(bool Function(Map<String, Object>) f) => catalog.firstWhere(f);
    final cover = first((l) => l['kind'] == 'cover' && (l['slots'] as List).length == 1);
    final one = first((l) => l['kind'] == 'photo' && (l['slots'] as List).length == 1 && l['slots'].toString().contains('L') &&
        (l['texts'] as Map).isNotEmpty);
    final vd = first((l) => l['kind'] == 'video');
    final photos = [for (final i in images) if (!i.id.startsWith('v')) i.id];
    return AiOk({
      'title': '제주 바다',
      'plan': '',
      'pages': [
        {'layout': cover['id'], 'intent': '', 'photos': [{'id': photos.first, 'fx': 50, 'fy': 50}], 'texts': [{'key': 'title', 'text': '제주'}]},
        for (final i in images)
          i.id.startsWith('v')
              ? {'layout': vd['id'], 'intent': '', 'photos': [{'id': i.id, 'fx': 50, 'fy': 50}], 'texts': []}
              : {
                  'layout': one['id'], 'intent': '', 'photos': [{'id': i.id, 'fx': 50, 'fy': 40}],
                  'texts': [{'key': (one['texts'] as Map).keys.first, 'text': '캡션 ${i.id}'}],
                },
      ],
    });
  }

  @override
  Future<AiResult> rewrite(List<AiImage> images, Map<String, String> page, String shape, {List<Map<String, String?>> notes = const []}) async =>
      onRewrite;

  @override
  bool get configured => true;
  @override
  String get token => 't';
  @override
  String get url => 'u';
}

void main() {
  late Directory tmp;
  late BookStore store;
  late FakeAi ai;
  setUp(() {
    tmp = Directory.systemTemp.createTempSync('pipe');
    store = BookStore(Directory('${tmp.path}/books'));
    ai = FakeAi();
  });
  tearDown(() => tmp.deleteSync(recursive: true));

  Pipeline pipe(FakeSource src) => Pipeline(
        src: src,
        ai: ai,
        store: store,
        layoutsJson: (t) => File('assets/book/themes/$t/layouts.json').readAsString(),
        newId: () => 'b1',
      );

  // 3 days x 6 sharp photos, + 1 screenshot, 2 blurred (one is must-include), a 2-frame burst, 1 video
  List<Shot> trip() => [
        for (var d = 12; d <= 14; d++)
          for (var i = 0; i < 6; i++) _shot('p$d-$i', d, i * 20, lat: 33.4),
        _shot('shot1', 12, 5, rel: 'Pictures/Screenshots/'),
        _shot('blur1', 13, 7),
        _shot('blur2', 13, 9),
        _shot('burstA', 14, 50),
        _shot('burstB', 14, 50),
        _shot('v1', 13, 30, video: true),
      ];
  FakeSource src() => FakeSource(flat: {'blur1', 'blur2'}, sameAs: {'burstA': 7, 'burstB': 7});

  test('pick: filters on the phone, keeps must-include, saves a picked book', () async {
    final progress = <PickProgress>[];
    final r = await pipe(src()).pick(
      from: DateTime(2026, 6, 12),
      to: DateTime(2026, 6, 14),
      shots: [...trip(), _shot('p12-0', 12, 0, lat: 33.4)], // a duplicate (two albums) counts once
      inRange: 412,
      albums: 2,
      opts: BookOptions(),
      must: {'blur2'},
      onProgress: progress.add,
    );
    final last = progress.last;
    expect((last.inRange, last.albumPhotos), (412, 24));
    expect((last.capture, last.blurDark, last.similar), (1, 1, 1));
    expect(last.sent, 18 + 1 + 1 + 1); // 18 sharp + burst keeper + video + must
    expect(last.picked, r.book.picks.length);
    final sentIds = {for (final i in ai.picked.single) i.id};
    expect(sentIds, containsAll(['blur2', 'v1']));
    expect(sentIds, isNot(contains('shot1')));
    expect(sentIds, isNot(contains('blur1')));
    expect(ai.picked.single.where((i) => i.id == 'v1').map((i) => i.atMs), [1500, 4000, 6500, 9000]);
    expect(r.ai, AiState.ok);
    expect(r.ask, hasLength(1));
    expect(r.book.picks.map((p) => p.id), contains('blur2'));
    expect(r.book.pool, hasLength(24));
    expect(r.book.pool.first.place, '협재');
    final saved = await store.load('b1');
    expect(saved!.stage, 'picked');
  });

  test('pick: never more than 60 images to the AI', () async {
    final shots = [
      for (var i = 0; i < 90; i++) _shot('p$i', 12 + i % 3, i % 60),
      _shot('v1', 12, 1, video: true),
      _shot('v2', 13, 1, video: true),
    ];
    await pipe(FakeSource()).pick(
        from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), shots: shots, inRange: 92, albums: 1, opts: BookOptions(), must: {'p5'});
    final imgs = ai.picked.single;
    expect(imgs.length, 60); // 2 videos x 4 scenes + must 1 + 51 photos
    expect(imgs.where((i) => i.atMs != null).length, 8);
  });

  test('pick with the AI off: a fallback book (no text), still saved', () async {
    ai.onPick = (_) => const AiOff();
    final r = await pipe(src())
        .pick(from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), shots: trip(), inRange: 25, albums: 1, opts: BookOptions());
    expect(r.ai, AiState.off);
    expect(r.ask, isEmpty);
    expect(r.book.picks, isNotEmpty);
    expect((await store.load('b1'))!.stage, 'picked');
  });

  test('pairs, [+], write, pages and files', () async {
    final p = pipe(src());
    final r = await p.pick(from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), shots: trip(), inRange: 25, albums: 1, opts: BookOptions());
    final b = r.book;
    final (a, x) = r.ask.single;
    await p.applyPairs(b, r.ask, {'$a|$x': x});
    expect(b.picks.map((p) => p.id), isNot(contains(a)));
    expect(await p.addOne(b, DateTime(2026, 6, 12)), isTrue); // the pair's loser is the one usable photo left
    expect(b.picks.last.id, a);
    expect(await p.addOne(b, DateTime(2026, 6, 12)), isFalse);

    expect(await p.write(b), AiState.ok);
    expect(b.stage, 'written');
    expect(b.title, '제주 바다');
    expect(b.pages.first.layout, startsWith('a/c-'));
    expect(b.pages.where((p) => p.text.values.any((t) => t.startsWith('캡션 '))), isNotEmpty);
    expect(b.pages.skip(1).first.crop.first, '50% 40%');
    expect(ai.laidOut.first['theme'], 'a');
    final js = bookJs(b);
    final media = js['media'] as Map<String, Object?>;
    for (final m in media.values) {
      final src = (m as Map)['src'] as String;
      expect(File('${tmp.path}${src.replaceFirst('/books', '/books')}').existsSync(), isTrue, reason: src);
    }
    expect(media['v1'], anyOf(isNull, containsPair('video', '/books/b1/vid/v1.mp4')));
    final shelf = shelfOf((await store.load('b1'))!, store.root.path);
    expect(shelf.stage, 'written');
    expect(freeLeft([shelf]), 1);
  });

  test('write failure keeps the picks and stage; [다시 시도] writes only', () async {
    final p = pipe(src());
    final r = await p.pick(from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), shots: trip(), inRange: 25, albums: 1, opts: BookOptions());
    final picks = [for (final x in r.book.picks) x.id];
    ai.onLayout = (_) => const AiFailed('offline');
    expect(await p.write(r.book), AiState.failed);
    final saved = (await store.load('b1'))!;
    expect(saved.stage, 'picked');
    expect(saved.texts, isEmpty);
    expect(saved.picks.map((x) => x.id), picks);
    ai.onLayout = null;
    expect(await p.write(saved), AiState.ok);
    expect(ai.picked, hasLength(1)); // no second /pick
    expect((await store.load('b1'))!.stage, 'written');
  });

  test('a book without text when /write stays off', () async {
    final p = pipe(src());
    final r = await p.pick(from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), shots: trip(), inRange: 25, albums: 1, opts: BookOptions());
    await p.finish(r.book);
    expect(r.book.pages, isNotEmpty);
    expect(r.book.stage, 'picked'); // does not use a free book
  });

  test('rewrite a page: counts, stops at the cap', () async {
    final p = pipe(src());
    final r = await p.pick(from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 14), shots: trip(), inRange: 25, albums: 1, opts: BookOptions());
    await p.write(r.book);
    final i = r.book.pages.indexWhere((x) => x.text.values.any((t) => t.startsWith('캡션 ')));
    final key = r.book.pages[i].text.entries.firstWhere((e) => e.value.startsWith('캡션 ')).key;
    expect(await p.rewritePage(r.book, i, max: 2), AiState.ok);
    expect(r.book.pages[i].text[key], '물이 차갑다.');
    ai.onRewrite = const AiFailed('x');
    expect(await p.rewritePage(r.book, i, max: 2), AiState.failed);
    ai.onRewrite = const AiOk({'caption': '둘'});
    expect(await p.rewritePage(r.book, i, max: 2), AiState.ok);
    expect(await p.rewritePage(r.book, i, max: 2), isNull);
    expect(r.book.rewrites, 2);
  });
}

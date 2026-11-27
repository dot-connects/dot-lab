// The book pipeline behind the screens: trip scan -> on-phone filtering -> /pick -> the user's review -> /write ->
// pages -> files. Every step saves the Book (books/<id>/book.json), so a restart resumes at the review.
// Device, AI and store are injected so the order and the failure paths are tested with fakes (test/pipeline_test.dart).
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as img;

import 'ai/client.dart';
import 'book/store.dart';
import 'core/book.dart';
import 'core/book_layout.dart';
import 'core/chapters.dart';
import 'core/edit.dart' show textScale;
import 'core/models.dart';
import 'core/paginate.dart';
import 'core/plan_check.dart';
import 'core/prefilter.dart';
import 'core/refdocs.dart';
import 'core/scenes.dart';
import 'core/selection.dart';
import 'core/text_shape.dart';
import 'core/timeline.dart';
import 'device/gallery.dart' as g;

/// What the pipeline needs from the phone. PhoneSource (lib/book/phone_source.dart) is the real one.
abstract class Source {
  /// Photo / video counts per local day of [month] (calendar dots).
  Future<Map<DateTime, (int photos, int videos)>> monthCounts(DateTime month);
  Future<List<g.Album>> scan(DateTime from, DateTime to);
  /// JPEG with the long side = [long] (photo, or a video's poster).
  Future<Uint8List?> jpeg(String id, int long, {int quality = 80});
  /// Four scenes of a video (15/40/65/90 %), 512 px.
  Future<List<Uint8List>> videoFrames(String id);
  Future<String?> trim(String id, int startMs, int endMs, String out);
  Future<String?> placeOf(double lat, double lng);
}

/// Test builds for tuning the picker (--dart-define=LAB=true): each pick keeps its previews and numbers in
/// <cache>/lab, and 설정 offers them as one zip. Off in store builds.
const kLab = bool.fromEnvironment('LAB');

/// Where a LAB build keeps the last pick's previews and numbers (emptied when a new trip is scanned).
Future<Directory> labDir(Directory cache) async => Directory('${cache.path}/lab');

/// The final look's images (photos at [finalPx] + 4 scenes per video + note photos): ~2.6x the book's photos.
const maxAiImages = 128;
const finalPx = 384;
/// The first look: at most this many small previews, [scoutBatch] per request, all requests at once.
const maxScout = 480, scoutBatch = 80;
const _chunk = 48;
const sceneAt = [.15, .40, .65, .90];

class PickProgress {
  final int inRange, albums, albumPhotos;
  final int blurDark, similar, capture, sent;
  final int measured, toMeasure; // photos looked at so far / to look at (the phone's own pass, before the AI)
  final int? picked;
  final int must;
  final List<String> sample; // up to 160 photo ids spread over the trip, time order (the picking mosaic)
  final List<String> sentIds; // sent to the AI, time order
  final Set<String>? pickedIds;
  final int scoutTotal, scouted; // the first look: photos in it / tagged so far
  final List<String> found; // what the picking found so far, newest last ("가장 많이 찍은 곳 · 성산일출봉 214장")
  const PickProgress({
    required this.inRange,
    required this.albums,
    required this.albumPhotos,
    this.blurDark = 0,
    this.similar = 0,
    this.capture = 0,
    this.sent = 0,
    this.measured = 0,
    this.toMeasure = 0,
    this.picked,
    this.must = 0,
    this.sample = const [],
    this.sentIds = const [],
    this.pickedIds,
    this.scoutTotal = 0,
    this.scouted = 0,
    this.found = const [],
  });
  PickProgress copyWith(
          {int? blurDark,
          int? similar,
          int? capture,
          int? sent,
          int? measured,
          int? toMeasure,
          int? picked,
          List<String>? sentIds,
          Set<String>? pickedIds,
          int? scoutTotal,
          int? scouted,
          List<String>? found}) =>
      PickProgress(
        inRange: inRange,
        albums: albums,
        albumPhotos: albumPhotos,
        blurDark: blurDark ?? this.blurDark,
        similar: similar ?? this.similar,
        capture: capture ?? this.capture,
        sent: sent ?? this.sent,
        measured: measured ?? this.measured,
        toMeasure: toMeasure ?? this.toMeasure,
        picked: picked ?? this.picked,
        must: must,
        sample: sample,
        sentIds: sentIds ?? this.sentIds,
        pickedIds: pickedIds ?? this.pickedIds,
        scoutTotal: scoutTotal ?? this.scoutTotal,
        scouted: scouted ?? this.scouted,
        found: found ?? this.found,
      );
}

enum AiState { ok, off, failed }

AiState stateOf(AiResult r) => switch (r) { AiOk() => AiState.ok, AiOff() => AiState.off, AiFailed() => AiState.failed };

class PickResult {
  final Book book;
  final List<(String, String)> ask;
  final AiState ai;
  const PickResult(this.book, this.ask, this.ai);
}

/// A note the user added: text pieces (by day) or a handwriting photo.
class NoteInput {
  final List<RefPiece> pieces;
  final Uint8List? image;
  const NoteInput({this.pieces = const [], this.image});
}

String fileId(String id) => id.replaceAll(RegExp(r'[^\w-]'), '_');
String _localIso(DateTime t) => t.toIso8601String().substring(0, 16);

class Pipeline {
  final Source src;
  final AiClient ai;
  final BookStore store;
  final Future<String> Function(String theme) layoutsJson; // assets/book/themes/<t>/layouts.json
  final String Function() newId;
  final Duration poll; // how often a running layout job is asked for its result
  final _places = <String, String?>{};

  Pipeline(
      {required this.src,
      required this.ai,
      required this.store,
      required this.layoutsJson,
      String Function()? newId,
      this.poll = const Duration(seconds: 5)})
      : newId = newId ?? (() => DateTime.now().millisecondsSinceEpoch.toString());

  Future<String?> _place(double lat, double lng) async {
    final k = '${lat.toStringAsFixed(2)},${lng.toStringAsFixed(2)}';
    if (_places.containsKey(k)) return _places[k];
    return _places[k] = await src.placeOf(lat, lng);
  }

  // ----- /pick -----

  /// Measure, filter and send. [shots] = the chosen albums' trip shots; [inRange] = every album's count.
  Future<PickResult> pick({
    required DateTime from,
    required DateTime to,
    required List<Shot> shots,
    required int inRange,
    required int albums,
    required BookOptions opts,
    Set<String> must = const {},
    List<NoteInput> notes = const [],
    Timeline? timeline,
    void Function(PickProgress)? onProgress,
    Directory? lab, // test builds (LAB=true): keep each 256 px preview + a shot table + timings here
  }) async {
    final clock = Stopwatch()..start(), times = <String, int>{};
    void lap(String k) => times[k] = clock.elapsedMilliseconds;
    if (lab != null) await Directory('${lab.path}/thumbs').create(recursive: true);
    final byId = {for (final s in shots) s.id: s}; // one shot can sit in two albums on other platforms
    final all = byId.values.toList();
    // only photos are measured (videos and screen captures are never judged by sharpness); a moment with very many
    // shots is thinned evenly in its own time first (40 + 6 per sqrt of its shots), so a huge trip stays quick
    final look = [for (final (i, s) in all.indexed) if (!s.isVideo && !isScreenshot(s)) i];
    final measuredIdx = [
      for (final m in momentsBy<int>(look, (i) => all[i].utc, (i) => all[i].lat == null || all[i].lng == null ? null : (all[i].lat!, all[i].lng!)))
        for (var k = 0, n = math.min(m.length, (40 + 6 * math.sqrt(m.length)).round()); k < n; k++) m[k * m.length ~/ n],
    ]..sort();
    final thinned = look.length - measuredIdx.length;
    var p = PickProgress(
      inRange: inRange,
      albums: albums,
      albumPhotos: all.length,
      must: must.length,
      toMeasure: measuredIdx.length,
      sample: [for (var k = 0; k < math.min(160, look.length); k++) all[look[k * look.length ~/ math.min(160, look.length)]].id],
    );
    onProgress?.call(p);

    // 256 px thumbnails, fetched [_chunk] at a time; each chunk is measured off the UI isolate while the next loads
    const none = (sharp: 0.0, luma: 128.0, dhash: 0);
    final m = List<({double sharp, double luma, int dhash})>.filled(all.length, none);
    final skipped = {for (final i in look) i}..removeAll(measuredIdx);
    var done = 0;
    Future<void>? measuring;
    for (var at = 0; at < measuredIdx.length; at += _chunk) {
      final part = measuredIdx.sublist(at, math.min(at + _chunk, measuredIdx.length));
      final thumbs = await Future.wait([for (final i in part) src.jpeg(all[i].id, 256, quality: 70)]);
      if (lab != null) {
        await Future.wait([
          for (final (k, i) in part.indexed)
            if (thumbs[k] != null) File('${lab.path}/thumbs/${fileId(all[i].id)}.jpg').writeAsBytes(thumbs[k]!),
        ]);
      }
      await measuring;
      measuring = _measureAll(thumbs).then((r) {
        for (final (k, i) in part.indexed) {
          m[i] = r[k];
        }
        done += part.length;
        onProgress?.call(p = p.copyWith(measured: done));
      });
    }
    await measuring;

    // place names: each distinct ~1 km cell once, all at the same time
    (double, double)? at(Shot s) {
      if (s.lat != null && s.lng != null) return (s.lat!, s.lng!);
      final v = timeline == null ? null : visitAt(timeline, s.utc);
      return v == null || v.name != null ? null : (v.lat, v.lng);
    }

    final cells = <String, (double, double)>{};
    for (final s in all) {
      final c = at(s);
      if (c != null) cells.putIfAbsent('${c.$1.toStringAsFixed(2)},${c.$2.toStringAsFixed(2)}', () => c);
    }
    lap('measured');
    await Future.wait([for (final (la, ln) in cells.values) _place(la, ln)]);
    lap('places');

    final photos = all.where((s) => !s.isVideo).toList();
    final cands = <Candidate>[];
    for (var i = 0; i < all.length; i++) {
      final s = all[i];
      String? place;
      if (s.lat != null && s.lng != null) {
        place = await _place(s.lat!, s.lng!);
      } else if (timeline != null) {
        final v = visitAt(timeline, s.utc);
        if (v != null) place = v.name ?? await _place(v.lat, v.lng);
      }
      cands.add(Candidate(s, sharp: m[i].sharp, luma: m[i].luma, dhash: m[i].dhash, place: place, local: localOf(s, photos)));
    }
    final skippedIds = {for (final i in skipped) all[i].id};

    final mustC = cands.where((c) => must.contains(c.id)).take(10).toList();
    final free = cands.where((c) => !must.contains(c.id)).toList();
    final vids = free.where((c) => c.shot.isVideo).toList();
    final shotsOnly = free.where((c) => !c.shot.isVideo && !isScreenshot(c.shot)).toList();
    final capture = free.length - vids.length - shotsOnly.length;
    final good = shotsOnly.where((c) => !skippedIds.contains(c.id) && !isBad(c)).toList();
    final blurDark = shotsOnly.length - thinned - good.length;

    // the trip as moments over every photo: the shots taken in a moment say how much that place mattered
    final photoCands = [...shotsOnly, ...mustC.where((c) => !c.shot.isVideo)];
    final moments = momentsBy(photoCands, (c) => c.local, posOf);
    final momentOf = <String, int>{}, momentSize = <int, int>{};
    for (final (i, m) in moments.indexed) {
      momentSize[i] = m.length;
      for (final c in m) {
        momentOf[c.id] = i;
      }
    }
    // per moment: same-frame bursts become one frame, pose series stay whole
    final keepIds = {for (final c in good) c.id, for (final c in mustC) c.id};
    final frames = <List<Candidate>>[], series = <String, int>{};
    for (final m in moments) {
      final r = collapse([for (final c in m) if (keepIds.contains(c.id)) c], firstSeries: series.values.fold(0, math.max) + 1);
      frames.add(r.frames);
      series.addAll(r.series);
    }
    final goodIds = {for (final c in good) c.id};
    final kept = frames.fold(0, (n, f) => n + f.where((c) => goodIds.contains(c.id)).length);
    final similar = math.max(0, good.length - kept) + thinned;
    final found = <String>[];
    if (moments.isNotEmpty) {
      final top = moments.reduce((a, x) => x.length > a.length ? x : a);
      final place = scenesOf(top).firstOrNull?.place ?? '';
      found.add('가장 많이 찍은 곳 · ${place.isNotEmpty ? place : '${top.first.local.month}월 ${top.first.local.day}일 ${top.first.local.hour}시쯤'} ${fmtCount(top.length)}장');
    }
    final nSeries = series.values.toSet().length;
    if (nSeries > 0) found.add('포즈만 다른 연속 사진 $nSeries묶음');
    final wide = shortlist(frames, [for (final m in moments) m.length], series, maxScout, must: must);
    p = p.copyWith(blurDark: blurDark, similar: similar, capture: capture, scoutTotal: wide.length, found: [...found]);
    onProgress?.call(p);
    lap('shortlist');

    // the first, wide look: small previews in batches at once; a small model tags each one
    final tags = <String, Tag>{};
    var scouted = 0;
    Future<void> look1(List<Candidate> batch) async {
      final ims = <AiImage>[], ids = <String>[];
      for (final c in batch) {
        final j = await src.jpeg(c.id, 256, quality: 70);
        if (j == null) continue;
        ims.add(AiImage(c.id, j));
        ids.add(c.id);
      }
      if (ims.isNotEmpty) {
        final r = await ai.scout(ims, [for (final c in batch) _aiShot(c, scene: momentOf[c.id] ?? -1)], opts: opts.toJson());
        if (r is AiOk) tags.addAll(parseScout(r.body, ids));
      }
      scouted += batch.length;
      onProgress?.call(p = p.copyWith(scouted: scouted));
    }

    await Future.wait([for (var k = 0; k < wide.length; k += scoutBatch) look1(wide.sublist(k, math.min(k + scoutBatch, wide.length)))]);
    lap('scout');
    if (tags.isNotEmpty) {
      int n(String k) => tags.values.where((t) => t.kind == k).length;
      final bits = [
        if (n('같이') > 0) '같이 찍은 사진 ${n('같이')}장',
        if (n('음식') > 0) '음식 ${n('음식')}장',
        if (n('명소') > 0) '명소 ${n('명소')}장',
        if (n('동물') + n('신기') > 0) '동물·신기한 장면 ${n('동물') + n('신기')}장',
      ];
      if (bits.isNotEmpty) found.add(bits.join(' · '));
    }

    // the finalists by the recipe, then the final look at a larger size
    final target = lengthTarget[opts.length] ?? 32;
    final sentVids = capEvenly(vids, maxPickVideos);
    final refImages = [for (final n in notes) if (n.image != null) n.image!];
    final room = math.max(0, maxAiImages - sentVids.length * sceneAt.length - refImages.length);
    final fin = finalists(wide, tags, momentOf, momentSize, series, math.min(room, (target * 2.6).round()), must: must, people: opts.people);
    final ranked = [...fin]..sort((a, b) {
        final d = (tags[b.id]?.score ?? 3) - (tags[a.id]?.score ?? 3);
        return d != 0 ? d : b.sharp.compareTo(a.sharp);
      });
    final sent = {...fin, ...sentVids, ...mustC}.toList()..sort((a, b) => a.local.compareTo(b.local));
    final sentIds = {for (final c in sent) c.id};
    p = p.copyWith(sent: sent.length, sentIds: [for (final c in sent) if (!c.shot.isVideo) c.id], found: [...found]);
    onProgress?.call(p);

    final images = <AiImage>[];
    for (final c in sent) {
      if (c.shot.isVideo) {
        final fs = await src.videoFrames(c.id);
        for (var i = 0; i < fs.length && i < sceneAt.length; i++) {
          images.add(AiImage(c.id, fs[i], atMs: (c.shot.durationMs * sceneAt[i]).round()));
        }
      } else {
        final j = await src.jpeg(c.id, finalPx);
        if (j != null) images.add(AiImage(c.id, j));
      }
    }
    for (final (i, r) in refImages.indexed) {
      images.add(AiImage('ref${i + 1}', r));
    }
    final noteList = _notes(notes);
    final recommended = recommendTheme(byDay(sent), hasDiaryNotes: noteList.isNotEmpty);
    lap('previews');
    AiShot shot(Candidate c) => _aiShot(c,
        tag: tags[c.id]?.kind ?? '', people: tags[c.id]?.people ?? 0, busy: momentSize[momentOf[c.id]] ?? 0, series: series[c.id] ?? 0);
    final r = await ai.pick(images, [for (final c in sent) shot(c)],
        notes: noteList, opts: opts.toJson(), mustInclude: [for (final c in mustC) c.id], target: target);
    // no answer: the recipe's own order is the book (best first), as many as the length asks for
    final plan = r is AiOk
        ? checkPick(r.body, sent, opts, recommended: recommended, must: sentIds.intersection(must))
        : fallbackPlan([...ranked, ...sentVids, ...mustC.where((c) => c.shot.isVideo)], opts,
            theme: recommended, must: sentIds.intersection(must), ranked: true, limit: target);

    final picks = plan.picks;
    // the person's theme (chosen before picking, operator 11-27) wins over the AI's suggestion
    final want = opts.theme ?? plan.theme;
    final theme = await _hasTheme(want) ? want : 'a';
    final book = Book(
      id: newId(),
      from: from,
      to: to,
      opts: opts,
      theme: theme,
      pool: cands,
      picks: picks,
      clips: plan.clips,
      pickLines: plan.chapterLines,
      mustInclude: must,
      cover: plan.covers.firstOrNull ?? _bestPhoto(picks, {for (final c in cands) c.id: c}),
      groups: plan.groups,
    );
    lap('ai');
    await store.save(book); // the answer is kept at once: a crash after this never pays twice
    if (lab != null) {
      final picked = {for (final x in picks) x.id: x};
      await File('${lab.path}/pick.json').writeAsString(jsonEncode({
        'from': ymd(from),
        'to': ymd(to),
        'opts': opts.toJson(),
        'ai': stateOf(r).name,
        'ms': times,
        'counts': {
          'all': all.length, 'measured': measuredIdx.length, 'blurDark': blurDark, 'similar': similar, 'capture': capture,
          'moments': moments.length, 'series': nSeries, 'shortlist': wide.length, 'tagged': tags.length, 'sent': sent.length, 'picked': picks.length,
        },
        'groups': [for (final (k, ids) in plan.groups) {'kind': k, 'ids': ids}],
        'covers': plan.covers,
        'shots': [
          for (final c in cands)
            {
              ...c.toJson(),
              'file': fileId(c.id),
              'shot_ms': c.shot.utc.millisecondsSinceEpoch,
              'screenshot': isScreenshot(c.shot),
              'sent': sentIds.contains(c.id),
              'moment': momentOf[c.id],
              if (series[c.id] != null) 'series': series[c.id],
              'wide': wide.any((x) => x.id == c.id),
              if (tags[c.id] != null) 'tag': tags[c.id]!.toJson(),
              if (picked[c.id] != null) 'pick': picked[c.id]!.toJson(),
            },
        ],
      }));
    }
    onProgress?.call(p.copyWith(picked: picks.length, pickedIds: {for (final x in picks) x.id}, found: [...found]));
    return PickResult(book, plan.ask, stateOf(r));
  }

  // ----- the user's review (each change is saved) -----

  Map<String, Candidate> _pool(Book b) => {for (final c in b.pool) c.id: c};

  /// [chosen] = "a|b" -> kept id; unanswered pairs keep the higher score.
  Future<void> applyPairs(Book b, List<(String, String)> ask, Map<String, String> chosen) async {
    final p = resolveAsk(BookPlan(titles: const [], theme: b.theme, picks: b.picks, ask: ask, clips: b.clips), chosen);
    b
      ..picks = p.picks
      ..clips = p.clips;
    if (!b.picks.any((x) => x.id == b.cover)) b.cover = _bestPhoto(b.picks, _pool(b));
    await store.save(b);
  }

  /// Picked candidates by trip day, time order.
  List<(DateTime, List<Candidate>)> picksByDay(Book b) {
    final pool = _pool(b);
    final out = <String, List<Candidate>>{};
    for (final p in b.picks) {
      final c = pool[p.id];
      if (c != null) out.putIfAbsent(tripDay(c.local), () => []).add(c);
    }
    final days = out.keys.toList()..sort();
    return [for (final d in days) (DateTime.parse(d), out[d]!..sort((a, x) => a.local.compareTo(x.local)))];
  }

  /// Unpicked photos of the same day, nearest in time first (same place before others).
  List<Candidate> alternatives(Book b, String id) {
    final pool = _pool(b), c = pool[id];
    if (c == null) return [];
    final picked = {for (final p in b.picks) p.id};
    final day = tripDay(c.local);
    final xs = b.pool.where((x) => !picked.contains(x.id) && !x.shot.isVideo && !isBad(x) && !isScreenshot(x.shot) && tripDay(x.local) == day).toList();
    int rank(Candidate x) => (x.place != null && x.place == c.place) ? 0 : 1;
    xs.sort((a, x) {
      final r = rank(a) - rank(x);
      return r != 0 ? r : a.local.difference(c.local).abs().compareTo(x.local.difference(c.local).abs());
    });
    return xs;
  }

  Future<void> swap(Book b, String oldId, String newId) async {
    b.picks = [for (final p in b.picks) p.id == oldId ? Pick(newId, '', p.score) : p];
    if (b.cover == oldId) b.cover = newId;
    b.mustInclude.remove(oldId);
    await store.save(b);
  }

  Future<void> remove(Book b, String id) async {
    b.picks = [for (final p in b.picks) if (p.id != id) p];
    b.clips = [for (final c in b.clips) if (c.id != id) c];
    b.mustInclude.remove(id);
    if (b.cover == id) b.cover = _bestPhoto(b.picks, _pool(b));
    await store.save(b);
  }

  Future<void> setCover(Book b, String id) async {
    b.cover = id;
    await store.save(b);
  }

  /// [+] on a day: the sharpest unpicked usable photo of that day. False when the book is full or the day has none.
  Future<bool> addOne(Book b, DateTime day) async {
    if (b.picks.length >= maxBookImages) return false;
    final picked = {for (final p in b.picks) p.id};
    final d = ymd(day);
    final xs = b.pool.where((x) => !picked.contains(x.id) && !x.shot.isVideo && !isBad(x) && !isScreenshot(x.shot) && tripDay(x.local) == d).toList()
      ..sort((a, x) => x.sharp.compareTo(a.sharp));
    if (xs.isEmpty) return false;
    b.picks = [...b.picks, Pick(xs.first.id, '', 50)];
    await store.save(b);
    return true;
  }

  // ----- /layout (the AI editor) -----

  /// The AI editor lays out the whole book from the theme's designer layouts (order, layout per page, photos, texts).
  /// The work runs as a server job (b.job, saved at once): closing the app does not lose it, and calling this again
  /// after a restart only waits for the same job (no second model call, no second charge).
  /// One more job when checkLayout finds problems; whatever comes back is repaired into a usable book.
  /// A failure leaves the book at "picked" with its picks untouched: [다시 시도] calls this again (no new /pick).
  Future<AiState> write(Book b, {List<NoteInput> notes = const []}) async {
    final pool = _pool(b);
    final picked = [for (final p in b.picks) pool[p.id]].whereType<Candidate>().toList();
    final defs = await _layouts(b.theme);
    final theme = layoutRules.containsKey(b.theme) ? b.theme : 'a';
    final rules = layoutRules[theme]!;
    final byId = {for (final c in picked) c.id: c};
    final tags = {for (final p in b.picks) p.id: p};
    // /pick's page groups go to the editor as "연속 1", "음식 1": one page each
    final groupOf = <String, String>{};
    for (final (i, (k, ids)) in b.groups.indexed) {
      for (final id in ids) {
        groupOf.putIfAbsent(id, () => '$k ${i + 1}');
      }
    }
    final shots = [
      for (final c in picked)
        AiShot(c.id, _localIso(c.local), c.place,
            video: c.shot.isVideo,
            durMs: c.shot.durationMs,
            w: c.shot.w,
            h: c.shot.h,
            kind: tags[c.id]?.kind ?? '',
            what: tags[c.id]?.what ?? '',
            group: groupOf[c.id] ?? ''),
    ];
    final scenes = scenesOf(picked);
    final order = sceneIndex(scenes);
    final kinds = {for (final p in b.picks) p.id: p.kind};
    List<AiImage>? images;
    Future<List<AiImage>> previews() async {
      if (images != null) return images!;
      final out = <AiImage>[];
      for (final c in picked) {
        if (c.shot.isVideo) {
          final fs = await src.videoFrames(c.id); // one scene per video
          if (fs.isNotEmpty) out.add(AiImage(c.id, fs[fs.length > 1 ? 1 : 0]));
        } else {
          final j = await src.jpeg(c.id, 512);
          if (j != null) out.add(AiImage(c.id, j));
        }
      }
      return images = out;
    }

    Future<AiResult> run({List<String>? fix, Map<String, dynamic>? prev}) => _job(
        b,
        (job) async => ai.layout(await previews(), shots,
            job: job, theme: theme, catalog: layoutCatalog(defs), rules: rules.toJson(picked.length), scenes: outlineJson(scenes), notes: _notes(notes),
            opts: b.opts.toJson(), fix: fix, prev: prev));
    final r = await run();
    if (r is! AiOk) return r is AiOff ? AiState.off : AiState.failed;
    var body = r.body;
    final errs = checkLayout(body, defs, byId, rules, order: order, kinds: kinds);
    if (errs.isNotEmpty) {
      b.job = null; // the retry is a new job
      final r2 = await run(fix: errs.take(20).toList(), prev: body);
      if (r2 is AiOk && checkLayout(r2.body, defs, byId, rules, order: order, kinds: kinds).length < errs.length) body = r2.body;
    }
    final out = repairLayout(body, defs, byId, fallbackTitle: _defaultTitle(b), coverPhoto: b.cover);
    if (out.pages.length < 2) return AiState.failed;
    final used = {for (final p in out.pages) ...p.ids};
    if (b.title.isEmpty) b.title = out.title;
    b
      ..pages = out.pages
      ..original = out.pages
      ..spare = [for (final p in b.picks) if (!used.contains(p.id)) p.id]
      ..stage = 'written';
    await _copyMedia(b, used);
    b.job = null;
    await store.save(b);
    return AiState.ok;
  }

  /// Start a layout job (unless b.job already names one) and wait for its plan. The job name is saved before the call,
  /// so an app closed mid-way finds it again. Failed / timed out -> the name is dropped and the next try starts afresh.
  Future<AiResult> _job(Book b, Future<AiResult> Function(String job) start) async {
    AiResult r;
    if (b.job == null) {
      b.job = _jobName(); // unguessable: the job name is the only key to its result (the server checks nothing else)
      await store.save(b);
      r = await start(b.job!);
    } else {
      r = await ai.result(b.job!);
    }
    final deadline = DateTime.now().add(const Duration(minutes: 15));
    while (r is AiOk && r.body['status'] == 'pending' && DateTime.now().isBefore(deadline)) {
      await Future<void>.delayed(poll);
      r = await ai.result(b.job!);
    }
    if (r is! AiOk || r.body['pages'] is! List) {
      b.job = null; // nothing usable: the next [다시 시도] starts a new job
      await store.save(b);
      return r is AiOk ? const AiFailed('timeout') : r;
    }
    return r;
  }

  /// Pages + image/video files. Also used for a book without text ([글 없이 만들기] / AI off).
  Future<void> finish(Book b) async {
    final pool = _pool(b);
    final text = b.texts[shapeOf(b.theme)];
    if (b.title.isEmpty) b.title = (text?.titles.firstWhere((t) => t.isNotEmpty, orElse: () => '') ?? '').isNotEmpty ? text!.titles.first : _defaultTitle(b);
    final picks = [
      for (final p in b.picks) Pick(p.id, text?.captions[p.id] ?? '', p.id == b.cover ? 1000 : p.score), // the cover photo ranks first
    ];
    final picked = [for (final p in picks) pool[p.id]].whereType<Candidate>().toList();
    final chapters = b.opts.chapterBy == 'place' ? byPlace(picked) : byDay(picked);
    final plan = BookPlan(
      titles: text?.titles ?? const [],
      theme: b.theme,
      picks: picks,
      chapterLines: {...b.pickLines, ...?text?.chapterLines},
      essay: text?.essay ?? '',
      clips: b.clips,
      aiText: text != null,
    );
    final defs = await _layouts(b.theme);
    final out = paginate(plan, chapters, b.opts, defs, cover: '', title: b.title);
    b
      ..pages = out.pages
      ..original = out.pages
      ..spare = out.spare;
    await _copyMedia(b, {for (final pg in out.pages) ...pg.ids});
    await store.save(b);
  }

  Future<bool> _hasTheme(String t) async {
    try {
      await layoutsJson(t);
      return true;
    } catch (_) {
      return false;
    }
  }

  /// The theme's page designs (다듬기 swaps cover / closes up a page with them).
  Future<List<LayoutDef>> layouts(String theme) => _layouts(theme);

  /// 다듬기 put new photos on pages: their files must sit in the book's folder before the WebView shows them.
  Future<void> copyMedia(Book b, Set<String> ids) => _copyMedia(b, ids);

  Future<List<LayoutDef>> _layouts(String theme) async {
    String raw;
    try {
      raw = await layoutsJson(theme);
    } catch (_) {
      raw = await layoutsJson('a'); // M1 ships theme a only
    }
    return [for (final j in jsonDecode(raw) as List) LayoutDef.fromJson(j as Map<String, dynamic>)];
  }

  Future<void> _copyMedia(Book b, Set<String> ids) async {
    final dir = store.dir(b.id);
    final long = b.opts.quality == 'original' ? 4000 : 1600;
    final pool = _pool(b);
    for (final id in ids) {
      final c = pool[id];
      if (c == null) continue;
      final f = File('${dir.path}/img/${fileId(id)}.jpg');
      if (!await f.exists()) {
        final bytes = c.shot.isVideo ? (await src.videoFrames(id)).firstOrNull : await src.jpeg(id, long, quality: 90);
        if (bytes != null) {
          await f.parent.create(recursive: true);
          await f.writeAsBytes(bytes, flush: true);
        }
      }
      if (c.shot.isVideo) {
        final clip = b.clips.where((x) => x.id == id).firstOrNull;
        final v = File('${dir.path}/vid/${fileId(id)}.mp4');
        if (clip != null && !await v.exists()) {
          await v.parent.create(recursive: true);
          await src.trim(id, clip.startMs, clip.endMs, v.path);
        }
      }
    }
  }

  // ----- after the book is made -----

  /// [글 다시 쓰기] for one page's first caption. False when the 20 are spent or the AI did not answer.
  Future<AiState?> rewritePage(Book b, int page, {int max = 20, List<NoteInput> notes = const []}) async {
    if (b.rewrites >= max || page < 0 || page >= b.pages.length) return null;
    final spec = b.pages[page];
    final c = spec.ids.isEmpty ? null : _pool(b)[spec.ids.first];
    if (c == null) return null;
    final shape = shapeOf(b.theme);
    final j = c.shot.isVideo ? (await src.videoFrames(c.id)).firstOrNull : await src.jpeg(c.id, 512);
    final r = await ai.rewrite([if (j != null) AiImage(c.id, j)],
        {'local': _localIso(c.local), 'place': c.place ?? '', 'caption': spec.text[_captionKey(spec)] ?? ''}, shape,
        notes: _notes(notes));
    final cap = r is AiOk && r.body['caption'] is String ? fitText(r.body['caption'] as String, shape) : null;
    if (cap == null) return r is AiOff ? AiState.off : AiState.failed;
    b.rewrites++;
    spec.text[_captionKey(spec)] = cap;
    final t = b.texts[shape];
    if (t != null) t.captions[c.id] = cap;
    await store.save(b);
    return AiState.ok;
  }

  /// [사진 바꾸기] on a page: the new photo takes the first slot; other shapes' saved text no longer matches it.
  Future<void> swapOnPage(Book b, int page, String newId) async {
    final spec = b.pages[page];
    if (spec.ids.isEmpty) return;
    final old = spec.ids.first;
    b.pages[page] = PageSpec(spec.layout, [newId, ...spec.ids.skip(1)], crop: spec.crop, text: {...spec.text, 'caption0': ''});
    b.picks = [for (final p in b.picks) p.id == old ? Pick(newId, '', p.score) : p];
    b.spare = [for (final s in b.spare) if (s != newId) s];
    b.texts.removeWhere((k, _) => k != shapeOf(b.theme));
    await _copyMedia(b, {newId});
    await store.save(b);
  }

  // ----- helpers -----

  /// 128 random bits, base64url (22 chars) behind a "j": a job name nobody can guess.
  static String _jobName() {
    final r = math.Random.secure();
    return 'j${base64Url.encode(List<int>.generate(16, (_) => r.nextInt(256))).replaceAll('=', '')}';
  }

  /// The page's own words to rewrite: its first filled text slot that is not a heading or a date.
  String _captionKey(PageSpec p) =>
      p.text.entries.where((e) => e.value.isNotEmpty && !const {'title', 'period', 'stamp', 'colophon', 'dayline', 'day', 'date'}.contains(e.key)).firstOrNull?.key ??
      'caption0';

  AiShot _aiShot(Candidate c, {String tag = '', int people = 0, int busy = 0, int series = 0, int scene = -1}) => AiShot(
      c.id, _localIso(c.local), c.place,
      video: c.shot.isVideo, durMs: c.shot.durationMs, w: c.shot.w, h: c.shot.h, tag: tag, people: people, busy: busy, series: series, scene: scene);

  List<Map<String, String?>> _notes(List<NoteInput> ns) =>
      [for (final p in trimForAi([for (final n in ns) ...n.pieces])) {'day': p.day, 'text': p.text}];

  String? _bestPhoto(List<Pick> picks, Map<String, Candidate> pool) {
    final xs = picks.where((p) => !(pool[p.id]?.shot.isVideo ?? true)).toList()..sort((a, b) => b.score.compareTo(a.score));
    return xs.firstOrNull?.id;
  }

  String _defaultTitle(Book b) {
    final n = <String, int>{};
    final pool = _pool(b);
    for (final p in b.picks) {
      final pl = pool[p.id]?.place;
      if (pl != null) n[pl] = (n[pl] ?? 0) + 1;
    }
    final where = n.isEmpty ? '여행' : (n.entries.toList()..sort((a, x) => x.value.compareTo(a.value))).first.key;
    final d = b.to.difference(b.from).inDays + 1;
    return d == 1 ? '$where 당일' : '$where ${d - 1}박 $d일';
  }
}

/// A top-level function so the isolate closure captures only the thumbnails.
Future<List<({double sharp, double luma, int dhash})>> _measureAll(List<Uint8List?> thumbs) =>
    Isolate.run(() => [for (final t in thumbs) _measure(t)]);

/// Decode + measure in the worker isolate. An unreadable thumbnail counts as blurred (it is never sent).
({double sharp, double luma, int dhash}) _measure(Uint8List? jpeg) {
  final im = jpeg == null ? null : img.decodeImage(jpeg);
  if (im == null) return (sharp: 0, luma: 0, dhash: 0);
  return measure(im);
}

/// What book.js renders: {id, theme, format, pages, media:{id:{src, video?}}}; files are served under /books/.
Map<String, Object?> bookJs(Book b, {String base = '/books'}) {
  final pool = {for (final c in b.pool) c.id: c};
  final ids = {for (final p in b.pages) ...p.ids};
  return {
    'id': b.id,
    'theme': b.theme,
    'format': b.opts.format,
    'toneMatch': b.opts.toneMatch,
    'pages': [for (final p in b.pages) p.toJson()],
    'style': {'font': b.font, 'size': textScale[b.textSize] ?? 1.0, 'cover': b.coverColor},
    'media': {
      for (final id in ids)
        id: {
          'src': '$base/${b.id}/img/${fileId(id)}.jpg',
          if ((pool[id]?.shot.isVideo ?? false) && b.clips.any((c) => c.id == id)) 'video': '$base/${b.id}/vid/${fileId(id)}.mp4',
        },
    },
  };
}

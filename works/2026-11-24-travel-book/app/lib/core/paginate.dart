// Picks + chapters -> pages, using the theme's layouts (assets/book/themes/<t>/layouts.json).
// Rules: cover -> per chapter an opener + photo pages -> numbers page; slot orientation must match the photo,
// the same layout never twice in a row, a chapter's first photo page is a one-photo page. No pick is lost:
// what does not fit goes to `spare` (the [사진 바꾸기] pool).
import 'dart:convert';
import 'dart:math' as math;

import 'book_layout.dart';
import 'models.dart';

int recommendPages(int photos, int days) => 2 + days + (photos / 1.8).ceil();

const _lengthFactor = {'short': 0.7, 'normal': 1.0, 'long': 1.4};
const _dayNames = ['첫째', '둘째', '셋째', '넷째', '다섯째', '여섯째', '일곱째', '여덟째', '아홉째', '열째'];
const _weekdays = ['월', '화', '수', '목', '금', '토', '일'];
const _months = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];

String _2(int n) => n.toString().padLeft(2, '0');
String _hm(DateTime t) => '${_2(t.hour)}:${_2(t.minute)}';
String _md(DateTime t) => '${_2(t.month)}.${_2(t.day)}';
DateTime _day(String ymd) => DateTime.parse(ymd);

bool _fits(String slot, Candidate c) => switch (slot) {
      'A' || 'S' => !c.shot.isVideo, // S = near-square slot: any photo, cropped
      'P' => !c.shot.isVideo && c.shot.portrait,
      'L' => !c.shot.isVideo && !c.shot.portrait,
      'V' => c.shot.isVideo,
      _ => false,
    };

/// Straight-line km along consecutive geotagged picks.
double travelKm(List<Candidate> xs) {
  double? lat, lng;
  var km = 0.0;
  for (final c in xs) {
    final a = c.shot.lat, b = c.shot.lng;
    if (a == null || b == null) continue;
    if (lat != null) {
      const r = 6371.0;
      final dLat = (a - lat) * math.pi / 180, dLng = (b - lng!) * math.pi / 180;
      final h = math.pow(math.sin(dLat / 2), 2) + math.cos(lat * math.pi / 180) * math.cos(a * math.pi / 180) * math.pow(math.sin(dLng / 2), 2);
      km += 2 * r * math.asin(math.sqrt(h));
    }
    lat = a;
    lng = b;
  }
  return km;
}

({List<PageSpec> pages, List<String> spare}) paginate(
  BookPlan p,
  List<Chapter> chapters,
  BookOptions o,
  List<LayoutDef> defs, {
  required String cover,
  required String title,
}) {
  final usable = defs.where((d) => d.formats.contains(o.format)).toList();
  LayoutDef? kind(String k) => usable.where((d) => d.kind == k).firstOrNull;
  final photoDefs = usable.where((d) => d.kind == 'photo' && !isSeriesLayout(d)).toList(); // series pages need a /pick group
  final videoDef = o.extras.contains('video') ? kind('video') : null;
  final score = {for (final x in p.picks) x.id: x.score};
  final caption = {for (final x in p.picks) x.id: x.caption};
  final clipIds = {for (final c in p.clips) c.id};

  // picked candidates per chapter, time order; videos without a clip or a video layout are dropped to spare
  final spare = <String>[];
  final chs = <(Chapter, List<Candidate>)>[];
  for (final ch in chapters) {
    final xs = <Candidate>[];
    for (final c in ch.items) {
      if (!score.containsKey(c.id)) continue;
      if (c.shot.isVideo && (videoDef == null || !clipIds.contains(c.id))) {
        spare.add(c.id);
        continue;
      }
      xs.add(c);
    }
    if (xs.isNotEmpty) chs.add((ch, xs..sort((a, b) => a.local.compareTo(b.local))));
  }
  final all = [for (final (_, xs) in chs) ...xs];
  if (all.isEmpty) return (pages: <PageSpec>[], spare: spare);

  final days = chs.map((e) => e.$1.day).toSet().length;
  final first = _day(chs.first.$1.day), last = _day(chs.last.$1.day);
  final nDays = last.difference(first).inDays + 1;
  final nights = nDays == 1 ? '당일' : '${nDays - 1}박 $nDays일';
  final running = '$title $nights'.trim();
  final target = (recommendPages(all.length, days) * (_lengthFactor[o.length] ?? 1.0)).round();

  final pages = <PageSpec>[];
  var folio = 0;
  Map<String, String> foot() => {'folio': '${++folio}', 'running': running};

  // cover: the best-scored photo
  final best = ([...all.where((c) => !c.shot.isVideo)]..sort((a, b) => (score[b.id] ?? 0).compareTo(score[a.id] ?? 0))).firstOrNull;
  // a one-photo cover whose slot fits the photo (designer themes have a P, an L and an S cover)
  final coverDef = usable.where((d) => d.id == cover).firstOrNull ??
      (best == null ? null : usable.where((d) => d.kind == 'cover' && d.slots.length == 1 && _fits(d.slots.first, best)).firstOrNull) ??
      kind('cover');
  if (coverDef != null && best != null) {
    pages.add(PageSpec(coverDef.id, [best.id], text: {
      'period': '${first.year}.${_md(first)} — ${_md(last)}',
      'nights': nights,
      'title': title,
      'title_latin': title,
      'tag': '${_months[first.month - 1]}, ${first.year}',
      'first_day': _md(first),
      'last_day': _md(last),
      'year': '${first.year}',
      'running': running,
    }));
  }

  final fixed = pages.length + chs.length + (o.extras.contains('numbers') && kind('numbers') != null ? 1 : 0);
  var budget = math.max(chs.length, target - fixed);
  var leftPhotos = all.length;
  String? prev;

  for (var ci = 0; ci < chs.length; ci++) {
    final (ch, xs) = chs[ci];
    final dayIndex = _day(ch.day).difference(first).inDays;
    final rest = [...xs];
    final o0 = rest.firstWhere((c) => !c.shot.isVideo, orElse: () => rest.first);
    // a one-photo opener whose slot fits the day's first photo (designer themes have P/L/A openers)
    final opener = usable.where((d) => d.kind == 'chapter' && d.slots.length == 1 && _fits(d.slots.first, o0)).firstOrNull;
    if (opener != null) {
      if (!o0.shot.isVideo) {
        rest.remove(o0);
        final d = _day(ch.day);
        pages.add(PageSpec(opener.id, [o0.id], text: {
          'day_name': '${dayIndex < _dayNames.length ? _dayNames[dayIndex] : '${dayIndex + 1}번째'} 날',
          'date': '${_md(d)} ${_weekdays[d.weekday - 1]}',
          'time0': _hm(o0.local),
          'place0': o0.place ?? '',
          'day_no': _2(dayIndex + 1),
          ...foot(),
        }));
        prev = opener.id;
      }
    }
    // this chapter's share of the remaining photo-page budget
    final share = leftPhotos == 0 ? 1 : math.max(1, (budget * xs.length / leftPhotos).round());
    leftPhotos -= xs.length;
    budget = math.max(0, budget - share);
    var pagesLeft = share;
    var firstPage = true;

    while (rest.isNotEmpty) {
      final head = rest.first;
      if (head.shot.isVideo) {
        rest.removeAt(0);
        pages.add(PageSpec(videoDef!.id, [head.id], text: {
          'time0': _hm(head.local),
          'place0': head.place ?? '',
          'dur': '영상 0:${_2(((p.clips.firstWhere((c) => c.id == head.id).endMs - p.clips.firstWhere((c) => c.id == head.id).startMs) / 1000).round())}',
          ...foot(),
        }));
        prev = videoDef.id;
        continue;
      }
      final want = pagesLeft <= 0 ? 99.0 : rest.where((c) => !c.shot.isVideo).length / pagesLeft;
      (LayoutDef, List<Candidate>)? pick;
      var pickCost = double.infinity;
      for (final d in photoDefs) {
        if (d.id == prev && photoDefs.length > 1) continue;
        if (firstPage && d.slots.length != 1) continue;
        final filled = _fill(d.slots, rest);
        if (filled == null) continue;
        final cost = (d.slots.length - want).abs();
        if (cost < pickCost) {
          pickCost = cost;
          pick = (d, filled);
        }
      }
      if (pick == null && firstPage) {
        firstPage = false; // no one-photo layout fits this photo: try any size
        continue;
      }
      if (pick == null) {
        spare.add(rest.removeAt(0).id); // orientation fits no layout
        continue;
      }
      final (d, used) = pick;
      for (final c in used) {
        rest.remove(c);
      }
      final t = <String, String>{};
      for (var i = 0; i < used.length; i++) {
        t['time$i'] = _hm(used[i].local);
        t['place$i'] = used[i].place ?? '';
        t['caption$i'] = caption[used[i].id] ?? '';
      }
      pages.add(PageSpec(d.id, [for (final c in used) c.id], text: {...t, ...foot()}));
      prev = d.id;
      firstPage = false;
      pagesLeft--;
    }
  }

  final numbers = o.extras.contains('numbers') ? kind('numbers') : null;
  if (numbers != null) {
    final photos = all.where((c) => !c.shot.isVideo).toList();
    final land = photos.where((c) => !c.shot.portrait).toList();
    final pic = (land.isNotEmpty ? land : photos).last;
    final km = travelKm(all);
    final places = all.map((c) => c.place).whereType<String>().toSet();
    final videos = all.where((c) => c.shot.isVideo).length;
    pages.add(PageSpec(numbers.id, [pic.id], text: {
      'n_photos': '사진 ${photos.length}',
      'n_videos': videos > 0 ? '영상 $videos' : '',
      'n_places': places.isNotEmpty ? '${places.length}곳' : '',
      'km': km >= 1 ? '이동 ${km.round()}km' : '',
      'last_line': p.chapterLines[chs.last.$1.day] ?? '',
      'index': jsonEncode([
        for (final (ch, xs) in chs)
          {
            'day': ch.day,
            'stops': [
              for (final c in _firstPerPlace(xs)) {'time': _hm(c.local), 'place': c.place},
            ],
          },
      ]),
      ...foot(),
    }));
  }
  return (pages: pages, spare: spare);
}

/// First shot at each new place (the index lists stops, not every photo).
List<Candidate> _firstPerPlace(List<Candidate> xs) {
  final out = <Candidate>[];
  for (final c in xs) {
    if (c.place == null) continue;
    if (out.isEmpty || out.last.place != c.place) out.add(c);
  }
  return out;
}

/// Fill [slots] from [rest] keeping time order: the first slot takes rest[0] if it fits any slot,
/// the others take the earliest fitting photos within the next few. Null when rest[0] cannot be placed.
List<Candidate>? _fill(List<String> slots, List<Candidate> rest) {
  final window = rest.take(slots.length + 3).where((c) => !c.shot.isVideo).toList();
  if (window.isEmpty || window.first != rest.first) return null;
  final out = List<Candidate?>.filled(slots.length, null);
  final used = <Candidate>{};
  // rest[0] first, into the first slot it fits
  final i0 = slots.indexWhere((s) => _fits(s, rest.first));
  if (i0 < 0) return null;
  out[i0] = rest.first;
  used.add(rest.first);
  for (var i = 0; i < slots.length; i++) {
    if (out[i] != null) continue;
    final c = window.where((c) => !used.contains(c) && _fits(slots[i], c)).firstOrNull;
    if (c == null) return null;
    out[i] = c;
    used.add(c);
  }
  // slot order follows time order where orientation allows: keep as placed
  return out.cast<Candidate>();
}

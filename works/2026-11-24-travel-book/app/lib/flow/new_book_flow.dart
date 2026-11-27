// D3 host: dates (real per-day counts) -> trip scan -> albums -> extras (must-include, notes, timeline) -> pick.
// The book's theme and length are chosen after the photos (making_flow -> choose_theme).
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart' show Clipboard;
import 'package:image/image.dart' as img;
import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';
import 'package:read_pdf_text/read_pdf_text.dart';

import '../core/chapters.dart';
import '../core/models.dart';
import '../core/refdocs.dart';
import '../core/timeline.dart';
import '../device/gallery.dart' as g;
import '../pipeline.dart';
import '../ui/failures.dart';
import '../ui/making/choose_theme.dart';
import '../ui/new_book/albums.dart';
import '../ui/new_book/book_shape.dart';
import '../ui/new_book/dates.dart';
import '../ui/new_book/draft.dart';
import '../ui/new_book/extras.dart';
import '../ui/new_book/must_pick.dart';
import '../ui/new_book/notes.dart';
import '../ui/new_book/timeline_help.dart';
import '../ui/price_sheet.dart';
import '../ui/tokens.dart' show fmtInt;
import '../ui/widgets.dart';
import 'app.dart';
import 'making_flow.dart';

class NewBookFlow extends StatefulWidget {
  final App app;
  final int freeLeft;
  final int? partial; // visible photos under "일부만 허용"
  const NewBookFlow({super.key, required this.app, required this.freeLeft, this.partial});
  @override
  State<NewBookFlow> createState() => _NewBookFlowState();
}

class _NewBookFlowState extends State<NewBookFlow> {
  final _photos = <DateTime, int>{}, _videos = <DateTime, int>{};
  final _loaded = <DateTime>{};
  final _month = DateTime(DateTime.now().year, DateTime.now().month);
  App get app => widget.app;

  @override
  void initState() {
    super.initState();
    _load(_month);
  }

  /// The shown month, then the ones on either side in the background: a swipe lands on counts already read.
  Future<void> _load(DateTime m) async {
    await _read(m);
    for (final o in [-1, 1]) {
      final x = DateTime(m.year, m.month + o);
      if (!x.isAfter(_month)) unawaited(_read(x));
    }
  }

  Future<void> _read(DateTime m) async {
    if (!_loaded.add(m)) return;
    try {
      final c = await app.src.monthCounts(m);
      if (!mounted) return;
      setState(() {
        for (final e in c.entries) {
          _photos[e.key] = e.value.$1;
          _videos[e.key] = e.value.$2;
        }
      });
    } catch (_) {
      _loaded.remove(m); // try again next time the month is shown
    }
  }

  Future<void> _next(DateTime s, DateTime e) async {
    final sw = Stopwatch()..start();
    final albums = await busy(context, () => app.src.scan(s, e));
    if (kLab) {
      // a new trip: the previous lab files go, this scan's time is the first number of the new set
      final d = await labDir(await getTemporaryDirectory());
      if (await d.exists()) await d.delete(recursive: true);
      await d.create(recursive: true);
      await File('${d.path}/scan.json').writeAsString(jsonEncode({'ms': sw.elapsedMilliseconds, 'albums': [for (final a in albums) {'name': a.name, 'inRange': a.inRange.length}]}));
    }
    if (!mounted) return;
    final photos = {for (final a in albums) for (final x in a.inRange) if (!x.isVideo) x.id};
    if (photos.length < 5) return _few(s, e, albums);
    _albums(s, e, albums);
  }

  /// Fewer than 5 photos: count the range widened by a day each side before offering it.
  Future<void> _few(DateTime s, DateTime e, List<g.Album> albums) async {
    final ws = s.subtract(const Duration(days: 1)), we = e.add(const Duration(days: 1));
    final wider = await busy(context, () => app.src.scan(ws, we));
    if (!mounted) return;
    final thumbs = {for (final a in albums) for (final x in a.inRange) if (!x.isVideo) 'gallery:${x.id}'}.toList();
    final n = {for (final a in wider) for (final x in a.inRange) if (!x.isVideo) x.id}.length;
    final nav = Navigator.of(context);
    nav.push(MaterialPageRoute<void>(
      builder: (_) => FewPhotosScreen(
        thumbs: thumbs,
        wider: rangeShort(ws, we),
        widerCount: n,
        onClose: () => nav.popUntil((r) => r.isFirst),
        onWiden: () {
          nav.pop();
          if (n >= 5) _albums(ws, we, wider);
        },
        onRedo: nav.pop,
      ),
    ));
  }

  void _albums(DateTime s, DateTime e, List<g.Album> albums) {
    final nav = Navigator.of(context);
    nav.push(MaterialPageRoute<void>(
      builder: (_) => AlbumsScreen(
        albums: [
          for (final a in albums)
            Album(a.id, a.name, inRange: a.inRange.length, total: a.total, thumb: a.inRange.isEmpty ? null : 'gallery:${a.inRange.first.id}'),
        ],
        onBack: nav.pop,
        onNext: (ids, _) {
          final chosen = albums.where((a) => ids.contains(a.id));
          final shots = {for (final a in chosen) for (final x in a.inRange) x.id: x}.values.toList();
          final draft = NewBookDraft()
            ..start = s
            ..end = e
            ..albums = ids
            ..opts.quality = app.prefs.quality;
          nav.push(MaterialPageRoute<void>(
            builder: (_) => _Extras(
              app: app,
              draft: draft,
              shots: shots,
              inRange: albums.fold(0, (n, a) => n + a.inRange.length),
              freeLeft: widget.freeLeft,
            ),
          ));
        },
      ),
    ));
  }

  @override
  Widget build(BuildContext context) => DatesScreen(
        month: _month,
        photos: _photos,
        videos: _videos,
        partial: widget.partial,
        onOpenSettings: g.openSettings,
        onMonth: _load,
        onClose: Navigator.of(context).pop,
        onNext: _next,
      );
}

class _Extras extends StatefulWidget {
  final App app;
  final NewBookDraft draft;
  final List<Shot> shots;
  final int inRange, freeLeft;
  const _Extras({required this.app, required this.draft, required this.shots, required this.inRange, required this.freeLeft});
  @override
  State<_Extras> createState() => _ExtrasState();
}

class _ExtrasState extends State<_Extras> {
  Timeline? _timeline;
  NewBookDraft get d => widget.draft;
  App get app => widget.app;

  late final List<(DateTime, List<PhotoRef>)> _byDay = () {
    final photos = widget.shots.where((s) => !s.isVideo).toList();
    final m = <String, List<(DateTime, Shot)>>{};
    for (final s in photos) {
      final l = localOf(s, photos);
      m.putIfAbsent(tripDay(l), () => []).add((l, s));
    }
    final days = m.keys.toList()..sort();
    return [
      for (final k in days) (DateTime.parse(k), [for (final (_, s) in m[k]!..sort((a, b) => a.$1.compareTo(b.$1))) PhotoRef(s.id, 'gallery:${s.id}')]),
    ];
  }();

  Future<void> _push(Widget Function(VoidCallback back) screen) async {
    final nav = Navigator.of(context);
    await nav.push(MaterialPageRoute<void>(builder: (_) => screen(nav.pop)));
    if (mounted) setState(() {});
  }

  String _daysLabel(List<RefPiece> ps) {
    final days = ps.map((p) => p.day).nonNulls.toSet().toList()..sort();
    if (days.isEmpty) return '날짜를 못 찾으면 책 전체에 써요';
    final ds = [for (final x in days) DateTime.parse(x)];
    return '${ds.first.month}월 ${[for (final x in ds) '${x.day}일'].join(', ')} 글을 찾았어요';
  }

  NoteItem _textNote(String title, String raw) {
    final pieces = fromText(raw, title, tripStart: d.start!);
    return NoteItem(title, '${fmtInt(raw.length)}자 · ${_daysLabel(pieces)}', input: NoteInput(pieces: pieces));
  }

  Future<NoteItem?> _paste() async {
    final t = (await Clipboard.getData('text/plain'))?.text?.trim();
    return t == null || t.isEmpty ? null : _textNote('붙여 넣은 글', t);
  }

  Future<NoteItem?> _file() async {
    final f = (await FilePicker.pickFiles()).firstOrNull;
    final path = f?.path;
    if (f == null || path == null) return null;
    final ext = f.name.split('.').last.toLowerCase();
    try {
      final raw = switch (ext) {
        'txt' || 'md' => await File(path).readAsString(),
        'html' || 'htm' => htmlToText(await File(path).readAsString()),
        'pdf' => await ReadPdfText.getPDFtext(path),
        _ => null,
      };
      if (raw != null && raw.trim().isNotEmpty) return _textNote(f.name, raw);
    } catch (_) {}
    return NoteItem(f.name, '이 파일만 빼고 만들어요. txt · md · html · pdf · 사진은 읽을 수 있어요', bad: true);
  }

  Future<NoteItem?> _handwriting() async {
    final f = (await FilePicker.pickFiles(type: FileType.image)).firstOrNull;
    final path = f?.path;
    if (f == null || path == null) return null;
    final im = img.decodeImage(await File(path).readAsBytes());
    if (im == null) return NoteItem(f.name, '이 파일만 빼고 만들어요. txt · md · html · pdf · 사진은 읽을 수 있어요', bad: true);
    final small = im.width >= im.height ? img.copyResize(im, width: 1024) : img.copyResize(im, height: 1024);
    return NoteItem('손글씨 사진 1장', '글 쓸 때 읽어요 · 날짜를 못 찾으면 책 전체에 써요',
        thumb: path, input: NoteInput(image: img.encodeJpg(small, quality: 85)));
  }

  void _notes() => _push((back) => StatefulBuilder(builder: (_, set) {
        Future<void> add(Future<NoteItem?> Function() f) async {
          final n = await f();
          if (n != null) set(() => d.notes = [...d.notes, n]);
        }

        return NotesScreen(
          items: d.notes,
          onBack: back,
          onPaste: () => add(_paste),
          onFile: () => add(_file),
          onHandwriting: () => add(_handwriting),
          onTapItem: (i) => set(() => d.notes = [...d.notes]..removeAt(i)),
          onDone: back,
        );
      }));

  Future<void> _pickTimeline() async {
    final nav = Navigator.of(context);
    final f = (await FilePicker.pickFiles()).firstOrNull;
    if (f?.path == null) return;
    Timeline? t;
    try {
      t = parseTimeline(await File(f!.path!).readAsString());
    } catch (_) {}
    final from = d.start!, to = d.end!.add(const Duration(days: 1));
    final hit = t?.visits.where((v) => v.end.isAfter(from) && v.start.isBefore(to)).isNotEmpty ?? false;
    if (hit) {
      _timeline = t;
      d.timeline = f!.path;
      nav.pop();
      return;
    }
    final noGps = widget.shots.where((s) => !s.isVideo && s.lat == null).length;
    nav.push(MaterialPageRoute<void>(
      builder: (_) => TimelineFailScreen(
        file: f!.name,
        range: rangeShort(d.start!, d.end!),
        noGps: noGps,
        onBack: nav.pop,
        onContinue: () => nav
          ..pop()
          ..pop(),
        onOther: () {
          nav.pop();
          _pickTimeline();
        },
      ),
    ));
  }

  Future<void> _make() async {
    final paid = widget.freeLeft == 0;
    if (paid) {
      final go = await showSheet<bool>(
        context,
        (c) => PriceSheet(
          line: rangeShort(d.start!, d.end!),
          onLater: () => Navigator.of(c).pop(false),
          onMake: () => Navigator.of(c).pop(true),
        ),
      );
      if (go != true || !mounted) return;
    }
    // 어떤 책으로 만들까요? comes before the picking (operator 11-27): the length sets how many photos are picked
    // and the people share moves the mix, so the picker has to know them first. The theme must be chosen.
    final nav = Navigator.of(context), photos = widget.shots.where((s) => !s.isVideo).toList();
    final sample = [for (var k = 0; k < 8 && photos.isNotEmpty; k++) photos[k * photos.length ~/ 8]];
    // the recommendation reads "how many photos will the book have": about 1 in 25 shots, 5..60
    final est = (photos.length ~/ 25).clamp(5, 60), tall = photos.where((s) => s.portrait).length;
    final stats = (
      photos: est,
      portrait: photos.isEmpty ? 0 : tall * est ~/ photos.length,
      days: d.end!.difference(d.start!).inDays + 1,
      notes: d.notes.any((n) => !n.bad && n.input != null),
    );
    final rec = stats.notes ? 'diary' : 'a';
    Future<void> start() async {
      d.opts.theme ??= rec;
      nav.pushReplacement(MaterialPageRoute<void>(builder: (_) => _making(paid)));
    }

    nav.push(MaterialPageRoute<void>(
      builder: (_) => ChooseThemeScreen(
        draft: d,
        recommended: rec,
        photos: [for (final s in sample) 'gallery:${s.id}'],
        title: '${d.start!.month}월의 여행',
        stats: stats,
        lead: '이 여행 사진으로 미리 만들어 봤어요. 고른 모양과 길이에 맞춰 사진을 골라요.',
        action: '이 모양으로 사진 고르기',
        onBack: nav.pop,
        onMake: start,
        onMore: () => nav.push(MaterialPageRoute<void>(
          builder: (_) => BookShapeScreen(
            draft: d,
            recommended: d.opts.theme ?? rec,
            reason: '고른 앨범에 맞춰',
            photos: stats.photos, // about how many go in the book, not the trip's whole count
            days: stats.days,
            videos: widget.shots.length - photos.length,
            placesHint: '',
            onBack: nav.pop,
            onMake: () {
              nav.pop();
              start();
            },
          ),
        )),
      ),
    ));
  }

  Widget _making(bool paid) {
    final chosen = widget.shots.length;
    return MakingFlow(
        app: app,
        draft: d,
        shots: widget.shots,
        inRange: widget.inRange,
        albumPhotos: chosen,
        timeline: _timeline,
        paid: paid,
        freeLeftBefore: widget.freeLeft,
      );
  }

  @override
  Widget build(BuildContext context) => ExtrasScreen(
        draft: d,
        photos: widget.shots.length,
        mustThumbs: [for (final id in d.mustInclude) 'gallery:$id'],
        freeLeft: widget.freeLeft,
        onBack: Navigator.of(context).pop,
        onMust: () => _push((back) => MustPickScreen(days: _byDay, draft: d, onDone: back)),
        onNotes: _notes,
        onTimeline: () => _push((back) => TimelineHelpScreen(
              range: rangeShort(d.start!, d.end!),
              onBack: back,
              onPickFile: _pickTimeline,
              onOpenSettings: g.openSettings, // ponytail: app settings; the OS location screen needs a channel call
            )),
        onMake: _make,
      );
}

// D4 host: /pick with live numbers -> pairs -> review -> /write -> the book. Also resumes a "picked" book.
import 'dart:async';

import 'package:material_ui/material_ui.dart';

import '../core/book.dart';
import '../core/library.dart' show freeBooks;
import '../core/models.dart';
import '../core/timeline.dart';
import '../device/media_channel.dart' as mc;
import '../pipeline.dart';
import '../ui/making/pairs.dart';
import '../ui/making/picking.dart';
import '../ui/making/picks_review.dart';
import '../ui/making/swap_sheet.dart';
import '../ui/making/writing.dart';
import '../ui/new_book/book_shape.dart';
import '../ui/new_book/draft.dart';
import '../ui/tokens.dart';
import '../ui/widgets.dart';
import 'app.dart';
import 'book_screen.dart';

const _amount = {'photos': '사진만', 'caption': '짧은 캡션', 'caption_essay': '캡션 + 에세이'};
const _tones = {'kind': '다정한 존댓말', 'humor': '유머'};

class MakingFlow extends StatefulWidget {
  final App app;
  final NewBookDraft? draft; // null when resuming [book]
  final Book? book;
  final List<Shot> shots;
  final int inRange, albumPhotos;
  final Timeline? timeline;
  final bool paid;
  final int freeLeftBefore;
  const MakingFlow({
    super.key,
    required this.app,
    this.draft,
    this.book,
    this.shots = const [],
    this.inRange = 0,
    this.albumPhotos = 0,
    this.timeline,
    this.paid = false,
    this.freeLeftBefore = freeBooks,
  });
  @override
  State<MakingFlow> createState() => _MakingFlowState();
}

enum _Phase { picking, pairs, review, writing }

class _MakingFlowState extends State<MakingFlow> {
  late var _phase = widget.book == null ? _Phase.picking : _Phase.review;
  PickProgress? _p;
  late Book? _book = widget.book;
  List<(String, String)> _ask = [];
  Timer? _tick;
  int _page = 0;

  App get app => widget.app;
  List<NoteInput> get _notes => [for (final n in widget.draft?.notes ?? const <NoteItem>[]) if (!n.bad && n.input != null) n.input!];

  @override
  void initState() {
    super.initState();
    if (widget.book == null) _pick();
  }

  @override
  void dispose() {
    _tick?.cancel();
    mc.keepScreenOn(false).catchError((_) {});
    super.dispose();
  }

  Future<void> _pick() async {
    final d = widget.draft!;
    unawaited(mc.keepScreenOn(true).catchError((_) {}));
    final r = await app.pipe.pick(
      from: d.start!,
      to: d.end!,
      shots: widget.shots,
      inRange: widget.inRange,
      albums: d.albums.length,
      opts: d.opts,
      must: d.mustInclude,
      notes: _notes,
      timeline: widget.timeline,
      onProgress: (p) => mounted ? setState(() => _p = p) : null,
    );
    if (!mounted) return;
    _book = r.book;
    if (r.ai != AiState.ok) {
      // the picker did not answer: a book from the phone's own choice, without text, plus the banner
      await app.pipe.finish(r.book);
      _open(failed: r.ai);
      return;
    }
    await Future<void>.delayed(const Duration(milliseconds: 700)); // let "38" be seen
    if (!mounted) return;
    setState(() {
      _ask = r.ask;
      _phase = _ask.isEmpty ? _Phase.review : _Phase.pairs;
    });
  }

  Future<void> _write() async {
    final b = _book!;
    final pages = lengthPages[b.opts.length] ?? 24;
    setState(() {
      _phase = _Phase.writing;
      _page = 0;
    });
    unawaited(mc.keepScreenOn(true).catchError((_) {}));
    // /write is one call: the page count is a time estimate (about 20 s), it never claims the last page early
    _tick = Timer.periodic(const Duration(milliseconds: 800), (_) => setState(() => _page = (_page + 1).clamp(0, pages - 1)));
    final r = await app.pipe.write(b, notes: _notes);
    _tick?.cancel();
    if (!mounted) return;
    if (r != AiState.ok) await app.pipe.finish(b); // pages without text; [다시 시도] on the book
    _open(failed: r == AiState.ok ? null : r);
  }

  void _open({AiState? failed}) {
    final n = freeBooks - widget.freeLeftBefore + 1;
    final tip = failed != null
        ? null
        : widget.paid || n > freeBooks
            ? '책장에 넣었어요.'
            : '책장에 넣었어요. 이번 책은 무료예요 ($freeBooks권 중 ${n == 1 ? '첫' : '두'} 번째).';
    // The new-book steps are done: back from the book goes to the shelf, not to step 3 (emulator run 2026-11-26).
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => BookScreen(app: app, book: _book!, failed: failed, tip: tip, notes: _notes)),
      (r) => r.isFirst,
    );
  }

  Candidate? _cand(String id) => _book!.pool.where((c) => c.id == id).firstOrNull;

  void _tapPhoto(PhotoRef p) {
    final b = _book!, c = _cand(p.id);
    if (c == null) return;
    final alts = app.pipe.alternatives(b, p.id);
    String hm(DateTime t) => '${two(t.hour)}:${two(t.minute)}';
    showSheet<void>(
      context,
      (sc) => SwapSheet(
        photo: p,
        place: c.place ?? '',
        when: whenOf(c.local),
        alts: [for (final a in alts.take(3)) SwapAlt(PhotoRef(a.id, 'gallery:${a.id}'), a.place == c.place ? '같은 곳' : '같은 날', hm(a.local))],
        samePlace: alts.length,
        isCover: b.cover == p.id,
        onSwap: (a) async {
          Navigator.of(sc).pop();
          await app.pipe.swap(b, p.id, a.photo.id);
          setState(() {});
        },
        onMore: () {}, // ponytail: first 3 only; the full grid comes with 다듬기 (M2)
        onRemove: () async {
          Navigator.of(sc).pop();
          await app.pipe.remove(b, p.id);
          setState(() {});
        },
        onCover: () async {
          Navigator.of(sc).pop();
          await app.pipe.setCover(b, p.id);
          setState(() {});
        },
      ),
    );
  }

  Future<void> _shape() async {
    final b = _book!;
    final d = widget.draft ?? (NewBookDraft()
      ..start = b.from
      ..end = b.to
      ..opts = b.opts);
    final nav = Navigator.of(context);
    await nav.push(MaterialPageRoute<void>(
      builder: (_) => BookShapeScreen(
        draft: d,
        recommended: b.theme,
        reason: '고른 사진에 맞춰',
        photos: b.pool.length,
        days: b.to.difference(b.from).inDays + 1,
        videos: b.pool.where((c) => c.shot.isVideo).length,
        placesHint: (b.pool.map((c) => c.place).nonNulls.toSet().take(3).join(' · ')),
        onBack: nav.pop,
        onMake: () {
          nav.pop();
          _write();
        },
      ),
    ));
    b
      ..opts = d.opts
      ..theme = d.opts.theme ?? b.theme;
    await app.store.save(b);
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final nav = Navigator.of(context);
    switch (_phase) {
      case _Phase.picking:
        final p = _p;
        return PickingScreen(
          thumbs: [for (final (id, kept) in p?.strip ?? const <(String, bool)>[]) ('gallery:$id', kept)],
          inRange: p?.inRange ?? widget.inRange,
          albums: p?.albums ?? widget.draft!.albums.length,
          albumPhotos: p?.albumPhotos ?? widget.albumPhotos,
          filtered: (blurDark: p?.blurDark ?? 0, similar: p?.similar ?? 0, capture: p?.capture ?? 0),
          sent: p?.sent ?? 0,
          picked: p?.picked,
          must: p?.must ?? 0,
          onClose: nav.pop,
        );
      case _Phase.pairs:
        String hm(Candidate? c) => c == null ? '' : '${two(c.local.hour)}:${two(c.local.minute)}';
        return PairsScreen(
          pairs: [
            for (final (a, x) in _ask)
              PairAsk(PhotoRef(a, 'gallery:$a'), PhotoRef(x, 'gallery:$x'),
                  aTime: hm(_cand(a)),
                  bTime: hm(_cand(x)),
                  label: [_cand(a)?.place, if (_cand(a) != null) '${_cand(a)!.local.month}월 ${_cand(a)!.local.day}일'].nonNulls.join(' · ')),
          ],
          onBack: nav.pop,
          onLeaveToApp: () async {
            await app.pipe.applyPairs(_book!, _ask, const {});
            setState(() => _phase = _Phase.review);
          },
          onDone: (kept) async {
            await app.pipe.applyPairs(_book!, _ask, {for (final (i, (a, x)) in _ask.indexed) '$a|$x': kept[i]});
            setState(() => _phase = _Phase.review);
          },
        );
      case _Phase.review:
        final b = _book!;
        final days = app.pipe.picksByDay(b);
        final o = b.opts;
        return PicksReviewScreen(
          days: [
            for (final (d, cs) in days) (d, [for (final c in cs) if (!c.shot.isVideo) PhotoRef(c.id, 'gallery:${c.id}')]),
          ],
          must: b.mustInclude,
          coverId: b.cover,
          from: widget.albumPhotos > 0 ? widget.albumPhotos : b.pool.length,
          videos: [for (final (_, cs) in days) ...cs].where((c) => c.shot.isVideo).length,
          shapeLine: '${themeNames[b.theme] ?? ''} · ${lengthNames[o.length] ?? ''} · ${_amount[o.textAmount] ?? ''}',
          pages: lengthPages[o.length] ?? 24,
          price: widget.paid ? '1,500원' : null,
          onTapPhoto: _tapPhoto,
          onAdd: (day) async {
            await app.pipe.addOne(b, day);
            setState(() {});
          },
          onBack: nav.pop, // the book stays "picked" on the shelf and resumes here
          onShape: _shape,
          onMake: _write,
        );
      case _Phase.writing:
        final b = _book!;
        final first = b.picks.map((p) => _cand(p.id)).nonNulls.where((c) => !c.shot.isVideo).firstOrNull;
        return WritingScreen(
          photo: first == null ? 'assets/covers/a.jpg' : 'gallery:${first.id}',
          meta: first == null ? '' : [hmOf(first.local), first.place].nonNulls.join(' · '),
          typing: '',
          picked: b.picks.length,
          page: _page,
          pages: lengthPages[b.opts.length] ?? 24,
          tone: _tones[b.opts.tone] ?? '담담한 일기체',
          theme: themeNames[b.theme] ?? '',
          onClose: nav.pop,
        );
    }
  }
}

String hmOf(DateTime t) => '${two(t.hour)}:${two(t.minute)}';

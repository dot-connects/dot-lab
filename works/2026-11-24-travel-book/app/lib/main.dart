import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';

import 'core/library.dart';
import 'core/prefs.dart';
import 'ui/book_view.dart';
import 'ui/making/pairs.dart';
import 'ui/making/picking.dart';
import 'ui/making/picks_review.dart';
import 'ui/making/swap_sheet.dart';
import 'ui/making/writing.dart';
import 'ui/new_book/albums.dart';
import 'ui/new_book/book_shape.dart';
import 'ui/new_book/dates.dart';
import 'ui/new_book/draft.dart';
import 'ui/new_book/extras.dart';
import 'ui/new_book/must_pick.dart';
import 'ui/new_book/notes.dart';
import 'ui/new_book/timeline_help.dart';
import 'ui/onboarding.dart';
import 'ui/price_sheet.dart';
import 'ui/sample.dart';
import 'ui/settings.dart';
import 'ui/shelf.dart';
import 'ui/tokens.dart';
import 'ui/widgets.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = Prefs.load(await getApplicationDocumentsDirectory());
  runApp(TripbookApp(prefs: prefs));
}

class TripbookApp extends StatelessWidget {
  final Prefs prefs;
  const TripbookApp({super.key, required this.prefs});
  @override
  Widget build(BuildContext context) =>
      MaterialApp(title: '여행 한 권', debugShowCheckedModeBanner: false, theme: appTheme(), home: _Root(prefs));
}

// Wiring with FAKE data (ui/sample.dart) until the gallery and the pipeline land.
class _Root extends StatefulWidget {
  final Prefs prefs;
  const _Root(this.prefs);
  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  bool _onboarded = false;
  final _books = [...sampleBooks];
  Prefs get p => widget.prefs;
  ShelfView get _view => p.shelfView == 'timeline' ? ShelfView.timeline : ShelfView.display;

  void _setView(ShelfView v) => setState(() {
        p
          ..shelfView = v == ShelfView.timeline ? 'timeline' : 'display'
          ..save();
      });

  void _settings() => Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (c) => StatefulBuilder(
          builder: (c, set) => SettingsScreen(
            view: _view,
            quality: p.quality,
            books: _books.length,
            freeLeft: freeLeft(_books),
            access: '모두 허용 · 위치 읽기 켜짐',
            version: '0.1.0',
            onView: (v) => set(() => _setView(v)),
            onQuality: (q) => set(() => p
              ..quality = q
              ..save()),
            onBack: Navigator.of(c).pop,
            onAccess: () {},
            onTerms: () {},
          ),
        ),
      ));

  @override
  Widget build(BuildContext context) {
    if (!_onboarded) return Onboarding(onAllow: () => setState(() => _onboarded = true));
    void none(ShelfBook _) {}
    return Shelf(
      books: _books,
      view: _view,
      onViewChanged: _setView,
      onOpen: (b) => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => _Book(title: b.title))),
      onExport: none,
      onRename: none,
      onDelete: (b) => setState(() => _books.remove(b)),
      onSettings: _settings,
      onNew: () => Navigator.of(context).push(MaterialPageRoute<void>(
            builder: (_) => _NewBook(
              freeLeft: freeLeft(_books),
              quality: p.quality,
              onMade: (b) => setState(() => _books.insert(0, b)),
            ),
          )),
    );
  }
}

class _NewBook extends StatelessWidget {
  final int freeLeft;
  final String quality;
  final ValueChanged<ShelfBook> onMade;
  const _NewBook({required this.freeLeft, required this.quality, required this.onMade});
  @override
  Widget build(BuildContext context) {
    final draft = NewBookDraft()..opts.quality = quality;
    final nav = Navigator.of(context);
    return DatesScreen(
      month: sampleMonth,
      photos: samplePhotosPerDay,
      videos: sampleVideosPerDay,
      start: DateTime(2026, 6, 12),
      end: DateTime(2026, 6, 14),
      onClose: nav.pop,
      onNext: (s, e) {
        draft
          ..start = s
          ..end = e;
        nav.push(MaterialPageRoute<void>(
          builder: (_) => AlbumsScreen(
            albums: sampleAlbums,
            onBack: nav.pop,
            onNext: (ids, n) {
              draft.albums = ids;
              nav.push(MaterialPageRoute<void>(builder: (_) => _Extras(draft: draft, photos: n, freeLeft: freeLeft, onMade: onMade)));
            },
          ),
        ));
      },
    );
  }
}

Widget _shapeScreen(NewBookDraft d, int photos, {required VoidCallback onBack, required VoidCallback onMake}) => BookShapeScreen(
      draft: d,
      recommended: 'a',
      reason: '풍경 사진이 많아서',
      photos: photos,
      days: d.end!.difference(d.start!).inDays + 1,
      videos: 9,
      placesHint: '협재 · 우도 · 성산 …',
      onBack: onBack,
      onMake: onMake,
    );

class _Extras extends StatefulWidget {
  final NewBookDraft draft;
  final int photos, freeLeft;
  final ValueChanged<ShelfBook> onMade;
  const _Extras({required this.draft, required this.photos, required this.freeLeft, required this.onMade});
  @override
  State<_Extras> createState() => _ExtrasState();
}

class _ExtrasState extends State<_Extras> {
  NewBookDraft get d => widget.draft;

  Future<void> _push(Widget Function(VoidCallback back) screen) async {
    final nav = Navigator.of(context);
    await nav.push(MaterialPageRoute<void>(builder: (_) => screen(nav.pop)));
    setState(() {});
  }

  Future<void> _make() async {
    final nav = Navigator.of(context);
    if (widget.freeLeft == 0) {
      final go = await showSheet<bool>(
        context,
        (c) => PriceSheet(
          line: '제주 ${rangeShort(d.start!, d.end!)} · 약 ${lengthPages[d.opts.length]}쪽',
          onLater: () => Navigator.of(c).pop(false),
          onMake: () => Navigator.of(c).pop(true),
        ),
      );
      if (go != true) return;
    }
    nav.push(MaterialPageRoute<void>(builder: (_) => _Making(draft: d, photos: widget.photos, paid: widget.freeLeft == 0, onMade: widget.onMade)));
  }

  @override
  Widget build(BuildContext context) => ExtrasScreen(
        draft: d,
        photos: widget.photos,
        mustThumbs: [for (final id in d.mustInclude) samplePath(id)],
        freeLeft: widget.freeLeft,
        onBack: Navigator.of(context).pop,
        onMust: () => _push((back) => MustPickScreen(days: sampleMustDays, draft: d, onDone: back)),
        onNotes: () => _push((back) => StatefulBuilder(
              builder: (_, set) => NotesScreen(
                items: d.notes,
                onBack: back,
                onPaste: () => set(() => d.notes = [...d.notes, sampleNotes[0]]),
                onFile: () => set(() => d.notes = [...d.notes, const NoteItem('여행일기.hwp', '이 파일만 빼고 만들어요. txt · md · html · pdf · 사진은 읽을 수 있어요', bad: true)]),
                onHandwriting: () => set(() => d.notes = [...d.notes, sampleNotes[1]]),
                onTapItem: (i) => set(() => d.notes = [...d.notes]..removeAt(i)),
                onDone: back,
              ),
            )),
        onTimeline: () => _push((back) => TimelineHelpScreen(
              range: rangeShort(d.start!, d.end!),
              onBack: back,
              onPickFile: () {
                d.timeline = 'Timeline.json';
                back();
              },
              onOpenSettings: () {},
            )),
        onShape: () => _push((back) => _shapeScreen(d, widget.photos, onBack: () {
              d.shapeTouched = true;
              back();
            }, onMake: _make)),
        onMake: _make,
      );
}

enum _Phase { picking, pairs, review, writing }

/// D4 with timers standing in for /pick and /write.
class _Making extends StatefulWidget {
  final NewBookDraft draft;
  final int photos;
  final bool paid;
  final ValueChanged<ShelfBook> onMade;
  const _Making({required this.draft, required this.photos, required this.paid, required this.onMade});
  @override
  State<_Making> createState() => _MakingState();
}

class _MakingState extends State<_Making> {
  var _phase = _Phase.picking;
  int? _picked;
  late final _days = [for (final (d, ps) in samplePicks) (d, [...ps])];
  late final Set<String> _must = {...sampleMustPicks, ...widget.draft.mustInclude};
  String _cover = sampleCoverId;
  int _page = 0;
  var _stage = WriteStage.lines;
  Timer? _t;
  int _added = 0;

  NewBookDraft get d => widget.draft;
  static const _line = '물이 생각보다 차갑다.';

  @override
  void initState() {
    super.initState();
    _t = Timer(const Duration(milliseconds: 1600), () {
      setState(() => _picked = 38);
      _t = Timer(const Duration(milliseconds: 900), () => setState(() => _phase = _Phase.pairs));
    });
  }

  @override
  void dispose() {
    _t?.cancel();
    super.dispose();
  }

  void _write() {
    setState(() => _phase = _Phase.writing);
    final pages = lengthPages[d.opts.length]!;
    _t = Timer.periodic(const Duration(milliseconds: 160), (t) {
      setState(() {
        if (_stage == WriteStage.lines && _page < pages) {
          _page++;
        } else if (_stage == WriteStage.lines) {
          _stage = WriteStage.essay;
        } else if (_stage == WriteStage.essay && t.tick % 6 == 0) {
          _stage = WriteStage.binding;
        } else if (_stage == WriteStage.binding && t.tick % 6 == 0) {
          t.cancel();
          _done(pages);
        }
      });
    });
  }

  void _done(int pages) {
    final n = _days.fold(0, (s, x) => s + x.$2.length);
    widget.onMade(ShelfBook(
      id: 'new-${DateTime.now().millisecondsSinceEpoch}',
      title: '제주 2박 3일',
      start: d.start!,
      end: d.end!,
      pages: pages,
      places: 9,
      photos: n,
      km: 214,
      cover: 'assets/covers/${d.opts.theme ?? 'a'}.jpg',
      theme: d.opts.theme ?? 'a',
    ));
    Navigator.of(context).pushReplacement(MaterialPageRoute<void>(
      builder: (_) => _Book(
        title: '제주 2박 3일',
        tip: widget.paid ? '책장에 넣었어요.' : '책장에 넣었어요. 이번 책은 무료예요 (2권 중 첫 번째).',
      ),
    ));
  }

  void _tapPhoto(PhotoRef p) => showSheet<void>(
        context,
        (c) => SwapSheet(
          photo: p,
          place: '협재 해변',
          when: '6.12 금 13:20',
          alts: sampleAlts,
          samePlace: 14,
          isCover: p.id == _cover,
          onSwap: (a) {
            Navigator.of(c).pop();
            setState(() {
              for (final (_, ps) in _days) {
                final i = ps.indexWhere((x) => x.id == p.id);
                if (i >= 0) ps[i] = a.photo;
              }
            });
          },
          onMore: () {},
          onRemove: () {
            Navigator.of(c).pop();
            setState(() {
              for (final (_, ps) in _days) {
                ps.removeWhere((x) => x.id == p.id);
              }
              _must.remove(p.id);
            });
          },
          onCover: () {
            Navigator.of(c).pop();
            setState(() => _cover = p.id);
          },
        ),
      );

  @override
  Widget build(BuildContext context) {
    final nav = Navigator.of(context);
    final o = d.opts;
    switch (_phase) {
      case _Phase.picking:
        return PickingScreen(
          thumbs: samplePickStrip,
          inRange: 412,
          albums: d.albums.length,
          albumPhotos: widget.photos,
          filtered: (blurDark: 34, similar: 88, capture: 7),
          sent: 57,
          picked: _picked,
          must: d.mustInclude.isEmpty ? 3 : d.mustInclude.length,
          onClose: nav.pop,
        );
      case _Phase.pairs:
        return PairsScreen(
          pairs: samplePairs,
          onBack: nav.pop,
          onLeaveToApp: () => setState(() => _phase = _Phase.review),
          onDone: (_) => setState(() => _phase = _Phase.review),
        );
      case _Phase.review:
        return PicksReviewScreen(
          days: _days,
          must: _must,
          coverId: _cover,
          from: widget.photos,
          videos: 2,
          shapeLine: '${themeNames[o.theme ?? 'a']} · ${lengthNames[o.length]} · ${const {'photos': '사진만', 'caption': '짧은 캡션', 'caption_essay': '캡션 + 에세이'}[o.textAmount]}',
          pages: lengthPages[o.length]!,
          price: widget.paid ? '1,500원' : null,
          onTapPhoto: _tapPhoto,
          onAdd: (day) => setState(() {
            final ps = _days.firstWhere((x) => x.$1 == day).$2;
            ps.add(PhotoRef('add-${_added++}', samplePath('14-${_added % 40}')));
          }),
          onBack: () => setState(() => _phase = _Phase.pairs),
          onShape: () async {
            await nav.push(MaterialPageRoute<void>(builder: (_) => _shapeScreen(d, widget.photos, onBack: nav.pop, onMake: () {
                  nav.pop();
                  _write();
                })));
            setState(() {});
          },
          onMake: _write,
        );
      case _Phase.writing:
        final typed = _line.substring(0, (_page * 2).clamp(0, _line.length));
        return WritingScreen(
          photo: 'assets/sample/hyeopjae_2.jpg',
          meta: '13:20 · 협재 해변',
          typing: typed,
          picked: _days.fold(0, (s, x) => s + x.$2.length),
          page: _page,
          pages: lengthPages[o.length]!,
          tone: const {'kind': '다정한 존댓말', 'humor': '유머'}[o.tone] ?? '담담한 일기체',
          theme: themeNames[o.theme ?? 'a']!,
          stage: _stage,
          onClose: nav.pop,
        );
    }
  }
}

/// D5 book view with a stand-in page until the WebView runtime is wired.
class _Book extends StatefulWidget {
  final String title;
  final String? tip;
  const _Book({required this.title, this.tip});
  @override
  State<_Book> createState() => _BookState();
}

class _BookState extends State<_Book> {
  late String? _tip = widget.tip;
  int _rewrites = 0;

  @override
  Widget build(BuildContext context) => BookView(
        title: widget.title,
        onBack: Navigator.of(context).pop,
        onEdit: () {},
        onExport: () {},
        tip: _tip == null
            ? const BookTip('쪽을 길게 누르면 그 쪽의 사진이나 글만 고쳐요.', soft: true)
            : BookTip(_tip!, onOk: () => setState(() => _tip = null)),
        page: Center(
          child: GestureDetector(
            onLongPress: () => showSheet<void>(
              context,
              (c) => PageSheet(
                heading: '2쪽 · 협재 해변',
                when: '6.12 금 13:20',
                samePlace: 14,
                rewrites: _rewrites,
                onSwapPhoto: Navigator.of(c).pop,
                onRewrite: () {
                  Navigator.of(c).pop();
                  setState(() => _rewrites = maxRewrites); // fake: spend them all to show the spent state
                },
              ),
            ),
            child: Container(
              margin: const EdgeInsets.fromLTRB(18, 10, 18, 120),
              decoration: const BoxDecoration(boxShadow: [BoxShadow(color: Color(0x333C2814), blurRadius: 24, offset: Offset(0, 10))]),
              child: Image.asset('assets/covers/a.jpg', fit: BoxFit.contain),
            ),
          ),
        ),
      );
}

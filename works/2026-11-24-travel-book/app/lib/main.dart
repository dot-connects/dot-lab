import 'package:material_ui/material_ui.dart';

import 'core/library.dart';
import 'ui/new_book/albums.dart';
import 'ui/new_book/book_shape.dart';
import 'ui/new_book/dates.dart';
import 'ui/new_book/draft.dart';
import 'ui/new_book/extras.dart';
import 'ui/new_book/must_pick.dart';
import 'ui/new_book/notes.dart';
import 'ui/new_book/timeline_help.dart';
import 'ui/onboarding.dart';
import 'ui/sample.dart';
import 'ui/shelf.dart';
import 'ui/tokens.dart';

void main() => runApp(const TripbookApp());

class TripbookApp extends StatelessWidget {
  const TripbookApp({super.key});
  @override
  Widget build(BuildContext context) =>
      MaterialApp(title: '여행 한 권', debugShowCheckedModeBanner: false, theme: appTheme(), home: const _Root());
}

// Wiring with FAKE data (ui/sample.dart) until the gallery and the pipeline land.
class _Root extends StatefulWidget {
  const _Root();
  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  bool _onboarded = false;
  final _books = [...sampleBooks];
  var _view = ShelfView.display;

  @override
  Widget build(BuildContext context) {
    if (!_onboarded) return Onboarding(onAllow: () => setState(() => _onboarded = true));
    void none(ShelfBook _) {}
    return Shelf(
      books: _books,
      view: _view,
      onViewChanged: (v) => setState(() => _view = v),
      onOpen: none,
      onExport: none,
      onRename: none,
      onDelete: (b) => setState(() => _books.remove(b)),
      onSettings: () {},
      onNew: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => _NewBook(freeLeft: freeLeft(_books)))),
    );
  }
}

class _NewBook extends StatelessWidget {
  final int freeLeft;
  const _NewBook({required this.freeLeft});
  @override
  Widget build(BuildContext context) {
    final draft = NewBookDraft();
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
              nav.push(MaterialPageRoute<void>(builder: (_) => _Extras(draft: draft, photos: n, freeLeft: freeLeft)));
            },
          ),
        ));
      },
    );
  }
}

class _Extras extends StatefulWidget {
  final NewBookDraft draft;
  final int photos, freeLeft;
  const _Extras({required this.draft, required this.photos, required this.freeLeft});
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

  void _make() => Navigator.of(context).popUntil((r) => r.isFirst); // D4 (making) is not built yet

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
                onFile: () => set(() => d.notes = [...d.notes, sampleNotes[0]]),
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
        onShape: () => _push((back) => BookShapeScreen(
              draft: d,
              recommended: 'a',
              reason: '풍경 사진이 많아서',
              photos: widget.photos,
              days: d.end!.difference(d.start!).inDays + 1,
              videos: 9,
              placesHint: '협재 · 우도 · 성산 …',
              onBack: () {
                d.shapeTouched = true;
                back();
              },
              onMake: _make,
            )),
        onMake: _make,
      );
}

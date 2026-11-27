// D5 host: a saved book in the WebView runtime, failure banners with [다시 시도], and the page long-press sheet.
import 'package:material_ui/material_ui.dart';

import '../book/web_view.dart';
import '../core/book.dart';
import '../core/models.dart';
import '../pipeline.dart';
import '../ui/book_view.dart';
import '../ui/failures.dart';
import '../ui/making/swap_sheet.dart';
import '../ui/new_book/draft.dart' show PhotoRef;
import '../ui/tokens.dart';
import '../ui/widgets.dart';
import 'app.dart';
import 'export_flow.dart';

String whenOf(DateTime t) => '${t.month}.${t.day} ${weekdays[t.weekday - 1]} ${two(t.hour)}:${two(t.minute)}';

class BookScreen extends StatefulWidget {
  final App app;
  final Book book;
  final AiState? failed; // the AI did not write this book (off = month limit, failed = offline/error)
  final String? tip;
  final List<NoteInput> notes;
  const BookScreen({super.key, required this.app, required this.book, this.failed, this.tip, this.notes = const []});
  @override
  State<BookScreen> createState() => _BookScreenState();
}

class _BookScreenState extends State<BookScreen> {
  late AiState? _failed = widget.failed;
  late String? _tip = widget.tip;
  int _version = 0;
  Book get b => widget.book;
  App get app => widget.app;

  Future<void> _retry() async {
    final r = await busy(context, () => app.pipe.write(b, notes: widget.notes));
    if (!mounted) return;
    setState(() {
      _failed = r == AiState.ok ? null : r;
      _version++;
    });
  }

  Candidate? _cand(String id) => b.pool.where((c) => c.id == id).firstOrNull;

  void _longPress(int page) {
    if (page >= b.pages.length) return;
    final spec = b.pages[page];
    final c = spec.ids.isEmpty ? null : _cand(spec.ids.first);
    if (c == null) return;
    final alts = app.pipe.alternatives(b, c.id);
    showSheet<void>(
      context,
      (sc) => PageSheet(
        heading: '${page + 1}쪽${c.place == null ? '' : ' · ${c.place}'}',
        when: whenOf(c.local),
        samePlace: alts.length,
        rewrites: b.rewrites,
        onSwapPhoto: () {
          Navigator.of(sc).pop();
          _swap(page, c, alts);
        },
        onRewrite: () async {
          Navigator.of(sc).pop();
          final r = await busy(context, () => app.pipe.rewritePage(b, page, max: maxRewrites, notes: widget.notes));
          if (!mounted) return;
          if (r == AiState.ok) {
            setState(() => _version++);
          } else {
            toast(context, '글을 못 썼어요');
          }
        },
      ),
    );
  }

  void _swap(int page, Candidate c, List<Candidate> alts) {
    if (alts.isEmpty) return;
    showSheet<void>(
      context,
      (sc) => SwapSheet(
        photo: PhotoRef(c.id, 'gallery:${c.id}'),
        place: c.place ?? '',
        when: whenOf(c.local),
        alts: [for (final a in alts.take(3)) SwapAlt(PhotoRef(a.id, 'gallery:${a.id}'), '같은 곳', '${two(a.local.hour)}:${two(a.local.minute)}')],
        samePlace: alts.length,
        isCover: b.cover == c.id,
        onSwap: (a) async {
          Navigator.of(sc).pop();
          await busy(context, () => app.pipe.swapOnPage(b, page, a.photo.id));
          if (mounted) setState(() => _version++);
        },
        onMore: () {}, // ponytail: the full same-day grid comes with 다듬기 (M2)
        onRemove: () => Navigator.of(sc).pop(), // removing from a laid-out book is 다듬기's job
        onCover: () async {
          Navigator.of(sc).pop();
          await app.pipe.setCover(b, c.id);
        },
      ),
    );
  }

  /// Two pages side by side: the person's choice, else wide screens (landscape, unfolded foldables, tablets).
  bool _spread(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    return app.prefs.spread ?? size.width > size.height * 0.9;
  }

  @override
  Widget build(BuildContext context) {
    final picked = b.picks.length;
    return BookView(
      title: b.title,
      onBack: Navigator.of(context).pop,
      onEdit: () {}, // 다듬기: M2
      onExport: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ExportFlow(app: app, book: b))),
      tip: _failed != null
          ? null
          : _tip != null
              ? BookTip(_tip!, onOk: () => setState(() => _tip = null))
              : null,
      banner: switch (_failed) {
        AiState.off => quotaBanner(onRetry: _retry, onClose: () => setState(() => _failed = null)),
        AiState.failed => offlineBanner(picked, onRetry: _retry, onNoText: () => setState(() => _failed = null)),
        _ => null,
      },
      spread: _spread(context),
      onSpread: () => setState(() => app.prefs
        ..spread = !_spread(context)
        ..save()),
      page: BookWebView(book: bookJs(b), booksDir: app.booksDir, version: _version, onLongPress: _longPress, spread: _spread(context)),
    );
  }
}

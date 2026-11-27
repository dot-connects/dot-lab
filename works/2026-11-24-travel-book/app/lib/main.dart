import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter/services.dart' show rootBundle;
import 'package:material_ui/material_ui.dart';
import 'package:archive/archive_io.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import 'ai/client.dart';
import 'book/phone_source.dart';
import 'book/shelf_of.dart';
import 'book/store.dart';
import 'core/book.dart';
import 'core/library.dart';
import 'core/prefs.dart';
import 'device/gallery.dart' as g;
import 'device/making_channel.dart';
import 'flow/app.dart';
import 'flow/book_screen.dart';
import 'flow/export_flow.dart';
import 'flow/making_flow.dart';
import 'flow/new_book_flow.dart';
import 'pipeline.dart';
import 'ui/onboarding.dart';
import 'ui/settings.dart';
import 'ui/shelf.dart';
import 'ui/tokens.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final docs = await getApplicationDocumentsDirectory();
  final store = BookStore(Directory('${docs.path}/books'));
  final src = PhoneSource();
  galleryThumbs = (id, long) => src.jpeg(id, long);
  final app = App(
    prefs: Prefs.load(docs),
    store: store,
    src: src,
    pipe: Pipeline(
      src: src,
      ai: AiClient(),
      store: store,
      layoutsJson: (t) => rootBundle.loadString('assets/book/themes/$t/layouts.json'),
    ),
  );
  runApp(TripbookApp(app: app));
}

class TripbookApp extends StatelessWidget {
  final App app;
  const TripbookApp({super.key, required this.app});
  @override
  Widget build(BuildContext context) =>
      MaterialApp(title: '여행 한 권', debugShowCheckedModeBanner: false, theme: appTheme(), home: _Root(app));
}

class _Root extends StatefulWidget {
  final App app;
  const _Root(this.app);
  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  List<Book> _books = [];
  App get app => widget.app;
  ShelfView get _view => app.prefs.shelfView == 'timeline' ? ShelfView.timeline : ShelfView.display;
  int get _free => freeLeft([for (final b in _books) shelfOf(b, app.booksDir)], made: app.prefs.made);

  @override
  void initState() {
    super.initState();
    _reload().then((_) async {
      _resumeJobs();
      final id = await takeLaunchBook(); // opened from "책이 다 됐어요"
      if (id != null) _openId(id);
    });
    onOpenBook(_openId);
    app.shelfChanged.addListener(_reload);
  }

  void _openId(String id) {
    final b = _books.where((x) => x.id == id).firstOrNull;
    if (b != null && mounted) _go(BookScreen(app: app, book: b, failed: b.written ? null : AiState.failed));
  }

  /// Books whose AI layout job was running when the app closed: wait for the same job in the background (the server
  /// kept working), then refresh the shelf; "다 됐어요" if the person is away by then.
  void _resumeJobs() {
    for (final b in _books.where((b) => b.job != null && !b.written && !app.making.contains(b.id))) {
      app.making.add(b.id);
      unawaited(() async {
        await makingStart(b.title);
        final r = await app.pipe.write(b);
        app.making.remove(b.id);
        final away = WidgetsBinding.instance.lifecycleState != AppLifecycleState.resumed;
        await (r == AiState.ok && away ? makingDone(b.title, b.id) : makingStop());
        await _reload();
      }());
    }
  }

  Future<void> _reload() async {
    if (app.prefs.onboarded) app.limited = await app.src.isLimited().catchError((_) => false);
    final bs = await app.store.all();
    if (mounted) setState(() => _books = bs);
    unawaited(_syncMade(bs));
  }

  /// Free books are counted per phone on the server: send the written books, keep the larger count (never goes down).
  Future<void> _syncMade(List<Book> bs) async {
    final written = [for (final b in bs) if (b.written) b.id];
    var n = written.length;
    final id = (await deviceId())?.toLowerCase();
    if (id != null) n = (await app.pipe.ai.made(id, written)) ?? n;
    if (n <= app.prefs.made) return;
    app.prefs
      ..made = n
      ..save();
    if (mounted) setState(() {});
  }

  Future<void> _allow() async {
    final a = await g.requestAccess();
    if (a == g.Access.denied) {
      await g.openSettings();
      return;
    }
    app.limited = a == g.Access.limited;
    app.prefs
      ..onboarded = true
      ..save();
    setState(() {});
  }

  void _setView(ShelfView v) => setState(() => app.prefs
    ..shelfView = v == ShelfView.timeline ? 'timeline' : 'display'
    ..save());

  Future<void> _go(Widget w) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => w));
    await _reload();
  }

  Book _book(ShelfBook s) => _books.firstWhere((b) => b.id == s.id);

  void _open(ShelfBook s) {
    final b = _book(s);
    // a book still at "picked" with no pages resumes at the photo review; one with pages opens (text or not)
    _go(b.pages.isEmpty ? MakingFlow(app: app, book: b, freeLeftBefore: _free) : BookScreen(app: app, book: b, failed: b.written ? null : AiState.failed));
  }

  Future<void> _rename(ShelfBook s) async {
    final b = _book(s);
    final c = TextEditingController(text: b.title);
    final t = await showDialog<String>(
      context: context,
      builder: (dc) => AlertDialog(
        title: const Text('이름 바꾸기'),
        content: TextField(controller: c, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.of(dc).pop(), child: const Text('취소')),
          TextButton(onPressed: () => Navigator.of(dc).pop(c.text.trim()), child: const Text('바꾸기')),
        ],
      ),
    );
    if (t == null || t.isEmpty) return;
    b.title = t;
    await app.store.save(b);
    await _reload();
  }

  void _settings() => _go(StatefulBuilder(
        builder: (c, set) => SettingsScreen(
          view: _view,
          quality: app.prefs.quality,
          books: _books.length,
          freeLeft: _free,
          access: app.limited ? '일부만 허용' : '모두 허용 · 위치 읽기 켜짐',
          version: '0.1.0',
          onView: (v) => set(() => _setView(v)),
          onQuality: (q) => set(() => app.prefs
            ..quality = q
            ..save()),
          onBack: Navigator.of(c).pop,
          onAccess: g.openSettings,
          onTerms: () {},
          onLab: kLab ? _shareLab : null,
        ),
      ));

  /// Test builds: the last pick's lab folder as one zip on the share sheet (to the PC, for tuning the picker).
  Future<void> _shareLab() async {
    final cache = await getTemporaryDirectory(), dir = await labDir(cache), out = '${cache.path}/lab.zip';
    if (!await dir.exists()) return;
    await Isolate.run(() => ZipFileEncoder().zipDirectory(dir, filename: out, level: ZipFileEncoder.store));
    await SharePlus.instance.share(ShareParams(files: [XFile(out)], title: '여행 한 권 실험 기록'));
  }

  Future<void> _new() async {
    final partial = app.limited ? await app.src.visibleCount() : null;
    await _go(NewBookFlow(app: app, freeLeft: _free, partial: partial));
  }

  @override
  Widget build(BuildContext context) {
    if (!app.prefs.onboarded) return Onboarding(onAllow: _allow);
    return Shelf(
      books: [for (final b in _books) shelfOf(b, app.booksDir)],
      view: _view,
      onViewChanged: _setView,
      onOpen: _open,
      onExport: (s) {
        final b = _book(s);
        if (b.pages.isNotEmpty) _go(ExportFlow(app: app, book: b));
      },
      onRename: _rename,
      onDelete: (s) async {
        await app.store.delete(s.id);
        await _reload();
      },
      onSettings: _settings,
      onNew: _new,
    );
  }
}

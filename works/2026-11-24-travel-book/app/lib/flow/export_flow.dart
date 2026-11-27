// [내보내기] host: renders the pages in the background as soon as the screen opens (thumbnails fill in), then saves the
// chosen ones to the gallery, a PDF (handed to the share sheet to keep), or shares them.
import 'dart:async';

import 'package:material_ui/material_ui.dart';

import '../book/exporter.dart';
import '../core/book.dart';
import '../pipeline.dart' show bookJs;
import '../ui/export_screen.dart';
import 'app.dart';

class ExportFlow extends StatefulWidget {
  final App app;
  final Book book;
  const ExportFlow({super.key, required this.app, required this.book});
  @override
  State<ExportFlow> createState() => _ExportFlowState();
}

class _ExportFlowState extends State<ExportFlow> {
  late final ex = BookExporter(widget.app.booksDir);
  late final n = widget.book.pages.length;
  late final List<String?> _thumbs = List.filled(n, null);
  late final _ready = [for (var i = 0; i < n; i++) Completer<String>()];
  StreamSubscription<(int, String)>? _sub;

  @override
  void initState() {
    super.initState();
    _sub = ex.render(widget.book.id, bookJs(widget.book), n).listen(
      (r) {
        _ready[r.$1].complete(r.$2);
        if (mounted) setState(() => _thumbs[r.$1] = r.$2);
      },
      onError: (Object e) {
        for (final c in _ready) {
          if (!c.isCompleted) c.completeError(e);
        }
      },
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  Future<List<String>> _paths(List<int> pages) => Future.wait([for (final i in pages) _ready[i].future]);

  Future<void> _go(List<int> pages, ExportKind kind, ValueChanged<int> tick) async {
    final t = widget.book.title;
    try {
      final paths = await _paths(pages);
      if (kind == ExportKind.pdf) {
        final pdf = await ex.toPdf(paths, t, tick);
        await ex.share([pdf], t);
      } else {
        await ex.toGallery(paths, t, tick);
      }
    } catch (_) {
      if (mounted) toast(context, '저장하지 못했어요. 다시 해 주세요');
      rethrow;
    }
  }

  Future<void> _share(List<int> pages) async {
    try {
      await ex.share(await _paths(pages), widget.book.title);
    } catch (_) {
      if (mounted) toast(context, '보내지 못했어요');
    }
  }

  @override
  Widget build(BuildContext context) => ExportScreen(
        title: widget.book.title,
        thumbs: _thumbs,
        onBack: Navigator.of(context).pop,
        onGo: _go,
        onShare: _share,
      );
}

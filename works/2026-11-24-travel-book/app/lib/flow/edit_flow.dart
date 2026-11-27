// [다듬기] host: loads the theme's designs, edits a copy of the book, and only on save writes it back (pops true).
import 'package:material_ui/material_ui.dart';

import '../book/edit_view.dart';
import '../core/book.dart';
import '../core/edit.dart';
import '../pipeline.dart' show bookJs;
import '../ui/edit_screen.dart';
import 'app.dart';

class EditFlow extends StatefulWidget {
  final App app;
  final Book book;
  const EditFlow({super.key, required this.app, required this.book});
  @override
  State<EditFlow> createState() => _EditFlowState();
}

class _EditFlowState extends State<EditFlow> {
  BookEdit? _edit;

  @override
  void initState() {
    super.initState();
    widget.app.pipe.layouts(widget.book.theme).then((defs) {
      if (mounted) setState(() => _edit = BookEdit(widget.book, defs));
    });
  }

  Future<void> _save() async {
    final e = _edit!;
    try {
      // saved first as a copy: the book the app holds changes only once the file is written
      final out = BookEdit.copyBook(widget.book);
      e.applyTo(out);
      await widget.app.store.save(out);
      e.applyTo(widget.book);
      if (mounted) Navigator.of(context).pop(true);
    } catch (_) {
      if (mounted) toast(context, '저장하지 못했어요. 다시 해 주세요');
    }
  }

  @override
  Widget build(BuildContext context) {
    final e = _edit;
    if (e == null) return const Scaffold();
    final app = widget.app;
    return EditScreen(
      edit: e,
      page: (args, events) => EditWebView(book: () => bookJs(e.b), booksDir: app.booksDir, args: args, events: events),
      alternatives: (id) => app.pipe.alternatives(e.b, id).where((c) => !e.b.pages.any((p) => p.ids.contains(c.id))).toList(),
      copyMedia: (ids) => app.pipe.copyMedia(e.b, ids),
      onSave: _save,
      onClose: () => Navigator.of(context).pop(false),
    );
  }
}

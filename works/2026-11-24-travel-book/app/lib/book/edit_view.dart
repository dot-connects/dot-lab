// 다듬기's page: the book runtime showing one page of the working copy, in the step's edit mode. A new version
// re-renders the book (colour, font, size, photos); a new page or mode only moves the runtime to it.
import 'dart:convert';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:material_ui/material_ui.dart';

import '../ui/edit_screen.dart';
import 'web_view.dart';

class EditWebView extends StatefulWidget {
  final Map<String, Object?> Function() book; // bookJs of the working copy, read at each render
  final String booksDir;
  final EditPageArgs args;
  final EditEvents events;
  const EditWebView({super.key, required this.book, required this.booksDir, required this.args, required this.events});
  @override
  State<EditWebView> createState() => _EditWebViewState();
}

class _EditWebViewState extends State<EditWebView> {
  InAppWebViewController? _c;
  bool _loaded = false;
  int _drawn = -1; // the version the runtime holds

  Future<void> _sync({bool force = false}) async {
    final c = _c, a = widget.args;
    if (c == null || !_loaded) return;
    if (force || a.version != _drawn) {
      _drawn = a.version;
      await c.callAsyncJavaScript(functionBody: 'await window.renderBook(${jsonEncode(widget.book())});');
    }
    await c.callAsyncJavaScript(functionBody: 'await window.showPage(${a.page}); window.editMode("${a.mode.name}", ${a.page}, ${a.slot});');
  }

  @override
  void didUpdateWidget(EditWebView old) {
    super.didUpdateWidget(old);
    final a = widget.args, o = old.args;
    if (a.version != o.version || a.page != o.page || a.mode != o.mode || a.slot != o.slot) _sync();
  }

  Map<String, Object?> _arg(List<dynamic> args) => args.isNotEmpty && args.first is Map ? (args.first as Map).cast<String, Object?>() : const {};

  @override
  Widget build(BuildContext context) => InAppWebView(
        initialUrlRequest: URLRequest(url: WebUri(bookIndex)),
        initialSettings: bookSettings(widget.booksDir),
        onWebViewCreated: (c) {
          _c = c;
          c.addJavaScriptHandler(handlerName: 'text', callback: (a) {
            final m = _arg(a);
            widget.events.onText(m['page'] as int, m['key'] as String, m['value'] as String);
          });
          c.addJavaScriptHandler(handlerName: 'slot', callback: (a) {
            final m = _arg(a);
            widget.events.onSlot(m['page'] as int, m['slot'] as int);
          });
          c.addJavaScriptHandler(handlerName: 'crop', callback: (a) {
            final m = _arg(a);
            widget.events.onCrop(m['page'] as int, m['slot'] as int, m['pos'] as String);
          });
          c.addJavaScriptHandler(handlerName: 'rendered', callback: (a) {
            final paper = _arg(a)['coverPaper'];
            if (paper is bool) widget.events.onCoverPaper(paper);
          });
        },
        onLoadStop: (c, _) async {
          _loaded = true;
          await _sync(force: true);
        },
      );
}

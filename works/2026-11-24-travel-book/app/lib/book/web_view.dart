// The book runtime (assets/book/index.html + book.js) in a WebView, for BookView's page slot.
// Flutter assets are served from https://appassets.androidplatform.net/assets/flutter_assets/..., the book's own
// image/video files from /books/<id>/... (InternalStoragePathHandler over <app documents>/books).
import 'dart:convert';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:material_ui/material_ui.dart';

const _origin = 'https://appassets.androidplatform.net';
const bookIndex = '$_origin/assets/flutter_assets/assets/book/index.html';

/// The runtime's WebView settings (also the headless one that exports pages).
InAppWebViewSettings bookSettings(String booksDir) => InAppWebViewSettings(
      webViewAssetLoader: WebViewAssetLoader(pathHandlers: [
        AssetsPathHandler(path: '/assets/'),
        InternalStoragePathHandler(path: '/books/', directory: booksDir),
      ]),
      mediaPlaybackRequiresUserGesture: false,
      allowFileAccess: false,
      transparentBackground: true,
      supportZoom: false,
    );

class BookWebView extends StatefulWidget {
  final Map<String, Object?> book; // bookJs(book)
  final String booksDir; // <app documents>/books
  final int version; // bump to re-render after an edit
  final ValueChanged<int> onLongPress; // page index
  final bool spread; // two pages side by side (an open book) instead of one
  const BookWebView(
      {super.key, required this.book, required this.booksDir, this.version = 0, required this.onLongPress, this.spread = false});
  @override
  State<BookWebView> createState() => _BookWebViewState();
}

class _BookWebViewState extends State<BookWebView> {
  InAppWebViewController? _c;
  bool _loaded = false;

  Future<void> _render() async {
    if (_c == null || !_loaded) return;
    await _c!.evaluateJavascript(source: 'window.renderBook(${jsonEncode(widget.book)}, {spread: ${widget.spread}})');
  }

  @override
  void didUpdateWidget(BookWebView old) {
    super.didUpdateWidget(old);
    if (old.version != widget.version || old.spread != widget.spread) _render();
  }

  @override
  Widget build(BuildContext context) => InAppWebView(
        initialUrlRequest: URLRequest(url: WebUri(bookIndex)),
        initialSettings: bookSettings(widget.booksDir),
        onWebViewCreated: (c) {
          _c = c;
          c.addJavaScriptHandler(
            handlerName: 'longPress',
            callback: (args) {
              final page = args.isNotEmpty && args.first is Map ? (args.first as Map)['page'] : null;
              if (page is int) widget.onLongPress(page);
            },
          );
        },
        onLoadStop: (c, _) async {
          _loaded = true;
          await _render();
        },
      );
}

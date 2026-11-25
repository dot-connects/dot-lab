// The book runtime (assets/book/index.html + book.js) in a WebView, for BookView's page slot.
// Flutter assets are served from https://appassets.androidplatform.net/assets/flutter_assets/..., the book's own
// image/video files from /books/<id>/... (InternalStoragePathHandler over <app documents>/books).
import 'dart:convert';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:material_ui/material_ui.dart';

const _origin = 'https://appassets.androidplatform.net';
const _index = '$_origin/assets/flutter_assets/assets/book/index.html';

class BookWebView extends StatefulWidget {
  final Map<String, Object?> book; // bookJs(book)
  final String booksDir; // <app documents>/books
  final int version; // bump to re-render after an edit
  final ValueChanged<int> onLongPress; // page index
  const BookWebView({super.key, required this.book, required this.booksDir, this.version = 0, required this.onLongPress});
  @override
  State<BookWebView> createState() => _BookWebViewState();
}

class _BookWebViewState extends State<BookWebView> {
  InAppWebViewController? _c;
  bool _loaded = false;

  Future<void> _render() async {
    if (_c == null || !_loaded) return;
    await _c!.evaluateJavascript(source: 'window.renderBook(${jsonEncode(widget.book)})');
  }

  @override
  void didUpdateWidget(BookWebView old) {
    super.didUpdateWidget(old);
    if (old.version != widget.version) _render();
  }

  @override
  Widget build(BuildContext context) => InAppWebView(
        initialUrlRequest: URLRequest(url: WebUri(_index)),
        initialSettings: InAppWebViewSettings(
          webViewAssetLoader: WebViewAssetLoader(pathHandlers: [
            AssetsPathHandler(path: '/assets/'),
            InternalStoragePathHandler(path: '/books/', directory: widget.booksDir),
          ]),
          mediaPlaybackRequiresUserGesture: false,
          allowFileAccess: false,
          transparentBackground: true,
          supportZoom: false,
        ),
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

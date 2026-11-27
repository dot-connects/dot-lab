// Export (mockup exp B): each page rendered alone in a headless WebView (the book runtime's export mode) and saved as a
// JPG in the cache; from there to the gallery album "여행 한 권", a PDF, or the share sheet. Nothing leaves the phone
// unless the person shares it.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:photo_manager/photo_manager.dart';
import 'package:share_plus/share_plus.dart';

import 'web_view.dart';

const album = '여행 한 권';
const _width = 420.0; // logical px; the phone's pixel ratio makes it ~1100 px wide

/// A safe file name from a book title.
String fileTitle(String t) {
  final s = t.replaceAll(RegExp(r'[\\/:*?"<>|\n]'), ' ').trim();
  return s.isEmpty ? '여행' : s;
}

class BookExporter {
  final String booksDir;
  BookExporter(this.booksDir);

  /// Renders every page to `<cache>/export/<id>/p<n>.jpg`, yielding (n, path) as each one is ready.
  Stream<(int, String)> render(String id, Map<String, Object?> bookJs, int pages) async* {
    final out = Directory('${(await getTemporaryDirectory()).path}/export/$id');
    if (out.existsSync()) out.deleteSync(recursive: true);
    out.createSync(recursive: true);
    final loaded = Completer<void>();
    final web = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(bookIndex)),
      initialSettings: bookSettings(booksDir),
      initialSize: const Size(_width, _width * 2),
      onLoadStop: (_, _) {
        if (!loaded.isCompleted) loaded.complete();
      },
    );
    try {
      await web.run();
      await loaded.future;
      final c = web.webViewController!;
      await c.callAsyncJavaScript(functionBody: 'await window.renderBook(${jsonEncode(bookJs)}, {exportMode: true});');
      // the viewport becomes exactly one page, so the screenshot is the page
      await c.callAsyncJavaScript(functionBody: 'await window.showPage(0);');
      final h = (await c.evaluateJavascript(source: "document.querySelector('.pg').getBoundingClientRect().height") as num).toDouble();
      await web.setSize(Size(_width, h.floorToDouble()));
      for (var n = 0; n < pages; n++) {
        await c.callAsyncJavaScript(functionBody: 'await window.showPage($n);');
        final jpg = await c.takeScreenshot(
            screenshotConfiguration: ScreenshotConfiguration(compressFormat: CompressFormat.JPEG, quality: 90));
        if (jpg == null) throw StateError('screenshot');
        final f = File('${out.path}/p${n + 1}.jpg')..writeAsBytesSync(jpg);
        yield (n, f.path);
      }
    } finally {
      await web.dispose();
    }
  }

  /// Saves the page images into the gallery album; [tick] after each one.
  Future<void> toGallery(List<String> paths, String title, void Function(int done) tick) async {
    final name = fileTitle(title);
    for (final (i, p) in paths.indexed) {
      await PhotoManager.editor.saveImageWithPath(p, title: '$name ${i + 1}.jpg', relativePath: 'Pictures/$album');
      tick(i + 1);
    }
  }

  /// One PDF page per image, 148 mm wide (A5 width) at the page's own ratio, no margin.
  // ponytail: images on paper, not re-laid out per print format; a real print size needs the runtime to lay out A5.
  Future<String> toPdf(List<String> paths, String title, void Function(int done) tick) async {
    final doc = pw.Document(title: title);
    const w = 148 * PdfPageFormat.mm;
    for (final (i, p) in paths.indexed) {
      final img = pw.MemoryImage(File(p).readAsBytesSync());
      final h = w * img.height! / img.width!;
      doc.addPage(pw.Page(pageFormat: const PdfPageFormat(w, w).copyWith(height: h), margin: pw.EdgeInsets.zero,
          build: (_) => pw.Image(img, fit: pw.BoxFit.cover)));
      tick(i + 1);
      await Future<void>.delayed(Duration.zero); // let the progress paint
    }
    final f = File('${File(paths.first).parent.path}/${fileTitle(title)}.pdf')..writeAsBytesSync(await doc.save());
    return f.path;
  }

  Future<void> share(List<String> paths, String title) =>
      SharePlus.instance.share(ShareParams(files: [for (final p in paths) XFile(p)], title: title));
}

// Books on the phone: <app documents>/books/<id>/{book.json, img/, vid/, cover.png}.
// book.json is written to a temp file and renamed, so a crash mid-save never leaves a broken book.
import 'dart:convert';
import 'dart:io';

import '../core/book.dart';

class BookStore {
  final Directory root; // <app documents>/books
  BookStore(this.root);

  Directory dir(String id) => Directory('${root.path}/$id');

  Future<void> save(Book b) async {
    final d = await dir(b.id).create(recursive: true);
    final tmp = File('${d.path}/book.json.tmp');
    await tmp.writeAsString(jsonEncode(b.toJson()), flush: true);
    await tmp.rename('${d.path}/book.json');
  }

  Future<Book?> load(String id) async {
    final f = File('${dir(id).path}/book.json');
    if (!await f.exists()) return null;
    try {
      return Book.fromJson(jsonDecode(await f.readAsString()) as Map<String, dynamic>);
    } on FormatException {
      return null;
    }
  }

  Future<List<Book>> all() async {
    if (!await root.exists()) return [];
    final out = <Book>[];
    await for (final e in root.list()) {
      if (e is Directory) {
        final b = await load(e.uri.pathSegments.where((s) => s.isNotEmpty).last);
        if (b != null) out.add(b);
      }
    }
    out.sort((a, b) => b.from.compareTo(a.from));
    return out;
  }

  Future<void> delete(String id) async {
    final d = dir(id);
    if (await d.exists()) await d.delete(recursive: true);
  }
}

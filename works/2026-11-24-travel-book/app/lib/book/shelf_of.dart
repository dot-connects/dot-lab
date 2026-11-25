// A saved Book as the shelf shows it.
import 'dart:io';

import '../core/book.dart';
import '../core/library.dart';
import '../core/paginate.dart' show travelKm;
import '../pipeline.dart' show fileId;

ShelfBook shelfOf(Book b, String booksDir) {
  final pool = {for (final c in b.pool) c.id: c};
  final picked = [for (final p in b.picks) pool[p.id]].nonNulls.toList()..sort((a, x) => a.local.compareTo(x.local));
  final cover = b.cover == null ? null : '$booksDir/${b.id}/img/${fileId(b.cover!)}.jpg';
  final km = travelKm(picked).round();
  return ShelfBook(
    id: b.id,
    title: b.title.isEmpty ? '만드는 중' : b.title,
    start: b.from,
    end: b.to,
    pages: b.pages.length,
    places: picked.map((c) => c.place).nonNulls.toSet().length,
    photos: picked.where((c) => !c.shot.isVideo).length,
    km: km > 0 ? km : null,
    cover: cover != null && File(cover).existsSync() ? cover : (picked.isEmpty ? null : 'gallery:${picked.first.id}'),
    theme: b.theme,
    stage: b.stage,
  );
}

// Shelf list, year grouping and the free-book count. Pure Dart: tested on the VM.

class ShelfBook {
  final String id;
  final String title;
  final DateTime start, end; // local calendar days, inclusive
  final int pages, places, photos;
  final int? km; // null = no distance known (no GPS track)
  final String? cover; // asset path or file path; null = no image yet
  final String theme;
  final String stage; // picked|written
  const ShelfBook({
    required this.id,
    required this.title,
    required this.start,
    required this.end,
    required this.pages,
    this.places = 0,
    this.photos = 0,
    this.km,
    this.cover,
    this.theme = 'a',
    this.stage = 'written',
  });

  /// Calendar days the trip covers, inclusive (06-12..06-14 = 3).
  int get days => DateTime.utc(end.year, end.month, end.day).difference(DateTime.utc(start.year, start.month, start.day)).inDays + 1;
}

/// Books grouped by the year the trip started (a 12-31 -> 01-01 trip counts in its start year).
/// Years newest first, books inside a year newest first.
List<(int, List<ShelfBook>)> groupByYear(List<ShelfBook> books) {
  final by = <int, List<ShelfBook>>{};
  for (final b in books) {
    (by[b.start.year] ??= []).add(b);
  }
  final years = by.keys.toList()..sort((a, b) => b.compareTo(a));
  return [for (final y in years) (y, by[y]!..sort((a, b) => b.start.compareTo(a.start)))];
}

/// Totals shown next to a year: books, places, photos, days.
({int books, int places, int photos, int days}) yearTotals(List<ShelfBook> books) => (
      books: books.length,
      places: books.fold(0, (s, b) => s + b.places),
      photos: books.fold(0, (s, b) => s + b.photos),
      days: books.fold(0, (s, b) => s + b.days),
    );

const freeBooks = 2;

/// Free books left: only books whose text was written (stage == written) use one up.
int freeLeft(List<ShelfBook> books) {
  final used = books.where((b) => b.stage == 'written').length;
  return used >= freeBooks ? 0 : freeBooks - used;
}

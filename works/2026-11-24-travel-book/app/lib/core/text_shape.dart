// Each theme lays text out in one of three shapes; a book keeps the text it wrote per shape, so switching
// themes only calls the writer when that shape has never been written for this book.

const _shapes = {'diary': 'diary', 'mag': 'mag'};
const _limits = {'short': 40, 'diary': 160, 'mag': 30};

/// a, b, c, album -> short captions; diary -> two or three lines a page; mag -> headline + columns.
String shapeOf(String theme) => _shapes[theme] ?? 'short';

bool needsRewrite({required Set<String> written, required String newTheme}) => !written.contains(shapeOf(newTheme));

/// A caption (or, for mag, a headline) longer than its shape allows is dropped rather than cut mid-sentence.
String fitText(String s, String shape) {
  final t = s.trim();
  return t.runes.length <= (_limits[shape] ?? 40) ? t : '';
}

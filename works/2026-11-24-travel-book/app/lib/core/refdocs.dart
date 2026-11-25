// Reference notes (diary, memo, blog text) -> pieces tagged with the trip day they talk about.

class RefPiece {
  final String? day; // yyyy-MM-dd
  final String text;
  final String source;
  const RefPiece(this.day, this.text, this.source);
}

const _ordinals = {'첫째': 1, '둘째': 2, '셋째': 3, '넷째': 4, '다섯째': 5, '여섯째': 6, '일곱째': 7, '여덟째': 8, '아홉째': 9, '열째': 10};

String _ymd(DateTime d) => '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// A line that starts with a date (or "N일차" / "둘째 날") is a heading. Returns the day it names.
String? _headingDay(String line, DateTime tripStart) {
  final s = line.trim();
  RegExpMatch? m;
  if ((m = RegExp(r'^(\d{4})[.\-/]\s*(\d{1,2})[.\-/]\s*(\d{1,2})').firstMatch(s)) != null) {
    return _ymd(DateTime(int.parse(m!.group(1)!), int.parse(m.group(2)!), int.parse(m.group(3)!)));
  }
  if ((m = RegExp(r'^(\d{1,2})\s*월\s*(\d{1,2})\s*일').firstMatch(s)) != null ||
      (m = RegExp(r'^(\d{1,2})[./](\d{1,2})(?!\d)').firstMatch(s)) != null) {
    return _ymd(DateTime(tripStart.year, int.parse(m!.group(1)!), int.parse(m.group(2)!)));
  }
  if ((m = RegExp(r'^(\d{1,2})\s*일\s*차').firstMatch(s)) != null) {
    return _ymd(tripStart.add(Duration(days: int.parse(m!.group(1)!) - 1)));
  }
  if ((m = RegExp(r'^(첫째|둘째|셋째|넷째|다섯째|여섯째|일곱째|여덟째|아홉째|열째)\s*날').firstMatch(s)) != null) {
    return _ymd(tripStart.add(Duration(days: _ordinals[m!.group(1)!]! - 1)));
  }
  return null;
}

List<RefPiece> fromText(String raw, String source, {required DateTime tripStart}) {
  final out = <RefPiece>[];
  String? day;
  final buf = StringBuffer();
  void flush() {
    final t = buf.toString().trim();
    if (t.isNotEmpty) out.add(RefPiece(day, t, source));
    buf.clear();
  }

  for (final line in raw.split(RegExp(r'\r?\n'))) {
    final d = _headingDay(line, tripStart);
    if (d != null) {
      flush();
      day = d;
    }
    buf.writeln(line);
  }
  flush();
  return out;
}

String htmlToText(String html) {
  var s = html.replaceAll(RegExp(r'<(script|style|nav|header|footer)[^>]*>.*?</\1>', caseSensitive: false, dotAll: true), '');
  s = s.replaceAll(RegExp(r'<(br|/p|/div|/li|/h\d)[^>]*>', caseSensitive: false), '\n');
  s = s.replaceAll(RegExp(r'<[^>]+>'), '');
  s = s
      .replaceAll('&nbsp;', ' ')
      .replaceAll('&amp;', '&')
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>')
      .replaceAll('&quot;', '"')
      .replaceAll('&#39;', "'");
  return s.split('\n').map((l) => l.trim()).where((l) => l.isNotEmpty).join('\n');
}

/// Fit the notes into [maxChars]: the longest piece is shortened first, so every day keeps some text.
List<RefPiece> trimForAi(List<RefPiece> xs, {int maxChars = 12000}) {
  final lens = [for (final p in xs) p.text.length];
  var total = lens.fold(0, (a, b) => a + b);
  while (total > maxChars) {
    var i = 0;
    for (var k = 1; k < lens.length; k++) {
      if (lens[k] > lens[i]) i = k;
    }
    final second = ([...lens]..sort()).reversed.skip(1).firstOrNull ?? 0;
    final cut = (total - maxChars).clamp(1, lens[i] - second > 0 ? lens[i] - second : 1);
    lens[i] -= cut;
    total -= cut;
  }
  return [for (var k = 0; k < xs.length; k++) RefPiece(xs[k].day, xs[k].text.substring(0, lens[k]), xs[k].source)];
}

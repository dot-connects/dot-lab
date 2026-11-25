import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/refdocs.dart';

void main() {
  final start = DateTime(2026, 6, 12);
  List<String?> days(String raw) => fromText(raw, 'n.txt', tripStart: start).map((p) => p.day).toList();

  test('date headings in common Korean and numeric forms', () {
    expect(days('2026.6.13\n바람이 셌다'), ['2026-06-13']);
    expect(days('6/14 흐림\n안개'), ['2026-06-14']);
    expect(days('6월 12일\n협재'), ['2026-06-12']);
    expect(days('2026-06-13 우도'), ['2026-06-13']);
    expect(days('2일차\n우도'), ['2026-06-13']);
    expect(days('셋째 날\n한라산'), ['2026-06-14']);
  });

  test('text before any heading has no day, each heading starts a piece', () {
    final ps = fromText('짐 쌌다\n6월 12일\n협재\n6월 13일\n우도', 'n', tripStart: start);
    expect(ps.map((p) => p.day), [null, '2026-06-12', '2026-06-13']);
    expect(ps[1].text.contains('협재'), isTrue);
  });

  test('numbers inside sentences are not headings', () {
    expect(days('커피 2잔 마셨다. 3시에 출발'), [null]);
  });

  test('htmlToText drops scripts and keeps paragraphs', () {
    final t = htmlToText('<html><script>x=1</script><nav>메뉴</nav><p>첫 줄</p><p>둘째&nbsp;줄</p></html>');
    expect(t, '첫 줄\n둘째 줄');
  });

  test('trimForAi keeps every day and cuts the longest first', () {
    final ps = [RefPiece('2026-06-12', 'a' * 9000, 'n'), RefPiece('2026-06-13', 'b' * 100, 'n')];
    final out = trimForAi(ps, maxChars: 3000);
    expect(out.length, 2);
    expect(out.fold<int>(0, (s, p) => s + p.text.length), lessThanOrEqualTo(3000));
    expect(out[1].text.length, 100);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/ui/new_book/draft.dart';

void main() {
  test('must-include caps at 10; unchecking always works', () {
    final d = NewBookDraft();
    for (var i = 0; i < 10; i++) {
      expect(d.toggleMust('p$i'), isTrue);
    }
    expect(d.toggleMust('p10'), isFalse);
    expect(d.mustInclude.length, 10);
    expect(d.toggleMust('p3'), isTrue);
    expect(d.mustInclude.contains('p3'), isFalse);
    expect(d.toggleMust('p10'), isTrue);
  });
}

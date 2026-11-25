import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/text_shape.dart';

void main() {
  test('themes map to the three text shapes', () {
    expect(['a', 'b', 'c', 'album'].map(shapeOf).toSet(), {'short'});
    expect(shapeOf('diary'), 'diary');
    expect(shapeOf('mag'), 'mag');
    expect(shapeOf('unknown'), 'short');
  });

  test('a rewrite is needed only for a shape with no saved text', () {
    expect(needsRewrite(written: {'short'}, newTheme: 'b'), isFalse); // same shape
    expect(needsRewrite(written: {'short'}, newTheme: 'diary'), isTrue); // new shape
    expect(needsRewrite(written: {'short', 'diary'}, newTheme: 'diary'), isFalse); // came back
    expect(needsRewrite(written: {}, newTheme: 'a'), isTrue); // nothing written yet
  });

  test('length limits per shape: over the limit means empty, never cut', () {
    expect(fitText('가' * 40, 'short'), '가' * 40);
    expect(fitText('가' * 41, 'short'), '');
    expect(fitText('가' * 160, 'diary'), '가' * 160);
    expect(fitText('가' * 161, 'diary'), '');
    expect(fitText('  짧다  ', 'mag'), '짧다');
  });
}

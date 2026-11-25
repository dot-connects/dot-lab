import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/prefs.dart';

void main() {
  test('prefs: defaults, round trip, broken file falls back to defaults', () {
    final dir = Directory.systemTemp.createTempSync('prefs');
    addTearDown(() => dir.deleteSync(recursive: true));
    final p = Prefs.load(dir);
    expect((p.shelfView, p.quality), ('display', 'reduced'));
    p
      ..shelfView = 'timeline'
      ..save();
    expect(Prefs.load(dir).shelfView, 'timeline');
    p.save(); // overwrite an existing file
    File('${dir.path}/prefs.json').writeAsStringSync('{not json');
    expect(Prefs.load(dir).shelfView, 'display');
  });
}

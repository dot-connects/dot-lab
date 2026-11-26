// Shared harness for the screen tests: a 360x740 phone, real fonts, optional PNG per screen (SHOTS=<dir>).
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/ui/tokens.dart';

final shots = Platform.environment['SHOTS'];
final shotKey = GlobalKey();

Future<void> loadFonts() async {
  final f = {
    fUi: ['assets/book/fonts/PretendardVariable.ttf'],
    fNum: ['assets/book/fonts/InterTightwght.ttf'],
    'MaterialIcons': ['${Platform.environment['FLUTTER_ROOT'] ?? 'C:/DC/tools/flutter'}/bin/cache/artifacts/material_fonts/materialicons-regular.otf'],
  };
  for (final e in f.entries) {
    final l = FontLoader(e.key);
    for (final p in e.value) {
      l.addFont(Future.value(ByteData.sublistView(File(p).readAsBytesSync())));
    }
    await l.load();
  }
}

Future<void> shot(WidgetTester t, String name) async {
  if (shots == null) return;
  await t.runAsync(() async {
    for (final e in find.byType(Image).evaluate()) {
      await precacheImage((e.widget as Image).image, e);
    }
  });
  await t.pumpAndSettle();
  final ro = shotKey.currentContext!.findRenderObject() as RenderRepaintBoundary;
  await t.runAsync(() async {
    final png = await (await ro.toImage(pixelRatio: 2)).toByteData(format: ui.ImageByteFormat.png);
    File('$shots/$name.png').writeAsBytesSync(png!.buffer.asUint8List());
  });
}

Future<void> pump(WidgetTester t, Widget w, [String? name]) async {
  t.view.physicalSize = const Size(360, 740) * 2;
  t.view.devicePixelRatio = 2;
  addTearDown(t.view.reset);
  await t.pumpWidget(RepaintBoundary(key: shotKey, child: MaterialApp(debugShowCheckedModeBanner: false, theme: appTheme(), home: w)));
  await t.runAsync(() async {
    for (final e in find.byType(Image).evaluate()) {
      await precacheImage((e.widget as Image).image, e);
    }
  });
  await t.pumpAndSettle();
  if (name != null) await shot(t, name);
}


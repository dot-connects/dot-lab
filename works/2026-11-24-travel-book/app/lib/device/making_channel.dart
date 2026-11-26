// Dart side of kr.dot.tripbook/making (android/.../Making.kt): the "책 만드는 중" foreground notification while the
// AI editor works, and "책이 다 됐어요" when the person is not looking at the app.
import 'package:flutter/services.dart';

const _ch = MethodChannel('kr.dot.tripbook/making');

Future<void> makingStart(String title) => _ch.invokeMethod('start', {'title': title}).catchError((_) {});
Future<void> makingDone(String title, String book) => _ch.invokeMethod('done', {'title': title, 'book': book}).catchError((_) {});
Future<void> makingStop() => _ch.invokeMethod('stop').catchError((_) {});

/// The book the app was opened for from a "다 됐어요" notification (once), or null.
Future<String?> takeLaunchBook() async {
  try {
    return await _ch.invokeMethod<String>('takeLaunch');
  } on PlatformException {
    return null;
  } on MissingPluginException {
    return null;
  }
}

/// A "다 됐어요" tap while the app is open.
void onOpenBook(void Function(String book) f) => _ch.setMethodCallHandler((c) async {
      if (c.method == 'open' && c.arguments is String) f(c.arguments as String);
    });

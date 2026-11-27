// Dart side of kr.dot.tripbook/making (android/.../Making.kt): the "책 만드는 중" foreground notification while the
// AI editor works, and "책이 다 됐어요" when the person is not looking at the app.
import 'package:flutter/services.dart';

const _ch = MethodChannel('kr.dot.tripbook/making');

// one service for every job (a picking and a write can overlap now that the picking can be left): count them and stop
// the service only when the last one ends
var _jobs = 0;
Future<void> makingStart(String title) {
  _jobs++;
  return _ch.invokeMethod('start', {'title': title}).catchError((_) {});
}

/// [head]: the notification's title ("책이 다 됐어요" unless the picking finished: "사진을 다 골랐어요").
Future<void> makingDone(String title, String book, {String? head}) {
  _jobs = _jobs > 0 ? _jobs - 1 : 0;
  return _ch.invokeMethod('done', {'title': title, 'book': book, 'head': ?head, 'stop': _jobs == 0}).catchError((_) {});
}

Future<void> makingStop() {
  _jobs = _jobs > 0 ? _jobs - 1 : 0;
  return _jobs == 0 ? _ch.invokeMethod('stop').catchError((_) {}) : Future.value();
}

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

/// The phone's ANDROID_ID (kr.dot.tripbook/device): free books are counted by it on the server. Null off Android.
Future<String?> deviceId() async {
  try {
    return await const MethodChannel('kr.dot.tripbook/device').invokeMethod<String>('id');
  } on PlatformException {
    return null;
  } on MissingPluginException {
    return null;
  }
}

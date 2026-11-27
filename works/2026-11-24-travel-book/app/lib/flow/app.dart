// What the screens' hosts share: settings, the book store, the pipeline and the phone source.
import 'package:material_ui/material_ui.dart';

import '../book/phone_source.dart';
import '../book/store.dart';
import '../core/prefs.dart';
import '../pipeline.dart';

class App {
  final Prefs prefs;
  final BookStore store;
  final Pipeline pipe;
  final PhoneSource src;
  bool limited = false; // photo access "일부만 허용"
  final making = <String>{}; // books whose AI layout job this process is waiting for (never twice)
  final writes = <String, Future<AiState>>{}; // a book being made from the making screen; outlives that screen
  final stages = <String, ValueNotifier<(WriteStage, int, int)?>>{}; // where each of those is (the making screen shows it)
  final shelfChanged = ValueNotifier(0); // bumped when a book finishes away from its screen: the shelf reloads
  App({required this.prefs, required this.store, required this.pipe, required this.src});
  String get booksDir => store.root.path;
}

/// Runs [work] behind a spinner (the trip scan, a rewrite); the page under it stays as it was. [label] says what it is
/// doing on a small card under the spinner.
Future<T> busy<T>(BuildContext context, Future<T> Function() work, {String? label}) async {
  final nav = Navigator.of(context);
  nav.push(PageRouteBuilder<void>(
    opaque: false,
    barrierDismissible: false,
    transitionDuration: const Duration(milliseconds: 150),
    pageBuilder: (_, _, _) => ColoredBox(
      color: const Color(0x331E160E),
      child: Center(
        child: label == null
            ? const CircularProgressIndicator()
            : Container(
                padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
                decoration: BoxDecoration(color: const Color(0xFFF4EFE7), borderRadius: BorderRadius.circular(18)),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  const SizedBox(width: 26, height: 26, child: CircularProgressIndicator(strokeWidth: 2.4, color: Color(0xFF2B2621))),
                  const SizedBox(height: 12),
                  Text(label, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13.5, color: Color(0xFF2B2621), height: 1.45, decoration: TextDecoration.none)),
                ]),
              ),
      ),
    ),
  ));
  try {
    return await work();
  } finally {
    nav.pop();
  }
}

void toast(BuildContext context, String s) => ScaffoldMessenger.of(context)
  ..hideCurrentSnackBar()
  ..showSnackBar(SnackBar(content: Text(s)));

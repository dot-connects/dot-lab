// App settings kept in <app documents>/prefs.json (shared_preferences is not a dependency). Pure Dart + dart:io.
import 'dart:convert';
import 'dart:io';

class Prefs {
  final File file;
  String shelfView = 'display'; // display|timeline
  String quality = 'reduced'; // reduced|original — default for new books
  bool onboarded = false;
  bool? spread; // book view: two pages side by side; null = by the screen (wide screens open as spreads)
  int made = 0; // books this phone has made, as last heard from the server or counted here (never goes down)
  Prefs(this.file);

  /// Reads [dir]/prefs.json; a missing or broken file gives the defaults.
  static Prefs load(Directory dir) {
    final p = Prefs(File('${dir.path}${Platform.pathSeparator}prefs.json'));
    try {
      final j = jsonDecode(p.file.readAsStringSync()) as Map<String, dynamic>;
      p.shelfView = j['shelfView'] as String? ?? p.shelfView;
      p.quality = j['quality'] as String? ?? p.quality;
      p.onboarded = j['onboarded'] as bool? ?? false;
      p.spread = j['spread'] as bool?;
      p.made = j['made'] as int? ?? 0;
    } catch (_) {}
    return p;
  }

  void save() {
    final tmp = File('${file.path}.tmp')..writeAsStringSync(jsonEncode({'shelfView': shelfView, 'quality': quality, 'onboarded': onboarded, 'spread': spread, 'made': made}));
    tmp.renameSync(file.path);
  }
}

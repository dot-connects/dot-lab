// The trip as days and scenes, from photo times and places (no AI): what the AI editor must follow in order.
// A new scene starts after a long gap (60 min) or when the place name changes. Sent to /layout as "여행의 흐름".
import 'models.dart';

const _days = ['첫째', '둘째', '셋째', '넷째', '다섯째', '여섯째', '일곱째', '여덟째', '아홉째', '열째'];
const _wd = ['월', '화', '수', '목', '금', '토', '일'];
String _hm(DateTime t) => '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

class Scene {
  final String day; // yyyy-MM-dd
  final List<Candidate> items;
  const Scene(this.day, this.items);
  String get place {
    final n = <String, int>{};
    for (final c in items) {
      if (c.place != null) n[c.place!] = (n[c.place!] ?? 0) + 1;
    }
    return n.isEmpty ? '' : (n.entries.toList()..sort((a, b) => b.value.compareTo(a.value))).first.key;
  }
}

List<Scene> scenesOf(List<Candidate> picked, {Duration gap = const Duration(minutes: 60)}) {
  final xs = [...picked]..sort((a, b) => a.local.compareTo(b.local));
  final out = <Scene>[];
  for (final c in xs) {
    final day = ymd(c.local);
    final last = out.isEmpty ? null : out.last;
    final prev = last?.items.last;
    final split = last == null ||
        last.day != day ||
        c.local.difference(prev!.local) > gap ||
        (c.place != null && prev.place != null && c.place != prev.place);
    if (split) {
      out.add(Scene(day, [c]));
    } else {
      last.items.add(c);
    }
  }
  return out;
}

/// photo id -> its scene's position in the book (0, 1, 2 ...): the order the pages must keep.
Map<String, int> sceneIndex(List<Scene> scenes) => {
      for (final (i, s) in scenes.indexed)
        for (final c in s.items) c.id: i,
    };

/// The /layout "scenes" field: [{label, scenes:[{from, to, place, ids}]}] per day.
List<Map<String, Object>> outlineJson(List<Scene> scenes) {
  final days = <String>[for (final s in scenes) s.day].toSet().toList();
  return [
    for (final (i, d) in days.indexed)
      {
        'label': () {
          final t = DateTime.parse(d);
          return '${i < _days.length ? _days[i] : '${i + 1}번째'} 날 · ${t.month}월 ${t.day}일 ${_wd[t.weekday - 1]}';
        }(),
        'scenes': [
          for (final s in scenes.where((s) => s.day == d))
            {'from': _hm(s.items.first.local), 'to': _hm(s.items.last.local), 'place': s.place, 'ids': [for (final c in s.items) c.id]},
        ],
      },
  ];
}

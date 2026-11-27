// Shared plain types for the book pipeline. No Flutter or plugin imports: the pure core is tested on the VM.

class Shot {
  final String id;
  final DateTime utc;
  final DateTime? wall; // EXIF DateTimeOriginal: what the camera clock showed
  final Duration? exifOffset; // EXIF OffsetTimeOriginal
  final bool isVideo;
  final int durationMs;
  final double? lat, lng;
  final String relPath;
  final int w, h;
  const Shot({
    required this.id,
    required this.utc,
    this.wall,
    this.exifOffset,
    this.isVideo = false,
    this.durationMs = 0,
    this.lat,
    this.lng,
    this.relPath = '',
    this.w = 0,
    this.h = 0,
  });
  bool get portrait => h > w;

  Map<String, dynamic> toJson() => {
        'id': id, 'utc': utc.toIso8601String(), 'wall': wall?.toIso8601String(), 'off': exifOffset?.inMinutes,
        'v': isVideo, 'dur': durationMs, 'lat': lat, 'lng': lng, 'rel': relPath, 'w': w, 'h': h,
      };
  factory Shot.fromJson(Map<String, dynamic> j) => Shot(
        id: j['id'] as String,
        utc: DateTime.parse(j['utc'] as String),
        wall: j['wall'] == null ? null : DateTime.parse(j['wall'] as String),
        exifOffset: j['off'] == null ? null : Duration(minutes: j['off'] as int),
        isVideo: j['v'] as bool,
        durationMs: j['dur'] as int,
        lat: (j['lat'] as num?)?.toDouble(),
        lng: (j['lng'] as num?)?.toDouble(),
        relPath: j['rel'] as String,
        w: j['w'] as int,
        h: j['h'] as int,
      );
}

class Candidate {
  final Shot shot;
  final double sharp, luma;
  final int dhash;
  final String? place;
  final DateTime local;
  const Candidate(this.shot, {required this.sharp, required this.luma, required this.dhash, this.place, required this.local});
  String get id => shot.id;
  Candidate withPlace(String? p) => Candidate(shot, sharp: sharp, luma: luma, dhash: dhash, place: p, local: local);
  Map<String, dynamic> toJson() =>
      {'shot': shot.toJson(), 'sharp': sharp, 'luma': luma, 'dhash': dhash, 'place': place, 'local': local.toIso8601String()};
  factory Candidate.fromJson(Map<String, dynamic> j) => Candidate(Shot.fromJson(j['shot'] as Map<String, dynamic>),
      sharp: (j['sharp'] as num).toDouble(), luma: (j['luma'] as num).toDouble(), dhash: j['dhash'] as int,
      place: j['place'] as String?, local: DateTime.parse(j['local'] as String));
}

class Pick {
  final String id;
  final String caption;
  final int score; // 0..100
  final String kind; // /pick's look at the photo: 풍경|사람|음식|실내|이동|물건|기타 ('' = unknown)
  final String what; // what the photo shows, a short noun phrase ('' = unknown)
  const Pick(this.id, this.caption, this.score, {this.kind = '', this.what = ''});
  Map<String, dynamic> toJson() => {'id': id, 'caption': caption, 'score': score, if (kind.isNotEmpty) 'kind': kind, if (what.isNotEmpty) 'what': what};
  factory Pick.fromJson(Map<String, dynamic> j) => Pick(j['id'] as String, j['caption'] as String, j['score'] as int,
      kind: j['kind'] as String? ?? '', what: j['what'] as String? ?? '');
}

class Clip {
  final String id;
  final int startMs, endMs;
  final String? file;
  const Clip(this.id, this.startMs, this.endMs, [this.file]);
  Map<String, dynamic> toJson() => {'id': id, 'startMs': startMs, 'endMs': endMs, 'file': file};
  factory Clip.fromJson(Map<String, dynamic> j) =>
      Clip(j['id'] as String, j['startMs'] as int, j['endMs'] as int, j['file'] as String?);
}

class BookOptions {
  String length = 'normal'; // short|normal|long
  String? theme; // null = recommended
  String textAmount = 'caption_essay'; // photos|caption|caption_essay
  String tone = 'auto'; // auto|diary|kind|humor
  String chapterBy = 'day'; // day|place
  String people = 'mix'; // mix|scenery|people
  Set<String> extras = {'numbers', 'map', 'essay', 'video'};
  String format = 'phone'; // phone|a5|square
  String quality = 'reduced'; // reduced|original
  bool toneMatch = false;
  int highlightSec = 45;
  String music = 'calm';
  String? who; // 혼자|연인·부부|친구|가족|부모님 or the user's own words; null = skipped
  String about = ''; // one free line: what kind of trip it was

  Map<String, dynamic> toJson() => {
        'length': length, 'theme': theme, 'textAmount': textAmount, 'tone': tone, 'chapterBy': chapterBy,
        'people': people, 'extras': extras.toList(), 'format': format, 'quality': quality,
        'toneMatch': toneMatch, 'highlightSec': highlightSec, 'music': music, 'who': who, 'about': about,
      };
  static BookOptions fromJson(Map<String, dynamic> j) => BookOptions()
    ..length = j['length'] as String
    ..theme = j['theme'] as String?
    ..textAmount = j['textAmount'] as String
    ..tone = j['tone'] as String
    ..chapterBy = j['chapterBy'] as String
    ..people = j['people'] as String
    ..extras = (j['extras'] as List).cast<String>().toSet()
    ..format = j['format'] as String
    ..quality = j['quality'] as String
    ..toneMatch = j['toneMatch'] as bool
    ..highlightSec = j['highlightSec'] as int
    ..music = j['music'] as String
    ..who = j['who'] as String?
    ..about = j['about'] as String? ?? '';
}

class Chapter {
  final String day; // yyyy-MM-dd, local
  final String? place;
  final List<Candidate> items;
  const Chapter(this.day, this.place, this.items);
}

class BookPlan {
  final List<String> titles;
  final String theme;
  final List<Pick> picks;
  final List<(String, String)> ask;
  final Map<String, String> chapterLines; // yyyy-MM-dd -> line
  final String essay;
  final List<Clip> clips;
  final bool aiText;
  const BookPlan({
    required this.titles,
    required this.theme,
    required this.picks,
    this.ask = const [],
    this.chapterLines = const {},
    this.essay = '',
    this.clips = const [],
    this.aiText = false,
  });
}

class LayoutDef {
  final String id, kind; // kind: cover|chapter|photo|text|end|video (older themes: numbers|map|essay|back)
  final List<String> slots; // P|L|S|A|V
  final List<String> texts;
  final Map<String, int> limits; // text key -> max characters (designer layouts, r11)
  final String when; // when the editor should use it (read by the model)
  final List<String> formats;
  const LayoutDef(this.id, this.kind, this.slots,
      {this.texts = const [], this.limits = const {}, this.when = '', this.formats = const ['phone', 'a5', 'square']});
  factory LayoutDef.fromJson(Map<String, dynamic> j) {
    final t = j['texts'];
    final limits = t is Map ? {for (final e in t.entries) e.key as String: (e.value as num).toInt()} : <String, int>{};
    return LayoutDef(
      j['id'] as String,
      j['kind'] as String,
      (j['slots'] as List).cast<String>(),
      texts: t is Map ? limits.keys.toList() : (t as List? ?? const []).cast<String>(),
      limits: limits,
      when: j['when'] as String? ?? '',
      formats: (j['formats'] as List? ?? const ['phone', 'a5', 'square']).cast<String>(),
    );
  }
}

class PageSpec {
  final String layout;
  final List<String> ids;
  final List<String> crop;
  final Map<String, String> text;
  PageSpec(this.layout, this.ids, {List<String>? crop, Map<String, String>? text})
      : crop = crop ?? List.filled(ids.length, '50% 50%'),
        text = text ?? {};
  Map<String, dynamic> toJson() => {'layout': layout, 'ids': ids, 'crop': crop, 'text': text};
  factory PageSpec.fromJson(Map<String, dynamic> j) => PageSpec(j['layout'] as String, (j['ids'] as List).cast<String>(),
      crop: (j['crop'] as List).cast<String>(), text: (j['text'] as Map).cast<String, String>());
}

String ymd(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

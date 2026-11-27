// One book as saved on the phone (books/<id>/book.json). It is written after every step, so a crash or a restart
// resumes where it stopped: "picked" = photos chosen, waiting for the user's review and /write; "written" = done.
import 'models.dart';
import 'plan_check.dart';

class Book {
  final String id;
  String stage; // picked | written
  String title;
  final DateTime from, to; // trip days (local)
  BookOptions opts;
  String theme;
  final List<Candidate> pool; // every candidate (swap / [+] / write labels need them after a restart)
  List<Pick> picks;
  List<Clip> clips;
  Map<String, String> pickLines; // chapter lines from /pick
  Set<String> mustInclude;
  Map<String, WriteResult> texts; // text shape -> what /write produced
  int rewrites;
  String? cover; // photo id on the cover
  String? coverColor; // 다듬기: "#rrggbb" behind the cover's words; null = the theme's own paper
  String? font; // 다듬기: a CSS family from themeFonts; null = the theme's own
  String textSize; // 다듬기: s|m|l for captions and body text
  List<PageSpec> pages;
  List<PageSpec> original;
  List<String> spare;
  String? job; // the AI editor's server job while it runs (survives the app being closed); null when idle
  List<(String kind, List<String> ids)> groups; // /pick's page groups (연속 · 음식), handed to the layout editor

  Book({
    required this.id,
    required this.from,
    required this.to,
    required this.opts,
    required this.theme,
    required this.pool,
    required this.picks,
    this.stage = 'picked',
    this.title = '',
    this.clips = const [],
    this.pickLines = const {},
    Set<String>? mustInclude,
    Map<String, WriteResult>? texts,
    this.rewrites = 0,
    this.cover,
    this.coverColor,
    this.font,
    this.textSize = 'm',
    this.pages = const [],
    this.original = const [],
    this.spare = const [],
    this.job,
    this.groups = const [],
  })  : mustInclude = mustInclude ?? {},
        texts = texts ?? {};

  bool get written => stage == 'written';

  Map<String, dynamic> toJson() => {
        'v': 1, 'id': id, 'stage': stage, 'title': title, 'from': ymd(from), 'to': ymd(to), 'opts': opts.toJson(),
        'theme': theme, 'pool': [for (final c in pool) c.toJson()], 'picks': [for (final p in picks) p.toJson()],
        'clips': [for (final c in clips) c.toJson()], 'pickLines': pickLines, 'must': mustInclude.toList(),
        'texts': {for (final e in texts.entries) e.key: e.value.toJson()}, 'rewrites': rewrites, 'cover': cover,
          'coverColor': coverColor, 'font': font, 'size': textSize, 'pages': [for (final p in pages) p.toJson()],
        'original': [for (final p in original) p.toJson()], 'spare': spare, 'job': job,
        'groups': [for (final (k, ids) in groups) {'kind': k, 'ids': ids}],
      };

  factory Book.fromJson(Map<String, dynamic> j) => Book(
        id: j['id'] as String,
        stage: j['stage'] as String,
        title: j['title'] as String? ?? '',
        from: DateTime.parse(j['from'] as String),
        to: DateTime.parse(j['to'] as String),
        opts: BookOptions.fromJson(j['opts'] as Map<String, dynamic>),
        theme: j['theme'] as String,
        pool: [for (final c in j['pool'] as List) Candidate.fromJson(c as Map<String, dynamic>)],
        picks: [for (final p in j['picks'] as List) Pick.fromJson(p as Map<String, dynamic>)],
        clips: [for (final c in j['clips'] as List) Clip.fromJson(c as Map<String, dynamic>)],
        pickLines: (j['pickLines'] as Map).cast<String, String>(),
        mustInclude: (j['must'] as List).cast<String>().toSet(),
        texts: {
          for (final e in (j['texts'] as Map).entries) e.key as String: WriteResult.fromJson(e.value as Map<String, dynamic>),
        },
        rewrites: j['rewrites'] as int,
        cover: j['cover'] as String?,
        coverColor: j['coverColor'] as String?,
        font: j['font'] as String?,
        textSize: j['size'] as String? ?? 'm',
        pages: [for (final p in j['pages'] as List) PageSpec.fromJson(p as Map<String, dynamic>)],
        original: [for (final p in j['original'] as List) PageSpec.fromJson(p as Map<String, dynamic>)],
        spare: (j['spare'] as List).cast<String>(),
        job: j['job'] as String?,
        groups: [
          for (final g in (j['groups'] as List? ?? const []))
            if (g is Map) (g['kind'] as String, (g['ids'] as List).cast<String>()),
        ],
      );
}

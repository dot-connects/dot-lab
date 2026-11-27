// Worker client: /pick, /write, /rewrite. The response is a stream of keep-alive spaces then one JSON object;
// errors after the stream started arrive in the body ({error: "model" | "upstream" | "off"}), so the body decides.
// The token and URL come from --dart-define-from-file=secrets.json (never in source).
import 'dart:convert';

import 'package:http/http.dart' as http;

const _url = String.fromEnvironment('WORKER_URL');
const _token = String.fromEnvironment('APP_TOKEN');

/// ok = the answer map; off = month/day limit or AI switched off; failed = anything else (offline, model error).
sealed class AiResult {
  const AiResult();
}

class AiOk extends AiResult {
  final Map<String, dynamic> body;
  const AiOk(this.body);
}

class AiOff extends AiResult {
  const AiOff();
}

class AiFailed extends AiResult {
  final String why;
  const AiFailed(this.why);
}

class AiImage {
  final String id;
  final List<int> jpeg; // 512 px long side
  final int? atMs; // video scene time
  const AiImage(this.id, this.jpeg, {this.atMs});
  Map<String, dynamic> toJson() => {'id': id, 'b64': base64Encode(jpeg), if (atMs != null) 'atMs': atMs};
}

class AiShot {
  final String id, local; // "2026-06-12T13:20"
  final String? place;
  final bool video;
  final int durMs, w, h;
  final String kind, what; // /pick's tags, sent to /layout
  // what the phone and the first look found (/scout, /pick): kind tag, people, shots in its moment, pose series,
  // the moment's number; /layout: the page group ("연속 1", "음식 1")
  final String tag, group;
  final int people, busy, series, scene, hl; // hl: the first look's "main scene" mark 0..2
  const AiShot(this.id, this.local, this.place,
      {this.video = false,
      this.durMs = 0,
      this.w = 0,
      this.h = 0,
      this.kind = '',
      this.what = '',
      this.tag = '',
      this.group = '',
      this.people = 0,
      this.busy = 0,
      this.series = 0,
      this.scene = -1,
      this.hl = 0});
  Map<String, dynamic> toJson() => {
        'id': id, 'local': local, 'place': place ?? '', 'video': video, 'durMs': durMs, if (w > 0) 'w': w, if (h > 0) 'h': h,
        if (kind.isNotEmpty) 'kind': kind, if (what.isNotEmpty) 'what': what,
        if (tag.isNotEmpty) 'tag': tag, if (group.isNotEmpty) 'group': group, if (people > 0) 'people': people,
        if (busy > 0) 'busy': busy, if (series > 0) 'series': series, if (scene >= 0) 'scene': scene, if (hl > 0) 'hl': hl,
      };
}

class AiClient {
  final http.Client _http;
  final String url, token;
  AiClient({http.Client? client, String? url, String? token})
      : _http = client ?? http.Client(),
        url = url ?? _url,
        token = token ?? _token;

  bool get configured => url.isNotEmpty && token.isNotEmpty;

  Future<AiResult> pick(List<AiImage> images, List<AiShot> shots,
          {List<Map<String, String?>> notes = const [],
          Map<String, Object?> opts = const {},
          List<String> mustInclude = const [],
          int target = 0,
          Map<String, Object>? highlights}) =>
      _post('/pick', {
        'images': images, 'shots': shots, 'notes': notes, 'opts': opts, 'mustInclude': mustInclude, if (target > 0) 'target': target,
        'highlights': ?highlights,
      });

  /// The first, wide look: up to 100 small previews tagged by a small model ({tags: [{n, k, p, s, e}]}, n = index).
  Future<AiResult> scout(List<AiImage> images, List<AiShot> shots, {Map<String, Object?> opts = const {}}) =>
      _post('/scout', {'images': images, 'shots': shots, 'opts': opts});

  Future<AiResult> write(List<AiImage> images, List<AiShot> shots, String shape,
          {List<Map<String, String?>> notes = const [], Map<String, Object?> opts = const {}}) =>
      _post('/write', {'images': images, 'shots': shots, 'notes': notes, 'opts': opts, 'shape': shape});

  /// The whole book: order, a designer layout per page, photos into its slots, the texts (mockups/r11/engine).
  /// Starts a server job named [job] and answers at once ({status: pending}); the plan comes from [result].
  /// The same [job] twice never calls the model twice. [fix]/[prev] = one more try after checkLayout found problems.
  Future<AiResult> layout(List<AiImage> images, List<AiShot> shots,
          {required String job,
          required String theme,
          required List<Map<String, Object>> catalog,
          required Map<String, Object> rules,
          List<Map<String, Object>> scenes = const [],
          List<Map<String, String?>> notes = const [],
          Map<String, Object?> opts = const {},
          List<String>? fix,
          Map<String, dynamic>? prev}) =>
      _post('/layout', {
        'job': job,
        'images': images, 'shots': shots, 'notes': notes, 'opts': opts, 'theme': theme, 'catalog': catalog, 'rules': rules,
        if (scenes.isNotEmpty) 'scenes': scenes,
        if (fix != null) 'fix': fix, if (prev != null) 'prev': prev,
      });

  /// The layout job's state: {status: pending} | the plan {title, plan, pages} | {error} (AiFailed).
  Future<AiResult> result(String job) => _post('/result', {'job': job});

  Future<AiResult> rewrite(List<AiImage> images, Map<String, String> page, String shape, {List<Map<String, String?>> notes = const []}) =>
      _post('/rewrite', {'images': images, 'page': page, 'notes': notes, 'shape': shape});

  /// Tells the server this phone's written [books] (already-known ones change nothing) and returns how many books the
  /// phone has ever made, or null when the server cannot be reached. Free books are counted by this, so deleting a
  /// book or reinstalling the app never gives one back.
  Future<int?> made(String device, List<String> books) async {
    final r = await _post('/made', {'device': device, 'books': books.take(50).toList()});
    return r is AiOk && r.body['made'] is int ? r.body['made'] as int : null;
  }

  Future<AiResult> _post(String path, Map<String, Object?> body) async {
    if (!configured) return const AiFailed('not configured');
    try {
      final r = await _http
          .post(Uri.parse('$url$path'),
              headers: {'content-type': 'application/json', 'x-app-token': token}, body: jsonEncode(body))
          .timeout(const Duration(seconds: 300));
      if (r.statusCode == 503) return const AiOff();
      if (r.statusCode != 200) return AiFailed('http ${r.statusCode}');
      final j = jsonDecode(utf8.decode(r.bodyBytes).trim());
      if (j is! Map<String, dynamic>) return const AiFailed('shape');
      if (j['error'] == 'off') return const AiOff();
      if (j.containsKey('error')) return AiFailed('${j['error']}');
      return AiOk(j);
    } catch (e) {
      return AiFailed('$e');
    }
  }
}

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
  final int durMs;
  const AiShot(this.id, this.local, this.place, {this.video = false, this.durMs = 0});
  Map<String, dynamic> toJson() => {'id': id, 'local': local, 'place': place ?? '', 'video': video, 'durMs': durMs};
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
          {List<Map<String, String?>> notes = const [], Map<String, Object?> opts = const {}, List<String> mustInclude = const []}) =>
      _post('/pick', {'images': images, 'shots': shots, 'notes': notes, 'opts': opts, 'mustInclude': mustInclude});

  Future<AiResult> write(List<AiImage> images, List<AiShot> shots, String shape,
          {List<Map<String, String?>> notes = const [], Map<String, Object?> opts = const {}}) =>
      _post('/write', {'images': images, 'shots': shots, 'notes': notes, 'opts': opts, 'shape': shape});

  Future<AiResult> rewrite(List<AiImage> images, Map<String, String> page, String shape, {List<Map<String, String?>> notes = const []}) =>
      _post('/rewrite', {'images': images, 'page': page, 'notes': notes, 'shape': shape});

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

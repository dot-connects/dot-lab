// Dart side of kr.dot.tripbook/media (android/.../MediaChannel.kt).
import 'package:flutter/services.dart';
import 'package:photo_manager/photo_manager.dart';

const _ch = MethodChannel('kr.dot.tripbook/media');

/// "+09:00" / "-10:00" -> Duration.
Duration? parseOffset(String? s) {
  final m = RegExp(r'^([+-])(\d{2}):?(\d{2})$').firstMatch(s ?? '');
  if (m == null) return null;
  final d = Duration(hours: int.parse(m.group(2)!), minutes: int.parse(m.group(3)!));
  return m.group(1) == '-' ? -d : d;
}

/// Camera wall clock and EXIF offset of one photo (null fields when the file has none).
Future<({DateTime? wall, Duration? offset})> exifOf(AssetEntity a) async {
  try {
    final uri = await a.getMediaUrl();
    if (uri == null) return (wall: null, offset: null);
    final r = await _ch.invokeMapMethod<String, String?>('exif', {'uri': uri});
    final w = r?['wall'];
    return (wall: w == null ? null : DateTime.tryParse(w), offset: parseOffset(r?['offset']));
  } on PlatformException {
    return (wall: null, offset: null);
  }
}

/// Four scenes (15/40/65/90 % of the length), JPEG, long side 512.
Future<List<Uint8List>> videoFrames(AssetEntity a) async {
  final uri = await a.getMediaUrl();
  if (uri == null) return const [];
  final r = await _ch.invokeListMethod<Uint8List>('videoFrames', {'uri': uri});
  return r ?? const [];
}

/// A 720p copy of [startMs]..[endMs] at [out] (the book's video slot plays it).
Future<String?> trimClip(AssetEntity a, int startMs, int endMs, String out) async {
  final uri = await a.getMediaUrl();
  if (uri == null) return null;
  return _ch.invokeMethod<String>('trim', {'uri': uri, 'startMs': startMs, 'endMs': endMs, 'out': out});
}

Future<void> keepScreenOn(bool on) => _ch.invokeMethod('keepScreenOn', {'on': on});

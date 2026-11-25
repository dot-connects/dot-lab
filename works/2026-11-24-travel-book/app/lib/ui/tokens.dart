// App-screen design tokens, copied from mockups/shelf/r4-all.src.html :root and its classes.
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' show Codec, ImmutableBuffer;

import 'package:flutter/foundation.dart' show SynchronousFuture;

import 'package:material_ui/material_ui.dart';

class C {
  static const paper = Color(0xFFF4EFE7);
  static const ink = Color(0xFF2B2621);
  static const ink2 = Color(0xFF7D7366);
  static const oak1 = Color(0xFFC69C6D);
  static const oak2 = Color(0xFF9A6F45);
  static const tint = Color(0xFFE7DCCB);
  static const line = Color(0xFFCCC7BC);
  static const body = Color(0xFF4D463E); // paragraph text
  static const card = Color(0xFFEFE8DD); // .alb, .add>div, .sg>div
  static const cardOn = Color(0xFFE4D7C3); // .alb.sel, .add>div.fill
  static const warn = Color(0xFFEFDCC2); // delete confirm
  static const wallBottom = Color(0xFFEFE8DC);
  static const dash = Color(0xFFBBAA90);
  static const toggleOff = Color(0xFFD8CDBC);
  static const pickedBg = Color(0xFFD9C9B0);
  static const gridBg = Color(0xFFDDD3C4);
  static const zeroRing = Color(0xFFB9AE9E);
  static const subOnInk = Color(0xFFCFC6B9);
}

// App UI font. The mockup uses IBM Plex Sans KR, which is not bundled yet: Pretendard stands in until it is.
const fUi = 'Pretendard';
const fNum = 'InterTight';

/// UI text style (Pretendard).
TextStyle ui(double size, {FontWeight w = FontWeight.w400, Color color = C.ink, double? h}) =>
    TextStyle(fontFamily: fUi, fontSize: size, fontWeight: w, color: color, height: h);

/// Number text style (Inter Tight, a variable font: weight goes through the wght axis).
TextStyle nm(double size, {FontWeight w = FontWeight.w500, Color color = C.ink, double? h, double? ls}) => TextStyle(
      fontFamily: fNum,
      fontFamilyFallback: const [fUi],
      fontSize: size,
      fontWeight: w,
      fontVariations: [FontVariation('wght', w.value.toDouble())],
      color: color,
      height: h,
      letterSpacing: ls,
    );

/// CSS word-break:keep-all: glue the letters of each word so lines break only at spaces.
String kw(String s) => s.split(' ').map((w) => w.characters.join('⁠')).join(' ');

const weekdays = ['월', '화', '수', '목', '금', '토', '일']; // DateTime.weekday 1..7

String fmtInt(int n) => n.toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => ',');

String two(int n) => n.toString().padLeft(2, '0');

ThemeData appTheme() => ThemeData(
      fontFamily: fUi,
      scaffoldBackgroundColor: C.paper,
      colorScheme: ColorScheme.fromSeed(seedColor: C.ink, surface: C.paper),
      splashFactory: NoSplash.splashFactory,
    );

/// "assets/..." = bundled, `gallery:<id>` = a phone photo's thumbnail (via [galleryThumbs]), else a file.
ImageProvider imageOf(String path) => path.startsWith('assets/')
    ? AssetImage(path)
    : path.startsWith('gallery:')
        ? GalleryImage(path.substring(8))
        : FileImage(File(path));

/// Thumbnail loader for `gallery:<id>` images; set once at start-up (main.dart) to the phone source.
Future<Uint8List?> Function(String id, int long)? galleryThumbs;

class GalleryImage extends ImageProvider<GalleryImage> {
  final String id;
  final int long;
  const GalleryImage(this.id, {this.long = 320});

  @override
  Future<GalleryImage> obtainKey(ImageConfiguration configuration) => SynchronousFuture(this);

  @override
  ImageStreamCompleter loadImage(GalleryImage key, ImageDecoderCallback decode) =>
      MultiFrameImageStreamCompleter(codec: _load(decode), scale: 1);

  Future<Codec> _load(ImageDecoderCallback decode) async {
    final bytes = await galleryThumbs?.call(id, long);
    if (bytes == null) throw StateError('no thumbnail for $id');
    return decode(await ImmutableBuffer.fromUint8List(bytes));
  }

  @override
  bool operator ==(Object other) => other is GalleryImage && other.id == id && other.long == long;
  @override
  int get hashCode => Object.hash(id, long);
}

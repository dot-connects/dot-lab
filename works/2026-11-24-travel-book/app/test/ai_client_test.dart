import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:tripbook/ai/client.dart';

AiClient client(int status, String body) =>
    AiClient(client: MockClient((_) async => http.Response.bytes(utf8.encode(body), status)), url: 'https://w', token: 't');

void main() {
  const img = AiImage('p0', [1, 2, 3]);

  test('keep-alive spaces before the JSON are fine', () async {
    final r = await client(200, '      {"picks":[]}').pick([img], const []);
    expect(r, isA<AiOk>());
  });

  test('body error after a 200 stream decides: off -> AiOff, model -> AiFailed', () async {
    expect(await client(200, '  {"error":"off"}').pick([img], const []), isA<AiOff>());
    expect(await client(200, '  {"error":"model"}').write([img], const [], 'short'), isA<AiFailed>());
  });

  test('503 before streaming is the limit; other statuses fail', () async {
    expect(await client(503, '{"error":"off"}').pick([img], const []), isA<AiOff>());
    expect(await client(413, '{}').pick([img], const []), isA<AiFailed>());
  });

  test('not configured: no request, failed', () async {
    expect(await AiClient(url: '', token: '').pick([img], const []), isA<AiFailed>());
  });

  test('made: the server count, or null when it cannot be had (the app then keeps its own count)', () async {
    expect(await client(200, '{"made":3}').made('a1b2c3d4e5f60718', ['1']), 3);
    expect(await client(400, '{"error":"body"}').made('x', []), isNull);
    expect(await client(200, '{"other":1}').made('a1b2c3d4e5f60718', []), isNull);
  });
}

# Subset the Hangul clock fonts (gowun_batang_700, jua, nanum_pen) to the glyphs the clock draws: 13 MB -> 0.2 MB.
# Run: PYTHONIOENCODING=utf-8 python tools/kfont_chars.py > chars.txt
#      uvx --from fonttools pyftsubset <full.ttf> --text-file=chars.txt --layout-features="*" --output-file=standby/src/main/res/font/<name>.ttf
"""Characters the Hangul clock draws in its kfont (time words, date line, info line). Missing glyphs fall back to the system font."""
words = ("오전 오후 정각 영 한 두 세 네 다섯 여섯 일곱 여덟 아홉 열 시 분 일 이 삼 사 오 육 칠 팔 구 십 "
         "월 화 수 목 금 토 일 요일 알람 내일 오늘 맑음 흐림 구름 비 눈 소나기 안개 천둥 번개 많음 조금 최고 최저")
ascii = "".join(chr(c) for c in range(0x20, 0x7F))
extra = "·°…–—"
print("".join(sorted(set(words.replace(" ", "") + ascii + extra))))

package kr.dot.sb.ui.clock

/**
 * Fixed colours of the r10 faces with their own look (mockup `PART`, `COMBO2`, `COMBO3`, `LEDS`; plan-r10 "New-face
 * colours"). Read at render time when the `ClockStyle` field is null; not theme-derived, `withTheme` leaves them.
 * The digits/text `color` and the pre-r10 colour fields keep coming from the theme defaults.
 */
object FaceDefaults {
    const val HOUR = 0xFFFF9C56
    const val MINUTE = 0xFFFFFFFF
    const val ACCENT = 0xFFD8B46A
    const val PLATE = 0xFF1E1E20
    const val DIGIT = 0xFFFFFFFF
    const val PANEL = 0xFFAAB59B
    const val INK = 0xFF1C2217
    const val DOT = 0xFFFFB000
    const val FACE = 0xFFF1ECE2
    const val CALENDAR = 0xFFC8402A
    const val BAR = 0xFF30CEDA
    const val RIM = 0xFFFF9C56
    const val PAPER = 0xFFF4EFE6

    /** LED colours: 빨강, 초록, 파랑, 호박; the first is the default. */
    val LEDS = listOf(0xFFFF3A22, 0xFF3DFF6E, 0xFF3AA0FF, 0xFFFFB000)

    /** 색 블록 background pairs (hour half, minute half); [kr.dot.sb.model.ClockStyle.pairPreset] indexes it, null = 0. */
    val PAIRS = listOf(
        listOf(0xFFFFCF33, 0xFFFF6F8F), listOf(0xFF7BDFF2, 0xFFFFD166), listOf(0xFFB8F2A0, 0xFF5B6CFF),
        listOf(0xFFFF8A5B, 0xFF2D2A4A), listOf(0xFFF4EFE6, 0xFF16130F), listOf(0xFFC6B3FF, 0xFFFFE08A),
    )

    /** 스티커 / 공 숫자 per-digit trios; [kr.dot.sb.model.ClockStyle.trioPreset] indexes it, null = 0. */
    val TRIOS = listOf(
        listOf(0xFFFF6B6B, 0xFFFFD93D, 0xFF4D96FF), listOf(0xFFFF8A5B, 0xFFFFD166, 0xFF7BDFF2),
        listOf(0xFFF72585, 0xFF7209B7, 0xFF4CC9F0), listOf(0xFFB8F2A0, 0xFFFFE08A, 0xFFFFB3C7),
        listOf(0xFFFFFFFF, 0xFFD5D9DF, 0xFF9A9AA0),
    )
}

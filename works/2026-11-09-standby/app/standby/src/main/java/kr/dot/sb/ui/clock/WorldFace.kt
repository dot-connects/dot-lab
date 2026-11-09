package kr.dot.sb.ui.clock

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.ui.theme.Ink
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Seoul until the city list reaches the face (see report: contract). */
private const val CITY_LAT = 37.57f
private const val CITY_LON = 126.98f

/** Solar declination (rad) and subsolar longitude (deg) for a local time in the system zone. */
internal fun sun(now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Pair<Float, Float> {
    val utc = now.atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
    val decl = (-23.44 * cos(2 * PI / 365 * (utc.dayOfYear + 10)) * PI / 180).toFloat()
    val lon = (-((utc.hour + utc.minute / 60.0) - 12) * 15).toFloat()
    return decl to lon
}

/**
 * World (spec C5, design-system §4.3): dot map with the sunlit half brighter, terminator line, city dot in the
 * accent; bottom-left block = arrow + city (accent) over "오후 9:41" (time in ink, 12 h with 오전/오후).
 */
@Composable
internal fun WorldFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val bold = rememberUi(700)
    val reg = rememberUi(500)
    val look = Look.of(style, ink, base = Color.White, base2 = Color(style.color2))
    val lit = ink.mapped(Color(style.litDotColor)).toArgb()
    val dark = ink.ink3.toArgb()
    val acc = ink.acc.toArgb()
    val grey = ink.ink2.toArgb()
    val bg = if (inSlot) null else bgColors(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val portrait = h > w * 1.15f
        val s = min(w, h)
        // map box → square cells, centred in the box
        val bx0 = if (portrait) 0.06f * w else 0.128f * w
        val bx1 = if (portrait) 0.94f * w else 0.936f * w
        val by0 = if (portrait) 0.24f * h else 0.129f * h
        val by1 = if (portrait) 0.62f * h else 0.861f * h
        val rows = WORLD_MAP.size; val cols = WORLD_MAP[0].length
        val cell = min((bx1 - bx0) / cols, (by1 - by0) / rows)
        val mx0 = (bx0 + bx1 - cell * cols) / 2
        // portrait: centre the map between the alarm row and the city/time block
        val my0 = if (portrait) (0.14f * h + 0.86f * h - 0.25f * s - cell * rows) / 2 else (by0 + by1 - cell * rows) / 2
        val mw = cell * cols; val mh = cell * rows
        val (decl, sslon) = sun(d.now)
        val rad = (PI / 180).toFloat()
        val litDots = ArrayList<Float>(); val darkDots = ArrayList<Float>()
        for (r in 0 until rows) for (k in 0 until cols) {
            if (WORLD_MAP[r][k] != '1') continue
            val lat = MAP_LAT0 - (r + 0.5f) / rows * (MAP_LAT0 - MAP_LAT1)
            val lon = -180f + (k + 0.5f) / cols * 360f
            val cz = sin(lat * rad) * sin(decl) + cos(lat * rad) * cos(decl) * cos((lon - sslon) * rad)
            (if (cz > 0) litDots else darkDots).apply { add(mx0 + (k + 0.5f) * cell); add(my0 + (r + 0.5f) * cell) }
        }
        val term = Path()
        var pen = false
        for (i in 0..180) {
            val lon = -180f + i * 2f
            val lat = atan(-cos(decl) * cos((lon - sslon) * rad) / sin(decl)) / rad
            // only where the line crosses the map's latitude band (no flat runs along its edge)
            if (lat !in MAP_LAT1..MAP_LAT0) { pen = false; continue }
            val y = my0 + (MAP_LAT0 - lat) / (MAP_LAT0 - MAP_LAT1) * mh
            val x = mx0 + (lon + 180f) / 360f * mw
            if (pen) term.lineTo(x, y) else term.moveTo(x, y)
            pen = true
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark; this.style = Paint.Style.STROKE; strokeWidth = max(1.dp.toPx(), cell * 0.14f) }
        val cityX = mx0 + (CITY_LON + 180f) / 360f * mw
        val cityY = my0 + (MAP_LAT0 - CITY_LAT) / (MAP_LAT0 - MAP_LAT1) * mh

        // bottom-left block: city label over the time
        val bx = 0.074f * w
        val bottom = if (portrait) 0.86f * h else 0.854f * h
        val timeCap = 0.105f * s * style.sizePct / 100f
        val fs = timeCap / p.capEm
        val t = "${d.h}:${d.m}"
        reg.textSize = 0.062f * s
        bold.textSize = 0.058f * s
        val cityCap = bold.textSize * bold.capRatio()
        val ap = if (d.use24h) "" else d.ampm + " "
        val cityBase = bottom - timeCap - 0.045f * s
        val arrow = Path().apply {
            // location arrow (filled), size = city cap
            val a = cityCap * 1.05f; val x = bx; val y = cityBase - cityCap
            moveTo(x, y + a * 0.45f); lineTo(x + a, y); lineTo(x + a * 0.58f, y + a); lineTo(x + a * 0.45f, y + a * 0.55f); close()
        }
        val alarm = d.alarm?.let { listOf(Seg("알람 ", grey), Seg("${d.alarmAmPm}$it", grey)) }
        val small = android.text.TextPaint(reg).apply { textSize = 0.05f * s }
        val alarmBase = 0.08f * h + small.textSize * small.capRatio()
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            dot.color = dark
            for (i in darkDots.indices step 2) drawCircle(darkDots[i], darkDots[i + 1], cell * 0.3f, dot)
            dot.color = lit
            for (i in litDots.indices step 2) drawCircle(litDots[i], litDots[i + 1], cell * 0.3f, dot)
            drawPath(term, line)
            dot.color = acc
            drawCircle(cityX, cityY, cell * 0.6f, dot)
            drawPath(arrow, dot)
            bold.color = acc
            drawText(d.city ?: "서울", bx + cityCap * 1.45f, cityBase, bold)
            reg.color = ink.ink.toArgb()
            drawText(ap, bx, bottom, reg)
            p.draw(this, t, bx + reg.measureText(ap), bottom, fs, look)
            if (alarm != null) drawSegs(alarm, bx, alarmBase, small)
        }
    }
}

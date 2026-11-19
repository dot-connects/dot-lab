package kr.dot.wherepark.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkRules
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.Style
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.ui.big

data class WidgetSpec(val appWidgetId: Int, val shape: Shape, val carId: String?, val strength: Int, val style: Style, val prevStyle: Style?)
data class Hit(val carId: String, val rectDp: RectF, val kind: String)   // kind = "MIC" | "ROW"
data class Rendered(val bitmap: Bitmap, val hits: List<Hit>)

/** Draws a widget as one bitmap (spec 8.1) at a launcher size; the card keeps the mockup proportions and is centred. */
object WidgetRenderer {
    /** Room around the card for its drop shadow, dp (left/right, top, bottom). */
    internal const val MX = 6f
    internal const val MT = 4f
    internal const val MB = 12f

    /** Placeholder when no car exists yet: the mic still opens the sheet (which creates "내 차"). */
    private val NO_CAR = Car("", "내 차", 0xFFB9B6B0.toInt(), null, 0)

    fun render(ctx: Context, spec: WidgetSpec, cars: List<Car>, parks: Map<String, Park?>, sizeDp: SizeF,
               density: Float, wallpaperColor: Int, now: Long, micReady: Boolean): Rendered =
        draw(ctx, spec, cars, parks, sizeDp, density, listOf(wallpaperColor), now, micReady, pending(ctx)).rendered

    /** Cars whose latest park is still in the send queue while a family exists ("전송 대기"). */
    internal fun pending(ctx: Context): Set<String> =
        if (!FamilyStore.joined(ctx)) emptySet() else SyncQueue.ops(ctx).filter { it.key.startsWith("park:") }.map { it.key.removePrefix("park:") }.toSet()

    internal class Out(val rendered: Rendered, val texts: List<TextBox>, val card: RectF)

    /** Which cars a widget shows: its one car, else all cars in the fixed order (at most 4). */
    fun carsFor(spec: WidgetSpec, cars: List<Car>): Pair<List<Car>, Boolean> {
        val one = spec.carId?.let { id -> cars.firstOrNull { it.id == id } }
        if (one != null) return listOf(one) to true
        return cars.sortedBy { it.order }.take(4).ifEmpty { listOf(NO_CAR) } to false
    }

    internal fun line(car: Car, park: Park?, pending: Boolean, now: Long): Line {
        if (park == null) return Line(car, "–", "", St.NONE, "", "")
        val (f, s) = park.spot.big()
        return when {
            park.state == ParkState.DRIVING -> Line(car, f, s, St.GONE, park.drivingBy ?: park.byName, "")
            pending -> Line(car, f, s, St.PEND, park.byName, ParkRules.whenLabel(park.atMs, now))
            else -> Line(car, f, s, St.OK, park.byName, ParkRules.whenLabel(park.atMs, now))
        }
    }

    /** Card placement in a widget of [w]x[h] dp: scale (<= 1) and offset; a very wide cell centres the card at 1.5x its width. */
    internal fun place(w: Float, h: Float, nw: Float, nh: Float): Triple<Float, RectF, Float> {
        val aw = max(1f, w - 2 * MX); val ah = max(1f, h - MT - MB)
        val s = min(1f, min(aw / nw, ah / nh))
        val cw = min(aw / s, nw * 1.5f)
        val ox = MX + (aw - cw * s) / 2; val oy = MT + (ah - nh * s) / 2
        return Triple(s, RectF(ox, oy, ox + cw * s, oy + nh * s), cw)
    }

    internal fun draw(ctx: Context, spec: WidgetSpec, cars: List<Car>, parks: Map<String, Park?>, sizeDp: SizeF, density: Float,
                      samples: List<Int>, now: Long, micReady: Boolean, pending: Set<String>): Out {
        val (shown, one) = carsFor(spec, cars)
        val lines = shown.map { line(it, parks[it.id], it.id in pending, now) }
        val (nw, nh) = Shapes.natural(spec.shape, one, lines.size, micReady)
        val (s, card, cw) = place(sizeDp.width, sizeDp.height, nw, nh)
        val bw = max(1, (sizeDp.width * density).roundToInt()); val bh = max(1, (sizeDp.height * density).roundToInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(density, density)
        c.translate(card.left, card.top)
        c.scale(s, s)
        val look = Look.of(spec.style, spec.strength / 100.0, samples)
        val sh = Shapes(ctx, c, spec.style, look, spec.shape, micReady)
        sh.draw(lines, one, cw, nh)
        fun map(r: RectF) = RectF(card.left + r.left * s, card.top + r.top * s, card.left + r.right * s, card.top + r.bottom * s)
        val hits = sh.hits.map { h ->
            val r = map(h.rectDp)
            if (h.kind == "MIC" && r.width() < 44f) {   // touch target stays >= 44 dp when the card is scaled down
                val cx = r.centerX(); val cy = r.centerY()
                r.set(cx - 22f, cy - 22f, cx + 22f, cy + 22f)
                r.offset(max(0f, -r.left) - max(0f, r.right - sizeDp.width), max(0f, -r.top) - max(0f, r.bottom - sizeDp.height))
            }
            Hit(h.carId, r, h.kind)
        }
        // grown mic targets on a shrunken card may touch the next car's mic: split each overlap at the middle of the two centres
        val mics = hits.filter { it.kind == "MIC" }
        val centres = mics.map { it.rectDp.centerY() }
        for (i in mics.indices) for (j in mics.indices) {
            val a = mics[i]; val b = mics[j]; val ca = centres[i]; val cb = centres[j]
            if (ca < cb && kotlin.math.abs(a.rectDp.centerX() - b.rectDp.centerX()) < 44f && a.rectDp.bottom > b.rectDp.top) {
                val mid = (ca + cb) / 2
                a.rectDp.bottom = min(a.rectDp.bottom, mid); b.rectDp.top = max(b.rectDp.top, mid)
            }
        }
        for (m in mics) hits.firstOrNull { it.kind == "ROW" && it.carId == m.carId }?.rectDp?.let { r -> if (r.right > m.rectDp.left && r.centerX() < m.rectDp.centerX()) r.right = m.rectDp.left }
        return Out(Rendered(bmp, hits), sh.texts.map { TextBox(map(it.rect), it.text, card.top + it.base * s) }, card)
    }

    /**
     * Resolution per size so the bitmaps of every size fit [limitBytes] (RemoteViews cap = 1.5 x screen x 4 bytes):
     * the biggest one drops to 0.75x until the sum fits.
     */
    fun budget(sizes: List<SizeF>, density: Float, limitBytes: Long): List<Float> {
        val k = MutableList(sizes.size) { 1f }
        fun bytes(i: Int): Long { val d = density * k[i]; return (sizes[i].width * d).roundToInt().toLong() * (sizes[i].height * d).roundToInt() * 4 }
        var guard = 0
        while (sizes.indices.sumOf(::bytes) > limitBytes && guard++ < 64) k[sizes.indices.maxBy(::bytes)] *= .75f
        return k
    }

    // ---------- talkback ----------

    private fun floorWords(f: Floor) = when (f) { is Floor.Under -> "지하 ${f.n}층"; is Floor.Above -> "${f.n}층"; Floor.Roof -> "옥상" }

    internal fun spotWords(s: Spot): String = listOfNotNull(
        s.floor?.let(::floorWords), s.zone?.let { "${it}구역" }, s.number?.let { "${it}번" }, s.pillar?.let { "기둥 $it" },
    ).joinToString(" ")

    /** "14:32" -> "14시 32분", "어제 19:05" -> "어제 19시 5분"; dates stay. */
    internal fun timeWords(t: String): String = Regex("(\\d{1,2}):(\\d{2})").replace(t) { m ->
        val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
        if (mm == 0) "${h}시" else "${h}시 ${mm}분"
    }

    /** "흰 차 지하 2층 C구역 37번, 아빠 14시 32분" (spec 8.1). */
    fun describe(car: Car, park: Park?, pending: Boolean, now: Long): String = when {
        park == null -> "${car.name} 기록 없음"
        park.state == ParkState.DRIVING -> "${car.name} 운행 중, ${park.drivingBy ?: park.byName} 출발"
        else -> "${car.name} ${spotWords(park.spot)}, ${park.byName} ${timeWords(ParkRules.whenLabel(park.atMs, now))}" + (if (pending) ", 전송 대기" else "")
    }
}

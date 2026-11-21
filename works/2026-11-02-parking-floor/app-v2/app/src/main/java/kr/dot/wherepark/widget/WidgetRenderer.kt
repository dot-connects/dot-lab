package kr.dot.wherepark.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import kotlin.math.max
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
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.big
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** [text] = 글자 색: "auto" | "white" | "black". */
data class WidgetSpec(
    val appWidgetId: Int, val shape: Shape, val carId: String?, val strength: Int, val style: Style, val prevStyle: Style?, val text: String = "auto",
)
data class Hit(val carId: String, val rectDp: RectF, val kind: String)   // kind = "MIC" | "ROW"
data class Rendered(val bitmap: Bitmap, val hits: List<Hit>)

/** Draws a widget as one bitmap (spec 8.1) laid out against the launcher size itself (mockup v3, A안: the content fills the cell). */
object WidgetRenderer {
    /** Placeholder when no car exists yet: the mic still opens the sheet (which creates "내 차"). */
    internal val NO_CAR = Car("", "내 차", 0xFFB9B6B0.toInt(), null, 0)

    /** Height from which a widget counts as two rows: zoomed displays and 6-7 row grids report two rows at 146-165 dp; stacked cars then get ~74 dp each (compact tiles). */
    const val TWO_ROW_DP = 148f

    fun twoRow(hDp: Float): Boolean = hDp >= TWO_ROW_DP

    fun render(ctx: Context, spec: WidgetSpec, cars: List<Car>, parks: Map<String, Park?>, sizeDp: SizeF,
               density: Float, wallpaperColor: Int, now: Long, micReady: Boolean): Rendered =
        draw(ctx, spec, cars, parks, sizeDp, density, listOf(wallpaperColor), now, micReady, pending(ctx), AppPrefs.mainCar(ctx)).rendered

    /** Cars whose latest park is still in the send queue while a family exists ("저장 중"). */
    internal fun pending(ctx: Context): Set<String> =
        if (!FamilyStore.joined(ctx)) emptySet() else SyncQueue.ops(ctx).filter { it.key.startsWith("park:") }.map { it.key.removePrefix("park:") }.toSet()

    internal class Out(val rendered: Rendered, val texts: List<TextBox>, val mics: List<RectF> = emptyList(), val radius: Float = 0f)

    /** 메인 차량: the chosen car, else (unset or deleted) the first registered one. */
    fun mainCar(cars: List<Car>, mainId: String?): Car? = cars.firstOrNull { it.id == mainId } ?: cars.minByOrNull { it.order }

    /**
     * Which cars a size shows (A안), main car first, and how many are left out ("외 N대"):
     * one row -> the main car only; two rows -> every car when there are at most two, else the main car alone.
     */
    fun carsFor(cars: List<Car>, mainId: String?, twoRow: Boolean): Pair<List<Car>, Int> {
        val main = mainCar(cars, mainId) ?: return listOf(NO_CAR) to 0
        val rest = cars.sortedBy { it.order }.filter { it.id != main.id }
        return if (twoRow && cars.size <= 2) (listOf(main) + rest) to 0 else listOf(main) to rest.size
    }

    /** "3일 전" once a park is two or more calendar days old (yesterday still reads "어제 21:03"). */
    internal fun staleLabel(atMs: Long, now: Long, zone: ZoneId = ZoneId.of("Asia/Seoul")): String? {
        val d = ChronoUnit.DAYS.between(Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        return if (d >= 2) "${d}일 전" else null
    }

    /** A park still unsent after this long reads as 실패 (the queue keeps retrying; no upload error is kept). */
    const val FAIL_AFTER_MS = 30 * 60_000L

    internal fun failed(park: Park, pending: Boolean, now: Long) = pending && now - park.atMs > FAIL_AFTER_MS

    internal fun line(car: Car, park: Park?, pending: Boolean, now: Long): Line {
        if (park == null) return Line(car, "", "", "", St.NONE, "", "")
        val (f, s) = park.spot.big()
        val memo = park.spot.memo?.trim().orEmpty()
        return when {
            park.state == ParkState.DRIVING -> Line(car, f, s, "", St.GONE, park.drivingBy ?: park.byName, "")
            failed(park, pending, now) -> Line(car, f, s, memo, St.FAIL, park.byName, ParkRules.whenLabel(park.atMs, now))
            pending -> Line(car, f, s, memo, St.PEND, park.byName, ParkRules.whenLabel(park.atMs, now))
            else -> Line(car, f, s, memo, St.OK, park.byName, ParkRules.whenLabel(park.atMs, now), staleLabel(park.atMs, now))
        }
    }

    /** [textScale] = font scale x 글자 크게 (1..1.3), small text only. */
    internal fun draw(ctx: Context, spec: WidgetSpec, cars: List<Car>, parks: Map<String, Park?>, sizeDp: SizeF, density: Float,
                      samples: List<Int>, now: Long, micReady: Boolean, pending: Set<String>, mainId: String? = null,
                      textScale: Float = 1f): Out {
        val w = sizeDp.width; val h = sizeDp.height
        val (shown, more) = carsFor(cars, mainId, twoRow(h))
        val lines = shown.map { line(it, parks[it.id], it.id in pending, now) }
        val bw = max(1, (w * density).roundToInt()); val bh = max(1, (h * density).roundToInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(density, density)
        val look = Look.pick(spec.style, spec.strength, samples, spec.text)
        val sh = Shapes(ctx, c, spec.style, look, spec.shape, micReady, textScale.coerceIn(1f, 1.3f))
        sh.draw(lines, more, w, h)
        return Out(Rendered(bmp, sh.hits.map { Hit(it.carId, RectF(it.rectDp), it.kind) }), sh.texts.toList(), sh.mics.toList(), sh.cardRadius)
    }

    /** Steps below each reported size (fine near 1 for launcher padding, down to ~0.5): a launcher that lays the widget out smaller than it reports still finds a layout. */
    private val STEPS = floatArrayOf(1f, .95f, .9f, .82f, .74f, .67f, .6f, .53f)

    /** RemoteViews(Map) takes at most 16 sizes. */
    private const val MAX_SIZES = 16

    /**
     * The sizes to draw for: every reported size first (as is), then each one scaled down in [STEPS], aspect kept, at most 16.
     * Android applies the layout whose size is closest among those that fit the real view and, when none fits, the smallest
     * (RemoteViews.findBestFitLayout): with only the reported sizes, a host that lays the widget out smaller than it reports
     * got a layout drawn for a bigger size, shrunk into its view (11-21 phone: content at ~0.6x, mic 48 dp shown at ~29 dp).
     */
    fun layoutSizes(reported: List<SizeF>): List<SizeF> {
        val base = reported.distinct()
        val out = LinkedHashSet<SizeF>(base)
        for (k in STEPS.drop(1)) for (s in base) {
            if (out.size >= MAX_SIZES) break
            out += SizeF(Math.round(s.width * k * 10) / 10f, Math.round(s.height * k * 10) / 10f)
        }
        return out.toList().take(MAX_SIZES)
    }

    /**
     * Resolution per size so the bitmaps of every size fit [limitBytes] (RemoteViews cap = 1.5 x screen x 4 bytes):
     * the biggest one drops to 0.75x until the sum fits, the first [keep] (the reported sizes) only once the others are at 0.42x.
     */
    fun budget(sizes: List<SizeF>, density: Float, limitBytes: Long, keep: Int = 0): List<Float> {
        val k = MutableList(sizes.size) { 1f }
        fun bytes(i: Int): Long { val d = density * k[i]; return (sizes[i].width * d).roundToInt().toLong() * (sizes[i].height * d).roundToInt() * 4 }
        var guard = 0
        while (sizes.indices.sumOf(::bytes) > limitBytes && guard++ < 128) {
            val extra = if (keep == 0) emptyList() else sizes.indices.filter { it >= keep && k[it] > .43f }
            k[(extra.ifEmpty { sizes.indices.toList() }).maxBy(::bytes)] *= .75f
        }
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

    /** "흰 차 지하 2층 C구역 37번, 엘리베이터 옆, 아빠 14시 32분" (spec 8.1) + the chip the widget shows (저장 중 / 저장 실패 / N일 전). */
    fun describe(car: Car, park: Park?, pending: Boolean, now: Long): String = when {
        park == null -> "${car.name} 기록 없음"
        park.state == ParkState.DRIVING -> "${car.name} 운행 중, ${park.drivingBy ?: park.byName} 출발"
        else -> listOfNotNull("${car.name} ${spotWords(park.spot)}", park.spot.memo?.trim()?.takeIf { it.isNotEmpty() },
            "${park.byName} ${timeWords(ParkRules.whenLabel(park.atMs, now))}".trim(),
            when { failed(park, pending, now) -> "저장 실패"; pending -> "저장 중"; else -> staleLabel(park.atMs, now) }).joinToString(", ")
    }
}

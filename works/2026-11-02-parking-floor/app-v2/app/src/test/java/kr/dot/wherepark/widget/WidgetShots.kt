package kr.dot.wherepark.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.SizeF
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import kr.dot.wherepark.Device
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Palette
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.groupOn
import kr.dot.wherepark.shot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Widget bitmaps at 2x for the comparison with mockups/v3-widget-sizes (A안) and the contrast check (tools/contrast.py):
 * 5 shapes x 6 cell sizes (82x100 dp cells) x 1/2/3 cars x strength 0/100, plus states and long text
 * -> app-v2/shots/widget/<shape>-<cols>x<rows>-<cars>car-<strength>.png (+ .json text boxes and hits).
 * Each shot = the widget with a 12 dp strip of a dune-like wallpaper around it; the samples are that wallpaper's colours.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetShots {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = Kit.ctx()

    private val day = 1_763_564_400_000L   // 2025-11-20 00:00 KST
    private val now = day + 18 * 3_600_000L
    private val dune = intArrayOf(0xFF04141A.toInt(), 0xFF17404B.toInt(), 0xFF5A8391.toInt(), 0xFFB39883.toInt(), 0xFFC9AE96.toInt())
    private val samples = dune.toList()
    private val whiteWall = intArrayOf(0xFFF8F9FB.toInt(), 0xFFEEF1F5.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDE5EE.toInt(), 0xFFE4E9EF.toInt())

    private val cars = listOf(
        Car("p", "펠리", 0xFF2F6FDE.toInt(), null, 0), Car("w", "흰 차", 0xFFF4F4F2.toInt(), null, 1),
        Car("d", "아빠 차", 0xFF3D4148.toInt(), null, 2), Car("n", "새 차", 0xFFD8402F.toInt(), null, 3),
    )
    private fun park(car: String, spot: Spot, by: String, at: Long, state: ParkState = ParkState.PARKED) =
        Park(at, car, spot, at, "m", by, null, false, state)
    private val parks: Map<String, Park?> = mapOf(
        "p" to park("p", Spot(Floor.Under(4), "C", 156, null, "엘리베이터 옆"), "동찬", day + (17 * 60 + 15) * 60_000L),
        "w" to park("w", Spot(Floor.Under(2), "A", 12, null, null), "엄마", day - (2 * 60 + 57) * 60_000L),
        "d" to park("d", Spot(Floor.Under(1), "B", 3, null, null), "아빠", day + 3_600_000L, ParkState.DRIVING),
        "n" to null,
    )
    private val sizes = listOf(2 to 1, 3 to 1, 4 to 1, 2 to 2, 3 to 2, 4 to 2)

    private fun save(name: String, spec: WidgetSpec, n: Int, cols: Int, rows: Int, parks: Map<String, Park?> = this.parks, mic: Boolean = true,
                     pending: Set<String> = emptySet(), cars: List<Car> = this.cars, wall: IntArray = dune, scale: Float = 1f) =
        saveAt(name, spec, n, SizeF(cols * 82f, rows * 100f), parks, mic, pending, cars, wall, scale)

    private fun saveAt(name: String, spec: WidgetSpec, n: Int, size: SizeF, parks: Map<String, Park?> = this.parks, mic: Boolean = true,
                     pending: Set<String> = emptySet(), cars: List<Car> = this.cars, wall: IntArray = dune, scale: Float = 1f) {
        if (!groupOn("widget")) return
        val out = WidgetRenderer.draw(ctx, spec, cars.take(n), parks, size, 2f, wall.toList(), now, mic, pending, null, scale)
        val bmp = out.rendered.bitmap
        val m = 24   // 12 dp at 2x
        val shot = Bitmap.createBitmap(bmp.width + 2 * m, bmp.height + 2 * m, Bitmap.Config.ARGB_8888)
        val c = Canvas(shot)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, shot.width * .3f, shot.height.toFloat(), wall, null, Shader.TileMode.CLAMP) })
        c.drawBitmap(bmp, m.toFloat(), m.toFloat(), null)
        val f = File("../shots/widget/$name.png").apply { parentFile?.mkdirs() }
        if (name == "row-4x2-2car-100") File("../shots/widget-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }  // res previewImage
        f.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        fun box(l: Float, t: Float, r: Float, b: Float) = JSONArray(listOf(l, t, r, b).map { (it * 2 + m).toDouble() })
        val boxes = JSONArray(out.texts.map { t -> JSONObject().put("text", t.text).put("base", (t.base * 2 + m).toDouble()).put("box", box(t.rect.left, t.rect.top, t.rect.right, t.rect.bottom)) })
        val hits = JSONArray(out.rendered.hits.map { h -> JSONObject().put("car", h.carId).put("kind", h.kind).put("box", box(h.rectDp.left, h.rectDp.top, h.rectDp.right, h.rectDp.bottom)) })
        File("../shots/widget/$name.json").writeText(JSONObject().put("card", box(0f, 0f, size.width, size.height)).put("texts", boxes).put("hits", hits).toString())
    }

    private fun spec(shape: Shape, strength: Int, text: String = "auto") = WidgetSpec(1, shape, null, strength, Palette.default(shape), null, text)

    @Test fun shapes() {
        for (shape in Shape.values()) for (st in listOf(100, 0)) for (n in 1..3) for ((cw, ch) in sizes)
            save("${shape.name.lowercase()}-${cw}x$ch-${n}car-$st", spec(shape, st), n, cw, ch)
    }

    /** States and long text on the main car, at the smallest and the widest one-row and two-row sizes. */
    @Test fun states() {
        val p = parks.getValue("p")!!
        fun with(q: Park?) = parks + ("p" to q)
        val long = listOf(Car("p", "우리 가족 큰 카니발", 0xFF2F6FDE.toInt(), null, 0)) + cars.drop(1)
        for (shape in listOf(Shape.ROW, Shape.PILLAR, Shape.DOT, Shape.TICKET)) for ((cw, ch) in listOf(2 to 1, 4 to 1, 2 to 2, 4 to 2)) {
            val k = "${shape.name.lowercase()}-${cw}x$ch"
            val s = spec(shape, 100)
            save("st-pend-$k", s, 2, cw, ch, with(p.copy(atMs = now - 5 * 60_000L)), pending = setOf("p"))
            save("st-fail-$k", s, 2, cw, ch, with(p.copy(atMs = now - WidgetRenderer.FAIL_AFTER_MS - 60_000)), pending = setOf("p"))
            save("st-stale-$k", s, 2, cw, ch, with(p.copy(atMs = day - 3 * 86_400_000L + 10 * 3_600_000L)))
            save("st-drive-$k", s, 2, cw, ch, with(p.copy(state = ParkState.DRIVING, drivingBy = "엄마")))
            save("st-none-$k", s, 2, cw, ch, with(null))
            save("st-longmemo-$k", s, 2, cw, ch, with(p.copy(spot = p.spot.copy(memo = "엘리베이터 옆 기둥 뒤 두번째 칸, 소화기 앞"))))
            save("st-longfloor-$k", s, 2, cw, ch, with(p.copy(spot = Spot(null, "C", 156, "12", "엘리베이터 옆"))))
            save("st-longpillar-$k", s, 2, cw, ch, with(p.copy(spot = Spot(Floor.Under(2), "A", 1234, "C7", "엘리베이터 옆"))))
            save("st-longname-$k", s, 3, cw, ch, cars = long)
        }
        save("st-nomic-row-2x1", spec(Shape.ROW, 100), 2, 2, 1, mic = false)
        save("st-nomic-row-4x2", spec(Shape.ROW, 100), 2, 4, 2, mic = false)
        save("st-nocar-row-2x1", spec(Shape.ROW, 100), 0, 2, 1)
    }

    /** Short cells: two rows on a zoomed / 6-7 row grid (150 dp), and the smallest resize sizes. */
    @Test fun compact() {
        for (shape in Shape.values()) {
            val k = shape.name.lowercase()
            saveAt("short-$k-164x150-2car", spec(shape, 100), 2, SizeF(164f, 150f))
            saveAt("short-$k-110x50", spec(shape, 100), 2, SizeF(110f, 50f))
            saveAt("short-$k-164x60", spec(shape, 100), 2, SizeF(164f, 60f))
        }
    }

    /** 0 % on a white wallpaper (strongest halo) and small text at 1.3x (font scale / 글자 크게). */
    @Test fun whiteAndLargeText() {
        for (shape in Shape.values()) for ((cw, ch) in listOf(2 to 1, 4 to 1, 4 to 2)) {
            val k = "${shape.name.lowercase()}-${cw}x$ch"
            save("white0-$k", spec(shape, 0), 1, cw, ch, wall = whiteWall)
            save("text13-$k-2car", spec(shape, 100), 2, cw, ch, scale = 1.3f)
        }
    }

    /** 글자 색 forced, at 30 % and 100 %. */
    @Test fun textColours() {
        for (t in listOf("white", "black")) for (st in listOf(30, 100)) for (shape in listOf(Shape.ROW, Shape.POSTER))
            save("tc-$t-${shape.name.lowercase()}-$st", spec(shape, st, t), 2, 4, 2)
    }

    /** A few shuffled looks (one per material) so the materials can be eyeballed; not compared with the mockup. */
    @Test fun shuffled() {
        val r = java.util.Random(11)
        var prev: kr.dot.wherepark.core.Style? = null
        for (shape in Shape.values()) for (i in 0 until 2) {
            val st = Palette.shuffle(shape, prev, r); prev = st
            save("shuf-${shape.name.lowercase()}-$i-${st.material}", WidgetSpec(1, shape, null, if (i == 0) 100 else 60, st, null), 2, 4, 2)
        }
    }

    @Test fun config() {
        Kit.reset(FakeNet(), mutableListOf())
        listOf("p", "w").forEach { kr.dot.wherepark.data.ParkStore.save(ctx, parks.getValue(it)!!.copy(atMs = System.currentTimeMillis() - 3_600_000L)) }
        rule.shot("widget", "config", Device.MOCK_PHONE) {
            WidgetConfig(WidgetSpec(1, Shape.ROW, null, 100, Palette.default(Shape.ROW), null), cars.take(2), samples) {}
        }
    }
}

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
 * Widget bitmaps at 2x for the mockup comparison (tools/sheets.py) and contrast check (tools/contrast.py):
 * 5 shapes x (one car, 2, 3-4 cars) x strength 100/30, plus the 6 ROW states -> app-v2/shots/widget/<name>.png (+ .json text boxes).
 * Each shot = the card at its natural size + the shadow margins, over the mockup's sky backdrop; the wallpaper samples are the
 * mockup's sky samples so the colours can be compared hex for hex.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetShots {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = Kit.ctx()

    private val day = 1_763_564_400_000L   // 2025-11-20 00:00 KST
    private val now = day + 16 * 3_600_000L
    private val sky = listOf(0xFFCFE3F2, 0xFF9CC4E0, 0xFFA9CDE6, 0xFFE7D2B4, 0xFFD8A978, 0xFFE9C9A0).map { it.toInt() }

    private val cars = listOf(
        Car("w", "흰 차", 0xFFF4F1EA.toInt(), null, 0), Car("g", "회색 차", 0xFF8E9399.toInt(), null, 1),
        Car("k", "검정 차", 0xFF1C1C1C.toInt(), null, 2), Car("b", "파란 차", 0xFF2D5DA8.toInt(), null, 3),
    )
    private fun park(car: String, f: Int, z: String, n: Int, by: String, at: Long) =
        Park(at, car, Spot(Floor.Under(f), z, n, null, null), at, "m", by, null, false, ParkState.PARKED)
    private val parks: Map<String, Park?> = mapOf(
        "w" to park("w", 2, "C", 37, "아빠", day + (14 * 60 + 32) * 60_000L),
        "g" to park("g", 4, "A", 12, "엄마", day - (4 * 60 + 55) * 60_000L),
        "k" to park("k", 3, "D", 8, "민지", day + (9 * 60 + 10) * 60_000L),
        "b" to park("b", 1, "F", 21, "아빠", day - 3 * 86_400_000L + 10 * 3_600_000L),
    )

    private fun save(name: String, spec: WidgetSpec, n: Int, parks: Map<String, Park?> = this.parks, mic: Boolean = true, pending: Set<String> = emptySet()) {
        if (!groupOn("widget")) return
        val one = spec.carId != null
        val (nw, nh) = Shapes.natural(spec.shape, one, n, mic)
        val size = SizeF(nw + 2 * WidgetRenderer.MX, nh + WidgetRenderer.MT + WidgetRenderer.MB)
        val out = WidgetRenderer.draw(ctx, spec, cars.take(n), parks, size, 2f, sky, now, mic, pending)
        val bmp = out.rendered.bitmap
        val shot = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(shot)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, shot.height.toFloat(), 0xFFA9CDE6.toInt(), 0xFFE7D2B4.toInt(), Shader.TileMode.CLAMP) })
        c.drawBitmap(bmp, 0f, 0f, null)
        val f = File("../shots/widget/$name.png").apply { parentFile?.mkdirs() }
        if (name == "row-2-100") File("../shots/widget-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }  // res previewImage
        f.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val boxes = JSONArray(out.texts.map { t -> JSONObject().put("text", t.text).put("base", (t.base * 2).toDouble()).put("box", JSONArray(listOf(t.rect.left, t.rect.top, t.rect.right, t.rect.bottom).map { (it * 2).toDouble() })) })
        val hits = JSONArray(out.rendered.hits.map { h -> JSONObject().put("car", h.carId).put("kind", h.kind).put("box", JSONArray(listOf(h.rectDp.left, h.rectDp.top, h.rectDp.right, h.rectDp.bottom).map { (it * 2).toDouble() })) })
        File("../shots/widget/$name.json").writeText(JSONObject().put("card", JSONArray(listOf(out.card.left, out.card.top, out.card.right, out.card.bottom).map { (it * 2).toDouble() })).put("texts", boxes).put("hits", hits).toString())
    }

    private fun spec(shape: Shape, strength: Int, one: Boolean = false) = WidgetSpec(1, shape, if (one) "w" else null, strength, Palette.default(shape), null)

    @Test fun shapes() {
        for (shape in Shape.values()) for (st in listOf(100, 30)) {
            val k = shape.name.lowercase()
            save("$k-one-$st", spec(shape, st, one = true), 4)
            save("$k-2-$st", spec(shape, st), 2)
            val many = if (shape == Shape.PILLAR) listOf(3, 4) else if (shape == Shape.TICKET) listOf(4) else listOf(3)
            many.forEach { save("$k-$it-$st", spec(shape, st), it) }
        }
    }

    @Test fun rowStates() {
        val s = spec(Shape.ROW, 100)
        save("row-st-none", s, 2, parks + ("w" to null))
        save("row-st-pend", s, 2, pending = setOf("w"))
        save("row-st-ok", s, 2)
        save("row-st-gone", s, 2, parks + ("g" to parks["g"]!!.copy(state = ParkState.DRIVING, drivingBy = "엄마")))
        save("row-st-old", s, 2, parks + ("w" to parks["w"]!!.copy(atMs = day - 4 * 86_400_000L + (14 * 60 + 32) * 60_000L)))
        save("row-st-nomic", s, 2, mic = false)
    }

    /** A few shuffled looks (one per material) so the materials can be eyeballed; not compared with the mockup. */
    @Test fun shuffled() {
        val r = java.util.Random(11)
        var prev: kr.dot.wherepark.core.Style? = null
        for (shape in Shape.values()) for (i in 0 until 2) {
            val st = Palette.shuffle(shape, prev, r); prev = st
            save("shuf-${shape.name.lowercase()}-$i-${st.material}", WidgetSpec(1, shape, null, if (i == 0) 100 else 30, st, null), 2)
        }
    }

    @Test fun config() {
        Kit.reset(FakeNet(), mutableListOf())
        listOf("w", "g").forEach { kr.dot.wherepark.data.ParkStore.save(ctx, parks.getValue(it)!!.copy(atMs = System.currentTimeMillis() - 3_600_000L)) }
        rule.shot("widget", "config", Device.MOCK_PHONE) {
            WidgetConfig(WidgetSpec(1, Shape.ROW, null, 100, Palette.default(Shape.ROW), null), cars.take(2), sky) {}
        }
    }
}

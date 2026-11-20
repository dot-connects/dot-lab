package kr.dot.wherepark.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Random
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Palette
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Style
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.AssetFonts

private val BG = Color(0xFFE9E7E3); private val SF = Color(0xFFF6F5F2); private val FG = Color(0xFF161616)
private val MU = Color(0xFF6C6A66); private val ACC = Color(0xFFFF6A13); private val ON_ACC = Color(0xFF1A0F06)

/** Shape names and the combo line words (mockup "위젯 꾸미기"). */
internal val SHAPE_NAMES = mapOf(Shape.ROW to "줄 목록", Shape.POSTER to "포스터", Shape.PILLAR to "기둥 띠", Shape.DOT to "점 행렬", Shape.TICKET to "주차권")
private val FAMILY_NAMES = mapOf("pastel" to "파스텔", "bold" to "원색", "deep" to "깊은 색", "mono" to "무채색", "comp" to "보색")
private val MATERIAL_NAMES = mapOf("matte" to "무광", "gloss" to "유광", "paper" to "종이", "metal" to "금속", "soft" to "소프트")
private val FONT_NAMES = mapOf("Inter Tight" to "고딕", "Big Shoulders Display" to "좁은 고딕", "IBM Plex Mono" to "고정폭", "Doto" to "점 숫자(고정)",
    "Playfair Display" to "세리프", "Chakra Petch" to "각진 고딕")

/** 글자 색: auto = white with a halo under 50 %, the better of white/black from 50 %. */
private val TEXT_COLORS = listOf("auto" to "자동", "white" to "흰", "black" to "검정")

internal fun comboLine(s: Style): String? = if (s.family == "default") null else
    "지금: ${FAMILY_NAMES[s.family] ?: s.family} · ${MATERIAL_NAMES[s.material] ?: s.material} · ${FONT_NAMES[s.font] ?: s.font}"

/**
 * Widget settings (spec 8.1, mockup section 3 "위젯 꾸미기"): what to show, shape, live preview from the real renderer,
 * colour shuffle with one undo, background strength, place. Opened by the launcher on reconfigure, or from the app.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val start = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetSpecs.get(this, id) else WidgetSpecs.default(id)
        setContent { WidgetConfig(start, CarStore.cars(this)) { done(it) } }
    }

    private fun done(spec: WidgetSpec) {
        val app = applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        if (spec.appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pinWidget(app, spec)
            finish(); return
        }
        WidgetSpecs.put(app, spec)
        Thread { WidgetReceiver.update(app, mgr, spec.appWidgetId) }.start()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, spec.appWidgetId))
        finish()
    }
}

/** From the app (settings, onboarding step 4): the launcher asks where to put it; the pin callback moves [spec] to the new id. */
fun pinWidget(ctx: Context, spec: WidgetSpec = WidgetSpecs.default(AppWidgetManager.INVALID_APPWIDGET_ID)): Boolean {
    val app = ctx.applicationContext
    val mgr = AppWidgetManager.getInstance(app)
    if (!mgr.isRequestPinAppWidgetSupported) return false
    WidgetSpecs.putNext(app, spec)
    return mgr.requestPinAppWidget(ComponentName(app, WidgetReceiver::class.java), null, WidgetReceiver.pinnedCallback(app))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetConfig(start: WidgetSpec, cars: List<Car>, samples: List<Int>? = null, onPlace: (WidgetSpec) -> Unit) {
    val ctx = LocalContext.current
    var spec by remember { mutableStateOf(start) }
    var strength by remember { mutableFloatStateOf(start.strength.toFloat()) }
    val gro = remember { AssetFonts.family(ctx, "Inter Tight", 800) }
    val wall = remember { samples ?: WidgetReceiver.wallpaper(ctx) }
    Column(
        Modifier.fillMaxSize().background(BG).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("위젯 꾸미기", style = TextStyle(fontFamily = gro, fontSize = 22.sp, letterSpacing = (-.01).em, color = FG), modifier = Modifier.padding(vertical = 2.dp))
        Sect("모양")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Shape.values().forEach { sh ->
                val on = spec.shape == sh
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(SF).border(BorderStroke(2.dp, if (on) FG else Color.Transparent), RoundedCornerShape(14.dp))
                        .semantics { selected = on }.clickable(role = Role.RadioButton) {
                            if (!on) spec = spec.copy(shape = sh, style = Palette.default(sh), prevStyle = null)
                        }.padding(top = 7.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Thumb(sh, if (on) FG else MU)
                    Text(SHAPE_NAMES.getValue(sh), fontSize = 12.sp, color = if (on) FG else MU, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        Preview(spec.copy(strength = strength.toInt()), cars, wall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(44.dp).lift(999).clip(RoundedCornerShape(999.dp)).background(SF)
                    .clickable(role = Role.Button) { spec = spec.copy(style = Palette.shuffle(spec.shape, spec.style, Random()), prevStyle = spec.style) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(18.dp)) { icon(SHUFFLE, FG) }
                Spacer(Modifier.width(6.dp))
                Text("색 바꿔 보기", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = FG)
            }
            val canUndo = spec.prevStyle != null
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(999.dp)).alpha(if (canUndo) 1f else .4f)
                    .clickable(enabled = canUndo, role = Role.Button) { spec = spec.copy(style = spec.prevStyle!!, prevStyle = null) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("되돌리기", fontSize = 14.sp, color = MU) }
        }
        comboLine(spec.style)?.let { Text(it, fontSize = 12.sp, color = MU, modifier = Modifier.padding(horizontal = 4.dp).padding(top = 0.dp)) }
        Column(Modifier.fillMaxWidth().lift(18).background(SF, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("배경 진하기", fontSize = 14.5.sp, color = FG, modifier = Modifier.weight(1f))
                Text("${strength.toInt()}%", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FG)
            }
            val colors = SliderDefaults.colors(thumbColor = ACC, activeTrackColor = ACC, inactiveTrackColor = Color(0xFFD5D2CC))
            Slider(
                value = strength, onValueChange = { strength = it }, valueRange = 0f..100f, colors = colors,
                modifier = Modifier.semantics { contentDescription = "위젯 배경 진하기" },
                thumb = { Box(Modifier.size(20.dp).shadow(2.dp, CircleShape).background(ACC, CircleShape)) },
                track = { st -> SliderDefaults.Track(sliderState = st, modifier = Modifier.height(4.dp), colors = colors, drawStopIndicator = null, thumbTrackGapSize = 0.dp) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("글자 색", fontSize = 14.5.sp, color = FG, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(999.dp)).background(BG).padding(3.dp)) {
                    TEXT_COLORS.forEach { (k, label) ->
                        val on = spec.text == k
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(999.dp)).background(if (on) FG else Color.Transparent)
                                .semantics { selected = on }.clickable(role = Role.RadioButton) { spec = spec.copy(text = k) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) SF else FG) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(999.dp)).background(ACC)
                .clickable(role = Role.Button) { onPlace(spec.copy(carId = null, strength = strength.toInt())) },
            contentAlignment = Alignment.Center,
        ) { Text("완료", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = ON_ACC) }
    }
}

private fun Modifier.lift(r: Int) = shadow(2.dp, RoundedCornerShape(r.dp), ambientColor = Color(0x29000000), spotColor = Color(0x29000000))

@Composable
private fun Sect(t: String) = Text(t, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .02.em, color = MU,
    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp))

/** The real widget bitmap over the wallpaper's own colours, at the width it gets on the screen. */
@Composable
private fun Preview(spec: WidgetSpec, cars: List<Car>, wall: List<Int>) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val grad = Brush.verticalGradient(wall.map { Color(it) }.let { if (it.size == 1) it + it else it })
    var pick by remember { mutableStateOf(2) }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(grad).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        val size = PREVIEW_SIZES[pick].second
        val k = minOf(1f, (maxWidth.value - 16f) / size.width)
        val bmp = remember(spec, cars, pick, wall) { preview(ctx, spec, cars, size, density, wall) }
        Image(bmp.asImageBitmap(), contentDescription = "위젯 미리 보기", modifier = Modifier.size((size.width * k).dp, (size.height * k).dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        PREVIEW_SIZES.forEachIndexed { i, (label, _) ->
            val on = i == pick
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(999.dp)).background(if (on) FG else SF)
                    .semantics { selected = on }.clickable(role = Role.RadioButton) { pick = i }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) SF else FG) }
        }
    }
}

/** Preview sizes: cells of the smallest 4-column phone (82x100 dp), drawn at their true dp size and scaled down only to fit. */
private val PREVIEW_SIZES = listOf("2×1" to SizeF(164f, 100f), "4×1" to SizeF(328f, 100f), "4×2" to SizeF(328f, 200f))

internal fun preview(ctx: Context, spec: WidgetSpec, cars: List<Car>, size: SizeF, density: Float, wall: List<Int>): android.graphics.Bitmap {
    val parks = cars.associate { it.id to ParkStore.latest(ctx, it.id) }
    return WidgetRenderer.draw(ctx, spec, cars, parks, size, density, wall, System.currentTimeMillis(), true, emptySet(),
        AppPrefs.mainCar(ctx), WidgetReceiver.textScale(ctx)).rendered.bitmap
}

private const val SHUFFLE = "M3.5 7.5h3.2c2.2 0 3.3 1 4.6 3.1l1.4 2.8c1.3 2.1 2.4 3.1 4.6 3.1h3M3.5 16.5h3.2c1.4 0 2.4-.4 3.2-1.2M14.2 8.7c.8-.8 1.8-1.2 3.2-1.2h2.9M18 5l2.5 2.5L18 10M18 14l2.5 2.5L18 19"

private fun DrawScope.icon(d: String, color: Color) = scale(size.width / 24f, size.height / 24f, Offset.Zero) {
    drawPath(PathParser().parsePathString(d).toPath(), color, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 48x32 shape thumbs (mockup TH_SVG). */
@Composable
private fun Thumb(sh: Shape, col: Color) = Canvas(Modifier.size(48.dp, 32.dp)) {
    scale(size.width / 48f, size.height / 32f, Offset.Zero) {
        fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, stroke: Boolean = false) =
            drawRoundRect(col, Offset(x, y), Size(w, h), CornerRadius(r), style = if (stroke) Stroke(1.6f) else androidx.compose.ui.graphics.drawscope.Fill)
        when (sh) {
            Shape.ROW -> { rr(5f, 6f, 20f, 7f, 2f); drawCircle(col, 3.5f, Offset(41f, 9.5f)); rr(5f, 19f, 20f, 7f, 2f); drawCircle(col, 3.5f, Offset(41f, 22.5f)) }
            Shape.POSTER -> { rr(5f, 4f, 24f, 16f, 2f); rr(33f, 6f, 10f, 3f, 1.5f); rr(5f, 24f, 38f, 4f, 2f) }
            Shape.PILLAR -> listOf(6f, 26f).forEach { x -> rr(x, 4f, 16f, 24f, 2f, true); rr(x, 4f, 16f, 6f, 2f) }
            Shape.DOT -> {
                rr(4f, 5f, 30f, 22f, 3f, true)
                listOf(12f, 20f).forEach { y -> drawLine(col, Offset(8f, y), Offset(30f, y), 2.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.8f, 2.2f))) }
                drawCircle(col, 3f, Offset(41f, 11f)); drawCircle(col, 3f, Offset(41f, 21f))
            }
            Shape.TICKET -> listOf(5f, 17.5f).forEach { y ->
                rr(4f, y, 40f, 9.5f, 2f)
                drawLine(SF, Offset(29f, y), Offset(29f, y + 9.5f), 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 1.6f)))
            }
        }
    }
}

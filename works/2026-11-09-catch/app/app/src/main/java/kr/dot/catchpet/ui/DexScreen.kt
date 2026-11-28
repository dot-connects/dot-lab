@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package kr.dot.catchpet.ui

import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.dot.catchpet.R
import kr.dot.catchpet.ai.AiWork
import kr.dot.catchpet.dex.Dex

internal val BG = Color(0xFF101114)
internal val DIM = Color(0xFF9AA0AB)
internal val GOLD = Color(0xFFFFCF5A)

/**
 * The dex (felt mockup v5.2 screen 4): starred ones first (starred in the detail), then newest first, then the fur
 * critters not met yet. Long press picks several to delete. A real thing whose pet is being made wobbles in a capsule.
 */
@Composable
fun DexScreen(onOpen: (String) -> Unit, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    // the pet may have been turned off from its cloud meanwhile: the badge follows
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload++ }
    val entries by produceState<List<Dex.Entry>?>(null, reload, AiWork.rev) {
        value = withContext(Dispatchers.IO) { Dex.ordered(Dex.list(ctx), Dex.favs(ctx)) }
    }
    val favs = remember(reload) { Dex.favs(ctx).toSet() }
    val allCritters = remember { Dex.critterIds(ctx) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var picking by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val pet = remember(reload) { petId(ctx)?.takeIf { kr.dot.catchpet.pet.PetPrefs.overlayOn(ctx) } }
    BookPage(scroll = false, sky = 0.24f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (picking) {
                InkPill("취소", onClick = { picking = false; picked = emptySet() })
                Spacer(Modifier.weight(1f))
                InkPill(if (picked.isEmpty()) "골라 주세요" else "${picked.size}마리 지우기", if (picked.isEmpty()) Color(0xFFEFE3CF) else Color(0xFFFF8A80),
                    onClick = if (picked.isEmpty()) null else ({ asking = true }))
            } else {
                InkPill("‹ 뽑기", onClick = onBack)
                Spacer(Modifier.weight(1f))
                InkPill("펫 설정", onClick = onPet)
            }
        }
        val list = entries
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Heading("도감")
            Spacer(Modifier.weight(1f))
            if (list != null && allCritters.isNotEmpty()) {
                val met = list.count { it.critterId != null }
                Row(Modifier.padding(bottom = 6.dp).felt(Book.Cream, 999.dp).padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.felt_cap), null, Modifier.size(28.dp))
                    Text("털뭉치 $met / ${allCritters.size}", fontFamily = Book.Display, fontSize = 17.sp)
                }
            }
        }
        list ?: return@BookPage
        if (list.isEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { Box(Modifier.weight(1f)) { MissingCell() } }
            }
            Text("아직 뽑은 녀석이 없어요.", fontFamily = Book.Display, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            InkButton("첫 녀석을 뽑으러 가기") { onBack() }
            return@BookPage
        }
        val newest = list.maxByOrNull { it.caughtAt }?.takeIf { System.currentTimeMillis() - it.caughtAt < 86_400_000L }?.id
        LazyVerticalGrid(
            GridCells.Fixed(3), contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(list, key = { it.id }) { e ->
                DexCell(e, isPet = e.id == pet, fav = e.id in favs, new = e.id == newest, picking = picking, picked = e.id in picked,
                    onClick = { if (picking) picked = if (e.id in picked) picked - e.id else picked + e.id else onOpen(e.id) },
                    onLong = { if (!picking) { picking = true; picked = setOf(e.id) } else picked = if (e.id in picked) picked - e.id else picked + e.id })
            }
            // the fur critters not met yet, as "?" at the end (operator 11-15: no count, no split)
            if (!picking) {
                val met = list.mapNotNull { it.critterId }.toSet()
                items(allCritters.filter { it !in met }, key = { "missing-$it" }) { MissingCell() }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("길게 누르면 여러 마리를 골라 지울 수 있어요.", fontSize = 12.5.sp, color = Color(0xFF3D5A2A), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    if (asking) DeleteDialog(entries.orEmpty().filter { it.id in picked }) { yes ->
        asking = false
        if (yes) { removeEntries(ctx, picked.toList()); picked = emptySet(); picking = false; reload++ }
    }
}

@Composable
private fun MissingCell() {
    Column(
        Modifier.felt(Color(0xFFEFE6D2), 16.dp, stitch = false).padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.felt_cap), null, Modifier.fillMaxSize().padding(10.dp).alpha(0.55f), contentScale = ContentScale.Fit)
            Text("?", fontFamily = Book.Display, fontSize = 30.sp, color = Color(0xAA6E5640))
        }
        Text(" ", fontFamily = Book.Display, fontSize = 15.sp)
    }
}

@Composable
private fun DexCell(e: Dex.Entry, isPet: Boolean, fav: Boolean, new: Boolean, picking: Boolean, picked: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val pic = rememberPose(e, if (e.critterId != null) "front" else e.poses.first())
    val real = e.critterId == null
    val hatching = AiWork.busyOf(e.id).any { it.startsWith("sheet-") }
    val fill = when { picked -> Color(0xFFFFE9A8); fav && !picking -> Color(0xFFFFF4CF); else -> Book.Paper }
    Box {
        Column(
            Modifier.felt(fill, 16.dp).clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLong)
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                if (hatching) Wobble(Modifier.fillMaxSize().padding(8.dp)) { Image(painterResource(R.drawable.felt_cap), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                else pic?.let {
                    Image(it, e.name, Modifier.fillMaxSize().let { m -> if (real) m.rotate(-3f) else m }
                        .alpha(if (picking && !picked) 0.8f else 1f), contentScale = ContentScale.Fit)
                }
            }
            Text(e.name, fontFamily = Book.Display, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when {
                hatching -> "털뭉치 만드는 중"
                real -> "진짜 ${Dex.kindName(e.kind)}"
                else -> "털뭉치"
            }, color = if (hatching) Color(0xFFB5662A) else Book.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.align(Alignment.TopStart).padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (real) Badge("진짜", Color(0xFFCF4A3C))
            if (new) Badge("NEW", Color(0xFFF08A3C))
        }
        if (picking) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(23.dp).background(if (picked) Color(0xFF6AA84F) else Book.Paper, CircleShape)
            .border(2.dp, Book.Ink, CircleShape), contentAlignment = Alignment.Center) {
            if (picked) Text("✓", fontSize = 13.sp, color = Color.White)
        } else Row(Modifier.align(Alignment.TopEnd).padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (isPet) Text("펫", fontFamily = Book.Display, fontSize = 11.sp, modifier = Modifier.felt(Book.Sun, 999.dp, stitch = false).padding(horizontal = 6.dp))
            if (fav) Star(true, Modifier.size(21.dp))
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, fontFamily = Book.Display, fontSize = 10.sp, color = Color.White, lineHeight = 14.sp,
        modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp))
}

/** Rocks its content on the bottom like the hatching capsule (mockup kWob). */
@Composable
internal fun Wobble(modifier: Modifier, amp: Float = 7f, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "wob")
    val a by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "wobA")
    Box(modifier.graphicsLayer { rotationZ = a * amp; transformOrigin = TransformOrigin(0.5f, 0.85f) }, contentAlignment = Alignment.Center) { content() }
}

private val STAR_PTS = floatArrayOf(12f, 2.8f, 14.8f, 8.7f, 21.2f, 9.5f, 16.5f, 13.9f, 17.7f, 20.3f, 12f, 17.2f, 6.3f, 20.3f, 7.5f, 13.9f, 2.8f, 9.5f, 9.2f, 8.7f)

/** The favourite star (mockup STAR path): gold when on, paper with a tan line when off. */
@Composable
internal fun Star(on: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        val p = Path().apply {
            moveTo(STAR_PTS[0] * k, STAR_PTS[1] * k)
            for (i in 2 until STAR_PTS.size step 2) lineTo(STAR_PTS[i] * k, STAR_PTS[i + 1] * k)
            close()
        }
        drawPath(p, if (on) Color(0xFFFFC93C) else Book.Paper)
        drawPath(p, if (on) Color(0xFFB07A12) else Color(0xFFA8916E), style = Stroke(1.9f * k, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** A clear pill with a gold seam across the middle, like the capsule on the catch screen. */
@Composable
internal fun Capsule(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF))))
            .drawBehind { drawLine(Color(0x99FFCF5A), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
            .border(1.5.dp, Color(0x88FFFFFF), shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A pose picture, loaded off the main thread. */
@Composable
internal fun rememberPose(e: Dex.Entry, pose: String): ImageBitmap? {
    val ctx = LocalContext.current
    val v by produceState<ImageBitmap?>(null, e.id, pose) {
        value = withContext(Dispatchers.IO) { posePicture(ctx, e, pose)?.asImageBitmap() }
    }
    return v
}

/** A file in the entry folder (previews, sheet), loaded off the main thread. */
@Composable
internal fun rememberFile(id: String, name: String, keyGreen: Boolean = false): ImageBitmap? {
    val ctx = LocalContext.current
    val v by produceState<ImageBitmap?>(null, id, name) {
        value = withContext(Dispatchers.IO) {
            val b = BitmapFactory.decodeFile(Dex.file(ctx, id, name).path) ?: return@withContext null
            (if (keyGreen) keyed(b) else b).asImageBitmap()
        }
    }
    return v
}

/** Real things: <pose>.png in the entry. Critters: the baked asset for that view, else front, else the placeholder. */
fun posePicture(ctx: Context, e: Dex.Entry, pose: String): Bitmap? {
    val cid = e.critterId ?: return BitmapFactory.decodeFile(Dex.file(ctx, e.id, "$pose.png").path)
    val file = when (pose) { "side" -> "dir2"; "back" -> "dir4"; else -> pose }
    for (p in listOf("critters/$cid/$file.webp", "critters/$cid/front.webp", "critters/_placeholder/front.webp")) {
        runCatching { ctx.assets.open(p).use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { return it }
    }
    return null
}

/** The drawings come on a green screen: key it out so they sit on the page with no background. */
private fun keyed(b: Bitmap): Bitmap {
    val px = IntArray(b.width * b.height)
    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
    for (i in px.indices) px[i] = kr.dot.catchpet.ai.Sheet.keyGreen(px[i])
    return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
}

package kr.dot.catchpet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.dot.catchpet.dex.Dex

internal val BG = Color(0xFF101114)
internal val DIM = Color(0xFF9AA0AB)
internal val GOLD = Color(0xFFFFCF5A)

/** The dex: ink cards, 3 across (mockup A). Real things show their cut-out, critters their baked front picture. */
@Composable
fun DexScreen(onOpen: (String) -> Unit, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val entries by produceState<List<Dex.Entry>?>(null) { value = withContext(Dispatchers.IO) { Dex.list(ctx) } }
    BookPage(scroll = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InkPill("‹ 뽑기", onClick = onBack)
            Spacer(Modifier.weight(1f))
            InkPill("펫 설정", Book.Sun, onClick = onPet)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Heading("도감")
            Spacer(Modifier.weight(1f))
            entries?.let { if (it.isNotEmpty()) InkPill("${it.size}마리", Book.Sun) }
        }
        val list = entries ?: return@BookPage
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("아직 뽑은 녀석이 없어요.\n뽑기 화면에서 집게를 내려 보세요!", fontSize = 17.sp,
                    textAlign = TextAlign.Center, lineHeight = 26.sp)
            }
            return@BookPage
        }
        LazyVerticalGrid(
            GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list, key = { it.id }) { e -> DexCell(e) { onOpen(e.id) } }
        }
    }
}

@Composable
private fun DexCell(e: Dex.Entry, onClick: () -> Unit) {
    val pic = rememberPose(e, e.poses.first())
    Column(
        Modifier.padding(bottom = Book.DROP).inkCard().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.1f), contentAlignment = Alignment.Center) {
            pic?.let { Image(it, e.name, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) }
        }
        Text(e.name, fontFamily = Book.Display, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (e.critterId != null) "털뭉치" else Dex.kindName(e.kind), color = Book.Muted, fontSize = 11.sp)
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
internal fun rememberFile(id: String, name: String): ImageBitmap? {
    val ctx = LocalContext.current
    val v by produceState<ImageBitmap?>(null, id, name) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(Dex.file(ctx, id, name).path)?.asImageBitmap() }
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

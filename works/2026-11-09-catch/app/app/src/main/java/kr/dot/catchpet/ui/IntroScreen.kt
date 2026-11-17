package kr.dot.catchpet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.dot.catchpet.pet.Frames

/**
 * First launch only (operator 11-17, mockup C of scratchpad firstrun): three cards — catch it, collect it, it lives on
 * your phone — then 시작하기 opens the catch screen, which asks for the camera (it used to ask the moment the app
 * opened, with nothing said first). [Intro.seen] keeps it from showing again.
 */
object Intro {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
    fun seen(ctx: Context) = prefs(ctx).getBoolean("intro_seen", false)
    fun done(ctx: Context) = prefs(ctx).edit().putBoolean("intro_seen", true).apply()
}

private class Page(val title: String, val note: String)

private val PAGES = listOf(
    Page("우리 집 강아지·고양이를\n집게로 뽑아요", "카메라에 비친 강아지·고양이·사람을 집어요.\n찍은 화면은 폰 밖으로 나가지 않아요."),
    Page("캡슐에 담아\n도감에 모아요", "숨은 털뭉치 16마리도 함께 모을 수 있어요."),
    Page("털뭉치가 돼서\n내 폰에 살아요", "홈 화면을 걸어 다니고, 말도 걸고,\n두 번 톡 하면 자주 쓰는 앱을 열어 줘요."),
)

@Composable
fun IntroScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    // house fur-balls drawn from the app's own assets
    val pics = remember {
        fun pic(id: String, pose: String) = Frames.asset(ctx, id, pose)
        mapOf("catch" to pic("monggeul", "front"), "a" to pic("ppiyak", "front"), "b" to pic("dalsom", "front"),
            "c" to pic("bamtol", "front"), "home" to pic("monggeul", "smile"))
    }
    val finish = { Intro.done(ctx); onDone() }
    BookPage(scroll = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < PAGES.size - 1) Text("건너뛰기", color = Book.Muted, fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = finish).padding(8.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                Box(Modifier.fillMaxWidth(0.78f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    when (i) {
                        0 -> ClawHolding(pics["catch"])
                        1 -> DexCards(listOf(pics["a"], pics["b"], pics["c"]))
                        else -> pics["home"]?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(0.8f), contentScale = ContentScale.Fit) }
                    }
                }
                Heading(PAGES[i].title, 26.sp)
                Text(PAGES[i].note, color = Book.Muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { k ->
                Box(Modifier.padding(4.dp).size(8.dp).background(if (k == pager.currentPage) Book.Ink else Book.Field, CircleShape))
            }
        }
        val last = pager.currentPage == PAGES.size - 1
        InkButton(if (last) "시작하기" else "다음", primary = last) {
            if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** The claw (ClawArt, the catch screen's own drawing) hugging a fur-ball. */
@Composable
private fun ClawHolding(pic: Bitmap?) {
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { c ->
            val cv = c.nativeCanvas
            val k = size.width / 220f   // draw in a 220-unit box
            cv.save(); cv.scale(k, k)
            val hubX = 110f; val hubY = 46f
            val body = ClawArt.Body(a = 52f, top = 14f, h = 110f)
            val arms = ClawArt.on(body, 0f)
            ClawArt.back(cv, hubX, hubY, 0f, arms)
            pic?.let { cv.drawBitmap(it, null, RectF(hubX - 60f, hubY + 8f, hubX + 60f, hubY + 8f + 120f * it.height / it.width), null) }
            ClawArt.front(cv, hubX, hubY, arms)
            cv.restore()
        }
    }
}

/** Three dex cards, the middle one raised. */
@Composable
private fun DexCards(pics: List<Bitmap?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        pics.forEachIndexed { j, p ->
            Box(Modifier.size(if (j == 1) 96.dp else 82.dp).padding(bottom = Book.DROP).inkCard(), contentAlignment = Alignment.Center) {
                p?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) }
            }
        }
    }
}

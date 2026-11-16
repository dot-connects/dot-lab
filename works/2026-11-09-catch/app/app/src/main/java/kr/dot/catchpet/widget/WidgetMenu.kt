package kr.dot.catchpet.widget

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.dot.catchpet.MainActivity
import kr.dot.catchpet.R
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.pet.Frames
import kr.dot.catchpet.pet.PetPrefs
import kr.dot.catchpet.pet.PetService
import kr.dot.catchpet.ui.Book
import kr.dot.catchpet.ui.inkCard
import kr.dot.catchpet.ui.petId
import kr.dot.catchpet.ui.setPet

/** "몽글이랑" / "도토리랑": the "with" particle after a Korean name. */
internal fun withRang(word: String): String {
    val c = word.lastOrNull() ?: return word
    return word + if (c in '가'..'힣' && (c - '가') % 28 != 0) "이랑" else "랑"
}

/**
 * A tap on the home widget (operator 11-16, mockup widget-popup 1-A): a small bubble right above the widget (below
 * when there is no room), the home screen not dimmed; a tap outside closes it. 펫 띄우기 turns the same bubble into
 * a row of the widget family to pick from. The app does not open unless 도감 / 뽑기 is chosen.
 */
class WidgetMenu : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()   // window = whole screen, so the launcher's widget bounds line up with our coordinates
        super.onCreate(savedInstanceState)
        val at = intent.sourceBounds
        setContent { Bubble(at) }
    }

    private fun open(to: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN, to)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun float(id: String) {
        setPet(this, id)
        if (Settings.canDrawOverlays(this)) PetService.start(this)
        else { open("pet"); return }   // no overlay permission yet: the pet screen asks for it
        finish()
    }

    /** The bubble placed by the widget's screen bounds [at]; without them (some launchers) it sits in the middle. */
    @Composable
    private fun Bubble(at: Rect?) {
        var picking by remember { mutableStateOf(false) }
        val none = remember { MutableInteractionSource() }
        val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
        Layout(
            content = {
                Column(
                    Modifier.padding(bottom = Book.DROP).inkCard(18.dp).clickable(none, null) {}.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { if (!picking) Menu { picking = true } else Pick() }
            },
            modifier = Modifier.fillMaxSize().clickable(none, null) { finish() },
        ) { ms, c ->
            val p = ms[0].measure(Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight))
            layout(c.maxWidth, c.maxHeight) {
                val x: Int; val y: Int
                if (at == null) { x = (c.maxWidth - p.width) / 2; y = (c.maxHeight - p.height) / 2 }
                else {
                    x = (at.centerX() - p.width / 2).coerceIn(gap, (c.maxWidth - p.width - gap).coerceAtLeast(gap))
                    val above = at.top - p.height - gap
                    y = if (above > gap * 6) above else (at.bottom + gap).coerceAtMost(c.maxHeight - p.height - gap)
                }
                p.place(x, y)
            }
        }
    }

    @Composable
    private fun Menu(onFloat: () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile(R.drawable.ic_menu_pet, "띄우기", Color(0xFF9FD8FF), onFloat)
            Tile(R.drawable.ic_menu_dex, "도감", Color(0xFFBFE3A6)) { open("dex") }
            Tile(R.drawable.ic_menu_catch, "뽑기", Color(0xFFFF8FA3)) { open("catch") }
        }
    }

    @Composable
    private fun Tile(icon: Int, label: String, fill: Color, onClick: () -> Unit) {
        Column(Modifier.width(58.dp).clip(RoundedCornerShape(12.dp)).background(fill).border(2.dp, Book.Ink, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(painterResource(icon), null, Modifier.size(30.dp))
            Text(label, fontFamily = Book.Display, fontSize = 12.sp, color = Book.Ink, maxLines = 1)
        }
    }

    @Composable
    private fun Pick() {
        val shown = remember { petId(this) }
        val on = remember { PetPrefs.overlayOn(this) }
        // the family, or the current pet alone when no family was chosen yet
        val who by produceState<List<Pair<Dex.Entry, ImageBitmap?>>?>(null) {
            value = withContext(Dispatchers.IO) {
                val fam = Family.entries(this@WidgetMenu).ifEmpty { listOfNotNull(shown?.let { Dex.get(this@WidgetMenu, it) }?.takeIf(Family::canJoin)) }
                fam.map { e -> e to runCatching { Frames.load(this@WidgetMenu, e)?.get("front")?.asImageBitmap() }.getOrNull() }
            }
        }
        val list = who ?: return
        if (list.isEmpty()) {
            Text("도감에서 [위젯 가족]을\n골라 주세요", fontFamily = Book.Body, fontSize = 12.sp, color = Book.Muted, modifier = Modifier.widthIn(max = 180.dp))
        } else for (row in list.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((e, pic) in row) {
                val now = e.id == shown && on
                Column(Modifier.width(58.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (now) Color(0xFFFFF3CC) else Book.Paper)
                    .border(2.dp, if (now) Book.Ink else Color(0xFFD7DDCF), RoundedCornerShape(12.dp))
                    .clickable { float(e.id) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { pic?.let { Image(it, e.name, Modifier.fillMaxSize()) } }
                    Text(e.name, fontFamily = Book.Display, fontSize = 11.sp, color = Book.Ink, maxLines = 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (on) Small("펫 내리기") { PetService.stop(this@WidgetMenu); finish() }
            if (list.isEmpty()) Small("도감") { open("dex") }
        }
    }

    @Composable
    private fun Small(label: String, onClick: () -> Unit) {
        Box(Modifier.clip(CircleShape).border(2.dp, Book.Ink, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center) { Text(label, fontFamily = Book.Display, fontSize = 12.sp, color = Book.Ink) }
    }
}

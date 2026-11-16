package kr.dot.catchpet.widget

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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

/**
 * A tap on the home widget (operator 11-16, mockups widget-brag 1-A, widget-icons B, widget-family ②): a sheet over
 * home with 펫 띄우기 / 도감 / 뽑기. 펫 띄우기 picks one of the widget family to float; the app does not open.
 */
/** "몽글이랑" / "도토리랑": the "with" particle after a Korean name. */
internal fun withRang(word: String): String {
    val c = word.lastOrNull() ?: return word
    return word + if (c in '가'..'힣' && (c - '가') % 28 != 0) "이랑" else "랑"
}

class WidgetMenu : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Sheet() }
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

    @Composable
    private fun Sheet() {
        var picking by remember { mutableStateOf(false) }
        val none = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize().background(Color(0x73000000)).clickable(none, null) { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth().padding(bottom = Book.DROP).inkCard(20.dp)
                    .clickable(none, null) {}.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!picking) Menu { picking = true } else Pick()
            }
        }
    }

    @Composable
    private fun Menu(onFloat: () -> Unit) {
        val name = remember { petId(this)?.let { Dex.get(this, it)?.name } }
        Text(if (name != null) withRang(name) + " 뭐 할까?" else "뭐 할까?", fontFamily = Book.Display, fontSize = 19.sp, color = Book.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_menu_pet, "펫 띄우기", Color(0xFF9FD8FF), Modifier.weight(1f), onFloat)
            Tile(R.drawable.ic_menu_dex, "도감", Color(0xFFBFE3A6), Modifier.weight(1f)) { open("dex") }
            Tile(R.drawable.ic_menu_catch, "뽑기", Color(0xFFFF8FA3), Modifier.weight(1f)) { open("catch") }
        }
    }

    @Composable
    private fun Tile(icon: Int, label: String, fill: Color, m: Modifier, onClick: () -> Unit) {
        Column(m.padding(bottom = Book.DROP).inkCard(16.dp, fill).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(icon), null, Modifier.size(40.dp))
            Text(label, fontFamily = Book.Display, fontSize = 16.sp, color = Book.Ink)
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
        Text("누구를 띄울까?", fontFamily = Book.Display, fontSize = 19.sp, color = Book.Ink)
        val list = who ?: return
        if (Family.entries(this).isEmpty()) Text("도감에서 [위젯 가족]을 누르면 여기에 모여요", fontFamily = Book.Body, fontSize = 13.sp, color = Book.Muted)
        for (row in list.chunked(4)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((e, pic) in row) {
                val now = e.id == shown && on
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (now) Color(0xFFFFF3CC) else Color.Transparent)
                    .border(2.dp, if (now) Book.Ink else Color(0xFFD7DDCF), RoundedCornerShape(12.dp))
                    .clickable { float(e.id) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { pic?.let { Image(it, e.name, Modifier.fillMaxSize()) } }
                    Text(if (now) "${e.name} · 지금" else e.name, fontFamily = Book.Display, fontSize = 12.sp, color = Book.Ink, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (on) Small("펫 내리기", Modifier.weight(1f)) { PetService.stop(this@WidgetMenu); finish() }
            Small("도감", Modifier.weight(1f)) { open("dex") }
        }
    }

    @Composable
    private fun Small(label: String, m: Modifier, onClick: () -> Unit) {
        Box(m.padding(bottom = Book.DROP).inkCard(12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center) { Text(label, fontFamily = Book.Display, fontSize = 15.sp, color = Book.Ink) }
    }
}

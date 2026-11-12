package kr.dot.catchpet.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.game.Mask
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

enum class Screen { CATCH, DEX, DETAIL, PET }

/** The whole app: one screen at a time, switched by a single state. Back goes to the catch screen. */
@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.CATCH) }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    val sink = remember { DexSink(ctx.applicationContext) }
    val critters = remember { Critters(ctx.applicationContext) }
    BackHandler(enabled = screen != Screen.CATCH) {
        screen = if (screen == Screen.DETAIL) Screen.DEX else Screen.CATCH
    }
    when (screen) {
        Screen.CATCH -> CatchScreen(sink, critters, sink.count) { screen = Screen.DEX }
        Screen.DEX -> DexScreen(
            onOpen = { entryId = it; screen = Screen.DETAIL },
            onPet = { screen = Screen.PET },
            onBack = { screen = Screen.CATCH },
        )
        Screen.DETAIL -> DetailScreen(entryId ?: "", onPet = { screen = Screen.PET }) { screen = Screen.DEX }
        Screen.PET -> PetScreen { screen = Screen.CATCH }
    }
}

/**
 * Catches go to the Dex. The count moves at once (the capsule shows it); the PNG and the file writes run on one
 * background thread, in catch order, so the frame loop never waits on the disk.
 */
class DexSink(private val ctx: Context) : CatchSink {
    private val critters: MutableSet<String>
    var count by mutableIntStateOf(0); private set

    init {
        val all = Dex.list(ctx)
        critters = all.mapNotNull { it.critterId }.toMutableSet()
        count = all.size
    }

    override fun onCaught(kind: Mask.Kind, png: Bitmap?, critterId: String?, label: String): Boolean {
        val grew = critterId == null || critters.add(critterId)
        if (grew) count++
        writes.execute {
            val bytes = png?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
            runCatching { Dex.add(ctx, kind.name.lowercase(), bytes, critterId, label) }
                .onFailure { android.util.Log.e("DexSink", "save failed", it) }
        }
        return grew
    }

    private companion object { val writes = Executors.newSingleThreadExecutor() }
}

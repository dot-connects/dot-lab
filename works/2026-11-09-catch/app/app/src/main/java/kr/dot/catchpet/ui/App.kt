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
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

enum class Screen { CATCH, DEX, DETAIL, PET }

/** The whole app: one screen at a time, switched by a single state. Back goes to the catch screen. */
@Composable
fun App(openPet: Int = 0) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.CATCH) }
    // the pet's cloud said "펫 설정"
    androidx.compose.runtime.LaunchedEffect(openPet) { if (openPet > 0) screen = Screen.PET }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    val sink = remember { DexSink(ctx.applicationContext) }
    val critters = remember { Critters(ctx.applicationContext) }
    BackHandler(enabled = screen != Screen.CATCH) {
        screen = if (screen == Screen.DETAIL) Screen.DEX else Screen.CATCH
    }
    when (screen) {
        Screen.CATCH -> {
            androidx.compose.runtime.LaunchedEffect(Unit) { sink.refresh() }
            CatchScreen(sink, critters, sink.count) { screen = Screen.DEX }
        }
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
    private val critters = HashSet<String>()
    private val names = HashSet<String>()
    var count by mutableIntStateOf(0); private set

    init { load(Dex.list(ctx)) }

    private fun load(all: List<Dex.Entry>) {
        critters.clear(); critters += all.mapNotNull { it.critterId }
        names.clear(); names += all.map { it.name }
        count = all.size
    }

    /** Re-read the dex after the pending writes (deleting in the dex left the rail count high, review 11-15). */
    fun refresh() {
        writes.execute {
            val all = runCatching { Dex.list(ctx) }.getOrNull() ?: return@execute
            main.post { load(all) }
        }
    }

    override fun onCaught(kind: String, png: Bitmap?, critterId: String?, label: String): Caught {
        val grew = critterId == null || critters.add(critterId)
        if (grew) count++
        val name = if (critterId != null) Dex.critterName(ctx, critterId) else Dex.cuteName(names)
        names += name
        writes.execute {
            val bytes = png?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
            runCatching { Dex.add(ctx, kind, bytes, critterId, label, name = name) }
                .onFailure { android.util.Log.e("DexSink", "save failed", it) }
        }
        return Caught(grew, name)
    }

    private companion object {
        val writes = Executors.newSingleThreadExecutor()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
    }
}

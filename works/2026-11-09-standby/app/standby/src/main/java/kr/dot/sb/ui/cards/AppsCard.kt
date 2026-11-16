package kr.dot.sb.ui.cards

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.graphics.drawable.toBitmap
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.ui.state.StandbyActions
import kotlin.math.ceil
import kotlin.math.sqrt

/** A launchable app: label and icon (null = the icon could not be drawn). */
class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

private const val ICON_PX = 144

/** [pkg] with a launcher activity, else null (uninstalled / not launchable: its cell is hidden). */
fun appEntry(ctx: Context, pkg: String): AppEntry? {
    val pm = ctx.packageManager
    if (pm.getLaunchIntentForPackage(pkg) == null) return null
    val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
    return AppEntry(pkg, pm.getApplicationLabel(info).toString(), runCatching { pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull())
}

/** Every launcher app (앱 고르기), sorted by label, this app left out. Blocking: call off the main thread. */
fun launcherApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }
        .mapNotNull { pkg ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            AppEntry(pkg, pm.getApplicationLabel(info).toString(), runCatching { pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull())
        }
        .sortedBy { it.label.lowercase() }
}

/** How a card finds an app; shots swap in fakes (Robolectric has no launcher apps). */
private val realLookup: (Context, String) -> AppEntry? = ::appEntry
val LocalAppLookup = staticCompositionLocalOf { realLookup }

/**
 * 앱 바로가기 (plan-r14 C1): the picked apps as an icon grid sized to their count, labels from M up; tap = unlock and open.
 * No apps (or none still installed): "앱을 골라 주세요", tap = 앱 고르기 in settings ([menu] = that route).
 */
@Composable
internal fun AppsCard(e: Env, o: SlotOptions?, actions: StandbyActions, menu: String?) {
    val ctx = LocalContext.current
    val lookup = LocalAppLookup.current
    val pkgs = o?.apps.orEmpty()
    // looked up off the main thread, again on every resume (an app installed / removed meanwhile); the last list stays up
    val resumed = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val loaded by produceState<List<AppEntry>?>(null, pkgs, lookup, resumed) {
        // the PackageManager lookup goes to IO; a fake (shots) stays on the frame so the capture sees it
        if (resumed || value == null) value = withContext(if (lookup === realLookup) Dispatchers.IO else EmptyCoroutineContext) { pkgs.mapNotNull { lookup(ctx, it) } }
    }
    val apps = loaded ?: return // first lookup still running: a bare plate for a moment
    if (apps.isEmpty()) {
        TextCard(e, Modifier.tap(menu?.let { m -> { actions.openSettings(m) } })) {
            LabelRow(e, null, "앱 바로가기")
            Txt("앱을 골라 주세요", e.sz.title, e.ink.ink2, weight = 700, maxW = e.cw, lines = 2)
        }
        return
    }
    val cols = ceil(sqrt(apps.size * (e.w / e.h).toDouble())).toInt().coerceIn(1, apps.size)
    val rows = ceil(apps.size / cols.toDouble()).toInt()
    val named = e.atLeastM
    val cellW = e.cw / cols
    val cellH = e.ch / rows
    val labelH = if (named) e.meta * 1.6f else 0.dp
    val icon = min(min(cellW, cellH - labelH) * 0.72f, 96.dp)
    val filter = nightFilter(e.ink.night)
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        apps.chunked(cols).forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { a ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().tap { actions.openApp(a.pkg) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val bmp = a.icon
                        if (bmp != null) Image(bmp, a.label, Modifier.size(icon), colorFilter = filter)
                        else Box(Modifier.size(icon).clip(RoundedCornerShape(icon * 0.28f)).background(e.ink.plate2), Alignment.Center) {
                            Txt(a.label.take(1), icon * 0.4f, e.ink.ink2, weight = 700)
                        }
                        if (named) Txt(a.label, e.meta, e.ink.ink2, Modifier.padding(top = 4.dp), weight = 500, maxW = cellW - 4.dp, align = TextAlign.Center)
                    }
                }
                repeat(cols - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

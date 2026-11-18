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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.graphics.drawable.toBitmap
import kr.dot.sb.data.UsageApps
import kr.dot.sb.model.AppSource
import kr.dot.sb.model.IconStyle
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.ui.state.StandbyActions
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * A launchable app: label and icon (null = the icon could not be drawn). [mono] = the adaptive icon's monochrome layer
 * (API 33+, plan-r15 E6 테마 색), cropped to the safe zone; null = the app has none.
 */
class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?, val mono: ImageBitmap? = null)

private const val ICON_PX = 144

/** [pkg] with a launcher activity, else null (uninstalled / not launchable: its cell is hidden). */
fun appEntry(ctx: Context, pkg: String): AppEntry? {
    val pm = ctx.packageManager
    if (pm.getLaunchIntentForPackage(pkg) == null) return null
    val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
    val d = runCatching { pm.getApplicationIcon(info) }.getOrNull()
    return AppEntry(pkg, pm.getApplicationLabel(info).toString(), runCatching { d?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() }.getOrNull(), runCatching { mono(d) }.getOrNull())
}

/** The monochrome layer of an adaptive icon at [ICON_PX], its 108 dp layer cropped to the 72 dp safe zone. */
private fun mono(d: android.graphics.drawable.Drawable?): ImageBitmap? {
    if (android.os.Build.VERSION.SDK_INT < 33 || d !is android.graphics.drawable.AdaptiveIconDrawable) return null
    val m = d.monochrome ?: return null
    val bmp = android.graphics.Bitmap.createBitmap(ICON_PX, ICON_PX, android.graphics.Bitmap.Config.ARGB_8888)
    val inset = ICON_PX / 4 // 108 / 72 = 1.5: the layer spans 1.5x the visible icon
    m.setBounds(-inset, -inset, ICON_PX + inset, ICON_PX + inset)
    m.draw(android.graphics.Canvas(bmp))
    return bmp.asImageBitmap()
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

/** Null means usage access is missing; shots can supply a fixed package list. */
private val realUsage: (Context, AppSource, Int) -> List<String>? = { ctx, source, n ->
    if (UsageApps.granted(ctx)) UsageApps.load(ctx, source, n) else null
}
val LocalUsageApps = staticCompositionLocalOf { realUsage }

/**
 * App shortcuts from picked packages or usage history, sized to their count; tap = unlock and open.
 * No apps or missing usage access: show a prompt that opens this card's settings ([menu]).
 */
@Composable
internal fun AppsCard(e: Env, o: SlotOptions?, actions: StandbyActions, menu: String?) {
    val ctx = LocalContext.current
    val lookup = LocalAppLookup.current
    val usage = LocalUsageApps.current
    val pkgs = o?.apps.orEmpty()
    val source = o?.appSource ?: AppSource.PICKED
    val count = o?.appCount ?: 4
    // looked up off the main thread, again on every resume (an app installed / removed meanwhile); the last list stays up
    val resumed = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val loaded by produceState<Pair<Boolean, List<AppEntry>>?>(null, ctx, pkgs, source, count, lookup, usage, resumed) {
        // Real lookups go to IO; fakes stay on the frame so the capture sees them.
        val io = lookup === realLookup || (source != AppSource.PICKED && usage === realUsage)
        if (resumed || value == null) value = withContext(if (io) Dispatchers.IO else EmptyCoroutineContext) {
            val selected = if (source == AppSource.PICKED) pkgs else usage(ctx, source, count)
            (selected != null) to selected.orEmpty().mapNotNull { lookup(ctx, it) }
        }
    }
    val (granted, apps) = loaded ?: return // first lookup still running: a bare plate for a moment
    if (apps.isEmpty()) {
        TextCard(e, Modifier.tap(menu?.let { m -> { actions.openSettings(m) } })) {
            LabelRow(e, null, "앱 바로가기")
            val text = when {
                source == AppSource.PICKED -> "앱을 골라 주세요"
                !granted -> "사용 기록 허용이 필요해요"
                else -> "최근에 쓴 앱이 없어요"
            }
            Txt(text, e.sz.title, e.ink.ink2, weight = 700, maxW = e.cw, lines = 2)
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
                        val mono = a.mono?.takeIf { o?.iconStyle == IconStyle.THEMED }
                        // 테마 색: the monochrome layer in the accent on a plate a step off the card's own (no layer = own colours)
                        if (mono != null) Box(Modifier.size(icon).clip(RoundedCornerShape(icon * 0.28f)).background(e.ink.plate2), Alignment.Center) {
                            Image(mono, a.label, Modifier.size(icon), colorFilter = ColorFilter.tint(e.ink.acc))
                        }
                        else if (bmp != null) Image(bmp, a.label, Modifier.size(icon), colorFilter = filter)
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

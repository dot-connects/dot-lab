package kr.dot.sb.settings

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kr.dot.sb.platform.WidgetFit

/** One widget the picker lists, as plain values (shots fake them). [min] in dp, resize rules applied; [cells] = "4×2". */
data class WidgetChoice(val key: String, val pkg: String, val app: String, val name: String, val min: WidgetFit.Size, val cells: String?)

/** What the 위젯 고르기 sheet shows (plan-r13 B10). */
internal object WidgetList {
    /** 추천, in this order, when installed: Samsung Notes, Samsung Calendar, Keep, Google; then one weather app. */
    val RECOMMENDED = listOf("com.samsung.android.app.notes", "com.samsung.android.calendar", "com.google.android.keep", "com.google.android.googlequicksearchbox")

    private fun weather(c: WidgetChoice) = "weather" in c.pkg.lowercase() || "날씨" in c.app

    fun search(list: List<WidgetChoice>, q: String): List<WidgetChoice> {
        val k = q.trim().lowercase()
        return if (k.isEmpty()) list else list.filter { k in it.app.lowercase() || k in it.name.lowercase() }
    }

    /** The first widget of each recommended app that is installed and fits. */
    fun recommended(list: List<WidgetChoice>): List<WidgetChoice> {
        val pkgs = RECOMMENDED + listOfNotNull(list.firstOrNull(::weather)?.pkg)
        return pkgs.distinct().mapNotNull { p -> list.firstOrNull { it.pkg == p } }
    }

    /** By app name, each app's widgets in the provider order. */
    fun groups(list: List<WidgetChoice>): List<Pair<String, List<WidgetChoice>>> =
        list.groupBy { it.pkg }.values.map { it.first().app to it }.sortedBy { it.first }
}

/** How the sheet draws app icons and widget previews; the default is a letter tile (shots), [WidgetCatalog.art] the real one. */
class WidgetArt(
    val icon: @Composable (pkg: String, app: String, modifier: Modifier) -> Unit = { _, app, m -> LetterIcon(app, m) },
    val preview: @Composable (WidgetChoice, Modifier) -> Unit = { c, m -> Box(m.background(SK.bg), Alignment.Center) { LetterIcon(c.app, Modifier.size(36.dp)) } },
)

@Composable
private fun LetterIcon(app: String, modifier: Modifier) =
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(SK.line), Alignment.Center) { T(app.take(1), 12.sp, 800, SK.text, maxLines = 1) }

/**
 * "위젯 고르기" (mockup settings-r10/widget-picker.html): search, 추천 (installed apps only), then every app with widgets,
 * folded (tap the name to open its previews). Only widgets whose minimum fits [slots] are listed (operator 11-15).
 */
@Composable
fun WidgetPickerSheet(all: List<WidgetChoice>, slots: List<WidgetFit.Size>, art: WidgetArt, onDismiss: () -> Unit, onPick: (WidgetChoice) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val fit = remember(all, slots) { all.filter { WidgetFit.fits(it.min, slots) } }
    val list = WidgetList.search(fit, q)
    Sheet("위젯 고르기", onDismiss) {
        Field(q, { q = it }, "앱이나 위젯 이름")
        val recs = WidgetList.recommended(list)
        if (recs.isNotEmpty()) {
            Label("추천")
            Tiles(recs, art, named = true, onPick)
        }
        val groups = WidgetList.groups(list)
        if (groups.isNotEmpty()) {
            Label("앱별")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (app, ws) ->
                    val pkg = ws.first().pkg
                    val shown = q.isNotBlank() || pkg in open
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SK.surface2)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = if (pkg in open) open - pkg else open + pkg }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            art.icon(pkg, app, Modifier.size(28.dp))
                            T(app, 15.sp, 600, SK.text, Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
                            T("위젯 ${ws.size}개", 13.sp, 500, SK.muted, Modifier.padding(horizontal = 8.dp), maxLines = 1)
                            Icon(if (shown) Ic.UP else Ic.DOWN, SK.muted, 20.dp)
                        }
                        if (shown) Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) { Tiles(ws, art, named = false, onPick) }
                    }
                }
            }
        }
        if (list.isEmpty()) T("찾는 위젯이 없어요. 앱 이름으로 찾아보세요.", 14.sp, 400, SK.muted, Modifier.padding(top = 16.dp, start = 4.dp))
    }
}

@Composable
private fun Label(text: String) = T(text, 13.sp, 600, SK.muted, Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))

/** Two tiles a row: preview, then (with [named]) the app icon and "앱 · 위젯", and the size in cells. */
@Composable
private fun Tiles(list: List<WidgetChoice>, art: WidgetArt, named: Boolean, onPick: (WidgetChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // keyed: a tile's remembered preview must stay with its widget when a search shifts the list
                row.forEach { c -> key(c.key) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (named) SK.surface2 else SK.surface).clickable(onClickLabel = "${c.app} ${c.name}") { onPick(c) }.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        art.preview(c, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(11.dp)))
                        Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (named) art.icon(c.pkg, c.app, Modifier.size(18.dp).padding(end = 0.dp))
                            T(if (named) "${c.app} · ${c.name}" else c.name, 13.sp, 500, SK.text, Modifier.weight(1f).padding(start = if (named) 6.dp else 0.dp), maxLines = 2, lineHeight = 17.sp)
                            if (c.cells != null) T(c.cells, 12.sp, 500, SK.muted, Modifier.padding(start = 4.dp), maxLines = 1)
                        }
                    }
                } }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** The phone's widget providers as [WidgetChoice]s, and the art that draws them (thin: Robolectric has no real providers). */
class WidgetCatalog(val choices: List<WidgetChoice>, val infos: Map<String, AppWidgetProviderInfo>, val art: WidgetArt) {
    companion object {
        fun load(ctx: Context): WidgetCatalog {
            val pm = ctx.packageManager
            val dp = ctx.resources.displayMetrics.density
            val infos = AppWidgetManager.getInstance(ctx).installedProviders
            val apps = HashMap<String, String>()
            fun app(pkg: String) = apps.getOrPut(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
            val choices = infos.map { i ->
                val rw = i.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
                val rh = i.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
                val min = WidgetFit.Size(WidgetFit.min(i.minWidth / dp, i.minResizeWidth / dp, rw), WidgetFit.min(i.minHeight / dp, i.minResizeHeight / dp, rh))
                // launcher cells: the provider's own count (12+), else the classic (dp + 30) / 70
                val cw = i.targetCellWidth.takeIf { it > 0 } ?: ((i.minWidth / dp + 30) / 70).toInt().coerceAtLeast(1)
                val ch = i.targetCellHeight.takeIf { it > 0 } ?: ((i.minHeight / dp + 30) / 70).toInt().coerceAtLeast(1)
                WidgetChoice(i.provider.flattenToString(), i.provider.packageName, app(i.provider.packageName), i.loadLabel(pm), min, "$cw×$ch")
            }
            val byKey = infos.associateBy { it.provider.flattenToString() }
            return WidgetCatalog(choices, byKey, art(ctx, byKey))
        }

        private fun art(ctx: Context, infos: Map<String, AppWidgetProviderInfo>) = WidgetArt(
            icon = { pkg, app, m ->
                val bmp = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                if (bmp != null) Image(bmp, app, m) else LetterIcon(app, m)
            },
            preview = { c, m -> WidgetPreview(ctx, infos[c.key], c, m) },
        )

        /** getWidgetPreview (15+), the provider's preview layout, its preview image, else its app icon. */
        @Composable
        private fun WidgetPreview(ctx: Context, info: AppWidgetProviderInfo?, c: WidgetChoice, m: Modifier) {
            val views = remember(c.key) {
                info ?: return@remember null
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 35) AppWidgetManager.getInstance(ctx).getWidgetPreview(info.provider, info.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                    else null
                }.getOrNull() ?: info.previewLayout.takeIf { it != 0 }?.let { RemoteViews(info.provider.packageName, it) }
            }
            val image = remember(c.key) {
                if (views != null || info == null) null
                else runCatching { info.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi)?.toBitmap()?.asImageBitmap() }.getOrNull()
            }
            Box(m.background(SK.bg), Alignment.Center) {
                when {
                    views != null -> ScaledRemoteViews(views, c.min)
                    image != null -> Image(image, c.name, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                    else -> Box(Modifier.size(40.dp)) { CatalogIcon(ctx, c) }
                }
            }
        }

        @Composable
        private fun CatalogIcon(ctx: Context, c: WidgetChoice) {
            val bmp = remember(c.pkg) { runCatching { ctx.packageManager.getApplicationIcon(c.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
            if (bmp != null) Image(bmp, c.app, Modifier.fillMaxSize()) else LetterIcon(c.app, Modifier.fillMaxSize())
        }

        /** The preview layout laid out at the widget's own size (never squeezed) and scaled into the tile. */
        @Composable
        private fun ScaledRemoteViews(views: RemoteViews, size: WidgetFit.Size) = BoxWithConstraints(Modifier.fillMaxSize(), Alignment.Center) {
            val w = size.w.coerceAtLeast(40f)
            val h = size.h.coerceAtLeast(40f)
            val s = minOf(maxWidth.value / w, maxHeight.value / h)
            AndroidView(
                factory = { ctx -> FrameLayout(ctx) },
                modifier = Modifier.requiredSize(w.dp, h.dp).graphicsLayer(scaleX = s, scaleY = s),
                // a reused view gets the new widget's layout
                update = { f -> if (f.tag !== views) { f.tag = views; f.removeAllViews(); runCatching { f.addView(views.apply(f.context, f)) } } },
            )
        }
    }
}

/** A placed widget's long-press menu (plan-r13 B10): 위젯 설정 (only when it can be set again) · 바꾸기 · 보이는 모습… · 빼기. */
@Composable
fun WidgetMenuSheet(
    label: String?, configurable: Boolean, onConfigure: () -> Unit, onChange: () -> Unit, onLook: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit,
) = Sheet(label ?: "폰 위젯", onDismiss) {
    Group {
        if (configurable) item { NavRow("위젯 설정", onConfigure, subtitle = "위젯 앱이 가진 설정 화면") }
        item { NavRow("바꾸기", onChange, subtitle = "다른 위젯") }
        item { NavRow("보이는 모습…", onLook, subtitle = "투명도 · 모서리 · 밤에 숨기기") }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onRemove).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T("빼기", 16.sp, 600, SK.danger)
            }
        }
    }
}

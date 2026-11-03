package kr.dot.parkingfloor

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget, round-6 variant 2: a state tag above stencil numerals ("추정 · 눌러서 확정" yellow,
 * "확정" green), optional light panel with adjustable opacity, sizes 2x2 / 3x2 / 4x2 / 4x3.
 * Redrawn only when a park changes or the launcher resizes it.
 */
class ParkWidget : AppWidgetProvider() {

    enum class Size { S22, S32, S42, S43 }

    companion object {
        private const val ACTION = "kr.dot.parkingfloor.WIDGET"
        private const val EXTRA_OP = "op"
        private const val INK_WHITE = 0xFFFFFFFF.toInt()
        private const val INK_DARK = 0xFF16191C.toInt()
        private const val DARK_FROM_PANEL = 45

        fun classify(widthDp: Float, heightDp: Float): Size = when {
            widthDp < 170 -> Size.S22
            heightDp >= 230 -> Size.S43
            widthDp >= 240 -> Size.S42
            else -> Size.S32
        }

        /** Never throws: it runs inside the drive service process. */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetIds(ComponentName(context, ParkWidget::class.java)).forEach { draw(context, manager, it) }
            } catch (_: Throwable) {
            }
        }

        private fun sizeOf(manager: AppWidgetManager, id: Int): SizeF {
            val options: Bundle = manager.getAppWidgetOptions(id)
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)?.firstOrNull()?.let { return it }
            return SizeF(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180).toFloat(),
            )
        }

        private fun darkInk(context: Context): Boolean {
            if (Settings.panel(context) >= DARK_FROM_PANEL) return true
            return when (Settings.ink(context)) {
                Settings.INK_DARK -> true
                Settings.INK_LIGHT -> false
                else -> try {
                    val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    colors != null && colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
                } catch (_: Exception) {
                    false
                }
            }
        }

        fun ago(atMs: Long): String {
            val minutes = ((System.currentTimeMillis() - atMs) / 60_000).coerceAtLeast(0)
            return when {
                minutes < 2 -> "방금"
                minutes < 60 -> "${minutes}분 전"
                minutes < 24 * 60 -> "${minutes / 60}시간 전"
                else -> "${minutes / (24 * 60)}일 전"
            }
        }

        fun hhmm(atMs: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(atMs))

        fun shareText(park: Park): String {
            val floor = park.floor?.let(FloorMath::labelOf) ?: "층 모름"
            val pillar = if (park.pillar.isNotEmpty()) " ${park.pillar}" else ""
            val place = park.placeName ?: "주차장"
            return "차 위치: $place $floor$pillar (${hhmm(park.atMs)} 주차)"
        }

        private fun broadcast(context: Context, op: String): PendingIntent = PendingIntent.getBroadcast(
            context, op.hashCode(),
            Intent(context, ParkWidget::class.java).setAction("$ACTION.$op").putExtra(EXTRA_OP, op),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun activity(context: Context, code: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
            context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun share(context: Context, park: Park): Intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(park)), "차 위치 보내기",
        )

        private fun draw(context: Context, manager: AppWidgetManager, id: Int) {
            try {
                manager.updateAppWidget(id, build(context, sizeOf(manager, id)))
            } catch (_: Throwable) {
            }
        }

        fun build(context: Context, size: SizeF): RemoteViews {
            val kind = classify(size.width, size.height)
            val dark = darkInk(context)
            val ink = if (dark) INK_DARK else INK_WHITE
            val inverse = if (dark) INK_WHITE else INK_DARK
            val tint = if (dark) "dark" else "light"
            val layout = when {
                kind == Size.S42 -> if (dark) R.layout.w_wide_dark else R.layout.w_wide_light
                dark -> R.layout.w_main_dark
                else -> R.layout.w_main_light
            }
            val v = RemoteViews(context.packageName, layout)
            val density = context.resources.displayMetrics.density
            fun show(viewId: Int, on: Boolean) = v.setViewVisibility(viewId, if (on) View.VISIBLE else View.GONE)
            fun res(name: String) = context.resources.getIdentifier(name, "drawable", context.packageName)

            val panel = Settings.panel(context)
            show(R.id.panel, panel > 0)
            v.setInt(R.id.panel, "setImageAlpha", panel * 255 / 100)
            v.setOnClickPendingIntent(R.id.root, activity(context, 10, Intent(context, MainActivity::class.java)))

            /** style: "line", "fill" or "yellow"; icon: "ok", "edit", "cam", "share" or null; label may be empty. */
            fun button(viewId: Int, label: String, style: String, icon: String?, click: PendingIntent) {
                show(viewId, true)
                v.setTextViewText(viewId, label)
                val (bg, fg, iconInk) = when (style) {
                    "fill" -> Triple(res("wb_fill_$tint"), inverse, if (dark) "light" else "dark")
                    "yellow" -> Triple(R.drawable.wb_yellow, INK_DARK, "dark")
                    else -> Triple(res("wb_line_$tint"), ink, tint)
                }
                v.setInt(viewId, "setBackgroundResource", bg)
                v.setTextColor(viewId, fg)
                v.setTextViewCompoundDrawablesRelative(viewId, icon?.let { res("ic_${it}_$iconInk") } ?: 0, 0, 0, 0)
                v.setOnClickPendingIntent(viewId, click)
            }

            val markDp = when (kind) {
                Size.S22 -> size.height * 0.30f
                Size.S32 -> size.height * 0.40f
                Size.S42 -> size.height * 0.46f
                Size.S43 -> size.height * 0.30f
            }.coerceIn(30f, 110f)
            val markPx = (markDp * density).toInt()
            val iconsOnly = kind == Size.S22
            show(R.id.row2, false)

            val park = ParkStore.latest(context)
            if (park == null) {
                v.setTextViewText(R.id.place, "주차 기록 없음")
                v.setTextViewText(R.id.time, "")
                show(R.id.tag, false)
                v.setImageViewBitmap(R.id.mark, PillarMark.render(context, "P", false, null, ink, markPx))
                v.setTextViewText(R.id.pz, "")
                v.setTextViewText(R.id.line1, "차와 연결하면")
                v.setTextViewText(R.id.line2, "자동으로 기록돼요")
                button(R.id.b1, "설정", "fill", null, activity(context, 10, Intent(context, MainActivity::class.java)))
                show(R.id.b2, false)
                show(R.id.b3, false)
                return v
            }

            val place = park.placeName ?: if (park.lat == null) "장소 모름" else "장소 찾는 중"
            v.setTextViewText(R.id.place, place)
            val label = park.floor?.let(FloorMath::labelOf) ?: "?"
            val pick = activity(context, 11, Intent(context, FloorPickActivity::class.java))
            if (!park.confirmed) {
                // the widget is not redrawn on a timer, so a clock time stays true where "10분 전" would not
                v.setTextViewText(R.id.time, hhmm(park.atMs))
                show(R.id.tag, true)
                v.setTextViewText(R.id.tag, if (park.floor == null) "층 모름 · 직접 입력" else "추정 · 눌러서 확정")
                v.setInt(R.id.tag, "setBackgroundResource", R.drawable.wtag_est)
                v.setImageViewBitmap(R.id.mark, PillarMark.render(context, label, true, null, ink, markPx))
                v.setTextViewText(R.id.pz, "")
                val alt = park.alt?.let(FloorMath::labelOf)
                v.setTextViewText(R.id.line1, alt?.let { "아니면 $it" } ?: "")
                v.setTextViewText(R.id.line2, if (iconsOnly) "" else "맞으면 확정")
                if (park.floor == null) {
                    button(R.id.b1, "직접 입력", "yellow", "edit", pick)
                    show(R.id.b2, false)
                    show(R.id.b3, false)
                } else {
                    button(R.id.b1, if (iconsOnly) "" else "확정", "yellow", "ok", broadcast(context, "ok"))
                    button(R.id.b2, if (iconsOnly) "" else if (kind == Size.S32) "직접" else "직접 입력", "line", "edit", pick)
                    if (alt != null) {
                        button(R.id.b3, if (iconsOnly || kind == Size.S32) alt else "아니면 $alt", "line", null, broadcast(context, "alt"))
                    } else show(R.id.b3, false)
                }
            } else {
                v.setTextViewText(R.id.time, hhmm(park.atMs))
                show(R.id.tag, true)
                v.setTextViewText(R.id.tag, "확정")
                v.setInt(R.id.tag, "setBackgroundResource", R.drawable.wtag_ok)
                val band = park.color.takeIf { it != 0 }
                v.setImageViewBitmap(R.id.mark, PillarMark.render(context, label, false, band, ink, markPx))
                v.setTextViewText(R.id.pz, park.pillar)
                v.setTextViewText(R.id.line1, "엘리베이터에서 $label")
                v.setTextViewText(R.id.line2, if (iconsOnly) "" else if (park.pillar.isEmpty()) "기둥 번호 아직" else "${hhmm(park.atMs)} 주차")
                button(R.id.b1, if (iconsOnly) "" else "기둥 사진", "line", "cam",
                    activity(context, 12, Intent(context, PillarActivity::class.java)))
                button(R.id.b2, if (iconsOnly) "" else if (kind == Size.S32) "위치" else "위치 입력", "line", "edit",
                    activity(context, 13, Intent(context, PillarActivity::class.java).putExtra(PillarActivity.EXTRA_TYPE, true)))
                button(R.id.b3, if (iconsOnly) "" else "공유", "fill", "share", activity(context, 14, share(context, park)))
            }

            if (kind == Size.S43) {
                ParkStore.previousAtSamePlace(context)?.let { prev ->
                    show(R.id.row2, true)
                    val prevLabel = FloorMath.labelOf(prev.floor ?: 0)
                    v.setImageViewBitmap(R.id.mark2, PillarMark.render(context, prevLabel, false, prev.color.takeIf { it != 0 }, ink, (34 * density).toInt()))
                    v.setTextViewText(R.id.r2a, "지난번 이곳 · $prevLabel ${prev.pillar}".trim())
                    v.setTextViewText(R.id.r2b, SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(prev.atMs)))
                }
            }
            return v
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { draw(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        draw(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            val park = ParkStore.latest(context) ?: return
            when (intent.getStringExtra(EXTRA_OP)) {
                "ok" -> park.est?.let { ParkStore.confirm(context, park.id, it) }
                "alt" -> park.alt?.let { ParkStore.confirm(context, park.id, it) }
                else -> return
            }
            refresh(context)
        } catch (_: Throwable) {
        }
    }
}

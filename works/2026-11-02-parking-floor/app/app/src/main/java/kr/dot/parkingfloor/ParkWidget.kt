package kr.dot.parkingfloor

import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Design prototype of the parking widget (round 5 "pillar base"). Fully transparent, one layout per
 * ink colour, redrawn for the size the launcher reports. State is demo data the app or a measurement
 * park mark sets; nothing here measures.
 */
class ParkWidget : AppWidgetProvider() {

    enum class Size { TINY, WIDE2, WIDE4, SQUARE, BIG, XL }

    companion object {
        private const val ACTION = "kr.dot.parkingfloor.WIDGET"
        private const val EXTRA_OP = "op"
        const val INK_AUTO = "auto"
        const val INK_LIGHT = "light"
        const val INK_DARK = "dark"
        const val BLUE = 0xFF2F6FD0.toInt()

        fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetIds(ComponentName(context, ParkWidget::class.java)).forEach { update(context, manager, it) }
            } catch (_: Throwable) {
                // same process as the measurement service; see update()
            }
        }

        fun setEstimate(context: Context, floor: String, alt: String, place: String? = null) {
            prefs(context).edit().putString("floor", floor).putString("alt", alt).putBoolean("est", true)
                .putString("pillar", "").putLong("at", System.currentTimeMillis())
                .apply { if (!place.isNullOrBlank()) putString("place", place) }.apply()
            refresh(context)
        }

        fun setParked(context: Context, floor: String, place: String?) {
            prefs(context).edit().putString("floor", floor).putBoolean("est", false).putString("pillar", "")
                .putLong("at", System.currentTimeMillis()).putInt("color", 0)
                .apply { if (!place.isNullOrBlank()) putString("place", place) }.apply()
            refresh(context)
        }

        fun setLearned(context: Context, pillar: String, color: Int) {
            prefs(context).edit().putBoolean("est", false).putString("pillar", pillar).putInt("color", color).apply()
            refresh(context)
        }

        fun setOption(context: Context, key: String, value: String) {
            prefs(context).edit().putString(key, value).apply()
            refresh(context)
        }

        fun setPanel(context: Context, on: Boolean) {
            prefs(context).edit().putBoolean("panel", on).apply()
            refresh(context)
        }

        fun classify(widthDp: Float, heightDp: Float): Size = when {
            heightDp < 100 && widthDp < 100 -> Size.TINY
            heightDp < 100 -> if (widthDp < 230) Size.WIDE2 else Size.WIDE4
            widthDp < 170 -> Size.SQUARE
            heightDp < 230 -> Size.BIG
            else -> Size.XL
        }

        private fun sizeOf(manager: AppWidgetManager, id: Int): SizeF {
            val options: Bundle = manager.getAppWidgetOptions(id)
            val sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            sizes?.firstOrNull()?.let { return it }
            val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
            return SizeF(w.toFloat(), h.toFloat())
        }

        private fun darkInk(context: Context): Boolean = when (prefs(context).getString("ink", INK_AUTO)) {
            INK_DARK -> true
            INK_LIGHT -> false
            else -> try {
                val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                colors != null && colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            } catch (_: Exception) {
                false
            }
        }

        private fun ago(at: Long): String {
            val minutes = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
            return when {
                minutes < 2 -> "방금"
                minutes < 60 -> "${minutes}분 전"
                minutes < 24 * 60 -> "${minutes / 60}시간 전"
                else -> "${minutes / (24 * 60)}일 전"
            }
        }

        private fun op(context: Context, op: String): PendingIntent = PendingIntent.getBroadcast(
            context, op.hashCode(),
            Intent(context, ParkWidget::class.java).setAction("$ACTION.$op").putExtra(EXTRA_OP, op),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun share(context: Context, text: String): PendingIntent = PendingIntent.getActivity(
            context, 2,
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "주차 위치 보내기")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Never throws: the widget shares the process with a running measurement. */
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            try {
                draw(context, manager, id)
            } catch (_: Throwable) {
                // a broken prototype frame is better than a killed measurement session
            }
        }

        private fun draw(context: Context, manager: AppWidgetManager, id: Int) {
            val p = prefs(context)
            val floor = p.getString("floor", "B2") ?: "B2"
            val alt = p.getString("alt", "B3") ?: "B3"
            val est = p.getBoolean("est", true)
            val pillar = p.getString("pillar", "") ?: ""
            val color = p.getInt("color", 0).takeIf { it != 0 }
            val place = p.getString("place", "스타필드 수원") ?: "스타필드 수원"
            val at = p.getLong("at", System.currentTimeMillis())
            val dark = darkInk(context)
            val ink = if (dark) PillarMark.HAZARD_BLACK else 0xFFFFFFFF.toInt()
            val size = sizeOf(manager, id)
            val kind = classify(size.width, size.height)
            val density = context.resources.displayMetrics.density

            val views = RemoteViews(context.packageName, if (dark) R.layout.widget_dark else R.layout.widget_light)
            views.setInt(
                R.id.root, "setBackgroundResource",
                if (p.getBoolean("panel", false)) (if (dark) R.drawable.wpanel_light else R.drawable.wpanel_dark) else 0,
            )
            views.setOnClickPendingIntent(R.id.root, openApp(context))

            val markDp = when (kind) {
                Size.TINY -> minOf(size.width, size.height) - 14f
                Size.WIDE2, Size.WIDE4 -> size.height - 16f
                Size.SQUARE -> size.height * 0.42f
                Size.BIG -> size.height * 0.48f
                Size.XL -> (size.height - 150f) / 2f
            }.coerceIn(28f, 130f)
            views.setImageViewBitmap(R.id.mark, PillarMark.render(context, floor, est, color, ink, (markDp * density).toInt()))

            val hhmm = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(at))
            val shareText = "차 위치: $place $floor${if (pillar.isNotEmpty()) " $pillar" else ""} ($hhmm 주차)"
            val header = if (kind == Size.XL) "우리 집 차 2대" else "$place · ${if (est) "방금 주차" else ago(at)}"
            views.setTextViewText(R.id.header, header)
            if (est) {
                views.setTextViewText(R.id.pillar, "쯤")
                views.setTextViewText(R.id.line1, "아니면 $alt")
                views.setTextViewText(R.id.line2, "한 번만 알려 주세요")
                views.setTextViewText(R.id.btnA, alt)
                views.setTextViewText(R.id.btnB, "맞아요")
                views.setOnClickPendingIntent(R.id.btnA, op(context, "alt"))
                views.setOnClickPendingIntent(R.id.btnB, op(context, "ok"))
                views.setTextViewText(R.id.side, "맞아요")
                views.setOnClickPendingIntent(R.id.side, op(context, "ok"))
            } else {
                views.setTextViewText(R.id.pillar, pillar.ifEmpty { if (kind == Size.WIDE2) hhmm else "" })
                views.setTextViewText(R.id.line1, "엘리베이터에서 $floor")
                views.setTextViewText(R.id.line2, "나 · $hhmm 주차")
                views.setTextViewText(R.id.btnA, if (pillar.isEmpty()) "기둥 번호" else "다시 추정")
                views.setOnClickPendingIntent(R.id.btnA, op(context, if (pillar.isEmpty()) "scan" else "reset"))
                views.setTextViewText(R.id.btnB, "보내기")
                views.setOnClickPendingIntent(R.id.btnB, share(context, shareText))
                views.setTextViewText(R.id.side, "보내기")
                views.setOnClickPendingIntent(R.id.side, share(context, shareText))
            }

            fun show(viewId: Int, on: Boolean) = views.setViewVisibility(viewId, if (on) View.VISIBLE else View.GONE)
            show(R.id.header, kind == Size.SQUARE || kind == Size.BIG || kind == Size.XL)
            show(R.id.info, kind != Size.TINY)
            show(R.id.pillar, kind != Size.TINY)
            show(R.id.line1, kind == Size.WIDE2 && est || kind == Size.WIDE4 || kind == Size.BIG || kind == Size.XL)
            show(R.id.line2, kind == Size.WIDE4 && !est || kind == Size.BIG || kind == Size.XL)
            show(R.id.side, kind == Size.WIDE4)
            show(R.id.buttons, kind == Size.SQUARE || kind == Size.BIG || kind == Size.XL)
            show(R.id.row2, kind == Size.XL)
            if (kind == Size.XL) {
                views.setImageViewBitmap(R.id.mark2, PillarMark.render(context, "B4", false, null, ink, (markDp * density).toInt()))
                views.setTextViewText(R.id.car2a, "모닝 · 가-07")
                views.setTextViewText(R.id.car2b, "엄마 · 어제 19:05 · 아주대병원")
                views.setTextViewText(R.id.line2, "쏘렌토 · 나 · $hhmm")
            }
            manager.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            handle(context, intent)
        } catch (_: Throwable) {
            // runs on the main thread of the measurement process
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val p = prefs(context)
        when (intent.getStringExtra(EXTRA_OP)) {
            "ok" -> p.edit().putBoolean("est", false).apply()
            "alt" -> p.edit().putString("floor", p.getString("alt", "B3")).putBoolean("est", false).apply()
            "scan" -> p.edit().putString("pillar", "C-12").putInt("color", PillarMark.HAZARD_YELLOW).apply()
            "reset" -> p.edit().putString("floor", "B2").putString("alt", "B3").putBoolean("est", true)
                .putString("pillar", "").putInt("color", 0).putLong("at", System.currentTimeMillis()).apply()
            else -> return
        }
        refresh(context)
    }
}

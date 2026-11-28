package kr.dot.catchpet.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.Notification
import kr.dot.catchpet.MainActivity

/** "감자 털뭉치 펫이 다 그려졌어요": one quiet notification when a bought sheet arrives (operator 11-15 review). */
object Done {
    fun notify(ctx: Context, name: String, style: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(NotificationChannel("drawn", "펫 그림 완성", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = when (style) {
            "both" -> "$name 털뭉치·피규어가 다 됐어요!"   // mockup v5.2 screen 5
            "figure" -> "$name 피규어 펫이 다 그려졌어요"
            else -> "$name 털뭉치 펫이 다 그려졌어요"
        }
        nm.notify(name.hashCode(), Notification.Builder(ctx, "drawn")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText("도감에서 펫으로 쓸 수 있어요")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}

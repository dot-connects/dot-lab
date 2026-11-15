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
        val look = if (style == "figure") "피규어" else "털뭉치"
        nm.notify(name.hashCode(), Notification.Builder(ctx, "drawn")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("$name $look 펫이 다 그려졌어요")
            .setContentText("도감에서 펫으로 쓸 수 있어요")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}

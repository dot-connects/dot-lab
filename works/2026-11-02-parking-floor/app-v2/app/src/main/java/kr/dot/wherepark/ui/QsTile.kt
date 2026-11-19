package kr.dot.wherepark.ui

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick settings tile "어디댔어 · 눌러서 말하기" (mockup section 9): opens the listen sheet over whatever is on screen. */
class QsTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "눌러서 말하기"
            updateTile()
        }
    }

    override fun onClick() {
        val i = Intent(this, ListenActivity::class.java).putExtra("from", "tile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // API 34+: the PendingIntent overload (the Intent one is deprecated and throws for apps targeting 34+)
        val pi = PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (isLocked) unlockAndRun { startActivityAndCollapse(pi) } else startActivityAndCollapse(pi)
    }
}

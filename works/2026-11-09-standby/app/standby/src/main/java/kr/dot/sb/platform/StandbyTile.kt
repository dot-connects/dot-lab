package kr.dot.sb.platform

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService

/** Quick-settings tile: start StandBy now, charger or not (the activity leaves on its own at low battery). */
class StandbyTile : TileService() {
    override fun onClick() {
        val open = PendingIntent.getActivity(
            this, 21,
            Intent(this, StandbyActivity::class.java).putExtra(StandbyActivity.EXTRA_TILE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(open) // the activity is showWhenLocked, so no unlock is needed
    }
}

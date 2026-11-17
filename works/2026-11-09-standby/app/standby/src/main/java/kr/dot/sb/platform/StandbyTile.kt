package kr.dot.sb.platform

import android.app.PendingIntent
import android.service.quicksettings.TileService

/** Quick-settings tile: start StandBy now, charger or not (the activity leaves on its own at low battery). */
class StandbyTile : TileService() {
    override fun onClick() {
        if (StandbyActivity.refused(this)) return // plan-r15 C9: say why instead of opening and closing at once
        val open = PendingIntent.getActivity(
            this, 21, StandbyActivity.intentNow(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(open) // the activity is showWhenLocked, so no unlock is needed
    }
}

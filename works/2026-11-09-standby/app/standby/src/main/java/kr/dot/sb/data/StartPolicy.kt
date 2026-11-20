package kr.dot.sb.data

/**
 * plan-r16 T1: when StandBy starts by itself. Only two triggers: a genuine plug-in (once per plug session) and the
 * screen going off after the phone was unlocked. A system wake (notification, lock-screen timeout) never starts it.
 * [used] = unlocked since the last screen-off (memory only: lost = no start, the safe side); [plugHandled] = this plug
 * session already had its start decision (persisted).
 */
data class StartState(val used: Boolean = false, val plugHandled: Boolean = false)

enum class StartEvent { USER_PRESENT, SCREEN_OFF, POWER_CONNECTED, UNPLUG_CONFIRMED, JOB }

object StartPolicy {
    /** An unplug counts only after this long with no power source (a wireless pad flaps for a moment). */
    const val FLAP_MS = 3_000L

    /** The new state and whether to try a launch (the charger rule and posture gate still decide after this). */
    fun next(s: StartState, e: StartEvent, sessionAlive: Boolean): Pair<StartState, Boolean> = when (e) {
        StartEvent.USER_PRESENT -> s.copy(used = true) to false
        StartEvent.SCREEN_OFF -> s.copy(used = false) to (s.used && !sessionAlive)
        StartEvent.POWER_CONNECTED, StartEvent.JOB -> s.copy(plugHandled = true) to (!s.plugHandled && !sessionAlive)
        StartEvent.UNPLUG_CONFIRMED -> s.copy(plugHandled = false) to false
    }
}
